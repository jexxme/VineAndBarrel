package jexxme.vineandbarrel.fabric.mixin.client;

import jexxme.vineandbarrel.tipsy.client.BlackoutClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A screen about to open (NeoForge: {@code ScreenEvent.Opening}): the blackout's bed has no "Leave Bed" screen. */
@Mixin(Minecraft.class) // on this Minecraft the screen is Minecraft's (the HUD mixin is HudMixin)
abstract class GuiMixin
{
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$screenOpening(@Nullable Screen screen, CallbackInfo callback) {
		if (BlackoutClient.blocksScreen(screen)) {
			callback.cancel();
		}
	}
}
