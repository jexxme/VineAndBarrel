package jexxme.vineandbarrel.tipsy;

/**
 * The body sway other players see, and the camera angles Tipsy adds, kept free of client classes so it is tested on a
 * headless server.
 * <p>
 * Tipsy never moves your own screen on its own (Tipsy overhaul, 2026-11-13: motion you didn't cause is what makes people
 * sick): your view only moves with your body (a hiccup's flick, a stumble's dip, a trip). The sway is what friends see
 * of you, {@link TipsyLevels.Legs#swayDegrees} at each level.
 */
public final class TipsyWobble
{
	private TipsyWobble() {
	}

	/** Degrees added to the camera's roll, pitch and yaw this frame. */
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

	/**
	 * The sideways lean other players see (degrees), slow and smooth. Each entity gets its own phase so a tavern full of
	 * tipsy players does not sway in lockstep.
	 *
	 * @param time game ticks plus partial tick
	 */
	public static float bodySway(int level, float time, int entityId) {
		float a = TipsyLevels.at(level).legs().swayDegrees();
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
