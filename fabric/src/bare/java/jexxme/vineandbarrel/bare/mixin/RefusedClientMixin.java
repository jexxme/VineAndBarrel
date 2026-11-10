package jexxme.vineandbarrel.bare.mixin;

import jexxme.vineandbarrel.bare.Bare;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A client without Vine & Barrel and without Fabric API ({@code -Dvineandbarrel.bare=refused}, joining a server with
 * Vine & Barrel through quick play). On this Minecraft the mod itself refuses nobody (the only point early enough is the
 * login phase, whose queries a proxy answers itself): such a client gets as far as vanilla lets it and loses the
 * connection on the first packet it can't read (the recipes), as with any content mod. So this passes once the client
 * is on the disconnect screen with a reason, whatever vanilla gives; it fails if the client is still in the world 20
 * seconds after it got in, or if nothing happens for 3 minutes. Then it quits.
 */
@Mixin(Minecraft.class)
abstract class RefusedClientMixin
{
	@Unique
	private int vineandbarrel_bare$ticks;
	@Unique
	private int vineandbarrel_bare$ticksInWorld;
	@Unique
	private boolean vineandbarrel_bare$done;

	@Inject(method = "tick", at = @At("TAIL"))
	private void vineandbarrel_bare$watch(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		mc.options.pauseOnLostFocus = false;
		if (!"refused".equals(Bare.MODE) || vineandbarrel_bare$done) {
			return;
		}
		String problem = null;
		if (mc.screen instanceof DisconnectedScreen screen) {
			String reason = screen.getNarrationMessage().getString();
			Bare.LOGGER.info("Disconnected from the server: {}", reason);
			problem = reason.isBlank() ? "disconnected without a reason" : null;
		} else if (mc.player != null && ++vineandbarrel_bare$ticksInWorld > 20 * 20) {
			problem = "a client without Vine & Barrel plays in the world of a server with it";
		} else if (mc.player != null) {
			return;
		} else if (++vineandbarrel_bare$ticks > 20 * 180) {
			problem = "neither disconnected nor in the world within 3 minutes";
		} else {
			return;
		}
		vineandbarrel_bare$done = true;
		Bare.report("a_client_without_vb_is_refused_with_a_reason", problem);
		mc.execute(mc::stop);
	}
}
