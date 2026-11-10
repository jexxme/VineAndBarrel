package jexxme.vineandbarrel.fabric;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.platform.ConfigValue;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The server's settings ({@link ConfigValue.Side#SERVER}) for a joining client, as Forge syncs its server config: the
 * client shows what this server does (the non-alcoholic tooltips, the maximum level). Sent on join and after
 * {@code /reload}, by key
 * ({@code section.name}) and value as text; the client keeps them until it leaves ({@code FabricConfig#useServerValues}).
 * On this Minecraft a packet of ours is its channel and a buffer: a map of text to text.
 */
public record ServerConfigPayload(Map<String, String> values)
{
	public static final ResourceLocation CHANNEL = VineAndBarrel.id("server_config");

	public FriendlyByteBuf write() {
		FriendlyByteBuf buffer = PacketByteBufs.create();
		buffer.writeMap(values, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);
		return buffer;
	}

	public static ServerConfigPayload read(FriendlyByteBuf buffer) {
		return new ServerConfigPayload(buffer.readMap(HashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf));
	}

	/** After {@code /reload}: the server's settings (read again) to every connected player who can take them. */
	static void sendToAll(MinecraftServer server) {
		ServerConfigPayload payload = current();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, CHANNEL)) {
				ServerPlayNetworking.send(player, CHANNEL, payload.write());
			}
		}
	}

	static void register() {
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			if (ServerPlayNetworking.canSend(listener, CHANNEL)) {
				sender.sendPacket(CHANNEL, current().write());
			}
		});
	}

	/** This side's server settings as they are now. */
	static ServerConfigPayload current() {
		Map<String, String> values = new LinkedHashMap<>();
		for (ConfigValue<?> value : ConfigValue.all()) {
			if (value.side() == ConfigValue.Side.SERVER) {
				values.put(FabricConfig.key(value), String.valueOf(value.get()));
			}
		}
		return new ServerConfigPayload(values);
	}
}
