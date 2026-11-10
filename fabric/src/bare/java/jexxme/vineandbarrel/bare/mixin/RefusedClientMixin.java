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
 * Vine & Barrel through quick play): passes once the server refuses it with a reason on the disconnect screen; fails if
 * it gets into the world or nothing happens for 3 minutes. Then it quits.
 */
@Mixin(Minecraft.class)
abstract class RefusedClientMixin
{
	@Unique
	private int vineandbarrel_bare$ticks;
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
			Bare.LOGGER.info("Refused by the server: {}", reason);
			problem = reason.isBlank() ? "refused without a reason" : null;
		} else if (mc.player != null) {
			problem = "a client without Vine & Barrel got into the world of a server with it";
		} else if (++vineandbarrel_bare$ticks > 20 * 180) {
			problem = "neither refused nor joined within 3 minutes";
		} else {
			return;
		}
		vineandbarrel_bare$done = true;
		Bare.report("a_client_without_vb_is_refused_with_a_reason", problem);
		mc.execute(mc::stop);
	}
}
