package jexxme.vineandbarrel.smoke.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the smoke test's game window hidden for the whole run. On 26.1.x the window is created visible and nothing
 * shows it later, so it is created hidden here, unfocused and never focused when shown: it never takes the keyboard or
 * the mouse from whoever works at this machine (26.3: SDL3, created hidden and not focusable). The game still renders
 * into its own framebuffer, which is what the screenshots read. If this mixin can't apply, the game fails before the
 * window exists (required, defaultRequire 1), so a broken one never shows a window.
 */
@Mixin(Window.class)
abstract class HiddenWindowMixin
{
	@Inject(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
	private static void vineandbarrel_smoke$hidden(CallbackInfoReturnable<Long> callback) {
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
	}
}
