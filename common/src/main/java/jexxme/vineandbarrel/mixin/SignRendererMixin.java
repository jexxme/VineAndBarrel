package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.tipsy.client.DrunkEyes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drunk eyes on signs: each line of writing is drawn as it looks to a tipsy player, the line itself through the original
 * call (fainter when blurred, so other mods wrapping it still see it) and the faint copies round it after
 * ({@link DrunkEyes#signSmear}). Both loaders; hanging signs are drawn by the same method. Newer versions draw a line
 * with one call whatever it is; 1.20.1 draws glowing writing with its outline through a call of its own, so both are
 * wrapped (the outline stays as it is, as there).
 */
@Mixin(SignRenderer.class)
abstract class SignRendererMixin
{
	@WrapOperation(method = "renderSignText", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))
	private int vineandbarrel$blurredWriting(Font font, FormattedCharSequence line, float x, float y, int color, boolean shadow, Matrix4f matrix,
		MultiBufferSource buffers, Font.DisplayMode mode, int background, int light, Operation<Integer> draw) {
		int width = draw.call(font, line, x, y, DrunkEyes.signLineColor(color), shadow, matrix, buffers, mode, background, light);
		DrunkEyes.signSmear(font, line, x, y, color, matrix, buffers, mode, light);
		return width;
	}

	@WrapOperation(method = "renderSignText", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Font;drawInBatch8xOutline(Lnet/minecraft/util/FormattedCharSequence;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
	private void vineandbarrel$blurredGlowingWriting(Font font, FormattedCharSequence line, float x, float y, int color, int outline, Matrix4f matrix,
		MultiBufferSource buffers, int light, Operation<Void> draw) {
		draw.call(font, line, x, y, DrunkEyes.signLineColor(color), outline, matrix, buffers, light);
		DrunkEyes.signSmear(font, line, x, y, color, matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, light);
	}
}
