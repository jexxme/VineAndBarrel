package jexxme.vineandbarrel.forge;

import io.netty.util.AttributeKey;
import jexxme.vineandbarrel.gametest.ForgeGameTests;
import jexxme.vineandbarrel.forge.transfer.ForgeItemAccess;
import jexxme.vineandbarrel.platform.EntityAttachment;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import jexxme.vineandbarrel.platform.Platform;
import jexxme.vineandbarrel.platform.SlotAccess;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** {@link jexxme.vineandbarrel.platform.Platform} on Forge (named in META-INF/services). */
public final class ForgePlatform implements Platform
{
	/**
	 * The Brew Kettle's recipe book type. Forge 47 extends the enum in code ({@code IExtensibleEnum}), once, here: this
	 * class loads while the mod is constructed, on the client and the server, before any menu or player exists. The
	 * constant's {@code init} tells {@code RecipeBookSettings} its save keys, so the book's open and filter state is
	 * saved like a vanilla book's. It is sent our own way: see {@link ForgeRecipeBook}.
	 */
	private static final RecipeBookType BREWING_BOOK = RecipeBookType.create(ForgeRecipeBook.BREWING);

	/** The mod's event bus, set by {@link VineAndBarrelForge} before anything registers. */
	private static @Nullable IEventBus modBus;

	/** The GameTest classes' registrations, run once the GameTest server collects its tests (see {@link #addGameTests}). */
	private static final List<Consumer<GameTestRegistrar>> GAME_TESTS = new ArrayList<>();
	private static boolean gameTestsRegistered;
	/** Our trades by profession and level, and the wandering trader's, added whenever Forge builds the trade tables. */
	private static final List<VillagerTradeEntry> VILLAGER_TRADES = new ArrayList<>();
	private static final List<VillagerTrades.ItemListing> WANDERING_TRADES = new ArrayList<>();

