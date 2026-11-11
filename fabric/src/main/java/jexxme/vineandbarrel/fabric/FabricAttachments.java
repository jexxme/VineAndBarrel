package jexxme.vineandbarrel.fabric;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.platform.EntityAttachment;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link EntityAttachment} on Fabric: Fabric API's data attachments keep the value on the entity, save it and copy it
 * through death. On this Minecraft they have no syncing of their own (26.x and 1.21.1: {@code syncWith}), so a synced
 * attachment travels in our own packet, to the same clients at the same moments: to the entity's own client and to
 * everyone tracking it whenever the value is set or removed on the server, to a player who starts tracking the entity
 * (right after its spawn packet, {@code ServerEntityMixin}), and to the player itself on join, after a respawn and after
 * a change of dimension. The client keeps what it is sent as the same attachment on its own copy of the entity.
 */
public final class FabricAttachments
{
	/** One packet per value: the entity's network id, the attachment's id, whether it is there, then the value. */
	public static final ResourceLocation CHANNEL = VineAndBarrel.id("attachment");
	/** The synced attachments by id, as the client looks them up; in the order they were made. */
	private static final Map<ResourceLocation, Synced<?>> SYNCED = new LinkedHashMap<>();

	private FabricAttachments() {
	}

	static <T> EntityAttachment<T> create(ResourceLocation id, EntityAttachment.Spec<T> spec) {
		AttachmentRegistry.Builder<T> builder = AttachmentRegistry.<T>builder().initializer(spec.defaultValue());
		if (spec.saveCodec() != null) {
			builder.persistent(spec.saveCodec().codec());
		}
		if (spec.copyOnDeath()) {
			builder.copyOnDeath();
		}
		AttachmentType<T> type = builder.buildAndRegister(id);
		Synced<T> synced = spec.syncCodec() != null ? new Synced<>(id, type, spec.syncCodec()) : null;
		if (synced != null) {
			SYNCED.put(id, synced);
		}
		return new EntityAttachment<>()
		{
			@Override
			public boolean has(Entity entity) {
				return entity.hasAttached(type);
			}

			@Override
			public T get(Entity entity) {
				T value = entity.getAttached(type);
				if (value == null) {
					// Attached through set, so a synced default reaches the clients (as Fabric API's getAttachedOrCreate does it
					// where it syncs)
					value = spec.defaultValue().get();
					set(entity, value);
				}
				return value;
			}

			@Override
			public @Nullable T getExisting(Entity entity) {
				return entity.getAttached(type);
			}

			@Override
			public void set(Entity entity, T value) {
				entity.setAttached(type, value);
				if (synced != null) {
					synced.changed(entity);
				}
			}

			@Override
			public void remove(Entity entity) {
				entity.removeAttached(type);
				if (synced != null) {
					synced.changed(entity);
				}
			}
		};
	}

	/** The player's own values: its client made a new player (join, respawn, another dimension). */
	static void register() {
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> sendAll(listener.player, listener.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (alive) {
				// home through the End's exit portal: 1.20.1 copies no effects to the new player
				jexxme.vineandbarrel.tipsy.Tipsy.carryOver(oldPlayer, newPlayer);
			}
			sendAll(newPlayer, newPlayer);
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> sendAll(player, player));
	}

	/**
	 * Everything synced about {@code entity} to one player's client, also what it does not have (the client may still
	 * hold a value from before). Called once the client knows the entity.
	 */
	public static void sendAll(Entity entity, ServerPlayer to) {
		if (SYNCED.isEmpty() || !canTake(to)) {
			return;
		}
		for (Synced<?> synced : SYNCED.values()) {
			// A newly tracked entity has no values on the client yet: only the ones it has are worth a packet
			if (entity == to || entity.hasAttached(synced.type)) {
				ServerPlayNetworking.send(to, CHANNEL, synced.write(entity));
			}
		}
	}

	private static boolean canTake(ServerPlayer player) {
		return player.connection != null && ServerPlayNetworking.canSend(player, CHANNEL);
	}

	/** On the client: reads one packet (on the network thread) into what to do with it on the client thread. */
	public static @Nullable Received<?> read(FriendlyByteBuf buffer) {
		int entityId = buffer.readVarInt();
		Synced<?> synced = SYNCED.get(buffer.readResourceLocation());
		return synced == null ? null : synced.read(entityId, buffer);
	}

	/** A value (null: removed) the server sent for the entity with this network id. */
	public record Received<T>(int entityId, AttachmentType<T> type, @Nullable T value)
	{
		public void applyTo(Entity entity) {
			if (value == null) {
				entity.removeAttached(type);
			} else {
				entity.setAttached(type, value);
			}
		}
	}

	private record Synced<T>(ResourceLocation id, AttachmentType<T> type, EntityAttachment.SyncCodec<T> codec)
	{
		/** The value was set or removed: to the entity's own client and everyone tracking it, from the server only. */
		void changed(Entity entity) {
			if (entity.level().isClientSide()) {
				return;
			}
			if (entity instanceof ServerPlayer self && canTake(self)) {
				ServerPlayNetworking.send(self, CHANNEL, write(entity));
			}
			for (ServerPlayer watcher : PlayerLookup.tracking(entity)) {
				// A player is never in its own list, but a mod's tracker might put it there
				if (watcher != entity && canTake(watcher)) {
					ServerPlayNetworking.send(watcher, CHANNEL, write(entity));
				}
			}
		}

		FriendlyByteBuf write(Entity entity) {
			FriendlyByteBuf buffer = PacketByteBufs.create();
			buffer.writeVarInt(entity.getId());
			buffer.writeResourceLocation(id);
			T value = entity.getAttached(type);
			buffer.writeBoolean(value != null);
			if (value != null) {
				codec.writer().accept(buffer, value);
			}
			return buffer;
		}

		Received<T> read(int entityId, FriendlyByteBuf buffer) {
			return new Received<>(entityId, type, buffer.readBoolean() ? codec.reader().apply(buffer) : null);
		}
	}
}
