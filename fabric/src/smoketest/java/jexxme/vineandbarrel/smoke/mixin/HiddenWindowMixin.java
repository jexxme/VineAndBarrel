package jexxme.vineandbarrel.smoke.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps the smoke test's game window hidden for the whole run. On 26.2 the window is created hidden (GLFW_VISIBLE off)
 * and shown once, by the one {@code glfwShowWindow} in Minecraft's constructor: that call does nothing here, and nothing
 * shows the window later, so it never takes the keyboard or the mouse either (26.3: SDL3, created hidden and not
 * focusable). The game still renders into its own framebuffer, which is what the screenshots read. If this mixin can't
 * apply, the game fails before the window could show (required, defaultRequire 1).
 */
@Mixin(Minecraft.class)
abstract class HiddenWindowMixin
{
	@Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwShowWindow(J)V"))
	private void vineandbarrel_smoke$neverShown(long window) {
	}
}
