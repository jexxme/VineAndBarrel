package jexxme.vineandbarrel.bare.mixin;

import jexxme.vineandbarrel.bare.Bare;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The server without Vine & Barrel says when it is up ({@code server-ready}, for the Gradle task that starts it). */
@Mixin(DedicatedServer.class)
abstract class BareServerReadyMixin
{
	@Inject(method = "initServer", at = @At("RETURN"))
	private void vineandbarrel_bare$ready(CallbackInfoReturnable<Boolean> callback) {
		if ("server".equals(Bare.MODE) && callback.getReturnValueZ()) {
			Bare.write("server-ready", "ready");
			Bare.LOGGER.info("Server without Vine & Barrel ready");
		}
	}
}
