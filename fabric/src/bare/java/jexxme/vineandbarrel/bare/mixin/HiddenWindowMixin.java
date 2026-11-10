package jexxme.vineandbarrel.bare.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the smoke test's game window hidden for the whole run. On 1.20.1 the window is created visible (in Window's
 * constructor) and nothing shows it later, so it is created hidden here, unfocused and never focused when shown: it
 * never takes the keyboard or the mouse from whoever works at this machine. The game still renders
 * into its own framebuffer, which is what the screenshots read. If this mixin can't apply, the game fails before the
 * window exists (required, defaultRequire 1), so a broken one never shows a window.
 */
@Mixin(Window.class)
abstract class HiddenWindowMixin
{
	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false, unsafe = true))
	private void vineandbarrel_bare$hidden(CallbackInfo callback) {
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
	}
}
