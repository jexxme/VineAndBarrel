package jexxme.vineandbarrel.tipsy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.TipsyEyes;
import jexxme.vineandbarrel.tipsy.TipsyLevels;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Drunk eyes on this client (Tipsy overhaul, step 4; the math is {@link TipsyEyes}), and the morning after's glare: a
 * post effect ({@code shaders/post/tipsy_eyes.json}) drawn over the world while the eyes see anything
 * ({@code jexxme.vineandbarrel.mixin.GameRendererMixin}, right after vanilla's own post effect), told how strong each
 * part is through its uniforms every frame. Holding sneak squints. Off with the "Double vision" setting, and while
 * blacked out (the blackout has its own picture).
 * <p>
 * 1.21.1 has one post effect slot (the spectator views use it) and no list of screen effects to join, so the effect is a
 * chain of its own here; and this Minecraft finds a post pass's program by its name in its own namespace only, so the
 * program is {@code minecraft:vineandbarrel_tipsy_eyes}.
 */
public final class DrunkEyes
{
	/** The post effect ({@code shaders/post/tipsy_eyes.json}). */
	public static final ResourceLocation EFFECT = VineAndBarrel.id("shaders/post/tipsy_eyes.json");

	private static @Nullable PostChain chain;
	/** The chain failed to load once: not tried again (logged then). */
	private static boolean broken;
	private static TipsyEyes.Look look = TipsyEyes.Look.NONE;
	private static float squint;
	/** How blurred signs look now (0 sharp .. 1), eased. */
	private static float signBlur;

	private DrunkEyes() {
	}

	/** Once a client tick: eases the eyes toward this level's (and a squint). */
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
	}

	/** A new world or a disconnect: eyes open and clear. */
	static void reset() {
		look = TipsyEyes.Look.NONE;
		squint = 0.0F;
		signBlur = 0.0F;
	}

	/** {@return how much the morning after's daylight glares now, 0 to 1} */
	static float glare() {
		return look.glare();
	}

	/**
	 * Right after the world (and vanilla's own post effect) is drawn, before the HUD: the drunk eyes over the picture, while
	 * they see anything.
	 */
	public static void render(DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!look.any() || minecraft.player == null || minecraft.level == null) {
			return;
		}
		PostChain effect = chain(minecraft);
		if (effect == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		// What later versions hand every shader as GameTime: the day's time, once round per 24000 ticks
		effect.setUniform("GameTime", (minecraft.level.getGameTime() % 24000L + partial) / 24000.0F);
		effect.setUniform("Split", Mth.clamp(look.split(), 0.0F, 1.0F));
		effect.setUniform("Ghost", Mth.clamp(look.ghost(), 0.0F, 1.0F));
		effect.setUniform("Edges", Mth.clamp(look.edges(), 0.0F, 1.0F));
		effect.setUniform("Glare", Mth.clamp(look.glare() / TipsyEyes.MOST_GLARE, 0.0F, 1.0F));
		RenderSystem.disableBlend();
		RenderSystem.disableDepthTest();
		RenderSystem.resetTextureMatrix();
		effect.process(delta.getGameTimeDeltaTicks());
	}

	/** The window changed size: the effect's own buffers with it. */
	public static void resize(int width, int height) {
		if (chain != null) {
			chain.resize(width, height);
		}
	}

	private static @Nullable PostChain chain(Minecraft minecraft) {
		if (chain == null && !broken) {
			try {
				chain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), minecraft.getMainRenderTarget(), EFFECT);
				chain.resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
			} catch (Exception e) {
				broken = true;
				chain = null;
				VineAndBarrel.LOGGER.warn("Tipsy: the drunk eyes' post effect failed to load", e);
			}
		}
		return chain;
	}

	/** {@return how blurred the writing on signs looks to this player now, 0 sharp to 1 unreadable} */
	public static float signBlur() {
		return signBlur;
	}

	/**
	 * {@return the colour a line of a sign's writing is drawn in now ({@code jexxme.vineandbarrel.mixin.SignRendererMixin}):
	 * as it is, or fainter when blurred}
	 */
	public static int signLineColor(int color) {
		return signBlur <= 0.01F ? color : withAlpha(color, TipsyEyes.sharpAlpha(signBlur));
	}

	/**
	 * When the writing is blurred, the faint copies drawn round a line (never scrambled: the letters stay the letters);
	 * nothing when it is sharp.
	 */
	public static void signSmear(Font font, PoseStack pose, MultiBufferSource buffers, float x, float y, FormattedCharSequence line,
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
			font.drawInBatch(line, x + side * dist, y, faint, false, pose.last().pose(), buffers, mode, 0, light);
		}
	}

	private static int withAlpha(int color, float alpha) {
		// the sign's colours come without alpha (fully there)
		int base = (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
		return FastColor.ARGB32.color(Math.round(FastColor.ARGB32.alpha(base) * Mth.clamp(alpha, 0.0F, 1.0F)), base);
	}
}
