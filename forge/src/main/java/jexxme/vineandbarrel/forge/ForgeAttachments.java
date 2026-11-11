package jexxme.vineandbarrel.forge;

import com.mojang.serialization.DynamicOps;
import io.netty.buffer.Unpooled;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.platform.EntityAttachment;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@link EntityAttachment}s on Forge 47, which has no data attachments: what NeoForge 21.1's attachments did, rebuilt.
 * <ul>
 * <li>Kept in a {@link Holder} per entity. Living entities get it as a Forge capability provider
 * ({@code AttachCapabilitiesEvent}), which is what saves it: under {@code ForgeCaps."vineandbarrel:attachments"} in
 * the entity's NBT, one compound per saved attachment, named by its id, holding the fields of its save codec (the same
 * field names NeoForge wrote under {@code neoforge:attachments}). Other entities get a holder that is not saved (none
 * of our attachments is ever set on one; it keeps every item and arrow from carrying an empty compound).</li>
 * <li>{@code has / get / getExisting / set / remove} mean what NeoForge's {@code hasData / getData /
 * getExistingDataOrNull / setData / removeData} mean: {@code get} attaches the default when there is none (without
 * sending it), {@code set} and {@code remove} on the server send a synced attachment to the entity's own client and
 * to everyone tracking it.</li>
 * <li>Synced attachments are also sent when a player starts tracking the entity, and to a player itself when it logs
 * in, respawns or changes dimension (its client makes a new player entity each time).</li>
 * <li>A player's saved attachments are copied when it is cloned: all of them when it returns from the End, only the
 * {@code copyOnDeath} ones after a death. A converted mob (zombie to drowned) keeps all saved ones. Copies are made
 * through the save codec, as NeoForge's default copy handler does.</li>
 * </ul>
 * The packets go over our own channel. A server with the mod only takes clients that have the channel in the same
 * protocol version, which keeps vanilla clients (and clients without the mod) out, as on NeoForge; a client with the
 * mod is not kept from servers without it by this channel.
 */
public final class ForgeAttachments
{
	private static final ResourceLocation KEY = VineAndBarrel.id("attachments");
	private static final String PROTOCOL = "1";
	private static final Map<ResourceLocation, Type<?>> TYPES = new LinkedHashMap<>();
	private static @Nullable SimpleChannel channel;

	private ForgeAttachments() {
	}

	/** One attachment: its id, how it behaves, and the {@link EntityAttachment} the common code holds. */
	static final class Type<T> implements EntityAttachment<T>
	{
		private final ResourceLocation id;
		private final Spec<T> spec;

		private Type(ResourceLocation id, Spec<T> spec) {
			this.id = id;
			this.spec = spec;
		}

		@Override
		public boolean has(Entity entity) {
			return holder(entity).values.containsKey(this);
		}

		@Override
		@SuppressWarnings("unchecked")
		public T get(Entity entity) {
			return (T) holder(entity).values.computeIfAbsent(this, type -> Objects.requireNonNull(spec.defaultValue().get(), () -> "default of " + id));
		}

		@Override
		@SuppressWarnings("unchecked")
		public @Nullable T getExisting(Entity entity) {
			return (T) holder(entity).values.get(this);
		}

		@Override
		public void set(Entity entity, T value) {
			holder(entity).values.put(this, Objects.requireNonNull(value, () -> "value of " + id));
			syncChange(entity, this);
		}

		@Override
		public void remove(Entity entity) {
			if (holder(entity).values.remove(this) != null) {
				syncChange(entity, this);
			}
		}

		private @Nullable Tag save(Object value, DynamicOps<Tag> ops) {
			@SuppressWarnings("unchecked")
			T typed = (T) value;
			return Objects.requireNonNull(spec.saveCodec()).codec().encodeStart(ops, typed)
				.resultOrPartial(problem -> VineAndBarrel.LOGGER.error("Can't save {}: {}", id, problem)).orElse(null);
		}

		private @Nullable T load(Tag tag, DynamicOps<Tag> ops) {
			return Objects.requireNonNull(spec.saveCodec()).codec().parse(ops, tag)
				.resultOrPartial(problem -> VineAndBarrel.LOGGER.error("Can't read {}: {}", id, problem)).orElse(null);
		}

		@Override
		public String toString() {
			return id.toString();
		}
	}

	/** An entity's attachments; for a living entity also the capability provider that saves them. */
	public static final class Holder implements ICapabilitySerializable<CompoundTag>
	{
		private final Entity entity;
		private final Map<Type<?>, Object> values = new LinkedHashMap<>(4);

		private Holder(Entity entity) {
			this.entity = entity;
		}

