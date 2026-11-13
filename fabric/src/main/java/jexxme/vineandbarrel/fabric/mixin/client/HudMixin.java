package jexxme.vineandbarrel.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import jexxme.vineandbarrel.tipsy.client.BlackoutClient;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tipsy's two HUD layers (Forge: {@code RegisterGuiOverlaysEvent}; Fabric API has no HUD layers on this Minecraft, its
 * {@code HudRenderCallback} only draws last): tunnel vision above the camera overlays and under the rest of the HUD, the
 * blackout above everything. And in a blackout's bed the eyes are opening: no darkening sleep overlay (Forge: the
 * overlay's Pre event). On this Minecraft the whole HUD is one method, so each has its spot inside {@code render}.
 * With the HUD hidden (F1) and no screen open the game does not call {@code render} at all: both layers are drawn by the
 * common {@code GameRendererMixin} then.
 * <p>
 * Applied after other mods' mixins of the default priority, so the blackout is drawn after what they add at the end of
 * the HUD (Fabric API's {@code HudRenderCallback} among them).
 */
@Mixin(value = Gui.class, priority = 1500)
abstract class HudMixin
{
	/**
	 * Right after the camera overlays (vignette, spyglass, pumpkin, powder snow, portal), which this Minecraft draws
	 * also with the HUD hidden, and before the hotbar: the first thing after them is the question whether the player is
	 * a spectator.
	 */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;", ordinal = 0))
	private void vineandbarrel$vision(GuiGraphics graphics, float partialTick, CallbackInfo callback) {
		TipsyClient.renderVision(graphics, partialTick);
	}

	/** The sleep overlay is drawn while the sleep timer runs: for the first question about it, it doesn't. */
	@ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getSleepTimer()I", ordinal = 0))
	private int vineandbarrel$noSleepOverlay(int sleepTimer) {
		return BlackoutClient.hidesSleepOverlay() ? 0 : sleepTimer;
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void vineandbarrel$blackout(GuiGraphics graphics, float partialTick, CallbackInfo callback) {
		BlackoutClient.renderBlackout(graphics, partialTick);
	}
}
