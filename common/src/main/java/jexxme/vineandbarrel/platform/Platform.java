package jexxme.vineandbarrel.platform;

import jexxme.vineandbarrel.platform.registry.DeferredRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Everything the common code needs from the mod loader, in one place. NeoForge and Fabric each implement it (see
 * {@link Services}); events go the other way, from each loader's glue into the common classes.
 */
public interface Platform
{
	enum Loader
	{
		NEOFORGE, FABRIC
	}

	Loader loader();

	boolean isModLoaded(String modId);

	/** The installed version of a mod, if it is installed. */
	Optional<String> modVersion(String modId);

	/** True in a dev environment (Gradle runs), false in a normal game. */
	boolean isDevelopment();

	// ------------------------------------------------------------------ registration

	/** Adds every entry of {@code register} to the game's registry at the loader's registration time. */
	<T> void register(DeferredRegister<T> register);

	/**
	 * The game's holder for a registry entry, also before it is registered (it is filled in by the registration). Only
	 * for registries without intrusive holders (not blocks or items) when called before registration.
	 */
	<T> Holder<T> holder(ResourceKey<T> key);

	<T> EntityAttachment<T> attachment(ResourceLocation id, EntityAttachment.Spec<T> spec);

	/**
	 * A block entity type for these blocks. Its factory interface is not public in vanilla (each loader opens it its own
	 * way), so common code can't call {@code BlockEntityType.Builder.of} itself on this Minecraft.
	 */
	<T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityType<T> blockEntityType(
		BlockEntityFactory<T> factory, net.minecraft.world.level.block.Block... blocks);

	@FunctionalInterface
	interface BlockEntityFactory<T extends net.minecraft.world.level.block.entity.BlockEntity>
	{
		T create(BlockPos pos, BlockState state);
	}

	/** Adds GameTests when the GameTest registries load (dev only; see {@code VineAndBarrel}). */
	void addGameTests(Consumer<GameTestRegistrar> registrar);

	/**
	 * A GameTest that needs the loader's own API (its event bus, its mod metadata), by name, or null if this loader has
	 * no such test. The common test classes register it under that name like their own.
	 */
	@Nullable
	Consumer<GameTestHelper> loaderGameTest(String name);

	/** Hands AppleSkin a drink's real food (its event differs between loaders). Only called with AppleSkin installed. */
	void registerAppleSkin();

	/** A creative tab builder with the loader's defaults (vanilla's needs a row and column). */
	CreativeModeTab.Builder creativeTabBuilder();

	// ------------------------------------------------------------------ menus

	/** A menu type whose client side is opened at a block (the block's position is sent along). */
	<M extends AbstractContainerMenu> MenuType<M> blockMenuType(BlockMenuFactory<M> factory);

	/** Opens a menu made by a {@link #blockMenuType} type on the player's client. */
	void openBlockMenu(ServerPlayer player, MenuProvider provider, BlockPos pos);

	/**
	 * Opens such a menu for a GameTest's mock player, whose connection can't take NeoForge's extra-data payload
	 * (NeoForge: vanilla's {@code openMenu}; Fabric: the normal way, which works there).
	 */
	void openMenuForGameTest(ServerPlayer player, MenuProvider provider, BlockPos pos);

	@FunctionalInterface
	interface BlockMenuFactory<M extends AbstractContainerMenu>
	{
		M create(int windowId, Inventory playerInventory, BlockPos pos);
	}

	/** The Brew Kettle's own recipe book type (an extended vanilla enum on both loaders). */
	RecipeBookType brewingRecipeBookType();

	// ------------------------------------------------------------------ game rules the loaders hook into

	/** Whether a crop may grow this random tick (other mods can veto it on NeoForge); {@code vanilla} is our own roll. */
	boolean canCropGrow(ServerLevel level, BlockPos pos, BlockState state, boolean vanilla);

	/** Tells other mods a crop has grown. */
	void cropGrown(ServerLevel level, BlockPos pos, BlockState state);

	/** Whether a mob may change blocks (mob griefing, which other mods can override on NeoForge). */
	boolean canEntityGrief(ServerLevel level, Entity entity);

	/** The unwaxed form of a waxed block (vanilla's and every mod's), or null if it isn't waxed. */
	@Nullable
	Block unwaxed(Block block);

	/** What chat makes of a player's message (slurred when Tipsy), through the loader's real chat decorators. */
	Component decorateChat(ServerPlayer player, Component message);

	/**
	 * What this stack leaves behind in a recipe (a bucket, a glass bottle), asked of the stack, so items whose remainder
	 * depends on their components (both loaders let items override it per stack) get theirs; null if nothing. A new stack
	 * each time (this Minecraft has no ItemStackTemplate; the caller may keep or change it).
	 */
	@Nullable
	ItemStack craftingRemainder(ItemStack stack);

	// ------------------------------------------------------------------ older Minecraft (26.2 and below have no item components for these)

	/** Whether this tool scrapes wax and oxidation off copper, as an axe does (26.3: the block_transformer component). */
	boolean canScrape(ItemStack stack);

	/** How long this stack burns, asked the way the loader's furnaces ask (26.3: the cooking_fuel component). */
	int burnTime(ItemStack stack, ServerLevel level);

	/** The composter's chance for this item, 0 if it can't be composted (26.3: the compostable component). */
	float compostChance(net.minecraft.world.item.Item item);

	// ------------------------------------------------------------------ item automation (GameTests)

	/** The block's inventory as pipes and hoppers see it from {@code side}, or null if there is none. */
	@Nullable
	SlotAccess itemAccess(ServerLevel level, BlockPos pos, @Nullable Direction side);

	/** A new outermost loader transaction for {@link SlotAccess}. */
	SlotAccess.Tx openTransaction();

	// ---- blocks (1.21.1 port)

	/**
	 * Adds trades to the pool a villager of this profession draws from at this level (1 novice to 5 master), next to the
	 * vanilla ones and other mods' (26.x: a {@code villager_trade} tag). Called once at startup, before any world loads.
	 * NeoForge: {@code VillagerTradesEvent}; Fabric: {@code TradeOfferHelper.registerVillagerOffers}.
	 */
	void addVillagerTrades(net.minecraft.world.entity.npc.VillagerProfession profession, int level,
			java.util.List<net.minecraft.world.entity.npc.VillagerTrades.ItemListing> trades);

	/**
	 * Adds trades to the wandering trader's ordinary pool (the one five offers are drawn from, not the rare one).
	 * Called once at startup. NeoForge: {@code WandererTradesEvent#getGenericTrades}; Fabric:
	 * {@code TradeOfferHelper.registerWanderingTraderOffers(1, ...)}.
	 */
	void addWanderingTraderTrades(java.util.List<net.minecraft.world.entity.npc.VillagerTrades.ItemListing> trades);

	// ---- gametest (1.21.1 port)

	/**
	 * Makes a GameTest's mock connection take what a real client's takes. NeoForge refuses to send a mod's payload
	 * (its own attachment sync, Jade's ping) over a connection that never negotiated channels:
	 * {@code NetworkRegistry.configureMockConnection}. Fabric: nothing to do.
	 */
	void prepareMockConnection(net.minecraft.network.Connection connection);

}
