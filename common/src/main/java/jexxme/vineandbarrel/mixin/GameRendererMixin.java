package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The FOV breathing at Tipsy V ({@link TipsyClient#fovFactor}), on both loaders: at the end of vanilla's field of view,
 * after its own effects (dying, under water) and the loader's event, for the world and the hand alike. Not in panorama
 * mode, which returns its fixed 90 degrees earlier. (The camera's own moves are in {@link CameraMixin}.)
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin
{
	@Inject(at = @At("TAIL"), method = "getFov", cancellable = true)
	private void vineandbarrel$breathe(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
		float factor = TipsyClient.fovFactor(camera.getEntity(), partialTicks);
		if (factor != 1.0F) {
			cir.setReturnValue(cir.getReturnValueD() * factor);
		}
	}
}
