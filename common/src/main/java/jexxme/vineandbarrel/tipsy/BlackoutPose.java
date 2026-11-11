package jexxme.vineandbarrel.tipsy;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The blackout as the clients draw it, as pure math over the ticks left (with the partial tick), so it is tested
 * headless like {@link TipsyWobble}: how shut the eyes are, and a player out on the ground ({@link #body}): falling on
 * the face, lying there with the cheek in the grass, and hauling themselves up again.
 */
public final class BlackoutPose
{
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

	/** {@return which way this player's head turns lying on the face, +1 to its right or -1 to its left: the same on every client} */
	public static int side(int entityId) {
		return (entityId & 1) == 0 ? 1 : -1;
	}

	/** {@return whether the player lies still now (fallen, not yet getting up): the stars circle then} */
	public static boolean lyingStill(float left) {
		return Blackout.TICKS - left >= Blackout.TIP + BOUNCE && left >= Blackout.GROUND_GET_UP;
	}

	/** {@return a point of the body's own frame ({@link Body}) turned the way a body facing {@code yaw} faces} */
	public static Vec3 toWorld(float yaw, Vec3 body) {
		double facing = (180.0F - yaw) * Mth.DEG_TO_RAD;
		double cos = Math.cos(facing), sin = Math.sin(facing);
		return new Vec3(body.x * cos + body.z * sin, body.y, -body.x * sin + body.z * cos);
	}

	private static float smooth(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	// ------------------------------------------------------------------ the body on the ground

	/*
	 * Worked out in the body's own frame, in model pixels from the feet: x to its right, y up, z to its back (it faces -z),
	 * the frame the game draws a standing player in. The body turns as a whole around its hip (12 pixels up), the head and
	 * the limbs turn on it at their joints. Everything is for a head that turns to the right (side +1) and is mirrored for
	 * the other side at the end.
	 *
	 * The fall: the whole body tips forward around the front edge of the feet, like a felled tree, the head turning aside
	 * as it goes, the arms flung out. Lying: face down, the cheek on the ground, the arms out at the sides by the hips.
	 * Getting up, nothing slides: the toes stay where they are on the ground and the hands where they lay, so the body can
	 * only rise one way (the legs, the torso and the straight arms are a linkage with the ground): up off the ground as a
	 * plank on toes and hands, the arms swinging in under it, onto all fours, then the legs straighten until the feet are
	 * flat, bent over with the hands on the ground; the hands let go and the body rolls up, rocks back onto the heels,
	 * and the toes come down again.
	 */

	/** One model pixel in blocks: the player model is drawn at 15/16. */
	public static final double PX = 0.9375 / 16.0;
	/** The hip, where the body turns as a whole, above the feet (blocks), as the game draws a standing player. */
	public static final double HIP = 0.9375 * (1.501 - 0.75);
	private static final double HIP_PX = HIP / PX;
	/** How far the game's standing soles are above the ground (pixels). */
	private static final double SOLE = HIP_PX - 12.0;
	/**
	 * Lying, what lies on the ground rests this far above it (pixels): the sleeves, trousers and jacket (a quarter pixel
	 * thicker than the arms, legs and body) just touch it, and nothing flickers in the grass. The head, with its hat half a
	 * pixel thicker, rests a quarter pixel higher.
	 */
	public static final double REST = 0.3, HEAD_REST = REST + 0.25;
	/** A standing player's eyes above the feet (blocks), as the game has them. */
	public static final double STANDING_EYES = 1.62;
	/** The eyes above the neck, along the head (pixels). */
	private static final double EYES = STANDING_EYES / PX - HIP_PX - 12.0;

	/** The fall rocks back once after the thud, over this many ticks. */
	private static final float BOUNCE = 6.0F;
	/** The body turns to where it has room to lie over this many ticks of the fall ({@link #body}'s {@code turn}). */
	private static final float TURN_TICKS = 8.0F;

	/**
	 * The get-up's beats, in ticks since it started: the head comes up off the cheek; the push onto all fours, a breath
	 * there; the legs straight until the hands let go; up, then the rock onto the heels.
	 */
	public static final float LOOK = 6.0F, BENT = 22.5F;
	private static final float FOURS = 15.0F, BREATH = 18.0F, UP = 31.0F;
	/**
	 * Once the head is up off the cheek, the eyes look this far down, along the ground ahead with the horizon in view (it
	 * drops away as the body comes up).
	 */
	private static final float LOOK_PITCH = 22.0F;
	/** At these many ticks left the hands press into the ground (a soft thud) and the toes come down after the rock (a step). */
	public static final int PUSH_AT = Blackout.GROUND_GET_UP - (int) LOOK;
	public static final int STEP_AT = Blackout.GROUND_GET_UP - 35;

	/** Lying, the arms lie out from the sides this far (degrees), the hands by the hips; they push from there. */
	private static final float ARMS_OUT = 28.0F;

	/**
	 * One part on top of how the game poses it (the head, an arm, a leg): turned by {@code xRot}, {@code yRot} and
	 * {@code zRot} (radians, the model part's own angles) and its joint moved by {@code x}, {@code y}, {@code z} (model
	 * pixels). The game's own pose counts {@link Body#vanilla} as much on top.
	 */
	public record Limb(float xRot, float yRot, float zRot, float x, float y, float z)
	{
		public static final Limb NONE = new Limb(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

		Limb mirrored() {
			return new Limb(xRot, -yRot, -zRot, -x, y, z);
		}
	}

	/**
	 * A blacked-out player on the ground, at one moment. The whole body is turned {@code turn} degrees (of yaw) around the
	 * feet; its hip (where the model turns) is at {@code hip} (blocks from the feet in the body's own frame: x to its right,
	 * y up, z to its back) and it is bent {@code lean} degrees forward there; head and limbs as they say, with the game's
	 * own pose on top as much as {@code vanilla} says (0 to 1). The eyes are at {@code eye} (same frame), looking
	 * {@code viewYaw} to the right, {@code viewPitch} down and rolled {@code viewRoll} (degrees): where the player's own
	 * camera goes.
	 */
	public record Body(float turn, Vec3 hip, float lean, Limb head, Limb rightArm, Limb leftArm, Limb rightLeg, Limb leftLeg,
		float vanilla, Vec3 eye, float viewYaw, float viewPitch, float viewRoll)
	{
	}

	/**
	 * {@return a blacked-out player on the ground with {@code left} ticks of the blackout left (standing exactly as the
	 * game draws one at its first tick and at its last), the head turning to {@code side}, the body turning {@code turn}
	 * degrees as it falls (to where it has room to lie), the eyes looking {@code lookPitch} down as the game has it (it
	 * counts at the first and the last moments only); null when not out}
	 */
	public static @Nullable Body body(float left, int side, float turn, float lookPitch) {
		if (left <= 0.0F || left > Blackout.TICKS) {
			return null;
		}
		Pose pose = left > Blackout.GROUND_GET_UP ? falling(Blackout.TICKS - left) : gettingUp(Blackout.GROUND_GET_UP - left);
		return pose.body(side, turn, lookPitch);
	}

	/** {@return where the stars circle while the player lies still: above the middle of the head (blocks, the body's own frame)} */
	public static Vec3 lyingHead() {
		Pose pose = falling(Blackout.TICKS);
		return pose.headMiddle().scale(PX);
	}

	// ------------------------------------------------------------------ the beats

	/** The pose at {@code e} ticks into the blackout, until the get-up: the fall, the bounce, lying. */
	private static Pose falling(float e) {
		Pose p = new Pose();
		float t = Mth.clamp(e / Blackout.TIP, 0.0F, 1.0F);
		// Slow at first, as the balance goes, then faster and faster into the ground
		double lean = 90.0 * Math.pow(t, 2.2);
		if (e > Blackout.TIP && e < Blackout.TIP + BOUNCE) {
			// The thud: the upper body rocks up off the ground once (on the toes) and drops back
			float b = (e - Blackout.TIP) / BOUNCE;
			lean -= 4.5 * Mth.sin(b * Mth.PI) * (1.0F - b);
		}
		// Rigid around the front edge of the feet, which sits a little higher by the end (so the body rests, see REST)
		double lift = (REST - SOLE) * t;
		double a = Math.toRadians(lean);
		p.hipY = SOLE + lift + 12.0 * Math.cos(a) + 2.0 * Math.sin(a);
		p.hipZ = -2.0 - 12.0 * Math.sin(a) + 2.0 * Math.cos(a);
		p.lean = lean;
		p.turn = smooth(e / TURN_TICKS);
		p.vanilla = 1.0F - smooth(e / 5.0F);
		// The arms fly up and out as the body goes, and come down limp at the sides
		p.armForward = curve(e, 0, 0, 3, 14, 6.5F, 10, Blackout.TIP, 0);
		p.armOut = curve(e, 0, 0, 3, 18, 7, 58, Blackout.TIP, 46, Blackout.TIP + 2.5F, 24, Blackout.TIP + BOUNCE, ARMS_OUT);
		p.legOut = curve(e, 0, 0, Blackout.TIP, 0, Blackout.TIP + 2.0F, 3.5F, Blackout.TIP + BOUNCE, 3);
		// The head flops forward as the body goes (the eyes go down into the ground), turns aside at the last moment and
		// comes down on the cheek; the cheek on the ground holds it a little off the neck (the head is twice as deep as
		// the body)
		p.headPitch = curve(e, 0, 0, 3, 5, 7.2F, 16, 9.9F, 0, Blackout.TIP + 2.0F, 4, Blackout.TIP + BOUNCE, 0);
		p.headYaw = curve(e, 0, 0, 7.2F, 0, 9.9F, 82, Blackout.TIP + 2.0F, 90, Blackout.TIP + BOUNCE, 84);
		p.headRoll = curve(e, 0, 0, Blackout.TIP, 0, Blackout.TIP + 3.0F, -6, Blackout.TIP + BOUNCE, -4);
		p.headBack = HEAD_BACK * smooth((e - 7.5F) / 2.5F);
		return p;
	}

	/** The pose at {@code u} ticks into getting up off the ground ({@link Blackout#GROUND_GET_UP} long). */
	private static Pose gettingUp(float u) {
		Pose p = new Pose();
		float end = Blackout.GROUND_GET_UP;
		p.turn = 1.0F;
		p.vanilla = smooth((u - (end - 4.0F)) / 4.0F);
		if (u < BENT) {
			// Planted: toes and hands stay where they are; how far the legs have come up drives the rest. The push gives
			// once on the way up, and on all fours the body breathes
			double beta = curve(u, 0, 90, LOOK, 90, 8.3F, 82, 11, 64, 12.4F, 67, FOURS, 37, 16.5F, 33, BREATH, 36, BENT, 0);
			Planted at = planted(beta);
			p.hipY = at.hipY;
			p.hipZ = at.hipZ;
			p.lean = at.lean;
			p.armForward = at.lean + at.arm;
			p.legForward = at.lean - beta;
		} else {
			// The hands let go, the body rolls up, rocks back onto the heels and forward again onto the soles
			double beta = curve(u, BENT, 0, 31.5F, 0, 33.5F, -6, 35.3F, 0, 36.4F, 1.2F, end, 0);
			double lean = curve(u, BENT, (float) RELEASE.lean, 24, (float) RELEASE.lean - 3, 27.5F, 62, 30, 20, UP, 2, 33.5F, -9, 35.3F, 3,
				36.6F, 2, end, 0);
			double heel = Math.toRadians(beta);
			if (beta >= 0.0) {
				p.hipY = SOLE + 12.0 * Math.cos(heel) + 2.0 * Math.sin(heel);
				p.hipZ = -2.0 - 12.0 * Math.sin(heel) + 2.0 * Math.cos(heel);
			} else {
				p.hipY = SOLE + 12.0 * Math.cos(heel) - 2.0 * Math.sin(heel);
				p.hipZ = 2.0 - 12.0 * Math.sin(heel) - 2.0 * Math.cos(heel);
			}
			p.lean = lean;
			p.legForward = lean - beta;
			// The arms come off the ground hanging, lag as the body comes up, fly forward for balance as it rocks back
			p.armForward = lean + curve(u, BENT, (float) RELEASE.arm, 24.5F, (float) RELEASE.arm + 4, 27.5F, 16, 30.5F, 2, 33, 26, 35, -6, end, 0);
		}
		p.armOut = curve(u, 0, ARMS_OUT, BENT, ARMS_OUT, 27.5F, 12, 32.5F, 22, 35.5F, 4, end, 0);
		p.legOut = curve(u, 0, 3, LOOK, 0);
		p.headBack = HEAD_BACK * (1.0F - smooth(u / LOOK));
		// The head: off the cheek and up to look ahead along the ground; it lolls on all fours; then the eyes stay on the
		// ground ahead of the hands while the body comes up under them (the head hangs as the body rolls up), and come up
		// last, to the horizon
		p.headYaw = curve(u, 0, 84, 1.5F, 80, LOOK, 0);
		p.headRoll = curve(u, 0, -4, LOOK, 0, 13.5F, 0, 16, 7, 18.5F, -5, 21, 0, 27, 0, 30, 6, 33, -4, end, 0);
		if (u < LOOK) {
			p.headPitch = curve(u, 0, 0, 2, -30, LOOK, LOOK_PITCH - 90);
		} else {
			float view = curve(u, LOOK, LOOK_PITCH, 10, 26, FOURS, 33, BREATH, 36, BENT, 44, 24.5F, 46, 28, 32, 31, 6, 33.5F, -10, 35.5F, 5, end, 0);
			p.headPitch = view - p.lean;
		}
		return p;
	}

	// ------------------------------------------------------------------ planted: the linkage of legs, torso and arms

	/** Where a hand lies on the ground (pixels, the body's own frame), and the arm's reach from the shoulder to there. */
	private static final double HAND_Y, HAND_Z, REACH_Y, REACH_Z;
	/** The planted pose as the hands let go (the legs straight up, bent over). */
	private static final Planted RELEASE;
	/** Lying, the head rests this far toward the back of the torso (pixels): the cheek on the ground holds it there. */
	private static final double HEAD_BACK;

	static {
		// Lying, the arm lies along the ground pointing back, out ARMS_OUT degrees; what touches the ground at its end is
		// the inner front edge of the hand (inside both the classic and the slim arm): worked out in the arm's own frame
		double out = Math.toRadians(ARMS_OUT);
		REACH_Y = -Math.sin(out) - 10.0 * Math.cos(out);
		REACH_Z = -2.0;
		// The shoulder lying is 10 pixels ahead of the hip, at the hip's height; the arm turned back flat (90 degrees)
		HAND_Y = REST + 2.0 + REACH_Z;
		HAND_Z = -14.0 - 10.0 - REACH_Y;
		RELEASE = planted(0.0);
		Pose lying = falling(Blackout.TICKS);
		HEAD_BACK = lying.headLift(lying.torso(), lying.head(0.0));
	}

	/**
	 * The planted get-up with the legs {@code beta} degrees from straight up (90 lying): the hip where the legs put it
	 * (the toes' front edge staying on the ground), the torso's lean that lets the straight arms reach the hands, and the
	 * arms' own angle from hanging (world; negative pointing back). Past straight down the hand rocks onto its back edge.
	 */
	private record Planted(double hipY, double hipZ, double lean, double arm)
	{
	}

	private static Planted planted(double beta) {
		double b = Math.toRadians(beta);
		double toeY = SOLE + (REST - SOLE) * Mth.clamp(beta / 90.0, 0.0, 1.0);
		double hipY = toeY + 12.0 * Math.cos(b) + 2.0 * Math.sin(b);
		double hipZ = -2.0 - 12.0 * Math.sin(b) + 2.0 * Math.cos(b);
		Planted front = reach(hipY, hipZ, HAND_Y, HAND_Z, REACH_Z);
		if (front.arm <= 0.0) {
			return front;
		}
		// Past straight down: the hand rocks over onto its back edge, 4 pixels behind
		return reach(hipY, hipZ, HAND_Y, HAND_Z + 4.0, -REACH_Z);
	}

	/** {@return the planted pose with the hip there and the hand's edge at ({@code handY}, {@code handZ}), {@code edgeZ} along the arm's depth} */
	private static Planted reach(double hipY, double hipZ, double handY, double handZ, double edgeZ) {
		double length = Math.hypot(REACH_Y, edgeZ);
		double dy = handY - hipY, dz = handZ - hipZ;
		double d = Math.hypot(dy, dz);
		double k = Mth.clamp((100.0 + d * d - length * length) / (20.0 * d), -1.0, 1.0);
		double lean = Math.atan2(-dz, dy) - Math.acos(k);
		double shoulderY = hipY + 10.0 * Math.cos(lean), shoulderZ = hipZ - 10.0 * Math.sin(lean);
		double arm = Math.atan2(handZ - shoulderZ, handY - shoulderY) - Math.atan2(edgeZ, REACH_Y);
		return new Planted(hipY, hipZ, Math.toDegrees(lean), Mth.wrapDegrees(Math.toDegrees(arm)));
	}

	// ------------------------------------------------------------------ the pose, and how it is drawn

	/** One moment, worked out: the torso, and each part's angles (degrees) on it; the limbs swing forward and out. */
	private static final class Pose
	{
		double hipY = HIP_PX, hipZ, lean;
		float turn, vanilla;
		double headPitch, headYaw, headRoll, headBack;
		double armForward, armOut, legForward, legOut;

		Body body(int side, float turn, float lookPitch) {
			double[] torso = torso();
			double[] head = head(0.0);
			double lift = headLift(torso, head);
			Vec3 hip = new Vec3(0.0, hipY, hipZ);
			// The eyes, and where they look (the game's look counting as much as it counts on the model)
			double[] look = head(vanilla * lookPitch);
			Vec3 eye = hip.add(apply(torso, neck(lift).add(apply(head, new Vec3(0.0, EYES, 0.0)))));
			Vec3 ahead = apply(torso, apply(look, new Vec3(0.0, 0.0, -1.0)));
			Vec3 up = apply(torso, apply(look, new Vec3(0.0, 1.0, 0.0)));
			float viewYaw = (float) Math.toDegrees(Math.atan2(ahead.x, -ahead.z));
			float viewPitch = (float) Math.toDegrees(Math.asin(Mth.clamp(-ahead.y, -1.0, 1.0)));
			Vec3 across = ahead.cross(new Vec3(0.0, 1.0, 0.0));
			Vec3 right = across.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : across.normalize();
			Vec3 level = right.cross(ahead);
			float viewRoll = (float) Math.toDegrees(Math.atan2(-up.dot(right), up.dot(level)));

			Limb headPart = new Limb((float) Math.toRadians(headPitch), (float) Math.toRadians(headYaw), (float) Math.toRadians(headRoll), 0.0F, 0.0F,
				(float) lift);
			Limb rightArm = limb(armForward, armOut), leftArm = limb(armForward, -armOut);
			Limb rightLeg = limb(legForward, legOut), leftLeg = limb(legForward, -legOut);
			float t = turn * this.turn;
			Vec3 hipBlocks = hip.scale(PX), eyeBlocks = eye.scale(PX);
			if (side >= 0) {
				return new Body(t, hipBlocks, (float) lean, headPart, rightArm, leftArm, rightLeg, leftLeg, vanilla, eyeBlocks, viewYaw, viewPitch,
					viewRoll);
			}
			return new Body(t, new Vec3(-hipBlocks.x, hipBlocks.y, hipBlocks.z), (float) lean, headPart.mirrored(), leftArm.mirrored(),
				rightArm.mirrored(), leftLeg.mirrored(), rightLeg.mirrored(), vanilla, new Vec3(-eyeBlocks.x, eyeBlocks.y, eyeBlocks.z), -viewYaw,
				viewPitch, -viewRoll);
		}

		/** The torso's turn: bent forward by the lean. */
		double[] torso() {
			return rx(-lean);
		}

		/** The head's turn on the neck (the game's look {@code look} degrees down on top). */
		double[] head(double look) {
			return part(headPitch + look, headYaw, headRoll);
		}

		/** The neck, from the hip, with the head moved toward the back of the torso by {@code lift} pixels. */
		Vec3 neck(double lift) {
			return new Vec3(0.0, 12.0, lift);
		}

		/**
		 * {@return how far the head is moved toward the back of the torso: as the pose has it ({@link #HEAD_BACK} lying),
		 * and never less than it takes to rest on the ground instead of in it}
		 */
		double headLift(double[] torso, double[] head) {
			double lowest = Double.MAX_VALUE;
			for (Vec3 corner : box(head, -4, 4, 0, 8, -4, 4)) {
				lowest = Math.min(lowest, hipY + apply(torso, neck(0.0).add(corner)).y);
			}
			double back = apply(torso, new Vec3(0.0, 0.0, 1.0)).y;
			double need = HEAD_REST - lowest;
			return need <= 0.0 || back < 0.2 ? headBack : Math.max(headBack, need / back);
		}

		Vec3 headMiddle() {
			double[] torso = torso();
			double lift = headLift(torso, head(0.0));
			return new Vec3(0.0, hipY, hipZ).add(apply(torso, neck(lift).add(apply(head(0.0), new Vec3(0.0, 4.0, 0.0)))));
		}
	}

	/**
	 * {@return the model part's angles for a limb swung {@code forward} degrees forward on the torso, after it was swung
	 * {@code out} degrees out to its own side (negative: the left one)}
	 */
	private static Limb limb(double forward, double out) {
		double[] turn = mul(rx(forward), rz(out));
		// The game turns a part by Z, then Y, then X, in the model's frame (x and y the other way round than here)
		double sy = -turn[6];
		double y = Math.asin(Mth.clamp(sy, -1.0, 1.0));
		double x = Math.atan2(turn[7], turn[8]);
		double z = Math.atan2(turn[3], turn[0]);
		return new Limb((float) -x, (float) -y, (float) z, 0.0F, 0.0F, 0.0F);
	}

	/** {@return the turn the game makes of a model part with these angles (degrees), in the body's own frame} */
	private static double[] part(double xDegrees, double yDegrees, double zDegrees) {
		return mul(rz(zDegrees), mul(ry(-yDegrees), rx(-xDegrees)));
	}

	private static double[] part(Limb limb) {
		return part(Math.toDegrees(limb.xRot()), Math.toDegrees(limb.yRot()), Math.toDegrees(limb.zRot()));
	}

	// ------------------------------------------------------------------ the boxes, for the tests: nothing sinks into the ground

	/**
	 * {@return the corners of each of the body's boxes (head, torso, right arm, left arm, right leg, left leg), in blocks
	 * from the feet in the body's own frame, as the game draws them for this pose (on a player whose own pose adds nothing)}
	 * The arms are the classic ones; the slim ones lie inside them.
	 */
	public static List<Vec3[]> boxes(Body body) {
		double[] torso = rx(-body.lean());
		Vec3 hip = body.hip().scale(1.0 / PX);
		List<Vec3[]> boxes = new ArrayList<>();
		boxes.add(placed(hip, torso, new Vec3(-body.head().x(), 12.0 - body.head().y(), body.head().z()), part(body.head()), -4, 4, 0, 8, -4, 4));
		boxes.add(placed(hip, torso, new Vec3(0.0, 12.0, 0.0), part(Limb.NONE), -4, 4, -12, 0, -2, 2));
		boxes.add(placed(hip, torso, new Vec3(5.0, 10.0, 0.0), part(body.rightArm()), -1, 3, -10, 2, -2, 2));
		boxes.add(placed(hip, torso, new Vec3(-5.0, 10.0, 0.0), part(body.leftArm()), -3, 1, -10, 2, -2, 2));
		boxes.add(placed(hip, torso, new Vec3(1.9, 0.0, 0.0), part(body.rightLeg()), -2, 2, -12, 0, -2, 2));
		boxes.add(placed(hip, torso, new Vec3(-1.9, 0.0, 0.0), part(body.leftLeg()), -2, 2, -12, 0, -2, 2));
		return boxes;
	}

	private static Vec3[] placed(Vec3 hip, double[] torso, Vec3 joint, double[] turn, double x0, double x1, double y0, double y1, double z0, double z1) {
		Vec3[] corners = box(turn, x0, x1, y0, y1, z0, z1);
		for (int i = 0; i < corners.length; i++) {
			corners[i] = hip.add(apply(torso, joint.add(corners[i]))).scale(PX);
		}
		return corners;
	}

	private static Vec3[] box(double[] turn, double x0, double x1, double y0, double y1, double z0, double z1) {
		Vec3[] corners = new Vec3[8];
		int i = 0;
		for (double x : new double[]{x0, x1}) {
			for (double y : new double[]{y0, y1}) {
				for (double z : new double[]{z0, z1}) {
					corners[i++] = apply(turn, new Vec3(x, y, z));
				}
			}
		}
		return corners;
	}

	// ------------------------------------------------------------------ turns (3x3, row by row)

	private static double[] rx(double degrees) {
		double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
		return new double[]{1, 0, 0, 0, c, -s, 0, s, c};
	}

	private static double[] ry(double degrees) {
		double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
		return new double[]{c, 0, s, 0, 1, 0, -s, 0, c};
	}

	private static double[] rz(double degrees) {
		double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
		return new double[]{c, -s, 0, s, c, 0, 0, 0, 1};
	}

	private static double[] mul(double[] a, double[] b) {
		double[] m = new double[9];
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				m[3 * r + c] = a[3 * r] * b[c] + a[3 * r + 1] * b[3 + c] + a[3 * r + 2] * b[6 + c];
			}
		}
		return m;
	}

	private static Vec3 apply(double[] m, Vec3 v) {
		return new Vec3(m[0] * v.x + m[1] * v.y + m[2] * v.z, m[3] * v.x + m[4] * v.y + m[5] * v.z, m[6] * v.x + m[7] * v.y + m[8] * v.z);
	}

	// ------------------------------------------------------------------ timing

	/**
	 * {@return the smooth curve through the keys (tick, value, tick, value...) at {@code u}: it eases into and out of
	 * every key where the movement turns, flows through the others, never overshoots between two keys, and is flat
	 * before the first and after the last}
	 */
	static float curve(float u, float... keys) {
		int n = keys.length / 2;
		if (u <= keys[0]) {
			return keys[1];
		}
		if (u >= keys[2 * (n - 1)]) {
			return keys[2 * n - 1];
		}
		int i = 0;
		while (u > keys[2 * (i + 1)]) {
			i++;
		}
		float t0 = keys[2 * i], v0 = keys[2 * i + 1], t1 = keys[2 * i + 2], v1 = keys[2 * i + 3];
		float h = t1 - t0;
		float m0 = slope(keys, i), m1 = slope(keys, i + 1);
		float t = (u - t0) / h, t2 = t * t, t3 = t2 * t;
		return (2 * t3 - 3 * t2 + 1) * v0 + (t3 - 2 * t2 + t) * h * m0 + (-2 * t3 + 3 * t2) * v1 + (t3 - t2) * h * m1;
	}

	/** {@return the curve's slope at key {@code i}: flat at the ends and where it turns (monotone cubic, Fritsch-Carlson)} */
	private static float slope(float[] keys, int i) {
		int n = keys.length / 2;
		if (i <= 0 || i >= n - 1) {
			return 0.0F;
		}
		float hBefore = keys[2 * i] - keys[2 * i - 2], hAfter = keys[2 * i + 2] - keys[2 * i];
		float before = (keys[2 * i + 1] - keys[2 * i - 1]) / hBefore, after = (keys[2 * i + 3] - keys[2 * i + 1]) / hAfter;
		if (before * after <= 0.0F) {
			return 0.0F;
		}
		float w1 = 2 * hAfter + hBefore, w2 = hAfter + 2 * hBefore;
		return (w1 + w2) / (w1 / before + w2 / after);
	}
}
