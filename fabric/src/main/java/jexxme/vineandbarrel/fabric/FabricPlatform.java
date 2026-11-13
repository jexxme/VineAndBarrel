package jexxme.vineandbarrel.fabric;

import jexxme.vineandbarrel.fabric.transfer.FabricItemAccess;
import jexxme.vineandbarrel.platform.EntityAttachment;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import jexxme.vineandbarrel.platform.Platform;
import jexxme.vineandbarrel.platform.SlotAccess;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameRules;
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
			if (!BuiltInRegistries.REGISTRY.containsKey(register.getRegistryKey().location())) {
				throw new IllegalStateException("No registry " + register.getRegistryKey().location() + " on Fabric");
			}
		}
		REGISTERS.clear();
		PENDING_TRADES.forEach(Runnable::run);
		PENDING_TRADES.clear();
	}

	@SuppressWarnings("unchecked")
	private static <T> void registerEntries(DeferredRegister<T> register) {
		Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.get(register.getRegistryKey().location());
		register.getEntries().forEach((id, factory) -> Registry.register(registry, id, factory.apply(id)));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Holder<T> holder(ResourceKey<T> key) {
		Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.get(key.registry());
		if (registry == null) {
			throw new IllegalStateException("No registry " + key.registry());
		}
		Optional<Holder.Reference<T>> registered = registry.getHolder(key);
		if (registered.isPresent()) {
			return registered.get();
		}
		// Not registered yet: the registry's own (stand-alone) holder for the key, which the registration fills in
		return ((WritableRegistry<T>) registry).createRegistrationLookup().getOrThrow(key);
	}

	@Override
	public <T> EntityAttachment<T> attachment(ResourceLocation id, EntityAttachment.Spec<T> spec) {
		return FabricAttachments.create(id, spec);
	}

	@Override
	public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... blocks) {
		return FabricBlockEntityTypeBuilder.<T>create(factory::create, blocks).build();
	}

	@Override
	public void addGameTests(Consumer<GameTestRegistrar> registrar) {
		FabricGameTestRegistration.add(registrar);
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
		return FabricItemGroup.builder();
	}

	// ------------------------------------------------------------------ menus

	@Override
	public <M extends AbstractContainerMenu> MenuType<M> blockMenuType(BlockMenuFactory<M> factory) {
		return new ExtendedScreenHandlerType<>((containerId, inventory, buffer) -> factory.create(containerId, inventory, buffer.readBlockPos()));
	}

	@Override
	public void openBlockMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
		player.openMenu(new ExtendedScreenHandlerFactory()
		{
			@Override
			public void writeScreenOpeningData(ServerPlayer opener, FriendlyByteBuf buffer) {
				buffer.writeBlockPos(pos);
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

	/** The kettle's own book type, added by {@code RecipeBookTypeMixin} (see {@link FabricRecipeBook}). */
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
		return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
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
		// The furnace's own table (Fabric's FuelRegistry is a view of it), asked through isFuel so a barrel with a batch in it
		// says no (AbstractFurnaceBlockEntityMixin)
		return AbstractFurnaceBlockEntity.isFuel(stack) ? AbstractFurnaceBlockEntity.getFuel().getOrDefault(stack.getItem(), 0) : 0;
	}

	@Override
	public float compostChance(net.minecraft.world.item.Item item) {
		return net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES.getOrDefault(item, 0.0F);
	}

	@Override
	public @Nullable ItemStack craftingRemainder(ItemStack stack) {
		// Fabric's per-stack remainder (FabricItem#getRecipeRemainder; vanilla's item remainder by default)
		ItemStack remainder = stack.getRecipeRemainder();
		return remainder.isEmpty() ? null : remainder.copy();
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

	// ------------------------------------------------------------------ villager trades

	/** Trades handed over before the registries are filled, added to the pools once they are ({@link #registerAll}). */
	private static final List<Runnable> PENDING_TRADES = new java.util.ArrayList<>();

	@Override
	public void addVillagerTrades(java.util.function.Supplier<? extends VillagerProfession> profession, int level, List<VillagerTrades.ItemListing> trades) {
		// Straight into vanilla's own pools, as on the newer versions (there Fabric API's TradeOfferHelper would also fill
		// the "trade rebalance" experiment's map; this Minecraft has no such experiment). Our own profession (the tavern
		// keeper) exists once the registries are filled.
		PENDING_TRADES.add(() -> append(VillagerTrades.TRADES.computeIfAbsent(profession.get(), key -> new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()),
			level, trades));
	}

	@Override
	public void addWanderingTraderTrades(List<VillagerTrades.ItemListing> trades) {
		// Level 1 is the ordinary pool (five offers are drawn from it), 2 the rare one
		append(VillagerTrades.WANDERING_TRADER_TRADES, 1, trades);
	}

	private static void append(it.unimi.dsi.fastutil.ints.Int2ObjectMap<VillagerTrades.ItemListing[]> pools, int level, List<VillagerTrades.ItemListing> trades) {
		List<VillagerTrades.ItemListing> pool = new java.util.ArrayList<>(List.of(pools.getOrDefault(level, new VillagerTrades.ItemListing[0])));
		trades.stream().filter(trade -> !pool.contains(trade)).forEach(pool::add);
		pools.put(level, pool.toArray(VillagerTrades.ItemListing[]::new));
	}

	@Override
	public void prepareMockConnection(net.minecraft.network.Connection connection) {
	}
}