	static void init(IEventBus bus) {
		modBus = bus;
		ForgeAttachments.init();
		ForgeRecipeBook.init();
		// The GameTest server asks for tests right before it collects them; a dev client's own server never does, so there
		// they are added when a server starts (for /test)
		bus.addListener((RegisterGameTestsEvent event) -> registerGameTests());
		MinecraftForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> registerGameTests());
		// Forge 47 builds its GameTest server on the dedicated server's world (see forge/build.gradle), so the two game
		// rules of vanilla's own test world (GameTestServer.TEST_GAME_RULES) are set here: no mobs spawn into the tests,
		// and the weather stays
		MinecraftForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
			if (event.getServer() instanceof GameTestServer) {
				event.getServer().getWorldData().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
				event.getServer().getWorldData().getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
			}
		});
		// Both fire from the vanilla tables on every server start and tag reload, so nothing is added twice
		MinecraftForge.EVENT_BUS.addListener((VillagerTradesEvent event) -> {
			for (VillagerTradeEntry entry : VILLAGER_TRADES) {
				if (entry.profession().get() == event.getType()) {
					event.getTrades().get(entry.level()).addAll(entry.trades());
				}
			}
		});
		MinecraftForge.EVENT_BUS.addListener((WandererTradesEvent event) -> event.getGenericTrades().addAll(WANDERING_TRADES));
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

	static RecipeBookType brewingBook() {
		return BREWING_BOOK;
	}

	private static IEventBus modBus() {
		return Objects.requireNonNull(modBus, "Vine & Barrel's Forge entry point has not run yet");
	}

	/** Forge 47 is this Minecraft's "NeoForge" side of the common code: the enum has no constant of its own for it. */
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
		modBus().addListener((RegisterEvent event) -> event.register(register.getRegistryKey(), helper -> {
			register.getEntries().forEach((id, factory) -> helper.register(id, factory.apply(id)));
			// On a server without Vine & Barrel, Forge takes our entries out of the client's registries for as long as it
			// plays there: the holders keep what they hold, so nothing of ours fails when it asks for its own item or effect
			register.resolve();
		}));
	}

	/**
	 * Before registration the registry's own registration lookup hands out the reference that registering fills in
	 * (Forge's registry wrapper and vanilla's registries both make it on demand).
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T> Holder<T> holder(ResourceKey<T> key) {
		Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.get(key.registry());
		if (registry == null) {
			throw new IllegalStateException("No registry " + key.registry() + " for " + key);
		}
		Optional<Holder.Reference<T>> registered = registry.getHolder(key);
		if (registered.isPresent()) {
			return registered.get();
		}
		if (registry instanceof net.minecraft.core.WritableRegistry<T> writable) {
			return writable.createRegistrationLookup().getOrThrow(key);
		}
		throw new IllegalStateException("Not registered yet: " + key);
	}

	@Override
	public <T> EntityAttachment<T> attachment(ResourceLocation id, EntityAttachment.Spec<T> spec) {
		return ForgeAttachments.create(id, spec);
	}

	/** Forge's own access transformer makes vanilla's factory interface public. */
	@Override
	@SuppressWarnings("DataFlowIssue") // no data fixer type, as every mod's block entity
	public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... blocks) {
		return BlockEntityType.Builder.<T>of(factory::create, blocks).build(null);
	}

	@Override
	public void addGameTests(Consumer<GameTestRegistrar> registrar) {
		GAME_TESTS.add(registrar);
	}

	@Override
	public @Nullable Consumer<GameTestHelper> loaderGameTest(String name) {
		return ForgeGameTests.get(name);
	}

	@Override
	public void registerAppleSkin() {
		jexxme.vineandbarrel.forge.compat.AppleSkinForge.register();
	}

	@Override
	public CreativeModeTab.Builder creativeTabBuilder() {
		return CreativeModeTab.builder();
	}

	// ------------------------------------------------------------------ menus

	@Override
	public <M extends AbstractContainerMenu> MenuType<M> blockMenuType(BlockMenuFactory<M> factory) {
		return IForgeMenuType.create((windowId, inventory, data) -> factory.create(windowId, inventory, data.readBlockPos()));
	}

	@Override
	public void openBlockMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
		NetworkHooks.openScreen(player, provider, pos);
	}

	/** Vanilla's own way: the mock player has no client that would read Forge's open-screen packet and its position. */
	@Override
	public void openMenuForGameTest(ServerPlayer player, MenuProvider provider, BlockPos pos) {
		player.openMenu(provider);
	}

	@Override
	public RecipeBookType brewingRecipeBookType() {
		return BREWING_BOOK;
	}

	// ------------------------------------------------------------------ game rules

	@Override
	public boolean canCropGrow(ServerLevel level, BlockPos pos, BlockState state, boolean vanilla) {
		return ForgeHooks.onCropsGrowPre(level, pos, state, vanilla);
	}

	@Override
	public void cropGrown(ServerLevel level, BlockPos pos, BlockState state) {
		ForgeHooks.onCropsGrowPost(level, pos, state);
	}

	@Override
	public boolean canEntityGrief(ServerLevel level, Entity entity) {
		return ForgeEventFactory.getMobGriefingEvent(level, entity);
	}

	@Override
	public @Nullable Block unwaxed(Block block) {
		return ForgeCopper.unwaxedOfAny(block);
	}

	@Override
	public boolean canScrape(ItemStack stack) {
		return stack.canPerformAction(ToolActions.AXE_SCRAPE);
	}

	/** As a furnace asks: the item's own answer or vanilla's table, then {@code FurnaceFuelBurnTimeEvent} (ours is in {@link VineAndBarrelForge}). */
	@Override
	public int burnTime(ItemStack stack, ServerLevel level) {
		return ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
	}

	/** Vanilla's table, which {@link VineAndBarrelForge} adds ours to (it answers -1 for what can't be composted). */
	@Override
	public float compostChance(net.minecraft.world.item.Item item) {
		return Math.max(0.0F, ComposterBlock.COMPOSTABLES.getFloat(item));
	}

	/**
	 * Forge 47's chat decorator is its {@code ServerChatEvent} (posted off the server thread for real chat); here the
	 * event is posted directly. A listener that cancels the message leaves nothing to show: the text as it came.
	 */
	@Override
	public Component decorateChat(ServerPlayer player, Component message) {
		Component decorated = ForgeHooks.onServerChatSubmittedEvent(player, message.getString(), message);
		return decorated == null ? message : decorated;
	}

	@Override
	public @Nullable ItemStack craftingRemainder(ItemStack stack) {
		// A new stack each time: items hand out a fresh one (vanilla's is new ItemStack(getCraftingRemainingItem()))
		return stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem().copy() : null;
	}

	// ------------------------------------------------------------------ item automation

	@Override
	public @Nullable SlotAccess itemAccess(ServerLevel level, BlockPos pos, @Nullable Direction side) {
		return ForgeItemAccess.of(level, pos, side);
	}

	@Override
	public SlotAccess.Tx openTransaction() {
		return ForgeItemAccess.Tx.openRoot();
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

	/**
	 * A mock connection never shook hands, so its channel does not say which kind of client it is, and Forge's
	 * networking fails on the missing answer ({@code NetworkHooks.isVanillaConnection}, asked for every joining
	 * player). It is marked as what a Forge client is. Sending to it is then harmless: packets, ours included
	 * ({@link ForgeAttachments}), end in the embedded channel's outbound queue.
	 */
	@Override
	public void prepareMockConnection(net.minecraft.network.Connection connection) {
		if (connection.channel() != null) {
			connection.channel().attr(AttributeKey.<String>valueOf("fml:netversion")).set(NetworkConstants.NETVERSION);
		}
	}
}
