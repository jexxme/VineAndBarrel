package jexxme.vineandbarrel.bare.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Keeps the smoke test's game window hidden for the whole run: it is created with {@code SDL_WINDOW_HIDDEN} (as
 * NeoForge does until its loading screen hands over; vanilla shows it right away) and nothing shows it later. The game
 * still renders into its own framebuffer, which is what the screenshots read. If this mixin can't apply, the game
 * fails before the window exists (required, defaultRequire 1), so a broken one never shows a window.
 */
@Mixin(Window.class)
abstract class HiddenWindowMixin
{
	@ModifyArg(method = "createWindow", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"), index = 3)
	private long vineandbarrel_bare$hidden(long flags) {
		// Never focusable either: it must not take the keyboard or the mouse from whoever works at this machine
		return flags | SDLVideo.SDL_WINDOW_HIDDEN | SDLVideo.SDL_WINDOW_NOT_FOCUSABLE;
	}
}
