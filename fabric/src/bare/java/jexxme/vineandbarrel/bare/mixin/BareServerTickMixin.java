package jexxme.vineandbarrel.bare.mixin;

import jexxme.vineandbarrel.bare.Bare;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** The server without Vine & Barrel passes once a client with it joined and left again, then stops. */
@Mixin(MinecraftServer.class)
abstract class BareServerTickMixin
{
	@Unique
	private boolean vineandbarrel_bare$joined;
	@Unique
	private boolean vineandbarrel_bare$finished;

	@Inject(method = "tickServer", at = @At("TAIL"))
	private void vineandbarrel_bare$watch(BooleanSupplier haveTime, CallbackInfo ci) {
		if (!"server".equals(Bare.MODE) || vineandbarrel_bare$finished) {
			return;
		}
		MinecraftServer server = (MinecraftServer) (Object) this;
		int players = server.getPlayerCount();
		if (players > 0 && !vineandbarrel_bare$joined) {
			vineandbarrel_bare$joined = true;
			Bare.LOGGER.info("A client with Vine & Barrel joined the server without it");
		} else if (vineandbarrel_bare$joined && players == 0) {
			vineandbarrel_bare$finished = true;
			Bare.report("a_client_with_vb_joins_and_leaves_a_server_without_it", null);
			server.halt(false);
		}
	}
}