		/** Nothing to hand out: the data is reached through {@link AttachmentAccess}, the provider is there for saving. */
		@Override
		public <C> LazyOptional<C> getCapability(Capability<C> capability, @Nullable Direction side) {
			return LazyOptional.empty();
		}

		@Override
		public CompoundTag serializeNBT() {
			CompoundTag tag = new CompoundTag();
			DynamicOps<Tag> ops = ops(entity);
			values.forEach((type, value) -> {
				if (type.spec.saveCodec() != null) {
					Tag saved = type.save(value, ops);
					if (saved != null) {
						tag.put(type.id.toString(), saved);
					}
				}
			});
			return tag;
		}

		@Override
		public void deserializeNBT(CompoundTag tag) {
			DynamicOps<Tag> ops = ops(entity);
			for (String key : tag.getAllKeys()) {
				ResourceLocation id = ResourceLocation.tryParse(key);
				Type<?> type = id == null ? null : TYPES.get(id);
				if (type == null || type.spec.saveCodec() == null) {
					VineAndBarrel.LOGGER.error("Unknown saved attachment {} on {}: dropped", key, entity);
					continue;
				}
				Object value = type.load(Objects.requireNonNull(tag.get(key)), ops);
				if (value != null) {
					values.put(type, value);
				}
			}
		}
	}

	private static DynamicOps<Tag> ops(Entity entity) {
		return RegistryOps.create(NbtOps.INSTANCE, entity.level().registryAccess());
	}

	private static Holder holder(Entity entity) {
		AttachmentAccess access = (AttachmentAccess) entity;
		Holder holder = access.vineandbarrel$attachments();
		if (holder == null) {
			holder = new Holder(entity);
			access.vineandbarrel$attachments(holder);
		}
		return holder;
	}

	static <T> EntityAttachment<T> create(ResourceLocation id, EntityAttachment.Spec<T> spec) {
		Type<T> type = new Type<>(id, spec);
		synchronized (TYPES) {
			if (TYPES.putIfAbsent(id, type) != null) {
				throw new IllegalStateException("Attachment registered twice: " + id);
			}
		}
		return type;
	}

	/**
	 * New players made of a living one (not a death): the one they were made of, by player UUID, until the respawn is
	 * through. Not by the new player object: the game gives it the old one's entity id in between, and an entity's
	 * equals and hash code go by that id.
	 */
	private static final java.util.Map<java.util.UUID, net.minecraft.world.entity.player.Player> NOT_DEAD = new java.util.HashMap<>();

