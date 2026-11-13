package jexxme.vineandbarrel.tipsy;

/**
 * The camera sway, the FOV breathing and the body sway math, kept free of client classes so it can be tested on a
 * headless server.
 * <p>
 * Slow, smooth and mostly roll (a gentle head tilt). Yaw and pitch only move a fraction of a degree, so aiming stays
 * true. How far it rolls at each level is {@link TipsyLevels.Level#rollDegrees}. The level may be fractional: the client
 * eases between levels so a change never jerks the camera.
 */
public final class TipsyWobble
{
	/** The aim drifts this much of the roll, and never more than {@link #MAX_AIM}, however strong the roll gets. */
	private static final float AIM_PER_ROLL = 1.0F / 1.3F;
	private static final float MAX_AIM = 5.5F;
	/** How much the whole body leans compared with the camera roll, as other players see it. */
	public static final float BODY_SWAY_SHARE = 0.6F;
	/** At V the field of view breathes in and out by this share, in step with the sway (FOV Effects scales it). */
	public static final float FOV_BREATHING = 0.035F;

	private TipsyWobble() {
	}

	public record Angles(float roll, float pitch, float yaw)
	{
		public static final Angles NONE = new Angles(0.0F, 0.0F, 0.0F);

		public Angles plus(float roll, float pitch, float yaw) {
			return roll == 0.0F && pitch == 0.0F && yaw == 0.0F ? this : new Angles(this.roll + roll, this.pitch + pitch, this.yaw + yaw);
		}

		public Angles scaled(float by) {
			return by == 1.0F || this == NONE ? this : new Angles(roll * by, pitch * by, yaw * by);
		}

		public boolean isNone() {
			return roll == 0.0F && pitch == 0.0F && yaw == 0.0F;
		}
	}

	/** {@return the peak roll in degrees for a (possibly fractional) level at intensity 1} */
	public static float amplitude(float level) {
		return lerpLevels(level, TipsyLevels.Level::rollDegrees);
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
		// Only the roll grows strong; the aim drifts no more than 5.5 degrees' worth, so aiming stays true
		float aim = Math.min(a * AIM_PER_ROLL, MAX_AIM * (float) Math.max(0.0, intensity));
		float pitch = aim * 0.15F * sin(time * 0.045F + 0.7F);
		float yaw = aim * 0.12F * sin(time * 0.033F + 2.1F);
		return new Angles(roll, pitch, yaw);
	}

	/**
	 * {@return the factor the field of view is multiplied by: at V it breathes in step with the sway's main swing, eased
	 * in from IV} 1 means unchanged.
	 */
	public static float fov(float level, float time, double intensity) {
		float breathing = Math.max(0.0F, Math.min(1.0F, level - (TipsyLevels.first(TipsyLevels.Level::fovBreathing) - 1)));
		if (breathing <= 0.0F || intensity <= 0.0) {
			return 1.0F;
		}
		return 1.0F + FOV_BREATHING * breathing * (float) intensity * sin(time * 0.060F);
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

	/** {@return a per-level value of the table, eased linearly between levels} */
	static float lerpLevels(float level, java.util.function.ToDoubleFunction<TipsyLevels.Level> value) {
		if (level <= 0.0F) {
			return 0.0F;
		}
		int max = TipsyLevels.LEVELS.size();
		if (level >= max) {
			return (float) value.applyAsDouble(TipsyLevels.at(max));
		}
		int low = (int) level;
		float t = level - low;
		float from = (float) value.applyAsDouble(TipsyLevels.at(low));
		float to = (float) value.applyAsDouble(TipsyLevels.at(low + 1));
		return from + (to - from) * t;
	}

	private static float sin(float x) {
		return (float) Math.sin(x);
	}
}
