package jexxme.vineandbarrel.tipsy;

import net.minecraft.util.Mth;

/**
 * The drunk walk (Tipsy overhaul, step 2): drunk shows in the body, not on the screen, and it is part of the walk, not
 * something pushing it (Jexxme on the first films: "looks like something shoves him from the side"). The client plays
 * it ({@code client.DrunkLegs}, {@code client.TipsyClient}); the math is here, free of client classes, so it is tested
 * headless.
 * <ul>
 *   <li><b>Late steering</b>: what you press arrives a little late, so you start late and can't stop at once
 *   ({@link TipsyLevels.Legs#steer}).</li>
 *   <li><b>Curves</b>: the walk turns off its course and back, a slow curve every {@link #WEAVE_BLOCKS} blocks, so you
 *   keep correcting ({@link TipsyLevels.Legs#weave}). The legs walk the curve (the body turns with it, as when you walk
 *   round a corner), and the body leans into it. It is the real path: friends see you weave.</li>
 *   <li><b>Tipping onto each foot</b>: in step with the legs, the body tips onto each foot as it lands
 *   ({@link TipsyLevels.Legs#stepRoll}), the swaying walk of someone drunk.</li>
 *   <li><b>Uneven steps</b>: every other step comes slower, a stagger ({@link TipsyLevels.Legs#stride}).</li>
 *   <li><b>Standing</b>: a slow sway in a loop, side to side and a little back and forth
 *   ({@link TipsyLevels.Legs#swayDegrees}).</li>
 * </ul>
 * Your own view only gets a small share of the body's tipping and leaning ({@link TipsyLevels.Legs#leanDegrees}), and
 * never moves on its own.
 */
public final class TipsyGait
{
	/** Blocks walked per full curve (off to one side and back the other way): about four steps. */
	public static final float WEAVE_BLOCKS = 3.4F;
	/** The game's walk animation: the legs swing with cos(position x this); one step is half a swing. */
	public static final float LEG_SWING = 0.6662F;
	/** Turning (degrees/tick) at which a body leans into a curve as far as it does at all. */
	public static final float FULL_LEAN_TURN = 2.5F;
	/** Per tick, how far a lean eases toward its target: a heavy body, not a camera rig. */
	public static final float LEAN_EASE = 0.12F;
	/** The body others see sways all the time, slowly, this share of its sway to the side and half that back and forth. */
	public static final float STANDING_SWAY = 0.6F;
	/** Your own view gets this share of the body's tipping onto each foot. */
	public static final float OWN_STEP_SHARE = 0.25F;

	private TipsyGait() {
	}

	/** {@return the steered amount after one tick: {@code current} eased toward {@code pressed} by {@code steer}} */
	public static float steer(float current, float pressed, float steer) {
		return current + (pressed - current) * Mth.clamp(steer, 0.0F, 1.0F);
	}

	/**
	 * {@return how far the walk is turned off its course (degrees, positive to the left) at this many blocks walked, for a
	 * body whose curves start at {@code phase} (radians)}
	 */
	public static float weave(float walked, float phase, float most) {
		if (most <= 0.0F) {
			return 0.0F;
		}
		// a slow curve with a smaller, quicker one on top: never quite the same twice, so it doesn't feel mechanical
		float a = walked / WEAVE_BLOCKS * Mth.TWO_PI + phase;
		return most * (0.8F * Mth.sin(a) + 0.2F * Mth.sin(1.9F * a + phase * 1.3F));
	}

	/**
	 * {@return what the walking speed is multiplied by: every other step comes slower, by up to {@code stride}, so the
	 * steps go long, short, long, short} {@code walkPos} is the game's walk animation position.
	 */
	public static float stride(float walkPos, float stride) {
		if (stride <= 0.0F) {
			return 1.0F;
		}
		// one full leg swing is two steps: the slow one is the second
		float s = Mth.sin(walkPos * LEG_SWING * 0.5F);
		return 1.0F - stride * s * s;
	}

	/**
	 * {@return how far the body tips onto the foot that is landing (degrees, positive to the left), in step with the legs}
	 *
	 * @param walkPos   the game's walk animation position
	 * @param walkSpeed the game's walk animation speed (0 standing, about 1 walking)
	 */
	public static float stepRoll(float walkPos, float walkSpeed, float most) {
		if (most <= 0.0F) {
			return 0.0F;
		}
		// The right leg is forward at cos = 1; a quarter swing later it carries the body: tip onto it (to the right)
		return -most * Mth.sin(walkPos * LEG_SWING) * Mth.clamp(walkSpeed * 1.5F, 0.0F, 1.0F);
	}

	/** {@return a body's own phase: two players never weave or sway in step} */
	public static float phase(int entityId) {
		return (entityId * 0.618034F) % 1.0F * Mth.TWO_PI;
	}

	/**
	 * {@return where a lean into a curve wants to be (degrees, positive to the left)}
	 *
	 * @param turning how fast the walk turns (degrees per tick, positive to the right, Minecraft's yaw)
	 * @param most    the most it leans
	 */
	public static float leanTarget(float turning, float most) {
		if (most <= 0.0F) {
			return 0.0F;
		}
		return Mth.clamp(-turning / FULL_LEAN_TURN, -1.0F, 1.0F) * most;
	}

	/** {@return a lean one tick on, eased toward its target} */
	public static float ease(float lean, float target) {
		return lean + (target - lean) * LEAN_EASE;
	}

	/** {@return the body's slow sway to the side (degrees, positive to its left) at this level and time, for others to see} */
	public static float standingSway(float most, float time, float phase) {
		return most * STANDING_SWAY * (0.8F * Mth.sin(time * 0.045F + phase) + 0.2F * Mth.sin(time * 0.07F + phase * 1.7F));
	}

	/**
	 * {@return the same sway's part back and forth (degrees, positive forward): with the side part it goes round in a slow
	 * loop, the way someone drunk stands, not a pendulum}
	 */
	public static float standingSwayForward(float most, float time, float phase) {
		return most * STANDING_SWAY * 0.5F * Mth.cos(time * 0.045F + phase);
	}

	/** Below this speed (blocks per tick) a body isn't walking anywhere: its heading is noise, nothing to lean into. */
	public static final double HEADING_SPEED = 0.04;
	/** A body never turns its walk faster than this (degrees per tick): anything faster is a stop, a start or a bump. */
	public static final float MOST_TURNING = 12.0F;

	/** {@return the direction (degrees of yaw) a horizontal motion goes, or {@code fallback} when it hardly moves} */
	public static float heading(double dx, double dz, float fallback) {
		if (dx * dx + dz * dz < HEADING_SPEED * HEADING_SPEED) {
			return fallback;
		}
		return (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
	}

	/** {@return how fast a walk turns (degrees per tick, positive to the right) from {@code from} to {@code to}, kept to what a walk can do} */
	public static float turning(float from, float to) {
		return Mth.clamp(Mth.wrapDegrees(to - from), -MOST_TURNING, MOST_TURNING);
	}
}
