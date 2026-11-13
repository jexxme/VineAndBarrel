package jexxme.vineandbarrel.tipsy;

import net.minecraft.util.Mth;

/**
 * Drunk eyes (Tipsy overhaul, step 4): from III a second image that drifts slowly apart from the first and back (double
 * vision), more with each level, and from I soft, slightly warm edges. Holding sneak squints: the two images come
 * together and the edges sharpen a little. Drawn by a post shader ({@code client.DrunkEyes}); the math is here, free of
 * client classes, so it is tested headless. Off with the player's own "Double vision" setting.
 */
public final class TipsyEyes
{
	/** The widest the two images ever get apart, as a share of the screen's width (the shader's MAX_SPLIT). */
	public static final float MAX_SPLIT = 0.03F;
	/** How strong the second image is when it shows at all, and how much more at the most double vision. */
	public static final float GHOST = 0.22F, GHOST_MORE = 0.22F;
	/** Double vision at which {@link #GHOST_MORE} is all there, and at which squinting helps least. */
	public static final float FULL_DOUBLE = 0.0185F;
	/** Double vision at which squinting helps most (III's). */
	public static final float LEAST_DOUBLE = 0.004F;
	/**
	 * Squinting (holding sneak) keeps this share of the split, of the soft edges and of the blur on signs, and this much
	 * more of the split and the blur the drunker you are (at V you can't quite see straight however hard you try).
	 */
	public static final float SQUINT_SPLIT = 0.12F, SQUINT_EDGES = 0.6F, SQUINT_SIGNS = 0.3F, SQUINT_DRUNKER = 0.38F;
	/** At full sign blur the writing is smeared this far (the font's pixels) around where it is. */
	public static final float SIGN_SMEAR = 1.6F;
	/** Per tick, how far the eyes ease toward a new level or a squint: over about a second, never a jump. */
	public static final float EASE = 0.08F;

	/**
	 * What the shader gets: how far apart (share of {@link #MAX_SPLIT}), how strong the second image, how soft the edges,
	 * and how much the morning after's daylight glares ({@link Hangover}), each 0 to 1.
	 */
	public record Look(float split, float ghost, float edges, float glare)
	{
		public static final Look NONE = new Look(0.0F, 0.0F, 0.0F, 0.0F);

		public boolean any() {
			return split > 0.001F || edges > 0.001F || glare > 0.001F;
		}

		/** {@return this look with the glare at {@code glare}} */
		public Look withGlare(float glare) {
			return new Look(split, ghost, edges, glare);
		}

		/** {@return this look one tick on, eased toward {@code target}} */
		public Look toward(Look target) {
			return new Look(Mth.approach(split, target.split, EASE), Mth.approach(ghost, target.ghost, EASE),
				Mth.approach(edges, target.edges, EASE), Mth.approach(glare, target.glare, GLARE_EASE));
		}
	}

	/** Hungover, how much daylight under the open sky glares, and the share of it left squinting. */
	public static final float GLARE = 1.0F, SQUINT_GLARE = 0.35F;
	/**
	 * Fresh out of a blackout or out of bed the morning after hits hardest: this much more glare outdoors, and this much
	 * indoors too, wearing off over {@link #FRESH_TICKS}. The most glare there is, {@link #MOST_GLARE}, is what the shader's
	 * channel stands for.
	 */
	public static final float FRESH_GLARE = 0.5F, FRESH_INDOORS = 0.8F, MOST_GLARE = 1.5F;
	public static final int FRESH_TICKS = 30 * 20;
	/** The glare comes and goes slower than the eyes (stepping out of a door into the sun). */
	public static final float GLARE_EASE = 0.04F;

