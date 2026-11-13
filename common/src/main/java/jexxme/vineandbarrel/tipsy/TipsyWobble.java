package jexxme.vineandbarrel.tipsy;

/**
 * The camera angles Tipsy adds, kept free of client classes so it is tested on a headless server.
 * <p>
 * Tipsy never moves your own screen on its own (Tipsy overhaul, 2026-11-13: motion you didn't cause is what makes people
 * sick): your view only moves with your body (the drunk walk's lean, a hiccup's flick, a stumble's dip, a trip). The
 * walk and the lean are {@link TipsyGait}.
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
}
