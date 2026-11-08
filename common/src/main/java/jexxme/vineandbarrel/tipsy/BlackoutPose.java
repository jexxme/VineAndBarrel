package jexxme.vineandbarrel.tipsy;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The blackout as the clients draw it, as pure math over the ticks left (with the partial tick), so it is tested
 * headless like {@link TipsyWobble}: how shut the eyes are, how far the body has tipped over, where the head is.
 */
public final class BlackoutPose
{
	/** The body tips this far onto its side (others see it), the camera rolls this far (the player sees it). */
	public static final float BODY_DEGREES = 90.0F;
	public static final float CAMERA_ROLL = 80.0F;
	/** Lying, the body is lifted this much so it rests on the ground instead of sinking into it. */
	public static final float LIFT = 0.25F;
	/** Lying, the camera sits this high above the ground. */
	public static final float LYING_EYES = 0.3F;
	/** After the thud, the body rocks back this much once (as a share of the fall). */
	private static final float BOUNCE = 0.08F;
	private static final float BOUNCE_TICKS = 5.0F;
	/** Opening, the eyes give up once halfway (how shut they get again) before they open for good. */
	private static final float BLINK = 0.85F;

	private BlackoutPose() {
	}

	/** {@return how shut the eyes are (0 open, 1 shut) with {@code left} ticks of the blackout left} */
	public static float eyesClosed(float left) {
		float elapsed = Blackout.TICKS - left;
		if (elapsed < Blackout.FADE_OUT) {
			float t = Math.max(0.0F, elapsed) / Blackout.FADE_OUT;
			// Heavy lids: slow at first, then all at once
			return t * t;
		}
		if (left > Blackout.EYES_OPEN) {
			return 1.0F;
		}
		if (left <= Blackout.EYES_UP) {
			return 0.0F;
		}
		// Opening: a first try to half open, a droop back, then open for good
		float t = (Blackout.EYES_OPEN - left) / (Blackout.EYES_OPEN - Blackout.EYES_UP);
		if (t < 0.45F) {
			return Mth.lerp(smooth(t / 0.45F), 1.0F, 0.45F);
		}
		if (t < 0.6F) {
			return Mth.lerp(smooth((t - 0.45F) / 0.15F), 0.45F, BLINK);
		}
		return Mth.lerp(smooth((t - 0.6F) / 0.4F), BLINK, 0.0F);
	}

	/**
	 * {@return how far a player lying on the ground has tipped over (0 standing, 1 lying) with {@code left} ticks left:
	 * a fall that speeds up, a little rock back after the thud, lying, then getting up at the end}
	 */
	public static float lying(float left) {
		if (left <= 0.0F) {
			return 0.0F;
		}
		float elapsed = Blackout.TICKS - left;
		if (elapsed < Blackout.TIP) {
			float t = Math.max(0.0F, elapsed) / Blackout.TIP;
			return t * t;
		}
		if (left < Blackout.GET_UP) {
			return smooth(left / Blackout.GET_UP);
		}
		if (elapsed < Blackout.TIP + BOUNCE_TICKS) {
			return 1.0F - BOUNCE * Mth.sin((elapsed - Blackout.TIP) / BOUNCE_TICKS * Mth.PI);
		}
		return 1.0F;
	}

	/** {@return whether the player lies still now (fallen, not yet getting up): the stars circle then} */
	public static boolean lyingStill(float left) {
		return Blackout.TICKS - left >= Blackout.TIP + BOUNCE_TICKS && left >= Blackout.GET_UP;
	}

	/** {@return which side this player falls to, +1 or -1: the same on every client} */
	public static int side(int entityId) {
		return (entityId & 1) == 0 ? 1 : -1;
	}

	/** {@return the body's tilt in degrees around its feet, toward its side} */
	public static float bodyAngle(float lying, int side) {
		return side * BODY_DEGREES * lying;
	}

	/**
	 * {@return where a point {@code height} above the feet ends up, relative to the feet, once the body facing
	 * {@code bodyRot} has tipped by {@code angle} degrees (the same turn the renderer makes: face the body's way, tip
	 * around the Z axis, turn back), lifted by {@link #LIFT} times {@code lying}}
	 */
	public static Vec3 tipped(float bodyRot, float angle, float lying, double height) {
		double facing = (180.0F - bodyRot) * Mth.DEG_TO_RAD;
		double tilt = angle * Mth.DEG_TO_RAD;
		double sideways = -height * Math.sin(tilt);
		return new Vec3(sideways * Math.cos(facing), height * Math.cos(tilt) + LIFT * lying, -sideways * Math.sin(facing));
	}

	private static float smooth(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}
}
