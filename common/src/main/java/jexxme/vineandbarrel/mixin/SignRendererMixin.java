package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import jexxme.vineandbarrel.tipsy.client.DrunkEyes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drunk eyes on signs (and hanging signs, drawn by the same code on this Minecraft): each line of writing is drawn as it
 * looks to a tipsy player, the line itself through the original call (fainter when blurred, so other mods wrapping it
 * still see it) and the faint copies round it after ({@link DrunkEyes#signSmear}). 1.21.1 draws a line one of two ways,
 * plain or (glowing black dye) with an outline: both are wrapped. Both loaders.
 */
@Mixin(SignRenderer.class)
abstract class SignRendererMixin
{
	@WrapOperation(method = "renderSignText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))
	private int vineandbarrel$blurredWriting(Font font, FormattedCharSequence line, float x, float y, int color, boolean shadow, Matrix4f matrix,
		MultiBufferSource buffers, Font.DisplayMode mode, int background, int light, Operation<Integer> draw, @Local(argsOnly = true) PoseStack pose) {
		int drawn = draw.call(font, line, x, y, DrunkEyes.signLineColor(color), shadow, matrix, buffers, mode, background, light);
		DrunkEyes.signSmear(font, pose, buffers, x, y, line, mode, light, color);
		return drawn;
	}

	@WrapOperation(method = "renderSignText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch8xOutline(Lnet/minecraft/util/FormattedCharSequence;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
	private void vineandbarrel$blurredOutlinedWriting(Font font, FormattedCharSequence line, float x, float y, int color, int outline, Matrix4f matrix,
		MultiBufferSource buffers, int light, Operation<Void> draw, @Local(argsOnly = true) PoseStack pose) {
		draw.call(font, line, x, y, DrunkEyes.signLineColor(color), outline, matrix, buffers, light);
		DrunkEyes.signSmear(font, pose, buffers, x, y, line, Font.DisplayMode.POLYGON_OFFSET, light, color);
	}
}
