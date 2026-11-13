package jexxme.vineandbarrel.tipsy;

import jexxme.vineandbarrel.tipsy.TipsyLevels.Level;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;


/**
 * Tipsy: the one API the drinks use. What each level does is {@link TipsyLevels}; this class runs it on the server:
 * once a tick, for every tipsy entity, {@link #tick} rolls the hiccups, the unsteady legs ({@link TipsyLegs}) and the
 * wild punches ({@link Rowdy}), and keeps the level synced to every client. While a player is blacked out
 * ({@link Blackout}) all of it pauses. The client draws the rest (the sway others see) from the level alone. Being
 * drunk costs control, never stats: Tipsy gives no Nausea, Slowness, Mining Fatigue or Weakness.
 * <p>
 * Every alcoholic drink calls {@link #addLevel(LivingEntity)} once when it is finished. Milk clears it (vanilla's
 * clear-all-effects, nothing to do here), a drink in {@link TipsyRegistry#SOBERING_DRINKS} lowers it by one, and
 * sleeping through the night clears it.
 */
public final class Tipsy
{
	public static final int ABSOLUTE_MAX_LEVEL = TipsyLevels.LEVELS.size();
	/** The first level that does each thing (from {@link TipsyLevels}). */
	public static final int HICCUP_LEVEL = TipsyLevels.first(level -> level.hiccups().any());
	public static final int SLUR_LEVEL = TipsyLevels.first(level -> level.chat().slurs());
	public static final int NO_BUFF_LEVEL = TipsyLevels.first(level -> !level.rules().buffs());
	public static final int STUMBLE_LEVEL = TipsyLevels.first(level -> level.legs().unsteady());

	/** Ticks between two hiccups of one bout: 2 to 4 s. */
	public static final int HICCUP_GAP_MIN = 40, HICCUP_GAP_MAX = 80;
	/** After a bout, at least this long without hiccups (10 s): then the level's chance rolls again. */
	public static final int HICCUP_QUIET = 200;

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
	 * One level down (a water bottle or a juice). The lower levels keep the time they had left, so sobering up never
	 * makes the rest of the night longer.
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
		syncLevel(entity);
		if (level == 0) {
			soberUp(entity);
		}
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

	/** Sober: the night's bookkeeping goes (the drink buffs stay, they just aren't tracked any more). */
	private static void soberUp(LivingEntity entity) {
		DrinkBuffs.forget(entity);
		if (TipsyRegistry.HICCUPS.has(entity)) {
			TipsyRegistry.HICCUPS.remove(entity);
		}
		if (entity instanceof Player player) {
			TipsyNod.wake(player);
			if (TipsyRegistry.STILL.has(player)) {
				TipsyRegistry.STILL.remove(player);
			}
		}
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

	// ------------------------------------------------------------------ the tick

	/**
	 * Once a tick on the server, for every entity that is tipsy (or was, a tick ago): the level's chance rolls, unless
	 * the player is out cold or lying on the ground after a trip, then the synced level and the bookkeeping.
	 */
	static void tick(LivingEntity entity) {
		int level = getLevel(entity);
		Level at = TipsyLevels.at(level);
		RandomSource random = entity.getRandom();
		boolean paused = entity instanceof Player player && Blackout.isOut(player);
		boolean down = TipsyLegs.isDown(entity);
		if (level > 0 && !paused && !down) {
			tickHiccups(entity, at, random);
			TipsyLegs.tick(entity, at, random);
			if (entity instanceof ServerPlayer player) {
				Rowdy.tick(player, at);
			}
		} else if (entity instanceof Player player) {
			Rowdy.calmDown(player);
		}
		TipsyLegs.tickTrip(entity);
		if (entity instanceof ServerPlayer player) {
			if (level > 0) {
				TipsyNod.tick(player, at);
			} else {
				TipsyNod.wake(player);
			}
		}
		syncLevel(entity);
		forgetOldSideEffects(entity);
		if (level == 0) {
			soberUp(entity);
		} else {
			DrinkBuffs.tick(entity);
		}
	}

	// ------------------------------------------------------------------ sync

	/** Keeps the level other clients see ({@link TipsyRegistry#SYNCED_LEVEL}, players only) in step with the effect. */
	static void syncLevel(LivingEntity entity) {
		if (!(entity instanceof Player)) {
			return;
		}
		int level = getLevel(entity);
		Integer synced = TipsyRegistry.SYNCED_LEVEL.getExisting(entity);
		if (level > 0 ? synced == null || synced != level : synced != null) {
			if (level > 0) {
				TipsyRegistry.SYNCED_LEVEL.set(entity, level);
			} else {
				// Sends the removal too, so other clients stop the sway
				TipsyRegistry.SYNCED_LEVEL.remove(entity);
			}
		}
	}

	/**
	 * Before the overhaul Tipsy gave itself Nausea, Slowness, Mining Fatigue and Weakness and marked them as its own
	 * ({@link TipsyRegistry#HEAVY_EFFECTS_OWNED}). A save from then still has the mark: it goes. The effects themselves
	 * were short (a few seconds, a Nausea pulse 15) and just run out; nothing refreshes them any more.
	 */
	static void forgetOldSideEffects(LivingEntity entity) {
		if (TipsyRegistry.HEAVY_EFFECTS_OWNED.has(entity)) {
			TipsyRegistry.HEAVY_EFFECTS_OWNED.remove(entity);
		}
	}

	/** Whether vanilla keeps a weaker, longer instance of the same effect underneath this one. */
	static boolean hasHiddenEffect(MobEffectInstance effect) {
		Tag saved = MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, effect).result().orElse(null);
		return saved instanceof CompoundTag compound && compound.contains("hidden_effect");
	}

