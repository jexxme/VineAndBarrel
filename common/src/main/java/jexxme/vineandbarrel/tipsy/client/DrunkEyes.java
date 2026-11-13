package jexxme.vineandbarrel.tipsy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.TipsyEyes;
import jexxme.vineandbarrel.tipsy.TipsyLevels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Drunk eyes on this client (Tipsy overhaul, step 4; the math is {@link TipsyEyes}), and the morning after's glare: the post effect
 * {@code vineandbarrel:tipsy_eyes} is drawn after the world while the eyes see anything (this version has no list of
 * the player's screen effects: {@code jexxme.vineandbarrel.mixin.GameRendererMixin}), and its shader
 * reads how strong each part is from a 1x1 texture this class writes every tick. Holding sneak squints. Off with the
 * "Double vision" setting, and while blacked out (the blackout has its own picture).
 */
public final class DrunkEyes
{
	/** The post effect ({@code post_effect/tipsy_eyes.json}). */
	public static final Identifier EFFECT = VineAndBarrel.id("tipsy_eyes");
	/** The texture the shader reads its strengths from (the post effect's "Params" input). */
	private static final Identifier PARAMS = VineAndBarrel.id("textures/effect/tipsy_eyes.png");

	private static @Nullable DynamicTexture params;
	private static TipsyEyes.Look look = TipsyEyes.Look.NONE;
	private static float squint;
	/** How blurred signs look now (0 sharp .. 1), eased. */
	private static float signBlur;

	private DrunkEyes() {
	}

	/** Once a client tick: eases the eyes toward this level's (and a squint), and puts the effect on or off. */
	static void tick(LocalPlayer player) {
		squint = Mth.approach(squint, player.isShiftKeyDown() ? 1.0F : 0.0F, 0.15F);
		TipsyEyes.Look target = TipsyEyes.Look.NONE;
		if (TipsyConfig.doubleVision()) {
			// Out cold the drunk eyes rest; the morning after is there as the eyes open
			target = (Blackout.isOut(player) ? TipsyEyes.Look.NONE : TipsyEyes.look(TipsyLevels.at(Tipsy.getLevel(player)).eyes(), squint))
				.withGlare(HangoverClient.glare(player, squint));
		}
		look = look.toward(target);
		if (HangoverClient.fresh() > 0.95F) {
			// The morning after doesn't creep in: it is there the moment the eyes open
			look = look.withGlare(target.glare());
		}
		float signTarget = TipsyConfig.doubleVision() && !Blackout.isOut(player)
			? TipsyEyes.signBlur(TipsyLevels.at(Tipsy.getLevel(player)).eyes(), squint) : 0.0F;
		signBlur = Mth.approach(signBlur, signTarget, TipsyEyes.EASE);
		active = look.any();
		if (active) {
			write(look, (player.level().getGameTime() % 24000L) / 20.0F);
		}
	}

	/** Whether the eyes see anything now: the effect is drawn after the world ({@code jexxme.vineandbarrel.mixin.GameRendererMixin}). */
	private static boolean active;

	/** {@return whether the drunk eyes' post effect is drawn this frame} */
	public static boolean active() {
		return active;
	}

	/** A new world or a disconnect: eyes open and clear (the effect goes with the old player). */
	static void reset() {
		active = false;
		look = TipsyEyes.Look.NONE;
		squint = 0.0F;
		signBlur = 0.0F;
	}

	/** {@return how much the morning after's daylight glares now, 0 to 1} */
	static float glare() {
		return look.glare();
	}

	/** {@return how blurred the writing on signs looks to this player now, 0 sharp to 1 unreadable} */
	public static float signBlur() {
		return signBlur;
	}

	/**
	 * {@return the colour a line of a sign's writing is drawn in now ({@code jexxme.vineandbarrel.mixin.AbstractSignRendererMixin}):
	 * as it is, or fainter when blurred}
	 */
	public static int signLineColor(int color) {
		return signBlur <= 0.01F ? color : withAlpha(color, TipsyEyes.sharpAlpha(signBlur));
	}

	/**
	 * When the writing is blurred, the faint copies drawn round a line (never scrambled: the letters stay the letters);
	 * nothing when it is sharp.
	 */
	public static void signSmear(SubmitNodeCollector collector, PoseStack pose, float x, float y, FormattedCharSequence line,
		Font.DisplayMode mode, int light, int color) {
		float blur = signBlur;
		if (blur <= 0.01F) {
			return;
		}
		// Smeared sideways, the way the eyes see double: faint copies left and right, near and a little further
		int faint = withAlpha(color, TipsyEyes.copyAlpha(blur));
		float r = TipsyEyes.SIGN_SMEAR * blur;
		for (int i = 0; i < TipsyEyes.SMEAR_COPIES; i++) {
			float side = i % 2 == 0 ? 1.0F : -1.0F;
			float dist = r * (i < 2 ? 0.5F : 1.0F);
			collector.submitText(pose, x + side * dist, y, line, false, mode, light, faint, 0, 0);
		}
	}

	private static int withAlpha(int color, float alpha) {
		// the sign's colours come without alpha (fully there)
		int base = (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
		return ARGB.color(Math.round(ARGB.alpha(base) * Mth.clamp(alpha, 0.0F, 1.0F)), base);
	}

	/** Writes the look into the texture the shader reads (made the first time it is needed). */
	private static void write(TipsyEyes.Look look, float seconds) {
		int pixel = channel(look.glare() / TipsyEyes.MOST_GLARE) << 24 | channel(look.split()) << 16 | channel(look.ghost()) << 8
			| channel(look.edges());
		// the drift's two waves (the shader can't read the game's time here), as 0..1
		float apart = Mth.sin(Mth.TWO_PI * seconds / DRIFT_PERIOD) * 0.5F + 0.5F;
		float upDown = Mth.sin(Mth.TWO_PI * seconds / (DRIFT_PERIOD * 1.7F) + 1.1F) * 0.5F + 0.5F;
		int drift = 0xFF000000 | channel(apart) << 16 | channel(upDown) << 8;
		DynamicTexture texture = params;
		if (texture == null) {
			texture = new DynamicTexture(() -> "Tipsy eyes", 2, 1, true);
			Minecraft.getInstance().getTextureManager().register(PARAMS, texture);
			params = texture;
		}
		texture.getPixels().setPixel(0, 0, pixel);
		texture.getPixels().setPixel(1, 0, drift);
		texture.upload();
	}

	/** Seconds for the second image to drift apart and back (the shader's PERIOD on 26.3). */
	private static final float DRIFT_PERIOD = 4.6F;

	private static int channel(float value) {
		return Math.round(Mth.clamp(value, 0.0F, 1.0F) * 255.0F);
	}
}
