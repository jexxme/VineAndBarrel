package jexxme.vineandbarrel.smoke.mixin;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetAddress;

/**
 * The smoke test's LAN host listens on 127.0.0.1 only and announces nothing on the network: no other machine can join,
 * and the operating system's firewall has nothing to ask about.
 */
@Mixin(IntegratedServer.class)
abstract class LoopbackLanMixin
{
	@ModifyArg(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"), index = 0)
	private InetAddress vineandbarrel_smoke$loopbackOnly(InetAddress address) {
		return InetAddress.getLoopbackAddress();
	}

	@Redirect(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/LanServerPinger;start()V"))
	private void vineandbarrel_smoke$noAnnouncement(LanServerPinger pinger) {
	}
}
