package jexxme.vineandbarrel.fabric;

import jexxme.vineandbarrel.fabric.transfer.FabricItemAccess;
import jexxme.vineandbarrel.platform.EntityAttachment;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import jexxme.vineandbarrel.platform.Platform;
import jexxme.vineandbarrel.platform.SlotAccess;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** {@link Platform} on Fabric (named in META-INF/services). */
public final class FabricPlatform implements Platform
{
	/** Every register the common code handed over; added to the game's registries by {@link #registerAll()}. */
	private static final List<DeferredRegister<?>> REGISTERS = new ArrayList<>();
	/** The GameTest classes' registrations, run when the GameTest registries load (see {@code RegistryDataLoaderMixin}). */
	static final List<Consumer<GameTestRegistrar>> GAME_TESTS = new ArrayList<>();

	@Override
	public Loader loader() {
		return Loader.FABRIC;
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	public Optional<String> modVersion(String modId) {
		return FabricLoader.getInstance().getModContainer(modId).map(mod -> mod.getMetadata().getVersion().getFriendlyString());
	}

	@Override
	public boolean isDevelopment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	// ------------------------------------------------------------------ registration

	@Override
	public <T> void register(DeferredRegister<T> register) {
		REGISTERS.add(register);
	}

	/**
	 * Adds every handed-over entry to the game's registries, registry by registry in the game's own order (blocks before
	 * items, as NeoForge does it), each register's entries in the order they were added.
	 */
	static void registerAll() {
		for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
			for (DeferredRegister<?> register : REGISTERS) {
				if (register.getRegistryKey().equals(registry.key())) {
					registerEntries(register);
				}
			}
		}
		for (DeferredRegister<?> register : REGISTERS) {
			if (!BuiltInRegistries.REGISTRY.containsKey(register.getRegistryKey().identifier())) {
				throw new IllegalStateException("No registry " + register.getRegistryKey().identifier() + " on Fabric");
			}
		}
		REGISTERS.clear();
	}

	@SuppressWarnings("unchecked")
	private static <T> void registerEntries(DeferredRegister<T> register) {
		Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.getValue(register.getRegistryKey().identifier());
		register.getEntries().forEach((id, factory) -> Registry.register(registry, id, factory.apply(id)));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Holder<T> holder(ResourceKey<T> key) {
		Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.getValue(key.registry());
		if (registry == null) {
			throw new IllegalStateException("No registry " + key.registry());
		}
		Optional<Holder.Reference<T>> registered = registry.get(key);
		if (registered.isPresent()) {
			return registered.get();
		}
		// Not registered yet: the registry's own (stand-alone) holder for the key, which the registration fills in
		return ((WritableRegistry<T>) registry).createRegistrationLookup().getOrThrow(key);
	}

	@Override
	public <T> EntityAttachment<T> attachment(Identifier id, EntityAttachment.Spec<T> spec) {
		AttachmentType<T> type = AttachmentRegistry.create(id, builder -> {
			builder.initializer(spec.defaultValue());
			if (spec.saveCodec() != null) {
				builder.persistent(spec.saveCodec().codec());
			}
			if (spec.syncCodec() != null) {
				builder.syncWith(spec.syncCodec(), AttachmentSyncPredicate.all());
			}
			if (spec.copyOnDeath()) {
				builder.copyOnDeath();
			}
		});
		return new EntityAttachment<>()
		{
			@Override
			public boolean has(Entity entity) {
				return entity.hasAttached(type);
			}

			@Override
			public T get(Entity entity) {
				return entity.getAttachedOrCreate(type);
			}

			@Override
			public @Nullable T getExisting(Entity entity) {
				return entity.getAttached(type);
			}

			@Override
			public void set(Entity entity, T value) {
				entity.setAttached(type, value);
			}

			@Override
			public void remove(Entity entity) {
				entity.removeAttached(type);
			}
		};
	}

	@Override
	public void addGameTests(Consumer<GameTestRegistrar> registrar) {
		GAME_TESTS.add(registrar);
	}

	@Override
	public @Nullable Consumer<GameTestHelper> loaderGameTest(String name) {
		return FabricGameTests.get(name);
	}

	@Override
	public void registerAppleSkin() {
		jexxme.vineandbarrel.fabric.compat.AppleSkinFabric.register();
	}

	@Override
	public CreativeModeTab.Builder creativeTabBuilder() {
		return FabricCreativeModeTab.builder();
	}

	// ------------------------------------------------------------------ menus

	@Override
	public <M extends AbstractContainerMenu> MenuType<M> blockMenuType(BlockMenuFactory<M> factory) {
		return new ExtendedMenuType<>(factory::create, BlockPos.STREAM_CODEC);
	}

	@Override
	public void openBlockMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
		player.openMenu(new ExtendedMenuProvider<BlockPos>()
		{
			@Override
			public BlockPos getScreenOpeningData(ServerPlayer opener) {
				return pos;
			}

			@Override
			public Component getDisplayName() {
				return provider.getDisplayName();
			}

			@Override
			public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
				return provider.createMenu(containerId, inventory, opener);
			}
		});
	}

	@Override
	public void openMenuForGameTest(ServerPlayer player, MenuProvider provider, BlockPos pos) {
		openBlockMenu(player, provider, pos);
	}

	/** The kettle's own book type, added by the class tweaker and {@code RecipeBookTypeMixin} (see {@link FabricRecipeBook}). */
	@Override
	public RecipeBookType brewingRecipeBookType() {
		return FabricRecipeBook.brewingType();
	}

	// ------------------------------------------------------------------ game rules

	@Override
	public boolean canCropGrow(ServerLevel level, BlockPos pos, BlockState state, boolean vanilla) {
		return vanilla;
	}

	@Override
	public void cropGrown(ServerLevel level, BlockPos pos, BlockState state) {
	}

	@Override
	public boolean canEntityGrief(ServerLevel level, Entity entity) {
		return level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	@Override
	public @Nullable Block unwaxed(Block block) {
		return HoneycombItem.WAX_OFF_BY_BLOCK.get().get(block);
	}

	@Override
	public boolean canScrape(ItemStack stack) {
		return stack.getItem() instanceof net.minecraft.world.item.AxeItem;
	}

	@Override
	public int burnTime(ItemStack stack, ServerLevel level) {
		return level.fuelValues().burnDuration(stack);
	}

	@Override
	public float compostChance(net.minecraft.world.item.Item item) {
		return net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES.getOrDefault(item, 0.0F);
	}

	@Override
	public Component decorateChat(ServerPlayer player, Component message) {
		return player.level().getServer().getChatDecorator().decorate(player, FabricChat.slur(player, message));
	}

	@Override
	public @Nullable ItemStackTemplate craftingRemainder(ItemStack stack) {
		return stack.getCraftingRemainder();
	}

	// ------------------------------------------------------------------ item automation

	@Override
	public @Nullable SlotAccess itemAccess(ServerLevel level, BlockPos pos, @Nullable Direction side) {
		return FabricItemAccess.of(level, pos, side);
	}

	@Override
	public SlotAccess.Tx openTransaction() {
		return new FabricItemAccess.Tx(Transaction.openOuter());
	}
}
