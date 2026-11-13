package jexxme.vineandbarrel.fabric.mixin.client;

import jexxme.vineandbarrel.tipsy.client.BlackoutClient;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tipsy's two HUD layers (NeoForge: {@code RegisterGuiLayersEvent}; Fabric API has no HUD layers on this Minecraft, its
 * {@code HudRenderCallback} only draws last): tunnel vision above the camera overlays and under the rest of the HUD, the
 * blackout above everything. Both stay with the HUD hidden (F1), as on NeoForge: vanilla skips the camera overlays then
 * but still draws the sleep overlay, so with F1 on the vision is drawn right after it instead. And in a blackout's bed
 * the eyes are opening: no darkening sleep overlay (NeoForge: the layer's Pre event).
 * <p>
 * Applied after other mods' mixins of the default priority, so the blackout is drawn after what they add at the end of
 * the HUD (Fabric API's {@code HudRenderCallback} among them).
 */
@Mixin(value = Gui.class, priority = 1500)
abstract class HudMixin
{
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "renderCameraOverlays", at = @At("TAIL"))
	private void vineandbarrel$vision(GuiGraphics graphics, DeltaTracker delta, CallbackInfo callback) {
		TipsyClient.renderVision(graphics, delta);
	}

	@Inject(method = "renderSleepOverlay", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$noSleepOverlay(GuiGraphics graphics, DeltaTracker delta, CallbackInfo callback) {
		if (BlackoutClient.hidesSleepOverlay()) {
			callback.cancel();
			// Cancelled before the end of the method, where the vision is drawn with the HUD hidden (below)
			vineandbarrel$visionIfHudHidden(graphics, delta);
		}
	}

	@Inject(method = "renderSleepOverlay", at = @At("TAIL"))
	private void vineandbarrel$afterSleepOverlay(GuiGraphics graphics, DeltaTracker delta, CallbackInfo callback) {
		vineandbarrel$visionIfHudHidden(graphics, delta);
	}

	@Unique
	private void vineandbarrel$visionIfHudHidden(GuiGraphics graphics, DeltaTracker delta) {
		if (minecraft.options.hideGui) {
			TipsyClient.renderVision(graphics, delta);
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void vineandbarrel$blackout(GuiGraphics graphics, DeltaTracker delta, CallbackInfo callback) {
		BlackoutClient.renderBlackout(graphics, delta);
	}
}
