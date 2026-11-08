package jexxme.vineandbarrel.tipsy;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Tipsy (DESIGN.md §6, §13.4, playtest 1): the one API the drinks use.
 * <pre>
 *   level 1-2  slight camera wobble, "liquid courage"              (client {@link TipsyWobble}; {@link TipsyEffect})
 *   level 3    stronger wobble, hiccups, Nausea pulses, courage     ({@link #hiccup}, {@link #nauseaPulse})
 *   level 4    + slurred chat and Weakness I; the courage is gone   ({@link TipsySlur})
 *   level 5    + Slowness II, Mining Fatigue II, stumbling          ({@link #stumble})
 * </pre>
 * Every alcoholic drink calls {@link #addLevel(LivingEntity)} once when it is finished. Cures: milk clears it
 * (vanilla's clear-all-effects, nothing to do here), a drink in {@link TipsyRegistry#SOBERING_DRINKS} lowers it by one,
 * and sleeping through the night clears it. All of it is server-side; the client only reads the result.
 */
public final class Tipsy
{
	public static final int ABSOLUTE_MAX_LEVEL = 5;
	public static final int HICCUP_LEVEL = 3;
	public static final int SLUR_LEVEL = 4;
	public static final int STUMBLE_LEVEL = 5;
	/** From this level on a drink gives no buff any more, only Tipsy (playtest 1: too drunk to enjoy it). */
	public static final int NO_BUFF_LEVEL = 4;
	/** Level at which Slowness II and Mining Fatigue II join in. */
	public static final int HEAVY_LEVEL = 5;
	/** Level at which Weakness I joins in. */
	public static final int WEAKNESS_LEVEL = 4;
	/** Level from which short Nausea pulses come now and then. */
	public static final int NAUSEA_LEVEL = 3;

	/** Average ticks between stumble rolls while walking at level 5 (one every ~10 s of walking). */
	public static final int STUMBLE_ONE_IN = 100;
	/** Upward speed of a hiccup hop: about a third of a jump (a jump is 0.42). */
	public static final double HICCUP_HOP = 0.2;
	/** Sideways speed of a stumble: a small nudge, about a step to the side. */
	public static final double STUMBLE_NUDGE = 0.28;
	/** Horizontal speed (blocks/tick) above which an entity counts as walking. Sneaking is slower and never stumbles. */
	private static final double WALKING_SPEED = 0.06;

	/** The steady effects are short and refreshed while their level lasts, so they end within seconds of dropping below. */
	static final int STEADY_EFFECT_TICKS = 60;
	private static final int STEADY_EFFECT_REFRESH_BELOW = 40;
	/**
	 * One Nausea pulse: 15 s. Vanilla fades Nausea in over 7.5 s and out over the last 3 s, so this reaches the full
	 * swirl for a few seconds (playtest 1: 7 s barely showed). The vanilla "Distortion Effects" option scales it.
	 */
	public static final int NAUSEA_PULSE_TICKS = 300;

	/**
	 * An effect Tipsy gives itself from {@code fromLevel} on. Its {@code bit} in {@link TipsyRegistry#HEAVY_EFFECTS_OWNED}
	 * says Tipsy still owns the instance, so only Tipsy's own instance is ever refreshed or removed. A pulse is not
	 * refreshed: {@link #nauseaPulse} starts it and it runs out on its own (or goes when the level drops below).
	 */
	record SideEffect(Holder<MobEffect> effect, int bit, int fromLevel, int amplifier, int ticks, boolean pulse)
	{
		MobEffectInstance create() {
			// No particles and no icon: the Tipsy icon (and its tooltip) stands for all of them
			return new MobEffectInstance(effect, ticks, amplifier, false, false, false);
		}

		/** Tipsy's own instance: its amplifier, no particles, not ambient, and never longer than Tipsy gives it. */
		boolean isOwn(MobEffectInstance instance) {
			return instance.getAmplifier() == amplifier && !instance.isAmbient() && !instance.isVisible()
					&& !instance.isInfiniteDuration() && instance.getDuration() <= ticks;
		}
	}

	/** Bits 0 and 1 are the ones saved before playtest 1, so old saves keep their meaning. */
	static final List<SideEffect> SIDE_EFFECTS = List.of(
			new SideEffect(MobEffects.SLOWNESS, 1, HEAVY_LEVEL, 1, STEADY_EFFECT_TICKS, false),
			new SideEffect(MobEffects.MINING_FATIGUE, 1 << 1, HEAVY_LEVEL, 1, STEADY_EFFECT_TICKS, false),
			new SideEffect(MobEffects.WEAKNESS, 1 << 2, WEAKNESS_LEVEL, 0, STEADY_EFFECT_TICKS, false),
			new SideEffect(MobEffects.NAUSEA, 1 << 3, NAUSEA_LEVEL, 0, NAUSEA_PULSE_TICKS, true));
	private static final SideEffect NAUSEA = SIDE_EFFECTS.get(3);

	private Tipsy() {
	}

	// ------------------------------------------------------------------ levels

	/** {@return the current Tipsy level, 0 when sober} */
	public static int getLevel(LivingEntity entity) {
		MobEffectInstance effect = entity.getEffect(TipsyRegistry.TIPSY);
		return effect == null ? 0 : effect.getAmplifier() + 1;
	}

	/**
	 * <b>The call for drinks:</b> one more level, up to the configured max, and the decay timer starts over. Does
	 * nothing in non-alcoholic mode and on the client.
	 *
	 * @return whether the level changed or its timer was refreshed
	 */
	public static boolean addLevel(LivingEntity entity) {
		if (!TipsyConfig.enabled() || entity.level().isClientSide()) {
			return false;
		}
		int next = Math.min(getLevel(entity) + 1, TipsyConfig.maxLevel());
		setLevel(entity, next);
		return true;
	}

	/**
	 * One level down (a water bottle or grape juice). The lower levels keep the time they had left, so sobering up
	 * never makes the rest of the night longer.
	 *
	 * @return whether there was anything to lower
	 */
	public static boolean lowerLevel(LivingEntity entity) {
		MobEffectInstance effect = entity.getEffect(TipsyRegistry.TIPSY);
		if (effect == null || entity.level().isClientSide()) {
			return false;
		}
		int level = effect.getAmplifier() + 1;
		int perLevel = TipsyConfig.ticksPerLevel();
		// The layer below the top one has the top's remaining time plus one full level left
		int nextTop = effect.isInfiniteDuration() ? perLevel : effect.getDuration() + perLevel;
		apply(entity, level - 1, nextTop, perLevel);
		return true;
	}

	/** Takes away the buffs our drinks gave (reaching Tipsy IV, a blackout); a potion's effects stay. */
	public static void removeDrinkBuffs(LivingEntity entity) {
		for (String given : entity.getData(TipsyRegistry.DRINK_BUFFS)) {
			net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(net.minecraft.resources.Identifier.parse(given)).ifPresent(entity::removeEffect);
		}
		entity.removeData(TipsyRegistry.DRINK_BUFFS);
	}

	/** Sober again (sleeping through the night). Milk needs no call: it clears every effect, Tipsy included. */
	public static void clear(LivingEntity entity) {
		if (!entity.level().isClientSide()) {
			apply(entity, 0, 0, 0);
		}
	}

	/** Sets the level directly (clamped to 0..max) with a fresh timer. For commands and tests; drinks use {@link #addLevel}. */
	public static void setLevel(LivingEntity entity, int level) {
		int perLevel = TipsyConfig.ticksPerLevel();
		apply(entity, Math.clamp(level, 0, TipsyConfig.maxLevel()), perLevel, perLevel);
	}

	private static void apply(LivingEntity entity, int level, int topTicks, int perLevel) {
		entity.removeEffect(TipsyRegistry.TIPSY);
		if (level > 0) {
			entity.addEffect(chain(level, topTicks, perLevel));
		}
		updateSideEffects(entity);
	}

	/**
	 * Builds vanilla's hidden-effect chain: the top level runs for {@code topTicks}, and each level below it for one
	 * more {@code perLevel} (hidden effects count down together with the visible one).
	 */
	static MobEffectInstance chain(int level, int topTicks, int perLevel) {
		MobEffectInstance below = null;
		for (int amplifier = 0; amplifier < level; amplifier++) {
			int duration = topTicks + (level - 1 - amplifier) * perLevel;
			below = new MobEffectInstance(TipsyRegistry.TIPSY, duration, amplifier, false, true, true, below);
		}
		return below;
	}

	// ------------------------------------------------------------------ cures

	/** Whether finishing this drink lowers Tipsy by one. A potion item only counts when it is plain water. */
	public static boolean isSoberingDrink(ItemStack stack) {
		if (!stack.is(TipsyRegistry.SOBERING_DRINKS)) {
			return false;
		}
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents == null || (contents.is(Potions.WATER) && contents.customEffects().isEmpty());
	}

	// ------------------------------------------------------------------ side effects: Nausea, Weakness, Slowness, Mining Fatigue

	/** Once a tick on the server: maybe a Nausea pulse, then {@link #updateSideEffects}. */
	static void tick(LivingEntity entity) {
		if (rollsNausea(getLevel(entity), entity.getRandom())) {
			nauseaPulse(entity);
		}
		if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
			Rowdy.tick(player);
		}
		updateSideEffects(entity);
	}

	/**
	 * Chance roll for one tick, while no pulse runs: the next Nausea pulse comes about 45 s later at level 3, 25 s at 4
	 * and 6 s at 5 (playtest 1: level 5 should feel properly sick, so it is dizzy more often than not).
	 */
	public static boolean rollsNausea(int level, RandomSource random) {
		return switch (level) {
			case 0, 1, 2 -> false;
			case 3 -> random.nextInt(900) == 0;
			case 4 -> random.nextInt(500) == 0;
			default -> random.nextInt(40) == 0;
		};
	}

	/**
	 * Starts one short Nausea pulse, from level 3 on, unless the entity already has Nausea (Tipsy's or anyone else's).
	 *
	 * @return whether a pulse started
	 */
	public static boolean nauseaPulse(LivingEntity entity) {
		if (entity.level().isClientSide() || getLevel(entity) < NAUSEA.fromLevel() || entity.hasEffect(NAUSEA.effect())) {
			return false;
		}
		entity.addEffect(NAUSEA.create());
		setOwned(entity, owned(entity) | NAUSEA.bit());
		return true;
	}

	/**
	 * Keeps the steady side effects and the synced level in step with the current level. Called every tick and after
	 * each level change. Only touches effects Tipsy gave itself (tracked in {@link TipsyRegistry#HEAVY_EFFECTS_OWNED}),
	 * never a potion's, a command's or another mod's.
	 */
	static void updateSideEffects(LivingEntity entity) {
		int level = getLevel(entity);
		int owned = owned(entity);
		for (SideEffect side : SIDE_EFFECTS) {
			MobEffectInstance current = entity.getEffect(side.effect());
			boolean ours = (owned & side.bit()) != 0 && current != null && side.isOwn(current);
			if (ours && current.showIcon() && !hasHiddenEffect(current)) {
				// From an older version or save (icons used to show): same effect and time left, without its icon
				entity.removeEffect(side.effect());
				entity.addEffect(new MobEffectInstance(side.effect(), current.getDuration(), current.getAmplifier(), false, false, false));
				current = entity.getEffect(side.effect());
			}
			if (level >= side.fromLevel()) {
				if (!side.pulse() && (current == null || (ours && current.getDuration() < STEADY_EFFECT_REFRESH_BELOW))) {
					entity.addEffect(side.create());
					ours = true;
				}
			} else if (ours) {
				// A foreign effect may wait underneath ours (vanilla's hidden effect); removing ours would drop it too,
				// so then ours just runs out (a few seconds at most) and lets the foreign one come back
				if (!hasHiddenEffect(current)) {
					entity.removeEffect(side.effect());
				}
				ours = false;
			}
			owned = ours ? owned | side.bit() : owned & ~side.bit();
		}
		setOwned(entity, owned);
		if (entity instanceof Player) {
			Integer synced = entity.getExistingDataOrNull(TipsyRegistry.SYNCED_LEVEL);
			if (level > 0 ? synced == null || synced != level : synced != null) {
				if (level > 0) {
					entity.setData(TipsyRegistry.SYNCED_LEVEL, level);
				} else {
					// Sends the removal too, so other clients stop the sway
					entity.removeData(TipsyRegistry.SYNCED_LEVEL);
				}
			}
		}
	}

	private static int owned(LivingEntity entity) {
		Integer owned = entity.getExistingDataOrNull(TipsyRegistry.HEAVY_EFFECTS_OWNED);
		return owned == null ? 0 : owned;
	}

	private static void setOwned(LivingEntity entity, int owned) {
		if (owned != 0) {
			if (owned != owned(entity)) {
				entity.setData(TipsyRegistry.HEAVY_EFFECTS_OWNED, owned);
			}
		} else if (entity.hasData(TipsyRegistry.HEAVY_EFFECTS_OWNED)) {
			entity.removeData(TipsyRegistry.HEAVY_EFFECTS_OWNED);
		}
	}

	/** Whether vanilla keeps a weaker, longer instance of the same effect underneath this one (it isn't exposed). */
	private static boolean hasHiddenEffect(MobEffectInstance effect) {
		return MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, effect).result()
				.map(tag -> tag instanceof CompoundTag compound && compound.contains("hidden_effect")).orElse(false);
	}

	// ------------------------------------------------------------------ level 3: hiccups

	/** Chance roll for one tick: about every 12 s at level 3, every 9 s at 4, every 7 s at 5. */
	public static boolean rollsHiccup(int level, RandomSource random) {
		return switch (level) {
			case 0, 1, 2 -> false;
			case 3 -> random.nextInt(240) == 0;
			case 4 -> random.nextInt(180) == 0;
			default -> random.nextInt(140) == 0;
		};
	}

	/**
	 * One hiccup: a small harmless hop (only when standing on the ground), the hiccup sound and a hiccup bubble in
	 * front of the face. Particles and sound go to everyone nearby (T1).
	 */
	public static void hiccup(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (canBeNudged(entity)) {
			Vec3 motion = currentMotion(entity);
			setMotion(entity, new Vec3(motion.x, Math.max(motion.y, 0.0) + HICCUP_HOP, motion.z));
		}
		RandomSource random = entity.getRandom();
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), TipsyRegistry.HICCUP_SOUND, entity.getSoundSource(),
				// Pitch varies +-10% around the sound's own pitch, so no two hiccups sound quite the same
				0.7F, 0.9F + random.nextFloat() * 0.2F);
		Vec3 look = entity.getLookAngle();
		level.sendParticles(TipsyRegistry.HICCUP_PARTICLE.get(),
				entity.getX() + look.x * 0.4, entity.getEyeY() - 0.15, entity.getZ() + look.z * 0.4,
				1, 0.04, 0.02, 0.04, 0.0);
	}

	// ------------------------------------------------------------------ level 5: stumbling

	/** {@return the horizontal walking motion, or {@link Vec3#ZERO} when standing, sneaking, flying or riding} */
	public static Vec3 walkingMotion(LivingEntity entity) {
		if (!canBeNudged(entity) || entity.isShiftKeyDown()) {
			return Vec3.ZERO;
		}
		Vec3 motion = currentMotion(entity);
		Vec3 horizontal = new Vec3(motion.x, 0.0, motion.z);
		return horizontal.lengthSqr() > WALKING_SPEED * WALKING_SPEED ? horizontal : Vec3.ZERO;
	}

	/**
	 * A stumble: a small sideways nudge, at right angles to the walking direction, to a random side. Does nothing
	 * below level 5, when stumbling is switched off, or when not walking.
	 *
	 * @param walking the horizontal walking motion, see {@link #walkingMotion}
	 * @return whether the entity stumbled
	 */
	public static boolean stumble(LivingEntity entity, Vec3 walking, RandomSource random) {
		if (!TipsyConfig.stumbling() || getLevel(entity) < STUMBLE_LEVEL || walking.lengthSqr() < 1.0E-6) {
			return false;
		}
		Vec3 side = new Vec3(-walking.z, 0.0, walking.x).normalize().scale(random.nextBoolean() ? STUMBLE_NUDGE : -STUMBLE_NUDGE);
		setMotion(entity, currentMotion(entity).add(side));
		return true;
	}

	// ------------------------------------------------------------------ level 4: slurred chat

	/** The chat message as a tipsy entity says it: slurred from level 4 on, when the server allows it. */
	public static String slurChat(LivingEntity entity, String message, long seed) {
		if (!TipsyConfig.slurredChat() || getLevel(entity) < SLUR_LEVEL) {
			return message;
		}
		return TipsySlur.slur(message, seed);
	}

	// ------------------------------------------------------------------ motion helpers

	private static boolean canBeNudged(LivingEntity entity) {
		return entity.onGround() && !entity.isPassenger() && !entity.isInWater() && !entity.isFallFlying()
				&& !entity.isSleeping() && !(entity instanceof Player player && player.getAbilities().flying);
	}

	/** Players move on their client, so the server's view of their speed is the last movement the client reported. */
	private static Vec3 currentMotion(LivingEntity entity) {
		return entity instanceof Player ? entity.getKnownMovement() : entity.getDeltaMovement();
	}

	private static void setMotion(LivingEntity entity, Vec3 motion) {
		entity.setDeltaMovement(motion);
		entity.needsSync = true;
		// Sends the new speed to the entity's own client too (a player moves on its client), like knockback does
		entity.hurtMarked = true; // 26.2 name of 26.3's syncVelocity
	}
}
