package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.client.DrunkEyes;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drunk eyes ({@link DrunkEyes}), on both loaders: their post effect over the world right after vanilla's own (the
 * spectator views) and before the HUD, the way later versions add it to the player's list of screen effects; and its
 * buffers follow the window's size.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin
{
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V", ordinal = 0))
	private void vineandbarrel$drunkEyes(DeltaTracker delta, boolean renderLevel, CallbackInfo callback) {
		DrunkEyes.render(delta);
	}

	@Inject(method = "resize", at = @At("TAIL"))
	private void vineandbarrel$resizeDrunkEyes(int width, int height, CallbackInfo callback) {
		DrunkEyes.resize(width, height);
	}
}
