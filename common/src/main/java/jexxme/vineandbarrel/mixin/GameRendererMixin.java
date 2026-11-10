package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.client.BlackoutClient;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The FOV breathing at Tipsy V ({@link TipsyClient#fovFactor}), on both loaders: at the end of vanilla's field of view,
 * after its own effects (dying, under water) and the loader's event, for the world and the hand alike. Not in panorama
 * mode, which returns its fixed 90 degrees earlier. (The camera's own moves are in {@link CameraMixin}.)
 * <p>
 * And Tipsy's screen with the HUD hidden: 1.20.1 doesn't draw the HUD at all then (F1, no screen open), so the
 * loaders' HUD hooks never run; the tunnel vision, the blinks and the blackout's closed eyes are drawn from here
 * instead, where the HUD would have been.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin
{
	@Shadow
	@Final
	Minecraft minecraft;

	@Shadow
	@Final
	private RenderBuffers renderBuffers;

	@Inject(at = @At("TAIL"), method = "getFov", cancellable = true)
	private void vineandbarrel$breathe(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
		float factor = TipsyClient.fovFactor(camera.getEntity(), partialTicks);
		if (factor != 1.0F) {
			cir.setReturnValue(cir.getReturnValueD() * factor);
		}
	}

	@Inject(method = "render(FJZ)V", at = @At(value = "INVOKE", ordinal = 0,
		target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;"))
	private void vineandbarrel$hudHidden(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
		// Exactly when vanilla skipped Gui#render just before this (see there)
		if (renderLevel && this.minecraft.level != null && this.minecraft.options.hideGui && this.minecraft.screen == null) {
			GuiGraphics gui = new GuiGraphics(this.minecraft, this.renderBuffers.bufferSource());
			TipsyClient.renderVision(gui, partialTicks);
			BlackoutClient.renderBlackout(gui, partialTicks);
			gui.flush();
		}
	}
}
