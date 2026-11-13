package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import jexxme.vineandbarrel.tipsy.client.DrunkEyes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drunk eyes on signs: each line of writing is drawn as it looks to a tipsy player, the line itself through the original
 * call (fainter when blurred, so other mods wrapping it still see it) and the faint copies round it after
 * ({@link DrunkEyes#signSmear}). Both loaders.
 */
@Mixin(AbstractSignRenderer.class)
abstract class AbstractSignRendererMixin
{
	@WrapOperation(method = "submitSignText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitText(Lcom/mojang/blaze3d/vertex/PoseStack;FFLnet/minecraft/util/FormattedCharSequence;ZLnet/minecraft/client/gui/Font$DisplayMode;IIII)V"))
	private void vineandbarrel$blurredWriting(SubmitNodeCollector collector, PoseStack pose, float x, float y, FormattedCharSequence line, boolean shadow,
		Font.DisplayMode mode, int light, int color, int background, int outline, Operation<Void> submit) {
		submit.call(collector, pose, x, y, line, shadow, mode, light, DrunkEyes.signLineColor(color), background, outline);
		DrunkEyes.signSmear(collector, pose, x, y, line, mode, light, color);
	}
}