	/**
	 * {@return how much daylight glares for a hungover player (0 to 1)}
	 *
	 * @param outdoors whether the open sky is right above them in daylight
	 * @param fresh    how fresh the hangover is, 1 the moment it started down to 0 after {@link #FRESH_TICKS}
	 * @param rain     how much it rains (0 to 1): an overcast sky glares less
	 * @param squint   how far into a squint (0 to 1)
	 */
	public static float glare(boolean hungover, boolean outdoors, float fresh, float rain, float squint) {
		if (!hungover) {
			return 0.0F;
		}
		float f = Mth.clamp(fresh, 0.0F, 1.0F);
		float glare = outdoors ? (GLARE + FRESH_GLARE * f) * (1.0F - 0.7F * Mth.clamp(rain, 0.0F, 1.0F)) : FRESH_INDOORS * f;
		return glare * Mth.lerp(Mth.clamp(squint, 0.0F, 1.0F), 1.0F, SQUINT_GLARE);
	}

	/** {@return how fresh a hangover seen {@code ticks} ago is: 1 at once, easing to 0 after {@link #FRESH_TICKS}} */
	public static float fresh(float ticks) {
		float t = Mth.clamp(ticks / FRESH_TICKS, 0.0F, 1.0F);
		return (1.0F - t) * (1.0F - t);
	}

	private TipsyEyes() {
	}

	/** {@return how blurred the writing on signs looks (0 sharp, 1 unreadable) at this level's {@code eyes}, squinting {@code squint}} */
	public static float signBlur(TipsyLevels.Eyes eyes, float squint) {
		return Mth.clamp(eyes.signBlur(), 0.0F, 1.0F) * Mth.lerp(Mth.clamp(squint, 0.0F, 1.0F), 1.0F, SQUINT_SIGNS + SQUINT_DRUNKER * drunker(eyes));
	}

	/** {@return how far past III's double vision the eyes are, 0 (III) to 1 (V)} */
	private static float drunker(TipsyLevels.Eyes eyes) {
		return Mth.clamp((eyes.doubleVision() - LEAST_DOUBLE) / (FULL_DOUBLE - LEAST_DOUBLE), 0.0F, 1.0F);
	}

	/**
	 * The blurred writing is drawn as the sharp line, fainter, and {@link #SMEAR_COPIES} faint copies round it.
	 * {@return the alpha (0 to 1) of the sharp line at this blur}
	 */
	public static float sharpAlpha(float blur) {
		return 1.0F - 0.68F * Mth.clamp(blur, 0.0F, 1.0F);
	}

	/** {@return the alpha (0 to 1) of each faint copy at this blur} */
	public static float copyAlpha(float blur) {
		return 0.3F * Mth.clamp(blur, 0.0F, 1.0F);
	}

	/**
	 * How many faint copies a blurred line gets: none. Copies of the writing looked like a rendering bug (Jexxme, playtest
	 * 3); the writing fades into the wood instead, and the drunk eyes' own double vision does the rest.
	 */
	public static final int SMEAR_COPIES = 0;

	/**
	 * {@return how a player's eyes see at this level's {@code eyes}}
	 *
	 * @param squint how far into a squint (0 eyes open, 1 squinting)
	 */
	public static Look look(TipsyLevels.Eyes eyes, float squint) {
		if (eyes.doubleVision() <= 0.0F && eyes.softEdges() <= 0.0F) {
			return Look.NONE;
		}
		float s = Mth.clamp(squint, 0.0F, 1.0F);
		float split = Mth.clamp(eyes.doubleVision() / MAX_SPLIT, 0.0F, 1.0F) * Mth.lerp(s, 1.0F, SQUINT_SPLIT + SQUINT_DRUNKER * drunker(eyes));
		float ghost = eyes.doubleVision() > 0.0F ? GHOST + GHOST_MORE * Mth.clamp(eyes.doubleVision() / FULL_DOUBLE, 0.0F, 1.0F) : 0.0F;
		float edges = Mth.clamp(eyes.softEdges(), 0.0F, 1.0F) * Mth.lerp(s, 1.0F, SQUINT_EDGES);
		return new Look(split, ghost, edges, 0.0F);
	}
}