	static void init() {
		SimpleChannel simple = NetworkRegistry.newSimpleChannel(KEY, () -> PROTOCOL, NetworkRegistry.acceptMissingOr(PROTOCOL), PROTOCOL::equals);
		simple.registerMessage(0, Sync.class, Sync::write, Sync::read, ForgeAttachments::onSync, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
		channel = simple;

		IEventBus game = MinecraftForge.EVENT_BUS;
		// Fired from the Entity constructor: the holder is there before anything can ask for it
		game.addGenericListener(Entity.class, (AttachCapabilitiesEvent<Entity> event) -> {
			Entity entity = event.getObject();
			if (entity instanceof LivingEntity) {
				Holder holder = new Holder(entity);
				((AttachmentAccess) entity).vineandbarrel$attachments(holder);
				event.addCapability(KEY, holder);
			}
		});
		game.addListener((PlayerEvent.Clone event) -> {
			copy(event.getOriginal(), event.getEntity(), event.isWasDeath());
			if (!event.isWasDeath()) {
				NOT_DEAD.put(event.getEntity().getUUID(), event.getOriginal());
			}
		});
		game.addListener((LivingConversionEvent.Post event) -> copy(event.getEntity(), event.getOutcome(), false));
		// After the spawn packets of the tracked entity, so the client knows it when the values arrive
		game.addListener((PlayerEvent.StartTracking event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				sendAll(event.getTarget(), player);
			}
		});
		game.addListener((PlayerEvent.PlayerLoggedInEvent event) -> sendOwn(event.getEntity()));
		game.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
			// Home through the End's exit portal: 1.20.1 copies no effects to the new player. Done here, not in the
			// clone event: by now the client knows the new player and takes the effects' packets
			net.minecraft.world.entity.player.Player old = NOT_DEAD.remove(event.getEntity().getUUID());
			if (old != null) {
				jexxme.vineandbarrel.tipsy.Tipsy.carryOver(old, event.getEntity());
			}
			sendOwn(event.getEntity());
		});
		game.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> sendOwn(event.getEntity()));
	}

	/** What the new entity keeps of the old one's saved attachments, each copied through its save codec. */
	private static void copy(Entity from, Entity to, boolean death) {
		Holder source = ((AttachmentAccess) from).vineandbarrel$attachments();
		if (source == null || source.values.isEmpty()) {
			return;
		}
		DynamicOps<Tag> ops = ops(from);
		Holder target = holder(to);
		source.values.forEach((type, value) -> {
			if (type.spec.saveCodec() == null || (death && !type.spec.copyOnDeath())) {
				return;
			}
			Tag saved = type.save(value, ops);
			Object copy = saved == null ? null : type.load(saved, ops);
			if (copy != null) {
				target.values.put(type, copy);
			}
		});
	}

	// ------------------------------------------------------------------ sync

	/** One attachment of one entity for its clients: the value, or none when it was removed. */
	private record Entry(Type<?> type, @Nullable Object value)
	{
	}

	/** The packet: the entity's network id and the attachments that changed (or all synced ones, for a new watcher). */
	public record Sync(int entityId, List<Entry> entries)
	{
		@SuppressWarnings({"unchecked", "rawtypes"})
		private static void write(Sync sync, FriendlyByteBuf buffer) {
			buffer.writeVarInt(sync.entityId);
			buffer.writeVarInt(sync.entries.size());
			for (Entry entry : sync.entries) {
				buffer.writeResourceLocation(entry.type.id);
				buffer.writeBoolean(entry.value != null);
				if (entry.value != null) {
					// Length first: a client that can't read one value (it should not happen) skips it and keeps the rest
					FriendlyByteBuf value = new FriendlyByteBuf(Unpooled.buffer());
					((FriendlyByteBuf.Writer) Objects.requireNonNull(entry.type.spec.syncCodec()).writer()).accept(value, entry.value);
					buffer.writeVarInt(value.readableBytes());
					buffer.writeBytes(value);
					value.release();
				}
			}
		}

		private static Sync read(FriendlyByteBuf buffer) {
			int entityId = buffer.readVarInt();
			int count = buffer.readVarInt();
			List<Entry> entries = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				ResourceLocation id = buffer.readResourceLocation();
				Type<?> type = TYPES.get(id);
				boolean known = type != null && type.spec.syncCodec() != null;
				Object value = null;
				boolean present = buffer.readBoolean();
				if (present) {
					FriendlyByteBuf slice = new FriendlyByteBuf(buffer.readSlice(buffer.readVarInt()));
					if (known) {
						value = type.spec.syncCodec().reader().apply(slice);
					}
				}
				if (!known || (present && value == null)) {
					VineAndBarrel.LOGGER.error("Unknown synced attachment {}: ignored", id);
					continue;
				}
				entries.add(new Entry(type, value));
			}
			return new Sync(entityId, entries);
		}
	}

	private static void onSync(Sync sync, Supplier<NetworkEvent.Context> context) {
		NetworkEvent.Context ctx = context.get();
		if (FMLEnvironment.dist == Dist.CLIENT) {
			ctx.enqueueWork(() -> jexxme.vineandbarrel.forge.client.ClientAttachments.receive(sync));
		}
		ctx.setPacketHandled(true);
	}

	/** Client: writes a received packet into the entity's holder. */
	public static void apply(Entity entity, Sync sync) {
		Holder holder = holder(entity);
		for (Entry entry : sync.entries) {
			if (entry.value == null) {
				holder.values.remove(entry.type);
			} else {
				holder.values.put(entry.type, entry.value);
			}
		}
	}

	/** A change on the server: to everyone tracking the entity, and to the entity's own client if it is a player. */
	private static void syncChange(Entity entity, Type<?> type) {
		if (type.spec.syncCodec() == null || channel == null || !(entity.level() instanceof ServerLevel)) {
			return;
		}
		Sync sync = new Sync(entity.getId(), List.of(new Entry(type, holder(entity).values.get(type))));
		// (An entity that is in no level yet has nobody tracking it; a player gets everything once it has joined)
		channel.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), sync);
	}

	private static void sendOwn(Entity player) {
		if (player instanceof ServerPlayer serverPlayer) {
			sendAll(serverPlayer, serverPlayer);
		}
	}

	/** Every synced attachment the entity has, to one player. */
	private static void sendAll(Entity entity, ServerPlayer to) {
		Holder holder = ((AttachmentAccess) entity).vineandbarrel$attachments();
		if (holder == null || holder.values.isEmpty() || channel == null || to.connection == null) {
			return;
		}
		List<Entry> entries = new ArrayList<>();
		holder.values.forEach((type, value) -> {
			if (type.spec.syncCodec() != null) {
				entries.add(new Entry(type, value));
			}
		});
		if (!entries.isEmpty()) {
			channel.send(PacketDistributor.PLAYER.with(() -> to), new Sync(entity.getId(), entries));
		}
	}
}
