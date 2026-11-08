package jexxme.vineandbarrel.smoke.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The hidden test client never touches the real mouse: grabbing it (when no screen is open) and releasing it both
 * move the system cursor to the window's centre, which drags the mouse of whoever is working at this machine. Nothing
 * in the run needs it, since every input is driven by code.
 */
@Mixin(MouseHandler.class)
abstract class HiddenMouseMixin
{
	@Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel_smoke$noGrab(CallbackInfo ci) {
		ci.cancel();
	}

	@Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel_smoke$noRelease(CallbackInfo ci) {
		ci.cancel();
	}
}
