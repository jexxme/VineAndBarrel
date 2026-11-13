package jexxme.vineandbarrel.tipsy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jexxme.vineandbarrel.advancement.BlackoutTrigger;
import jexxme.vineandbarrel.advancement.VBAdvancements;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * One drink too many at the top Tipsy level: the player blacks out, and wakes up somewhere ({@link BlackoutEnding}).
 * <p>
 * About 8.5 seconds ({@link #TICKS}): the eyes fall shut and the body falls on its face, the way it has room to
 * ({@link BlackoutSpots#fallYaw}; others see stars circling above the head); a few seconds of black in which nobody
 * can hurt the player (no mob, no player, no fall: {@link TipsyEvents#cancelsIncomingDamage}) and Tipsy pauses; at
 * {@link #WAKE_AT} the ending is drawn and the player is put there, in its pose, with a souvenir in the pockets (and
 * now and then a pumpkin on the head); the eyes open slowly; at {@link #GET_UP} they get out of the bed (off the ground
 * from {@link #GROUND_GET_UP}, slowly, up onto hands and toes and from there onto the feet), and at the end the
 * ending's line shows.
 * They wake hungry, back at Tipsy I. Nothing is lost, no spot is unsafe, there is no time skip (a bed is lain
 * in for less than vanilla's 100 ticks of deep sleep) and other players aren't affected. It can be switched off.
 * <p>
 * The draw prefers endings the player hasn't had yet and never repeats the last one when there is another; what was
 * seen is kept in {@link TipsyRegistry#BLACKOUT_MEMORY} (saved, kept through death). The clients follow the synced
 * {@link TipsyRegistry#BLACKOUT} (ticks left) and {@link TipsyRegistry#BLACKOUT_POSE}.
 */
public final class Blackout
{
	/** The whole blackout. */
	public static final int TICKS = 170;
	/** The eyes fall shut over this long; the body falls on its face in the first {@link #TIP} ticks of it (then the thud). */
	public static final int FADE_OUT = 25;
	public static final int TIP = 10;
	/** At this many ticks left, while the screen is black, the ending is drawn and the player wakes up there. */
	public static final int WAKE_AT = 100;
	/** The eyes start to open here and are fully open here. */
	public static final int EYES_OPEN = 70;
	public static final int EYES_UP = 40;
	/** Getting up out of the bed (or a boat) takes this long at the end. */
	public static final int GET_UP = 10;
	/**
	 * Getting up off the ground takes longer, from here to the end: the head comes up, the arms push the body off the
	 * ground onto all fours, the legs straighten, and up with a rock onto the heels ({@link BlackoutPose#body}). The eyes
	 * are fully open by then ({@link #EYES_UP}).
	 */
	public static final int GROUND_GET_UP = 38;
	/** The villager whose bed it was shakes its head here (vanilla's head shake lasts 40 ticks). */
	public static final int HEAD_SHAKE_AT = 50;

	/** Endings not seen yet weigh this much more in the draw. */
	public static final int UNSEEN_BONUS = 3;
	public static final int PUMPKIN_ONE_IN = 6;
	public static final int HUNGER_LOST = 6;
	/** Lying, the player's own look is level (the head's pose has the view); they stand up looking ahead. */
	static final float LIE_PITCH = 0.0F;
	/**
	 * A player who blacks out on the move slides on about this many times the last tick's movement before stopping (the
	 * ground's friction leaves a little under half of it each tick), with some to spare.
	 */
	static final double SLIDE = 2.5;

	private Blackout() {
	}

	// ------------------------------------------------------------------ saved state

	/** What a player remembers of their blackouts: which endings they had, the last one and its line. */
	public record Memory(List<String> seen, String last, String lastLine) {
		public static final Memory NONE = new Memory(List.of(), "", "");
		public static final MapCodec<Memory> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			Codec.STRING.listOf().optionalFieldOf("seen", List.of()).forGetter(Memory::seen),
			Codec.STRING.optionalFieldOf("last", "").forGetter(Memory::last),
			Codec.STRING.optionalFieldOf("last_line", "").forGetter(Memory::lastLine)
		).apply(inst, Memory::new));

		public boolean hasSeen(BlackoutEnding ending) {
			return seen.contains(ending.id());
		}

		public @Nullable BlackoutEnding lastEnding() {
			return BlackoutEnding.byId(last).orElse(null);
		}

		Memory after(BlackoutEnding ending, String line) {
			List<String> next = new ArrayList<>(seen);
			if (!next.contains(ending.id())) {
				next.add(ending.id());
			}
			return new Memory(List.copyOf(next), ending.id(), line);
		}
	}

	/** The blackout in progress after waking: its ending, the line to show, and the animal or villager that stares. */
	public record Scene(String ending, String line, Optional<UUID> friend, Optional<String> friendType) {
		public static final MapCodec<Scene> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			Codec.STRING.fieldOf("ending").forGetter(Scene::ending),
			Codec.STRING.fieldOf("line").forGetter(Scene::line),
			UUIDUtil.CODEC.optionalFieldOf("friend").forGetter(Scene::friend),
			Codec.STRING.optionalFieldOf("friend_type").forGetter(Scene::friendType)
		).apply(inst, Scene::new));

		public BlackoutEnding endingOrHere() {
			return BlackoutEnding.byId(ending).orElse(BlackoutEnding.RIGHT_HERE);
		}
	}

	public static Memory memory(Player player) {
		return TipsyRegistry.BLACKOUT_MEMORY.get(player);
	}

	// ------------------------------------------------------------------ state

	/** {@return whether this player is blacked out right now} */
	public static boolean isOut(Player player) {
		Integer left = TipsyRegistry.BLACKOUT.getExisting(player);
		return left != null && left > 0;
	}

	/** {@return the ticks left of this player's blackout, 0 if they aren't out} */
	public static int ticksLeft(Player player) {
		Integer left = TipsyRegistry.BLACKOUT.getExisting(player);
		return left == null ? 0 : left;
	}

	public static BlackoutEnding.Pose pose(Player player) {
		Integer pose = TipsyRegistry.BLACKOUT_POSE.getExisting(player);
		return BlackoutEnding.Pose.byId(pose == null ? 0 : pose);
	}

	/**
	 * {@return the way this blacked-out player's body lies on the face ({@link TipsyRegistry#BLACKOUT_YAW}, chosen as they
	 * blacked out); for one who is not out, the way they would fall right now}
	 */
	public static float fallYaw(ServerPlayer player) {
		Float yaw = isOut(player) ? TipsyRegistry.BLACKOUT_YAW.getExisting(player) : null;
		return yaw != null ? yaw : BlackoutSpots.fallYaw(player.level(), player.position(), player.getYRot());
	}

	/** {@return where {@code at} comes down on the floor below it (a player in the air lands there), or itself} */
	private static Vec3 floorUnder(ServerPlayer player, Vec3 at) {
		if (player.onGround() && at.distanceToSqr(player.position()) < 1.0E-6) {
			return at;
		}
		BlockHitResult hit = player.level().clip(new ClipContext(at.add(0.0, 0.5, 0.0), at.subtract(0.0, 6.0, 0.0), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
	}

	/** {@return whether one more drink now makes this player black out} */
	public static boolean wouldBlackOut(ServerPlayer player) {
		return TipsyConfig.enabled() && TipsyConfig.blackout() && !isOut(player) && !player.isSpectator()
			&& Tipsy.getLevel(player) >= TipsyConfig.maxLevel();
	}

	public static void start(ServerPlayer player) {
		TipsyRegistry.BLACKOUT.set(player, TICKS);
		TipsyRegistry.BLACKOUT_POSE.set(player, BlackoutEnding.Pose.GROUND.ordinal());
		// The way the body goes down, chosen once for everyone: where it has room, from where it is and from where a run
		// carries it before it stops
		Vec3 feet = floorUnder(player, player.position());
		Vec3 stop = floorUnder(player, player.position().add(player.getKnownMovement().multiply(SLIDE, 0.0, SLIDE)));
		TipsyRegistry.BLACKOUT_YAW.set(player, BlackoutSpots.fallYaw(player.level(), feet, stop, player.getYRot()));
		TipsyRegistry.BLACKOUT_SCENE.remove(player);
		// Can't walk or jump (the client also holds the keys and the mouse still)
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TICKS, 9, false, false, false));
		// Tipsy pauses while out (Tipsy#tick): no swirl behind the closed eyes, no hiccups
		if (TipsyRegistry.HICCUPS.has(player)) {
			TipsyRegistry.HICCUPS.remove(player);
		}
	}

	/** Once a tick on the server, for a blacked-out player. */
	public static void tick(ServerPlayer player) {
		Integer left = TipsyRegistry.BLACKOUT.getExisting(player);
		if (left == null) {
			return;
		}
		int next = left - 1;
		if (next == TICKS - TIP) {
			// The body hits the ground
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
		if (next == WAKE_AT) {
			RandomSource random = player.getRandom();
			wakeUp(player, choose(player, random), random);
		}
		if (next < WAKE_AT && next > 0) {
			watch(player, next);
		}
		if (next == EYES_OPEN) {
			Scene scene = TipsyRegistry.BLACKOUT_SCENE.getExisting(player);
			BlackoutEnding ending = scene == null ? BlackoutEnding.RIGHT_HERE : scene.endingOrHere();
			Entity friend = scene == null ? null : scene.friend().map(player.level()::getEntity).orElse(null);
			TipsySounds.wakeUp(player, ending, friend, TipsyLegs.ground(player));
		}
		BlackoutEnding.Pose pose = pose(player);
		if (next == GET_UP) {
			if (player.isSleeping()) {
				player.stopSleepInBed(true, true);
			}
			if (pose != BlackoutEnding.Pose.GROUND) {
				TipsySounds.getUp(player, pose, TipsyLegs.ground(player));
			}
		}
		if (pose == BlackoutEnding.Pose.GROUND) {
			// Off the ground: the hands press into it as the push starts, the toes come down after the rock onto the heels
			if (next == BlackoutPose.PUSH_AT) {
				TipsySounds.getUp(player, pose, TipsyLegs.ground(player));
			} else if (next == BlackoutPose.STEP_AT) {
				TipsySounds.catchStep(player, TipsyLegs.ground(player));
			}
		}
		if (next <= 0) {
			TipsyRegistry.BLACKOUT.remove(player);
			finish(player);
		} else {
			TipsyRegistry.BLACKOUT.set(player, next);
		}
	}

	// ------------------------------------------------------------------ the draw

	/** {@return where this player wakes up: an ending drawn from those possible here (see {@link #drawOrder})} */
	public static BlackoutSpots.Wake choose(ServerPlayer player, RandomSource random) {
		return choose(ending -> BlackoutSpots.find(ending, player, random), memory(player), random,
			() -> BlackoutSpots.find(BlackoutEnding.RIGHT_HERE, player, random));
	}

	/**
	 * Tries the endings in {@link #drawOrder} and takes the first that is possible ({@code find} returns its wake), else
	 * {@code fallback}. Trying them in a weighted random order is the same as drawing by weight among the possible ones,
	 * and only finds the spots it needs.
	 */
	public static BlackoutSpots.Wake choose(Function<BlackoutEnding, BlackoutSpots.@Nullable Wake> find, Memory memory, RandomSource random,
		java.util.function.Supplier<BlackoutSpots.Wake> fallback) {
		for (BlackoutEnding ending : drawOrder(memory, random)) {
			BlackoutSpots.Wake wake = find.apply(ending);
			if (wake != null) {
				return wake;
			}
		}
		return fallback.get();
	}

	/**
	 * {@return the collectable endings in a random order drawn by weight, where endings not seen yet weigh
	 * {@link #UNSEEN_BONUS} times as much; the last ending comes last, so it only repeats when nothing else is possible}
	 */
	public static List<BlackoutEnding> drawOrder(Memory memory, RandomSource random) {
		BlackoutEnding last = memory.lastEnding();
		List<BlackoutEnding> pool = new ArrayList<>(BlackoutEnding.COLLECTABLE);
		pool.remove(last);
		List<BlackoutEnding> order = new ArrayList<>();
		while (!pool.isEmpty()) {
			int total = pool.stream().mapToInt(e -> weight(e, memory)).sum();
			int roll = random.nextInt(total);
			for (BlackoutEnding ending : pool) {
				roll -= weight(ending, memory);
				if (roll < 0) {
					order.add(ending);
					pool.remove(ending);
					break;
				}
			}
		}
		if (last != null && last != BlackoutEnding.RIGHT_HERE) {
			order.add(last);
		}
		return order;
	}

	public static int weight(BlackoutEnding ending, Memory memory) {
		return ending.weight() * (memory.hasSeen(ending) ? 1 : UNSEEN_BONUS);
	}

	/** {@return one of the ending's lines, never the same as {@code last} (when it has another)} */
	public static String line(BlackoutEnding ending, String last, RandomSource random) {
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < ending.lineCount(); i++) {
			lines.add(ending.lineKey(i));
		}
		if (lines.size() > 1) {
			lines.remove(last);
		}
		return lines.get(random.nextInt(lines.size()));
	}

	// ------------------------------------------------------------------ waking up

	/**
	 * Puts the player where {@code wake} says, in its pose, and gives the wake-up: sober, hungover and hungry
	 * ({@link Hangover}; with the hangover switched off Tipsy I and {@link #HUNGER_LOST} food less), and a souvenir.
	 */
	public static void wakeUp(ServerPlayer player, BlackoutSpots.Wake chosen, RandomSource random) {
		ServerLevel level = player.level();
		BlackoutSpots.Wake wake = place(player, chosen);
		BlackoutEnding ending = wake.ending();
		player.fallDistance = 0.0;

		DrinkBuffs.removeAll(player);
		if (TipsyConfig.hangover()) {
			// sober, hungover and hungry (the hangover leaves three drumsticks at most)
			Tipsy.clear(player);
			Hangover.start(player, Hangover.AFTER_BLACKOUT);
		} else {
			Tipsy.setLevel(player, 1);
			var food = player.getFoodData();
			food.setFoodLevel(Math.max(0, food.getFoodLevel() - HUNGER_LOST));
			food.setSaturation(0.0F);
		}

		ItemStack souvenir = ending.souvenir(random);
		if (!souvenir.isEmpty() && !player.getInventory().add(souvenir)) {
			// Pockets full: it lies at your feet
			player.spawnAtLocation(level, souvenir);
		}
		if (random.nextInt(PUMPKIN_ONE_IN) == 0 && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		}

		Memory memory = memory(player);
		String line = line(ending, memory.lastLine(), random);
		Entity friend = wake.friend();
		TipsyRegistry.BLACKOUT_SCENE.set(player, new Scene(ending.id(), line, Optional.ofNullable(friend).map(Entity::getUUID),
			Optional.ofNullable(friend).map(f -> EntityType.getKey(f.getType()).toString())));
		TipsyRegistry.BLACKOUT_MEMORY.set(player, memory.after(ending, line));
	}

	/**
	 * Puts the player where {@code wake} says, in its pose. {@return the wake-up it became: the same, or right here (not
	 * moved at all) when the bed or the boat didn't work out after all (the bed was taken, something floats there now)}
	 */
	private static BlackoutSpots.Wake place(ServerPlayer player, BlackoutSpots.Wake wake) {
		ServerLevel level = player.level();
		Vec3 spot = wake.spot();
		BlackoutSpots.Wake here = new BlackoutSpots.Wake(BlackoutEnding.RIGHT_HERE, player.position(), fallYaw(player), null, null);
		if (wake.ending() == BlackoutEnding.RIGHT_HERE) {
			return stay(player, wake);
		}
		Vec3 from = player.position();
		float yaw = player.getYRot(), pitch = player.getXRot();
		BlackoutEnding.Pose pose = wake.ending().pose();
		switch (pose) {
			case BED -> {
				if (wake.bed() == null || BlackoutSpots.freeBedHead(level, wake.bed()) == null) {
					return stay(player, here);
				}
				player.stopRiding();
				player.teleportTo(level, spot.x, spot.y + 0.6, spot.z, Set.of(), wake.yaw(), 0.0F, true);
				// Lying down is not resting: phantoms keep counting (vanilla resets the counter when you lie down)
				Stat<?> rest = Stats.CUSTOM.get(Stats.TIME_SINCE_REST);
				int sinceRest = player.getStats().getValue(rest);
				// Not startSleepInBed: no sleep status, no spawn point, no message; and only until GET_UP, under deep sleep
				player.startSleeping(wake.bed());
				if (!player.isSleeping()) {
					player.teleportTo(level, from.x, from.y, from.z, Set.of(), yaw, pitch, true);
					return stay(player, here);
				}
				player.getStats().setValue(player, rest, sinceRest);
			}
			case SEAT -> {
				Boat boat = EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.TRIGGERED);
				if (boat == null || !BlackoutSpots.boatSafe(level, spot)) {
					return stay(player, here);
				}
				boat.snapTo(spot.x, spot.y, spot.z, wake.yaw(), 0.0F);
				level.addFreshEntity(boat);
				player.stopRiding();
				player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), wake.yaw(), 0.0F, true);
				if (!player.startRiding(boat, true, true)) {
					// Never left standing on the water: the boat goes, and the player wakes where they fell
					boat.discard();
					player.teleportTo(level, from.x, from.y, from.z, Set.of(), yaw, pitch, true);
					return stay(player, here);
				}
			}
			case GROUND -> {
				player.stopRiding();
				player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), wake.yaw(), LIE_PITCH, true);
				TipsyRegistry.BLACKOUT_YAW.set(player, wake.yaw());
			}
		}
		TipsyRegistry.BLACKOUT_POSE.set(player, pose.ordinal());
		return wake;
	}

	/**
	 * Stays where the body fell: it went down the way it had room to ({@link #fallYaw}, the way every client draws it),
	 * and now the player faces that way as well, while the screen is black: getting up ends facing where the body lies.
	 * Only turned, never moved (a player still sitting in a boat or on a horse stays sitting); not at all in a vehicle or
	 * a bed, where nobody lies on the ground.
	 */
	private static BlackoutSpots.Wake stay(ServerPlayer player, BlackoutSpots.Wake here) {
		if (!player.isPassenger() && !player.isSleeping()
			&& (Math.abs(net.minecraft.util.Mth.wrapDegrees(here.yaw() - player.getYRot())) > 1.0E-3F || player.getXRot() != LIE_PITCH)) {
			player.forceSetRotation(here.yaw(), false, LIE_PITCH, false);
		}
		TipsyRegistry.BLACKOUT_YAW.set(player, here.yaw());
		TipsyRegistry.BLACKOUT_POSE.set(player, BlackoutEnding.Pose.GROUND.ordinal());
		return here;
	}

	/** While waking up: the animal stares at the player, the villager stares and, as the eyes open, shakes its head. */
	private static void watch(ServerPlayer player, int left) {
		Scene scene = TipsyRegistry.BLACKOUT_SCENE.getExisting(player);
		if (scene == null || scene.friend().isEmpty() || !(player.level().getEntity(scene.friend().get()) instanceof Mob friend)) {
			return;
		}
		friend.getNavigation().stop();
		friend.getLookControl().setLookAt(player.getX(), player.getY() + 0.4, player.getZ(), 30.0F, 30.0F);
		if (left == HEAD_SHAKE_AT && friend instanceof Villager villager) {
			villager.setUnhappy();
		}
	}

	/** The end: the ending's line, and the advancements. */
	private static void finish(ServerPlayer player) {
		Scene scene = TipsyRegistry.BLACKOUT_SCENE.getExisting(player);
		TipsyRegistry.BLACKOUT_SCENE.remove(player);
		TipsyRegistry.BLACKOUT_POSE.remove(player);
		TipsyRegistry.BLACKOUT_YAW.remove(player);
		if (player.isSleeping()) {
			player.stopSleepInBed(true, true);
		}
		BlackoutEnding ending = scene == null ? BlackoutEnding.RIGHT_HERE : scene.endingOrHere();
		String line = scene == null ? ending.lineKey(0) : scene.line();
		Component friend = scene == null ? Component.empty() : scene.friendType().map(Identifier::tryParse)
			.flatMap(id -> BuiltInRegistries.ENTITY_TYPE.getOptional(id)).map(type -> (Component) type.getDescription()).orElse(Component.empty());
		player.sendOverlayMessage(Component.translatable(line, friend));
		VBAdvancements.award(VBAdvancements.BLACKED_OUT, player);
		BlackoutTrigger.award(player, ending);
	}
}
