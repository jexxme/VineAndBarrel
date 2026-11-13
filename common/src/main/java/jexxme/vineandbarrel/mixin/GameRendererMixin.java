package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.client.BlackoutClient;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
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

/**
 * Tipsy's screen with the HUD hidden, on both loaders: 1.20.1 doesn't draw the HUD at all then (F1, no screen open), so
 * the loaders' HUD hooks never run; what the eyes do ({@link TipsyClient#renderVision}) and the blackout's closed eyes
 * are drawn from here instead, where the HUD would have been.
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
