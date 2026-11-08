package jexxme.vineandbarrel.tipsy;

import java.util.List;
import java.util.function.Predicate;

/**
 * Everything each Tipsy level does, in one table (Jexxme, Tipsy tidy-up): I to III are the fun part with one or two new
 * things per level, III is "peak drunk", IV is the turn, V is really drunk. Every effect reads its numbers from here,
 * the tooltip ({@link TipsyInfo}) and the wiki export too, so they can't drift apart.
 * <p>
 * Chances are "one in n ticks" (0 = never). Kept free of client classes so it is tested headless.
 */
public final class TipsyLevels
{
	/**
	 * One level.
	 *
	 * @param level             1..5
	 * @param rollDegrees       peak camera roll at wobble intensity 1 (the body others see leans {@link TipsyWobble#BODY_SWAY_SHARE} of it)
	 * @param vignette          how dark the screen edges get (alpha 0..1)
	 * @param blinkEvery        ticks between heavy-eyed blinks (0 = none)
	 * @param longBlinkOneIn    of every this many blinks one is a long one (0 = never)
	 * @param knockbackResistance liquid courage: knockback resistance added (1.0 is full resistance)
	 * @param fallDamageReduction liquid courage: share of fall damage taken away
	 * @param hiccupBoutOneIn   chance per tick that a bout of hiccups starts, while none runs
	 * @param hiccupsPerBout    fewest and most hiccups in a bout
	 * @param slurs             chat comes out slurred
	 * @param nauseaOneIn       chance per tick that a Nausea pulse starts, while none runs
	 * @param weakness          Weakness I
	 * @param slowness          Slowness amplifier (-1 = none)
	 * @param miningFatigue     Mining Fatigue amplifier (-1 = none)
	 * @param buffs             drinks still give their buff (and the earlier drinks' buffs stay)
	 * @param rowdySwingOneIn   chance per tick of a wild punch
	 * @param cutOff            villagers and traders won't trade
	 * @param legsOneIn         chance per tick, while walking, of an "unsteady legs" roll (a stumble, now and then a trip)
	 * @param tripOneIn         of every this many unsteady-legs rolls one is a trip (sprinting: {@link #SPRINT_TRIP_ONE_IN})
	 * @param drift             walking drifts slowly to the side (the player keeps correcting)
	 * @param fovBreathing      the field of view breathes in step with the sway
	 */
	public record Level(int level, float rollDegrees, float vignette, int blinkEvery, int longBlinkOneIn,
		double knockbackResistance, double fallDamageReduction, int hiccupBoutOneIn, int[] hiccupsPerBout, boolean slurs,
		int nauseaOneIn, boolean weakness, int slowness, int miningFatigue, boolean buffs, int rowdySwingOneIn,
		boolean cutOff, int legsOneIn, int tripOneIn, boolean drift, boolean fovBreathing)
	{
		public boolean hasCourage() {
			return knockbackResistance > 0.0 || fallDamageReduction > 0.0;
		}

		public boolean hiccups() {
			return hiccupBoutOneIn > 0;
		}

		public boolean blinks() {
			return blinkEvery > 0;
		}

		public boolean nausea() {
			return nauseaOneIn > 0;
		}

		public boolean rowdy() {
			return rowdySwingOneIn > 0;
		}

		public boolean unsteadyLegs() {
			return legsOneIn > 0;
		}
	}

	/** Sober: nothing at all. */
	public static final Level SOBER = new Level(0, 0.0F, 0.0F, 0, 0, 0.0, 0.0, 0, new int[] {0, 0}, false, 0, false, -1, -1, true, 0,
		false, 0, 0, false, false);

	private static final int S = 20;

	/** Level I to V. */
	public static final List<Level> LEVELS = List.of(
		new Level(1, 1.7F, 0.16F, 0, 0, 0.10, 0.15, 0, new int[] {0, 0}, false, 0, false, -1, -1, true, 0, false, 0, 0, false, false),
		// II gets its own thing: now and then a short bout of hiccups (about one hiccup every 20 s)
		new Level(2, 3.0F, 0.32F, 0, 0, 0.20, 0.25, 45 * S, new int[] {2, 3}, false, 0, false, -1, -1, true, 0, false, 0, 0, false, false),
		// III, peak drunk: the most courage, slurred chat, short blinks; no Nausea yet, buffs still work
		new Level(3, 4.5F, 0.40F, 24 * S, 0, 0.30, 0.35, 35 * S, new int[] {3, 5}, true, 0, false, -1, -1, true, 0, false, 0, 0, false, false),
		// IV, the turn: Nausea now and then, more and long blinks, Weakness, no buffs, Rowdy, cut off, the courage is gone
		new Level(4, 7.8F, 0.64F, 16 * S, 3, 0.0, 0.0, 30 * S, new int[] {3, 5}, true, 25 * S, true, -1, -1, false, 30 * S, true, 0, 0, false, false),
		// V, really drunk: legs (drift, stumbles, trips, Slowness II), the spin (sway, FOV, Nausea in waves), heavy eyes
		new Level(5, 15.6F, 0.8F, 7 * S, 2, 0.0, 0.0, 25 * S, new int[] {3, 5}, true, 10 * S, true, 1, 0, false, 15 * S, true, 3 * S, 20, true, true));

	/** Sprinting at V, one in this many unsteady-legs rolls is a trip. */
	public static final int SPRINT_TRIP_ONE_IN = 8;

	private TipsyLevels() {
	}

	/** {@return what this level does; 0 and below is sober, above V is V} */
	public static Level at(int level) {
		if (level <= 0) {
			return SOBER;
		}
		return LEVELS.get(Math.min(level, LEVELS.size()) - 1);
	}

	/** {@return the first level that does this, or 0 if none does} */
	public static int first(Predicate<Level> does) {
		for (Level level : LEVELS) {
			if (does.test(level)) {
				return level.level();
			}
		}
		return 0;
	}
}
