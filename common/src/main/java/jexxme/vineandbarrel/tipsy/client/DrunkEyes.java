package jexxme.vineandbarrel.tipsy.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.mixin.PostChainAccessor;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.TipsyEyes;
import jexxme.vineandbarrel.tipsy.TipsyLevels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;

/**
 * Drunk eyes on this client (Tipsy overhaul, step 4; the math is {@link TipsyEyes}), and the morning after's glare: the
 * post chain {@code vineandbarrel:shaders/post/tipsy_eyes.json} is drawn over the world while the eyes see anything
 * ({@link #render}, from {@code jexxme.vineandbarrel.mixin.GameRendererMixin}), and its shader gets how strong each part
 * is as uniforms, set every frame. Holding sneak squints. Off with the "Double vision" setting, and while blacked out
 * (the blackout has its own picture).
 * <p>
 * Newer versions put the effect on the player's own list of screen effects and hand the strengths over in a 1x1
 * texture; 1.20.1 has neither (one screen effect slot, the game's own), so the chain is this class's own and its
 * uniforms are set the old way, by name.
 */
public final class DrunkEyes
{
	/** The post chain ({@code shaders/post/tipsy_eyes.json}). */
	public static final ResourceLocation EFFECT = VineAndBarrel.id("shaders/post/tipsy_eyes.json");

	private static @Nullable PostChain chain;
	/** Whether loading the chain failed (then the eyes stay clear, and it isn't tried again). */
	private static boolean broken;
	private static int chainWidth, chainHeight;
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

	/**
	 * Once a frame, right after the world is drawn and before the HUD: the drunk eyes over the picture, while they see
	 * anything. The game binds its main picture again right after.
	 */
	public static void render(float partial) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!look.any() || minecraft.player == null || minecraft.level == null) {
			return;
		}
		PostChain effect = chain(minecraft);
		if (effect == null) {
			return;
		}
		// (newer versions' GameTime uniform, in seconds: the game time within the day)
		float seconds = ((minecraft.level.getGameTime() % 24000L) + partial) / 20.0F;
		for (PostPass pass : ((PostChainAccessor) effect).vineandbarrel$passes()) {
			var shader = pass.getEffect();
			shader.safeGetUniform("Split").set(Mth.clamp(look.split(), 0.0F, 1.0F));
			shader.safeGetUniform("Ghost").set(Mth.clamp(look.ghost(), 0.0F, 1.0F));
			shader.safeGetUniform("Edges").set(Mth.clamp(look.edges(), 0.0F, 1.0F));
			shader.safeGetUniform("Glare").set(Mth.clamp(look.glare() / TipsyEyes.MOST_GLARE, 0.0F, 1.0F));
			shader.safeGetUniform("Seconds").set(seconds);
		}
		RenderSystem.disableBlend();
		RenderSystem.disableDepthTest();
		RenderSystem.resetTextureMatrix();
		// The picture is read smoothly (newer versions: the input's "bilinear"), then back to the game's own sharp reading
		var main = minecraft.getMainRenderTarget();
		main.setFilterMode(GL11.GL_LINEAR);
		effect.process(partial);
		main.setFilterMode(GL11.GL_NEAREST);
	}

	/** {@return the post chain, made the first time it is needed and resized with the window; null if it can't load} */
	private static @Nullable PostChain chain(Minecraft minecraft) {
		if (broken) {
			return null;
		}
		Window window = minecraft.getWindow();
		PostChain effect = chain;
		if (effect == null) {
			try {
				effect = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), minecraft.getMainRenderTarget(), EFFECT);
			} catch (Exception e) {
				broken = true;
				VineAndBarrel.LOGGER.warn("Tipsy: no drunk eyes, the post shader {} failed to load", EFFECT, e);
				return null;
			}
			chain = effect;
			chainWidth = -1;
		}
		if (chainWidth != window.getWidth() || chainHeight != window.getHeight()) {
			chainWidth = window.getWidth();
			chainHeight = window.getHeight();
			effect.resize(chainWidth, chainHeight);
		}
		return effect;
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
	public static void signSmear(Font font, FormattedCharSequence line, float x, float y, int color, Matrix4f matrix, MultiBufferSource buffers,
		Font.DisplayMode mode, int light) {
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
			font.drawInBatch(line, x + side * dist, y, faint, false, matrix, buffers, mode, 0, light);
		}
	}

	private static int withAlpha(int color, float alpha) {
		// the sign's colours come without alpha (fully there)
		int base = (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
		int a = Math.round((base >>> 24) * Mth.clamp(alpha, 0.0F, 1.0F));
		return a << 24 | base & 0xFFFFFF;
	}
}
