package jexxme.vineandbarrel.tipsy;

/**
 * The camera wobble and body sway math, kept free of client classes so it can be tested on a headless server.
 * <p>
 * Slow, smooth and mostly roll (a gentle head tilt). Yaw and pitch only move a fraction of a degree, so aiming stays
 * true. Level 1-2 is slight, level 3 and up is clearly stronger (DESIGN.md §6). The level may be fractional: the client
 * eases between levels so a change never jerks the camera.
 */
public final class TipsyWobble
{
	/** Peak roll in degrees at intensity 1, for level 0..5, before {@link #STRENGTH}. */
	private static final float[] ROLL_DEGREES = {0.0F, 1.3F, 2.3F, 4.2F, 6.0F, 12.0F};
	/** Playtest 1: the wobble is 30% stronger at every level. The client's wobble intensity still scales it (0 = off). */
	public static final float STRENGTH = 1.3F;
	/** The aim drift is sized from at most this roll (the old level-5 value), however strong the roll gets. */
	private static final float MAX_AIM_BASE = 5.5F;
	/** How much the whole body leans compared with the camera roll, as other players see it. */
	private static final float BODY_SWAY_SHARE = 0.6F;

	private TipsyWobble() {
	}

	public record Angles(float roll, float pitch, float yaw)
	{
		public static final Angles NONE = new Angles(0.0F, 0.0F, 0.0F);
	}

	/** {@return the peak roll in degrees for a (possibly fractional) level at intensity 1} */
	public static float amplitude(float level) {
		if (level <= 0.0F) {
			return 0.0F;
		}
		int max = ROLL_DEGREES.length - 1;
		if (level >= max) {
			return ROLL_DEGREES[max] * STRENGTH;
		}
		int low = (int) level;
		float t = level - low;
		return (ROLL_DEGREES[low] + (ROLL_DEGREES[low + 1] - ROLL_DEGREES[low]) * t) * STRENGTH;
	}

	/**
	 * @param level     Tipsy level, may be fractional while easing
	 * @param time      game ticks plus partial tick
	 * @param intensity the client's wobble intensity; 0 means off
	 * @return the camera offsets in degrees
	 */
	public static Angles camera(float level, float time, double intensity) {
		float a = amplitude(level) * (float) Math.max(0.0, intensity);
		if (a <= 0.0F) {
			return Angles.NONE;
		}
		float roll = a * (0.7F * sin(time * 0.060F) + 0.3F * sin(time * 0.137F + 1.3F));
		// Only the roll got stronger in playtest 1; the aim drifts no more than before (5.5 degrees at most), so aiming stays true
		float aim = Math.min(a / STRENGTH, MAX_AIM_BASE * (float) Math.max(0.0, intensity));
		float pitch = aim * 0.15F * sin(time * 0.045F + 0.7F);
		float yaw = aim * 0.12F * sin(time * 0.033F + 2.1F);
		return new Angles(roll, pitch, yaw);
	}

	/**
	 * The sideways lean other players see (T1). Each entity gets its own phase so a tavern full of tipsy players does
	 * not sway in lockstep.
	 */
	public static float bodySway(int level, float time, int entityId) {
		float a = amplitude(level) * BODY_SWAY_SHARE;
		if (a <= 0.0F) {
			return 0.0F;
		}
		float phase = (entityId * 0.618034F) % 1.0F * (float) (Math.PI * 2.0);
		return a * (0.75F * sin(time * 0.055F + phase) + 0.25F * sin(time * 0.121F + phase * 1.7F));
	}

	private static float sin(float x) {
		return (float) Math.sin(x);
	}
}
