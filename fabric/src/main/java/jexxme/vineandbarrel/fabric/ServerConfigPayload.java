package jexxme.vineandbarrel.fabric;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.platform.ConfigValue;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The server's settings ({@link ConfigValue.Side#SERVER}) for a joining client, as NeoForge syncs its server config: the
 * client shows what this server does (the non-alcoholic tooltips, the maximum level). Sent on join and after
 * {@code /reload}, by key
 * ({@code section.name}) and value as text; the client keeps them until it leaves ({@code FabricConfig#useServerValues}).
 */
public record ServerConfigPayload(Map<String, String> values) implements CustomPacketPayload
{
	public static final Type<ServerConfigPayload> TYPE = new Type<>(VineAndBarrel.id("server_config"));
	public static final StreamCodec<ByteBuf, ServerConfigPayload> CODEC = ByteBufCodecs.map(HashMap<String, String>::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8)
			.map(ServerConfigPayload::new, payload -> new HashMap<>(payload.values()));

	@Override
	public Type<ServerConfigPayload> type() {
		return TYPE;
	}

	/** After {@code /reload}: the server's settings (read again) to every connected player who can take them. */
	static void sendToAll(MinecraftServer server) {
		ServerConfigPayload payload = current();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	static void register() {
		PayloadTypeRegistry.playS2C().register(TYPE, CODEC);
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			if (ServerPlayNetworking.canSend(listener, TYPE)) {
				sender.sendPacket(current());
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
