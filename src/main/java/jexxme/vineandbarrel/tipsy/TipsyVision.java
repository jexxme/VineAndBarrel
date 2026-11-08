package jexxme.vineandbarrel.tipsy;

/**
 * The tunnel vision and the heavy-eyed blinks of a tipsy player, kept free of client classes so it is tested headless
 * (like {@link TipsyWobble}). Playtest 1: being drunk should be felt, not only seen in the effect list.
 * <ul>
 *   <li><b>Tunnel vision</b> from level 1: the screen edges darken by the same step with every level.</li>
 *   <li><b>Blinks</b> from level 3: now and then the eyes slowly close and open again. At 4 and 5 they come more often,
 *   and some are long ones where the eyes stay shut for a moment.</li>
 * </ul>
 * The level may be fractional (the client eases between levels), so nothing jumps when a level changes.
 */
public final class TipsyVision
{
	/** Darkness of the screen edges for level 0..5 (alpha of the vignette, 0 = none): linear, from level 1. */
	private static final float[] VIGNETTE = {0.0F, 0.16F, 0.32F, 0.48F, 0.64F, 0.8F};

	/** Ticks between blinks (one blink per slot of this length) for level 3, 4 and 5. */
	private static final int[] BLINK_SLOT = {24 * 20, 16 * 20, 7 * 20};
	/** Of every this many blinks, one is a long one, for level 3, 4 and 5 (0 = never). */
	private static final int[] LONG_ONE_IN = {0, 3, 2};
	static final int SHORT_BLINK_TICKS = 12;
	static final int LONG_BLINK_TICKS = 36;
	/** Share of a blink spent closing, and opening again; the rest the eyes stay shut. */
	private static final float CLOSE_SHARE = 0.3F;
	private static final float OPEN_SHARE = 0.4F;

	private TipsyVision() {
	}

	/** {@return how dark the screen edges are (0..1) at this (possibly fractional) level} */
	public static float vignette(float level) {
		if (level <= 0.0F) {
			return 0.0F;
		}
		int max = VIGNETTE.length - 1;
		if (level >= max) {
			return VIGNETTE[max];
		}
		int low = (int) level;
		float t = level - low;
		return VIGNETTE[low] + (VIGNETTE[low + 1] - VIGNETTE[low]) * t;
	}

	/**
	 * How far the eyes are closed at game time {@code time} (ticks, may be fractional): 0 open, 1 shut. Blinks only
	 * happen from level 3, at a spot in each slot that depends on {@code seed} so two players never blink together.
	 */
	public static float eyelids(int level, float time, int seed) {
		if (level < 3) {
			return 0.0F;
		}
		int index = Math.min(level, 5) - 3;
		int slotLength = BLINK_SLOT[index];
		long slot = (long) Math.floor(time / slotLength);
		int hash = mix(slot * 31L + seed);
		boolean longOne = LONG_ONE_IN[index] > 0 && Math.floorMod(hash >>> 8, LONG_ONE_IN[index]) == 0;
		int duration = longOne ? LONG_BLINK_TICKS : SHORT_BLINK_TICKS;
		// The blink starts somewhere in the first part of the slot, so it never runs into the next one
		int start = Math.floorMod(hash, slotLength - duration);
		float t = (time - slot * slotLength - start) / duration;
		if (t <= 0.0F || t >= 1.0F) {
			return 0.0F;
		}
		if (t < CLOSE_SHARE) {
			return smooth(t / CLOSE_SHARE);
		}
		if (t > 1.0F - OPEN_SHARE) {
			return smooth((1.0F - t) / OPEN_SHARE);
		}
		return 1.0F;
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}

	private static int mix(long value) {
		long z = value * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return (int) (z ^ (z >>> 31)) & Integer.MAX_VALUE;
	}
}
