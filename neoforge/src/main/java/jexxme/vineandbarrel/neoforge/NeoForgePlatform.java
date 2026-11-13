package jexxme.vineandbarrel.neoforge;

import jexxme.vineandbarrel.gametest.NeoForgeGameTests;
import jexxme.vineandbarrel.neoforge.transfer.NeoForgeItemAccess;
import jexxme.vineandbarrel.platform.EntityAttachment;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import jexxme.vineandbarrel.platform.Platform;
import jexxme.vineandbarrel.platform.SlotAccess;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.DataMapHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** {@link jexxme.vineandbarrel.platform.Platform} on NeoForge (named in META-INF/services). */
public final class NeoForgePlatform implements Platform
{
	/** The mod's event bus, set by {@link VineAndBarrelNeoForge} before anything registers. */
	private static @Nullable IEventBus modBus;
	private static final net.neoforged.neoforge.registries.DeferredRegister<AttachmentType<?>> ATTACHMENTS =
		net.neoforged.neoforge.registries.DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, jexxme.vineandbarrel.VineAndBarrel.MODID);

	/** The GameTest classes' registrations, run once the GameTest server collects its tests (see {@link #addGameTests}). */
	private static final List<Consumer<GameTestRegistrar>> GAME_TESTS = new ArrayList<>();
	private static boolean gameTestsRegistered;
	/** Our trades by profession and level, and the wandering trader's, added whenever NeoForge builds the trade tables. */
	private static final List<VillagerTradeEntry> VILLAGER_TRADES = new ArrayList<>();
	private static final List<VillagerTrades.ItemListing> WANDERING_TRADES = new ArrayList<>();

	static void init(IEventBus bus) {
		modBus = bus;
		ATTACHMENTS.register(bus);
		// The GameTest server asks for tests right before it collects them; a dev client's own server never does, so there
		// they are added when a server starts (for /test)
		bus.addListener(RegisterGameTestsEvent.class, event -> registerGameTests());
		NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, event -> registerGameTests());
		// Both fire from the vanilla tables on every server start and tag reload, so nothing is added twice
		NeoForge.EVENT_BUS.addListener(VillagerTradesEvent.class, event -> {
			for (VillagerTradeEntry entry : VILLAGER_TRADES) {
				if (entry.profession().get() == event.getType()) {
					event.getTrades().get(entry.level()).addAll(entry.trades());
				}
			}
		});
		NeoForge.EVENT_BUS.addListener(WandererTradesEvent.class, event -> event.getGenericTrades().addAll(WANDERING_TRADES));
	}

	/** Adds every test to vanilla's registry, once (the event's own methods only take annotated classes). */
	private static synchronized void registerGameTests() {
		if (gameTestsRegistered) {
			return;
		}
		gameTestsRegistered = true;
		GameTestRegistrar registrar = test -> GameTestRegistry.getAllTestFunctions().add(test);
		GAME_TESTS.forEach(tests -> tests.accept(registrar));
	}

	private static IEventBus modBus() {
		return Objects.requireNonNull(modBus, "Vine & Barrel's NeoForge entry point has not run yet");
	}

	@Override
	public Loader loader() {
		return Loader.NEOFORGE;
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public Optional<String> modVersion(String modId) {
		return ModList.get().getModContainerById(modId).map(mod -> mod.getModInfo().getVersion().toString());
	}

	@Override
	public boolean isDevelopment() {
		return !FMLEnvironment.production;
	}

	// ------------------------------------------------------------------ registration

	@Override
	public <T> void register(DeferredRegister<T> register) {
		modBus().addListener(RegisterEvent.class, event -> event.register(register.getRegistryKey(), helper ->
			register.getEntries().forEach((id, factory) -> helper.register(id, factory.apply(id)))));
	}

	@Override
	public <T> Holder<T> holder(ResourceKey<T> key) {
		return net.neoforged.neoforge.registries.DeferredHolder.create(key);
	}

	@Override
	public <T> EntityAttachment<T> attachment(ResourceLocation id, EntityAttachment.Spec<T> spec) {
		Supplier<AttachmentType<T>> type = ATTACHMENTS.register(id.getPath(), () -> {
			AttachmentType.Builder<T> builder = AttachmentType.builder(spec.defaultValue());
			if (spec.saveCodec() != null) {
				builder.serialize(spec.saveCodec().codec());
			}
			if (spec.syncCodec() != null) {
				builder.sync(spec.syncCodec());
			}
			if (spec.copyOnDeath()) {
				builder.copyOnDeath();
			}
			return builder.build();
		});
		return new EntityAttachment<>()
		{
			@Override
			public boolean has(Entity entity) {
				return entity.hasData(type);
			}

			@Override
			public T get(Entity entity) {
				return entity.getData(type);
			}

			@Override
			public @Nullable T getExisting(Entity entity) {
				return entity.getExistingDataOrNull(type);
			}

			@Override
			public void set(Entity entity, T value) {
				entity.setData(type, value);
			}

			@Override
			public void remove(Entity entity) {
				entity.removeData(type);
			}
		};
	}

	@Override
	public void addGameTests(Consumer<GameTestRegistrar> registrar) {
		GAME_TESTS.add(registrar);
	}

	@Override
	public @Nullable Consumer<GameTestHelper> loaderGameTest(String name) {
		return NeoForgeGameTests.get(name);
	}

	@Override
	public void registerAppleSkin() {
		jexxme.vineandbarrel.neoforge.compat.AppleSkinNeoForge.register();
	}

	@Override
	public CreativeModeTab.Builder creativeTabBuilder() {
		return CreativeModeTab.builder();
	}

	// ------------------------------------------------------------------ menus

	@Override
	public <M extends AbstractContainerMenu> MenuType<M> blockMenuType(BlockMenuFactory<M> factory) {
		return IMenuTypeExtension.create((windowId, inventory, data) -> factory.create(windowId, inventory, data.readBlockPos()));
	}

	@Override
	public void openBlockMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
		player.openMenu(provider, pos);
	}

	@Override
	public void openMenuForGameTest(ServerPlayer player, MenuProvider provider, BlockPos pos) {
		player.openMenu(provider);
	}

	@Override
	public RecipeBookType brewingRecipeBookType() {
		return RecipeBookType.valueOf("VINEANDBARREL_BREWING");
	}

	// ------------------------------------------------------------------ game rules

	@Override
	public boolean canCropGrow(ServerLevel level, BlockPos pos, BlockState state, boolean vanilla) {
		return CommonHooks.canCropGrow(level, pos, state, vanilla);
	}

	@Override
	public void cropGrown(ServerLevel level, BlockPos pos, BlockState state) {
		CommonHooks.fireCropGrowPost(level, pos, state);
	}

	@Override
	public boolean canEntityGrief(ServerLevel level, Entity entity) {
		return EventHooks.canEntityGrief(level, entity);
	}

	@Override
	public @Nullable Block unwaxed(Block block) {
		return DataMapHooks.getBlockUnwaxed(block);
	}

	@Override
	public boolean canScrape(ItemStack stack) {
		return stack.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.AXE_SCRAPE);
	}

	@Override
	public int burnTime(ItemStack stack, ServerLevel level) {
		return stack.getBurnTime(null);
	}

	@Override
	public float compostChance(net.minecraft.world.item.Item item) {
		var compostable = item.builtInRegistryHolder().getData(net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps.COMPOSTABLES);
		return compostable == null ? 0.0F : compostable.chance();
	}

	@Override
	public Component decorateChat(ServerPlayer player, Component message) {
		return CommonHooks.getServerChatSubmittedDecorator().decorate(player, message);
	}

	@Override
	public @Nullable ItemStack craftingRemainder(ItemStack stack) {
		// A new stack each time: items hand out a fresh one (vanilla's is new ItemStack(getCraftingRemainingItem()))
		return stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem().copy() : null;
	}

	// ------------------------------------------------------------------ item automation

	@Override
	public @Nullable SlotAccess itemAccess(ServerLevel level, BlockPos pos, @Nullable Direction side) {
		return NeoForgeItemAccess.of(level, pos, side);
	}

	@Override
	public SlotAccess.Tx openTransaction() {
		return NeoForgeItemAccess.Tx.openRoot();
	}

	// ------------------------------------------------------------------ villager trades

	/** Trades for a profession's level; the profession is looked up when the trades are built (it may be ours, registered later). */
	private record VillagerTradeEntry(java.util.function.Supplier<? extends VillagerProfession> profession, int level, List<VillagerTrades.ItemListing> trades) {
	}

	@Override
	public void addVillagerTrades(java.util.function.Supplier<? extends VillagerProfession> profession, int level, List<VillagerTrades.ItemListing> trades) {
		VILLAGER_TRADES.add(new VillagerTradeEntry(profession, level, List.copyOf(trades)));
	}

	@Override
	public void addWanderingTraderTrades(List<VillagerTrades.ItemListing> trades) {
		WANDERING_TRADES.addAll(trades);
	}

	@Override
	public void prepareMockConnection(net.minecraft.network.Connection connection) {
		net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
	}

	@Override
	public boolean dropsFoodRemainder() {
		return true;
	}
}