	/**
	 * The weaker, longer instance vanilla keeps underneath this one, or null. It isn't exposed, but it is saved: as the
	 * outer instance's details without the effect id (it is the same effect), so the id is put back to read it.
	 */
	static @Nullable MobEffectInstance hiddenEffect(MobEffectInstance effect) {
		Tag saved = MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, effect).result().orElse(null);
		if (!(saved instanceof CompoundTag compound) || !(compound.get("hidden_effect") instanceof CompoundTag hidden)) {
			return null;
		}
		CompoundTag whole = hidden.copy();
		Tag id = compound.get("id");
		if (id != null) {
			whole.put("id", id);
		}
		return MobEffectInstance.CODEC.parse(NbtOps.INSTANCE, whole).result().orElse(null);
	}

	// ------------------------------------------------------------------ hiccups

	/**
	 * A bout of hiccups in progress: how many are left, ticks until the next one, and how many came already. With none
	 * left it is the quiet stretch after a bout, {@code next} ticks long.
	 */
	public record HiccupBout(int left, int next, int index)
	{
	}

	/** Chance roll for one tick, while no bout runs: does a bout of hiccups start? */
	public static boolean rollsHiccupBout(int level, RandomSource random) {
		int oneIn = TipsyLevels.at(level).hiccups().boutOneIn();
		return oneIn > 0 && random.nextInt(oneIn) == 0;
	}

	/** Hiccups come in bouts: a few, two to four seconds apart, then a quiet stretch. */
	private static void tickHiccups(LivingEntity entity, Level at, RandomSource random) {
		HiccupBout bout = TipsyRegistry.HICCUPS.getExisting(entity);
		if (bout == null) {
			if (!rollsHiccupBout(at.level(), random)) {
				return;
			}
			TipsyLevels.Hiccups size = at.hiccups();
			bout = new HiccupBout(size.fewest() + random.nextInt(size.most() - size.fewest() + 1), 0, 0);
		}
		if (bout.next() > 0) {
			TipsyRegistry.HICCUPS.set(entity, new HiccupBout(bout.left(), bout.next() - 1, bout.index()));
			return;
		}
		if (bout.left() <= 0) {
			// The quiet stretch is over
			TipsyRegistry.HICCUPS.remove(entity);
			return;
		}
		hiccup(entity, bout.index());
		if (bout.left() == 1) {
			TipsyRegistry.HICCUPS.set(entity, new HiccupBout(0, HICCUP_QUIET, 0));
		} else {
			int gap = HICCUP_GAP_MIN + random.nextInt(HICCUP_GAP_MAX - HICCUP_GAP_MIN + 1);
			TipsyRegistry.HICCUPS.set(entity, new HiccupBout(bout.left() - 1, gap, bout.index() + 1));
		}
	}

	/**
	 * One hiccup: the sound ({@link TipsySounds#hiccup}) and a small bubble for everyone nearby, and on every client a
	 * jolt (the own view flicks up, others see the head and shoulders jerk; {@link TipsyMotion}). Moves nothing.
	 *
	 * @param index which hiccup of its bout this is (0 is the first)
	 */
	public static void hiccup(LivingEntity entity, int index) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		TipsySounds.hiccup(entity, index);
		Vec3 look = entity.getLookAngle();
		level.sendParticles(TipsyRegistry.HICCUP_PARTICLE.get(),
				entity.getX() + look.x * 0.4, entity.getEyeY() - 0.15, entity.getZ() + look.z * 0.4,
				1, 0.03, 0.02, 0.03, 0.0);
		TipsyMotion.hiccup(entity);
	}

	// ------------------------------------------------------------------ slurred chat

	/** {@return what a player's own chat line goes out as: a command as typed, a message as {@link #slurChat} says it} */
	public static String ownChat(LivingEntity entity, String line, long seed) {
		return line.startsWith("/") ? line : slurChat(entity, line, seed);
	}

	/** The chat message as a tipsy entity says it: worse with each level ({@link TipsySlur}), when the server allows it. */
	public static String slurChat(LivingEntity entity, String message, long seed) {
		int stage = TipsyLevels.at(getLevel(entity)).chat().stage();
		if (!TipsyConfig.slurredChat() || stage <= 0) {
			return message;
		}
		return TipsySlur.slur(message, seed, stage);
	}
}
