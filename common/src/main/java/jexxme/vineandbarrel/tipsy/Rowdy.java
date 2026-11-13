package jexxme.vineandbarrel.tipsy;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rowdy (from Tipsy IV, playtest 1): now and then you are spoiling for a fight ({@link #ITCH}), and the first one in
 * front of you, even as you look past, gets it (never thin air: it reads as picking a fight, not as a twitch; Jexxme,
 * overhaul playtest 3). The arm draws back for a moment
 * ({@link #WINDUP}, everyone sees it coming), then swings and throws a real punch at whoever is in front by then, and
 * the swing carries you a step forward; if they got away, you miss and stumble. The punch: fist strength whatever the hand holds. Never at pets, iron golems or armor stands; at other players
 * only where the server allows PvP. And the bar cuts you off: villagers and traders won't trade with you. Each has its
 * own server switch ({@code rowdy}, {@code cutOff}): the punches do real harm (a villager remembers, a golem gets
 * angry), the cut-off doesn't.
 */
public final class Rowdy
{
	public static final int ROWDY_LEVEL = TipsyLevels.first(level -> level.rules().rowdy());
	public static final int CUT_OFF_LEVEL = TipsyLevels.first(level -> level.rules().cutOff());
	/** A fist's damage (half a heart) and a fist's knockback, whatever is held. */
	public static final float PUNCH_DAMAGE = 1.0F;
	private static final double PUNCH_KNOCKBACK = 0.4;

	private Rowdy() {
	}

	/** Chance roll for one tick ({@link TipsyLevels.Rules#rowdySwingOneIn}): spoiling for a fight about every 20 s at IV, every 10 s at V. */
	public static boolean rollsSwing(int level, RandomSource random) {
		int oneIn = TipsyLevels.at(level).rules().rowdySwingOneIn();
		return oneIn > 0 && random.nextInt(oneIn) == 0;
	}

	/** The arm draws back this many ticks before the swing. */
	public static final int WINDUP = 16;
	/**
	 * Now and then ({@link #rollsSwing}) you are spoiling for a fight, for up to this many ticks: the first one in front of
	 * you in that time, even for a moment as you look past, gets the swing.
	 */
	public static final int ITCH = 20 * 20;
	/** The swing carries you this far forward (blocks per tick, once). */
	public static final double LUNGE = 0.35;

	/**
	 * Once a tick on the server, for tipsy players (from {@link Tipsy#tick}: never while out cold or down after a trip):
	 * now and then, with someone right in front who may be hit, the arm draws back; {@link #WINDUP} ticks later the
	 * swing. {@return whether it swung this tick}
	 */
	static boolean tick(ServerPlayer player, TipsyLevels.Level at) {
		if (!TipsyConfig.rowdy() || !at.rules().rowdy()) {
			// switched off, or sobered below Rowdy: the arm comes down at once, and the itch is gone
			calmDown(player);
			return false;
		}
		long start = windupStart(player);
		long now = player.level().getGameTime();
		if (start >= 0L) {
			if (now - start < WINDUP) {
				return false;
			}
			TipsyRegistry.ROWDY_WINDUP.remove(player);
			// asleep or a spectator meanwhile: the arm comes down, no swing
			if (player.isSpectator() || player.isSleeping()) {
				return false;
			}
			swing(player);
			return true;
		}
		if (player.isSpectator() || player.isSleeping()) {
			return false;
		}
		int itch = TipsyRegistry.ROWDY_ITCH.getExisting(player) instanceof Integer left ? left : 0;
		if (itch <= 0 && rollsSwing(at.level(), player.getRandom())) {
			itch = ITCH;
		}
		if (itch <= 0) {
			return false;
		}
		Entity target = inFront(player);
		if (target == null || !mayHit(player, target)) {
			TipsyRegistry.ROWDY_ITCH.set(player, itch - 1);
			return false;
		}
		TipsyRegistry.ROWDY_ITCH.set(player, 0);
		TipsyRegistry.ROWDY_WINDUP.set(player, now);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.6F, 0.8F);
		return false;
	}

	/** {@return the game time this player started winding up, or -1} */
	public static long windupStart(Player player) {
		Long start = TipsyRegistry.ROWDY_WINDUP.getExisting(player);
		return start == null ? -1L : start;
	}

	/** {@return how far the arm is drawn back now, 0 to 1 (every client, from the synced start)} */
	public static float windup(Player player, float partial) {
		long start = windupStart(player);
		if (start < 0L) {
			return 0.0F;
		}
		float t = Mth.clamp((player.level().getGameTime() - start + partial) / WINDUP, 0.0F, 1.0F);
		return t * (2.0F - t);
	}

	/** Gives up a wind-up and the itch (sobered up, out cold): the arm comes down, no swing. */
	static void calmDown(Player player) {
		if (TipsyRegistry.ROWDY_WINDUP.has(player)) {
			TipsyRegistry.ROWDY_WINDUP.remove(player);
		}
		if (TipsyRegistry.ROWDY_ITCH.has(player)) {
			TipsyRegistry.ROWDY_ITCH.remove(player);
		}
	}

	/**
	 * One wild swing: the arm, the whoosh, a step forward with it, and a punch on whoever stands right in front (if anyone
	 * may be hit); nobody there any more, a miss and a stumble.
	 */
	public static @Nullable Entity swing(ServerPlayer player) {
		ServerLevel level = player.level();
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Vec3 ahead = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
		Entity target = inFront(player);
		boolean missed = target == null || !mayHit(player, target);
		// swung at nothing: the swing takes you with it, a stumble on top of the step forward
		if (missed) {
			TipsyLegs.stumble(player, ahead, player.getRandom());
		}
		// The step forward: the swinger's own client takes it, from where they really are (ownLunge)
		TipsyMotion.lunge(player);
		if (missed) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8F, 0.8F);
			return null;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 1.0F, 0.9F);
		var source = level.damageSources().playerAttack(player);
		if (target.hurtServer(level, source, PUNCH_DAMAGE) && target instanceof LivingEntity living) {
			Vec3 look = player.getLookAngle();
			living.knockback(PUNCH_KNOCKBACK, -look.x, -look.z, source, PUNCH_DAMAGE);
		}
		return target;
	}

	/**
	 * On the swinger's own client, when it sees the swing's step forward ({@link TipsyMotion.Counts#lunges}): the push
	 * to add to how they move right now, toward where they face, or {@link Vec3#ZERO}. Only on the ground, and only where
	 * the ground ahead is safe for two steps from where they are now (the step carries a walking player further). The
	 * server never sends it as a speed: it only knows how the player moved a moment ago, and sent back with the step that
	 * carried someone who had just stopped or turned at an edge over it.
	 */
	public static Vec3 ownLunge(Player player) {
		Vec3 ahead = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
		if (!player.onGround() || ahead.lengthSqr() < 1.0E-6 || !TipsyLegs.safeToWeave(player, ahead, ahead)) {
			return Vec3.ZERO;
		}
		return ahead.normalize().scale(LUNGE);
	}

	/** Someone counts as in front within this angle either side of where you face (degrees, cosine). */
	private static final double IN_FRONT_COS = Math.cos(Math.toRadians(55.0));

	/**
	 * {@return the nearest one close in front of you who may be hit: within reach and roughly where you face (you
	 * needn't aim at them), or null}
	 */
	static @Nullable Entity inFront(ServerPlayer player) {
		double reach = player.entityInteractionRange();
		Vec3 eye = player.getEyePosition();
		Vec3 facing = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
		if (facing.lengthSqr() < 1.0E-6) {
			return null;
		}
		facing = facing.normalize();
		Entity nearest = null;
		double best = Double.MAX_VALUE;
		for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(reach), e -> !e.isSpectator() && e.isPickable())) {
			Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
			double distance = entity.getBoundingBox().distanceToSqr(eye);
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			// close, at about your own height, where you face, and nothing in between (no punches through walls or glass)
			double height = entity.getY() - player.getY();
			if (distance > reach * reach || flat.lengthSqr() < 1.0E-6 || flat.normalize().dot(facing) < IN_FRONT_COS
				|| height < -1.0 || height > 1.5 || !mayHit(player, entity) || !player.hasLineOfSight(entity)) {
				continue;
			}
			if (distance < best) {
				best = distance;
				nearest = entity;
			}
		}
		return nearest;
	}

	/** For tests: {@link #inFront}. */
	public static @Nullable Entity inFrontForTest(ServerPlayer player) {
		return inFront(player);
	}

	/** Never pets, iron golems or armor stands; other players only where PvP is on. */
	public static boolean mayHit(ServerPlayer player, Entity target) {
		if (!(target instanceof LivingEntity) || target instanceof ArmorStand || target instanceof IronGolem) {
			return false;
		}
		if (target instanceof OwnableEntity pet && pet.getOwnerReference() != null) {
			return false;
		}
		if (target instanceof Player other) {
			return player.canHarmPlayer(other);
		}
		return true;
	}

	/** {@return whether a villager or trader turns this player away: from Tipsy IV, with the cut-off on} */
	public static boolean cutOff(Player player) {
		return TipsyConfig.enabled() && TipsyConfig.cutOff() && TipsyLevels.at(Tipsy.getLevel(player)).rules().cutOff();
	}

	/** The villager shakes its head ("Hmm.", like when it has nothing to trade). */
	public static void refuse(AbstractVillager trader) {
		// Like a villager with nothing to sell: head shake and its "no" (setUnhappy is private to Villager)
		trader.setUnhappyCounter(40);
		trader.playSound(trader instanceof Villager ? SoundEvents.VILLAGER_NO : SoundEvents.WANDERING_TRADER_NO, 1.0F, 1.0F);
	}
}
