package jexxme.vineandbarrel.fabric;

import io.netty.buffer.ByteBuf;
import jexxme.vineandbarrel.VineAndBarrel;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A client without Vine & Barrel is refused when it connects, with the reason on its screen. On 26.x Fabric API's
 * registry sync does that; on this Minecraft it lets such a client in, which then loses the connection on the first
 * packet that names one of our recipes or blocks. The server never sends this payload: a client with the mod only says
 * it could take it.
 */
public record ModPresence() implements CustomPacketPayload
{
	public static final Type<ModPresence> TYPE = new Type<>(VineAndBarrel.id("present"));
	public static final StreamCodec<ByteBuf, ModPresence> CODEC = StreamCodec.unit(new ModPresence());
	/** Plain text: the client it is shown to has none of our translations. */
	private static final Component REASON = Component.literal("This server runs the mod Vine & Barrel. Install Vine & Barrel (Fabric) to join.");

	@Override
	public Type<ModPresence> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.configurationS2C().register(TYPE, CODEC);
		ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
			if (!ServerConfigurationNetworking.canSend(listener, TYPE)) {
				listener.disconnect(REASON);
			}
		});
	}
}
