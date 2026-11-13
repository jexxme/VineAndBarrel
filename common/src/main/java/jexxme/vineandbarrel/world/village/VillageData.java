package jexxme.vineandbarrel.world.village;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import jexxme.vineandbarrel.barrel.AgingBarrelItem;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The data of M6, set up like FD's: villager trades join the vanilla trade pools; chest loot is our own tables (generated
 * by {@code runData}, hooked in from {@code VBDataGenerators}), added to the vanilla village chests by global loot
 * modifiers.
 * <p>
 * This Minecraft has no data-driven trades (26.x: {@code villager_trade} files joined to the vanilla pools by tag), so
 * the same trades are built here and handed to the loader ({@link #registerTrades}).
 */
public final class VillageData
{
	// ------------------------------------------------------------------ trades (DESIGN 3: farmers and the wandering trader sell our seeds)
	// A farmer sells a few seeds for an emerald, like a vanilla sell trade (16 uses; 1 xp at novice, 5 at apprentice).
	// Grapes and hops are their own seed (planted like sweet berries), so the farmer sells the produce itself.
	public static final Trade FARMER_1_BARLEY_SEEDS = sells("farmer/1/barley_seeds", VBItems.BARLEY_SEEDS, 6, 16, 1);
	public static final Trade FARMER_2_RED_GRAPES = sells("farmer/2/red_grapes", VBItems.RED_GRAPES, 3, 16, 5);
	public static final Trade FARMER_2_HOPS = sells("farmer/2/hops", VBItems.HOPS, 3, 16, 5);
	public static final Trade FARMER_2_WHITE_GRAPES = sells("farmer/2/white_grapes", VBItems.WHITE_GRAPES, 3, 16, 5);
	// The wandering trader: one for an emerald, 12 uses, exactly like FD's seeds
	public static final Trade WANDERING_TRADER_RED_GRAPES = sells("wandering_trader/red_grapes", VBItems.RED_GRAPES, 1, 12, 1);
	public static final Trade WANDERING_TRADER_HOPS = sells("wandering_trader/hops", VBItems.HOPS, 1, 12, 1);
	public static final Trade WANDERING_TRADER_WHITE_GRAPES = sells("wandering_trader/white_grapes", VBItems.WHITE_GRAPES, 1, 12, 1);
	public static final Trade WANDERING_TRADER_BARLEY_SEEDS = sells("wandering_trader/barley_seeds", VBItems.BARLEY_SEEDS, 1, 12, 1);
	// Farmers also buy barley (novice) and red grapes (apprentice), like vanilla's wheat and FD's cabbages and rice.
	// Buying, 20 for an emerald. A farmer offers two trades per level: journeyman, expert and master have exactly two
	// vanilla ones, so a farmer always offers both, and we leave those levels alone. Novice and apprentice already
	// draw at random, so each takes one buy of ours: barley at novice like vanilla's wheat (16 uses, 2 xp), red grapes
	// at apprentice like FD's cabbages and rice (16 uses, 5 xp)
	public static final Trade FARMER_1_BARLEY_EMERALD = buys("farmer/1/barley_emerald", VBItems.BARLEY, 20, 16, 2);
	public static final Trade FARMER_2_RED_GRAPES_EMERALD = buys("farmer/2/red_grapes_emerald", VBItems.RED_GRAPES, 20, 16, 5);

	/** What joins the novice farmer's pool (26.x: {@code #minecraft:farmer/level_1}), in the order the tag listed them. */
	public static final List<Trade> FARMER_LEVEL_1 = List.of(FARMER_1_BARLEY_SEEDS, FARMER_1_BARLEY_EMERALD);
	/** What joins the apprentice farmer's pool (26.x: {@code #minecraft:farmer/level_2}). */
	public static final List<Trade> FARMER_LEVEL_2 = List.of(FARMER_2_RED_GRAPES, FARMER_2_HOPS, FARMER_2_WHITE_GRAPES, FARMER_2_RED_GRAPES_EMERALD);
	/** What joins the wandering trader's ordinary pool (26.x: {@code #minecraft:wandering_trader/common}). */
	public static final List<Trade> WANDERING_TRADER_COMMON = List.of(WANDERING_TRADER_RED_GRAPES, WANDERING_TRADER_HOPS, WANDERING_TRADER_BARLEY_SEEDS,
			WANDERING_TRADER_WHITE_GRAPES);

	private VillageData() {
	}

	/**
	 * One of our trades: {@code count} of {@code item} for an emerald, or an emerald for them ({@code buys}). The id is the
	 * one its 26.x data file had.
	 */
	public record Trade(ResourceLocation id, boolean buys, Supplier<? extends ItemLike> item, int count, int maxUses, int xp, float priceMultiplier)
			implements VillagerTrades.ItemListing
	{
		@Override
		public MerchantOffer getOffer(Entity trader, RandomSource random) {
			return buys
					? new MerchantOffer(new ItemStack(item.get(), count), new ItemStack(Items.EMERALD), maxUses, xp, priceMultiplier)
					: new MerchantOffer(new ItemStack(Items.EMERALD), new ItemStack(item.get(), count), maxUses, xp, priceMultiplier);
		}
	}

	/**
	 * Hands the trades to the loader, once at startup: they join the pools a farmer (novice, apprentice) and the wandering
	 * trader draw from at random, next to the vanilla trades and other mods'.
	 */
	public static void registerTrades() {
		Services.PLATFORM.addVillagerTrades(() -> VillagerProfession.FARMER, 1, List.copyOf(FARMER_LEVEL_1));
		Services.PLATFORM.addVillagerTrades(() -> VillagerProfession.FARMER, 2, List.copyOf(FARMER_LEVEL_2));
		Services.PLATFORM.addWanderingTraderTrades(List.copyOf(WANDERING_TRADER_COMMON));
		for (int level = 1; level <= 5; level++) {
			Services.PLATFORM.addVillagerTrades(TavernKeeper.TAVERN_KEEPER, level, List.of(new KeeperDraw(level, 0), new KeeperDraw(level, 1)));
		}
	}

	private static Trade buys(String path, Supplier<? extends ItemLike> item, int count, int maxUses, int xp) {
		return new Trade(VineAndBarrel.id(path), true, item, count, maxUses, xp, 0.05F);
	}

	private static Trade sells(String path, Supplier<? extends ItemLike> item, int count, int maxUses, int xp) {
		return new Trade(VineAndBarrel.id(path), false, item, count, maxUses, xp, 0.05F);
	}

	// ------------------------------------------------------------------ the tavern keeper's trades (1.2)

	/**
	 * One tavern keeper trade: {@code wantsCount} of {@code wants} for {@code givesCount} of {@code gives}. With village
	 * types, only a keeper of one of them offers it (26.x: the trade's {@code merchant_predicate}); his level draws two
	 * that he offers, like vanilla's. A {@code sealed} trade gives an Aging Barrel holding that aged drink
	 * ({@link TavernKeeper#sealedCask}). The id is the one its 26.x data file has.
	 */
	public record KeeperTrade(ResourceLocation id, Supplier<? extends ItemLike> wants, int wantsCount, Supplier<? extends ItemLike> gives, int givesCount,
		int maxUses, int xp, List<VillagerType> types, @Nullable Supplier<? extends Item> sealed) implements VillagerTrades.ItemListing
	{
		@Override
		public @Nullable MerchantOffer getOffer(Entity trader, RandomSource random) {
			if (!offeredBy(trader)) {
				return null;
			}
			ItemStack result = sealed != null && trader.level() instanceof ServerLevel level
				? TavernKeeper.sealedCask(level, sealed.get(), TavernKeeper.SEALED_SERVINGS)
				: new ItemStack(gives.get(), givesCount);
			return new MerchantOffer(new ItemStack(wants.get(), wantsCount), result, maxUses, xp, 0.05F);
		}

		/** Whether a trader of this village type offers it. */
		public boolean offeredBy(Entity trader) {
			return types.isEmpty() || (trader instanceof VillagerDataHolder villager && types.contains(villager.getVillagerData().getType()));
		}
	}

	/**
	 * One of the two offers a keeper's level draws. This Minecraft draws two different entries of a level's pool and keeps
	 * whatever they offer, so a trade for another village type (no offer) would leave him one short; his pool is these two
	 * draws instead, which pick two different trades among those his village type offers (26.x and 1.21.1 draw two from
	 * the whole pool, passing over the ones he doesn't offer). Both draws of a villager's level see the same order.
	 */
	public record KeeperDraw(int level, int slot) implements VillagerTrades.ItemListing
	{
		@Override
		public @Nullable MerchantOffer getOffer(Entity trader, RandomSource random) {
			List<KeeperTrade> offered = new ArrayList<>(keeperTrades(level).stream().filter(trade -> trade.offeredBy(trader)).toList());
			java.util.Collections.shuffle(offered, new java.util.Random(trader.getUUID().getLeastSignificantBits() * 31L + level));
			return slot < offered.size() ? offered.get(slot).getOffer(trader, random) : null;
		}
	}

	private static KeeperTrade keeper(int level, String name, Supplier<? extends ItemLike> wants, int wantsCount, Supplier<? extends ItemLike> gives,
		int givesCount, int maxUses, int xp, List<VillagerType> types) {
		return new KeeperTrade(VineAndBarrel.id(TavernKeeper.NAME + "/" + level + "/" + name), wants, wantsCount, gives, givesCount, maxUses, xp, types, null);
	}

	/** Buys {@code count} of {@code item} for an emerald. */
	private static KeeperTrade keeperBuys(int level, String name, Supplier<? extends ItemLike> item, int count, int maxUses, int xp, List<VillagerType> types) {
		return keeper(level, name, item, count, () -> Items.EMERALD, 1, maxUses, xp, types);
	}

	/** Sells {@code count} of {@code item} for an emerald. */
	private static KeeperTrade keeperSells(int level, String name, Supplier<? extends ItemLike> item, int count, int maxUses, int xp) {
		return keeper(level, name, () -> Items.EMERALD, 1, item, count, maxUses, xp, List.of());
	}

	/** Sells one {@code item} for {@code emeralds}. */
	private static KeeperTrade keeperSellsFor(int level, String name, Supplier<? extends ItemLike> item, int emeralds, int maxUses, int xp,
		List<VillagerType> types) {
		return keeper(level, name, () -> Items.EMERALD, emeralds, item, 1, maxUses, xp, types);
	}

	/** A drink a keeper of these village types offers. */
	private record Offer(String name, Supplier<? extends Item> item, List<VillagerType> types) {
	}

	private static Offer offer(String name, Supplier<? extends Item> item, List<VillagerType> types) {
		return new Offer(name, item, types);
	}

	/** Every keeper trade of a level (1 novice to 5 master); a trade with village types is only offered by a keeper of one of them. */
	public static List<KeeperTrade> keeperTrades(int level) {
		return KEEPER_TRADES.stream().filter(trade -> trade.id().getPath().startsWith(TavernKeeper.NAME + "/" + level + "/")).toList();
	}

	/** Every keeper trade, by level. */
	public static final List<KeeperTrade> KEEPER_TRADES = keeperTrades();

	private static List<KeeperTrade> keeperTrades() {
		VillagerType P = VillagerType.PLAINS, D = VillagerType.DESERT, V = VillagerType.SAVANNA, N = VillagerType.SNOW, T = VillagerType.TAIGA,
			J = VillagerType.JUNGLE, W = VillagerType.SWAMP;
		List<KeeperTrade> trades = new ArrayList<>();
		// Novice: buys the brewer's crops, sells vessels
		trades.add(keeperBuys(1, "barley", VBItems.BARLEY, 20, 16, 2, List.of()));
		trades.add(keeperBuys(1, "hops", VBItems.HOPS, 16, 16, 2, List.of()));
		trades.add(keeperSells(1, "mugs", DrinkItems.MUG, 3, 12, 1));
		trades.add(keeperSells(1, "wine_glasses", DrinkItems.WINE_GLASS, 2, 12, 1));
		// Apprentice: grapes, and a young beer
		trades.add(keeperBuys(2, "red_grapes", VBItems.RED_GRAPES, 20, 16, 10, List.of()));
		trades.add(keeperBuys(2, "white_grapes", VBItems.WHITE_GRAPES, 20, 16, 10, List.of()));
		trades.add(keeperSellsFor(2, "barley_ale", DrinkItems.BARLEY_ALE, 1, 12, 5, List.of()));
		trades.add(keeperSellsFor(2, "wheat_beer", DrinkItems.WHEAT_BEER, 1, 12, 5, List.of()));
		// Journeyman: his region's drinks, and what they are made of
		for (Offer drink : List.of(offer("red_wine", DrinkItems.RED_WINE, List.of(D, V, J)), offer("white_wine", DrinkItems.WHITE_WINE, List.of(P, T)),
			offer("mead", DrinkItems.MEAD, List.of(N, T)), offer("sweet_berry_wine", DrinkItems.SWEET_BERRY_WINE, List.of(T, N)),
			offer("melon_wine", DrinkItems.MELON_WINE, List.of(V, J, D)), offer("hard_cider", DrinkItems.HARD_CIDER, List.of(P, W)))) {
			trades.add(keeperSellsFor(3, drink.name(), drink.item(), 2, 12, 10, drink.types()));
		}
		trades.add(keeperBuys(3, "honey_bottle", () -> Items.HONEY_BOTTLE, 4, 16, 20, List.of()));
		trades.add(keeperBuys(3, "sweet_berries", () -> Items.SWEET_BERRIES, 16, 16, 20, List.of(T, N)));
		trades.add(keeperBuys(3, "apples", () -> Items.APPLE, 10, 16, 20, List.of(P, W)));
		trades.add(keeperBuys(3, "melon_slices", () -> Items.MELON_SLICE, 16, 16, 20, List.of(D, V, J)));
		// Expert: aged drinks of his region
		for (Offer drink : List.of(offer("aged_red_wine", DrinkItems.AGED_RED_WINE, List.of(D, V, J)),
			offer("aged_white_wine", DrinkItems.AGED_WHITE_WINE, List.of(P, T)), offer("aged_mead", DrinkItems.AGED_MEAD, List.of(N, T)),
			offer("aged_sweet_berry_wine", DrinkItems.AGED_SWEET_BERRY_WINE, List.of(T, N)), offer("aged_melon_wine", DrinkItems.AGED_MELON_WINE, List.of(V, J, D)),
			offer("aged_hard_cider", DrinkItems.AGED_HARD_CIDER, List.of(P, W)), offer("aged_wheat_beer", DrinkItems.AGED_WHEAT_BEER, List.of(P, N, T)))) {
			trades.add(keeperSellsFor(4, drink.name(), drink.item(), 5, 6, 15, drink.types()));
		}
		trades.add(keeperSellsFor(4, "aged_barley_ale", DrinkItems.AGED_BARLEY_ALE, 5, 6, 15, List.of()));
		// Master: a sealed cask of the house's aged best (four servings, it places full), and an aged white wine
		for (Offer cask : List.of(offer("sealed_cask_barley_ale", DrinkItems.AGED_BARLEY_ALE, List.of(P)), offer("sealed_cask_red_wine", DrinkItems.AGED_RED_WINE, List.of(D)),
			offer("sealed_cask_melon_wine", DrinkItems.AGED_MELON_WINE, List.of(V, J)), offer("sealed_cask_mead", DrinkItems.AGED_MEAD, List.of(N)),
			offer("sealed_cask_sweet_berry_wine", DrinkItems.AGED_SWEET_BERRY_WINE, List.of(T)), offer("sealed_cask_hard_cider", DrinkItems.AGED_HARD_CIDER, List.of(W)))) {
			trades.add(new KeeperTrade(VineAndBarrel.id(TavernKeeper.NAME + "/5/" + cask.name()), () -> Items.EMERALD, 20, BarrelRegistry.AGING_BARREL_ITEM, 1, 3, 30,
				cask.types(), cask.item()));
		}
		trades.add(keeperSellsFor(5, "aged_white_wine", DrinkItems.AGED_WHITE_WINE, 6, 6, 30, List.of()));
		return List.copyOf(trades);
	}

	// ------------------------------------------------------------------ chest loot (DESIGN 3 and 8)

	/** A loot entry: {@code min}..{@code max} of an item. */
	private record Entry(Supplier<? extends ItemLike> item, int weight, int min, int max) {
		LootPoolEntryContainer.Builder<?> build() {
			LootPoolSingletonContainer.Builder<?> entry = LootItem.lootTableItem(item.get()).setWeight(weight);
			if (min != 1 || max != 1) {
				entry.apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)));
			}
			return entry;
		}
	}

	private static Entry e(Supplier<? extends ItemLike> item, int weight, int min, int max) {
		return new Entry(item, weight, min, max);
	}

	private static LootPool.Builder pool(int minRolls, int maxRolls, Entry... entries) {
		LootPool.Builder pool = LootPool.lootPool().setRolls(UniformGenerator.between(minRolls, maxRolls));
		for (Entry entry : entries) {
			pool.add(entry.build());
		}
		return pool;
	}

	/**
	 * What every village house chest of a type gains: 1 to 2 rolls, mostly seeds of the crops that type's farms grow,
	 * now and then a drink of the region. FD adds 1 to 3 rolls of its seeds to the same chests.
	 */
	public static LootTable.Builder houseLoot(VillageType type) {
		return LootTable.lootTable().withPool(pool(1, 2, houseEntries(type).toArray(Entry[]::new)));
	}

	private static List<Entry> houseEntries(VillageType type) {
		Entry barley = e(VBItems.BARLEY_SEEDS, 4, 1, 3);
		Entry grapes = e(VBItems.RED_GRAPES, 3, 1, 3);
		Entry hops = e(VBItems.HOPS, 3, 1, 3);
		Entry whiteGrapes = e(VBItems.WHITE_GRAPES, 2, 1, 3);
		Entry ale = e(DrinkItems.BARLEY_ALE, 1, 1, 1);
		Entry wheatBeer = e(DrinkItems.WHEAT_BEER, 1, 1, 1);
		Entry redWine = e(DrinkItems.RED_WINE, 1, 1, 1);
		Entry berryWine = e(DrinkItems.SWEET_BERRY_WINE, 1, 1, 1);
		Entry whiteWine = e(DrinkItems.WHITE_WINE, 1, 1, 1);
		Entry mead = e(DrinkItems.MEAD, 1, 1, 1);
		return switch (type) {
			case PLAINS -> List.of(barley, grapes, hops, whiteGrapes, ale, wheatBeer, whiteWine);
			case DESERT -> List.of(grapes, barley, redWine);
			case SAVANNA -> List.of(barley, grapes, redWine, ale);
			case SNOWY -> List.of(barley, hops, ale, wheatBeer, mead);
			case TAIGA -> List.of(barley, hops, whiteGrapes, ale, berryWine, mead);
		};
	}

	/**
	 * The drinks of a tavern's region (DESIGN 8): its young drinks at weight 3, its aged one at {@code agedWeight}.
	 */
	private static List<Entry> tavernDrinks(VillageType type, int agedWeight) {
		Entry ale = e(DrinkItems.BARLEY_ALE, 3, 1, 2);
		Entry wheatBeer = e(DrinkItems.WHEAT_BEER, 3, 1, 2);
		Entry redWine = e(DrinkItems.RED_WINE, 3, 1, 1);
		Entry berryWine = e(DrinkItems.SWEET_BERRY_WINE, 3, 1, 1);
		return switch (type) {
			case PLAINS -> List.of(ale, wheatBeer, redWine, e(DrinkItems.SWEET_BERRY_WINE, 1, 1, 1), e(DrinkItems.AGED_BARLEY_ALE, agedWeight, 1, 1));
			case DESERT -> List.of(redWine, wheatBeer, ale, e(DrinkItems.AGED_RED_WINE, agedWeight, 1, 1));
			case SAVANNA -> List.of(redWine, ale, berryWine, e(DrinkItems.AGED_RED_WINE, agedWeight, 1, 1));
			case SNOWY -> List.of(ale, wheatBeer, berryWine, e(DrinkItems.AGED_BARLEY_ALE, agedWeight, 1, 1));
			case TAIGA -> List.of(berryWine, ale, wheatBeer, e(DrinkItems.AGED_SWEET_BERRY_WINE, agedWeight, 1, 1));
		};
	}

	/** Seeds and harvest to start brewing with. */
	private static Entry[] tavernCrops() {
		return new Entry[] {e(VBItems.BARLEY_SEEDS, 3, 2, 6), e(VBItems.RED_GRAPES, 2, 2, 4), e(VBItems.WHITE_GRAPES, 1, 2, 4), e(VBItems.HOPS, 2, 2, 4),
			e(VBItems.BARLEY, 2, 2, 5)};
	}

	/**
	 * The chest by the bar (DESIGN 8, 1.2): what an ordinary house chest of the village holds (vanilla's own table, one
	 * draw), then a roll of seeds, mugs and glasses, and a drink of the region. Until 1.1 it held two or three rolls of
	 * seeds and one to three drinks; what the vanilla draw adds, the seeds give up, and the rest of the drinks went
	 * into the cellar's chest ({@link #tavernCellarLoot}).
	 */
	static LootTable.Builder tavernLoot(VillageType type) {
		return LootTable.lootTable()
			.withPool(LootPool.lootPool().setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1))
				.add(net.minecraft.world.level.storage.loot.entries.LootTableReference.lootTableReference(type.vanillaHouseLoot())))
			.withPool(pool(1, 1, tavernCrops()))
			.withPool(pool(1, 2, e(DrinkItems.MUG, 3, 1, 3), e(DrinkItems.WINE_GLASS, 2, 1, 2)))
			.withPool(pool(1, 1, tavernDrinks(type, 1).toArray(Entry[]::new)));
	}

	/** The chest hidden in the cellar (1.2): a roll or two of seeds and harvest, and a drink, more often an aged one. */
	static LootTable.Builder tavernCellarLoot(VillageType type) {
		return LootTable.lootTable()
			.withPool(pool(1, 2, tavernCrops()))
			.withPool(pool(1, 1, tavernDrinks(type, 4).toArray(Entry[]::new)));
	}

	public static class ChestLoot implements LootTableSubProvider
	{
		@Override
		public void generate(BiConsumer<ResourceLocation, LootTable.Builder> output) {
			for (VillageType type : VillageType.values()) {
				output.accept(type.houseLoot(), houseLoot(type));
			}
			for (VillageType type : VillageStructures.TAVERN_TYPES) {
				output.accept(type.tavernLoot(), tavernLoot(type));
				output.accept(type.tavernCellarLoot(), tavernCellarLoot(type));
			}
		}
	}

	/** For tests: every item our village house chests of this type can gain. */
	public static List<Item> houseLootItems(VillageType type) {
		return houseEntries(type).stream().map(entry -> entry.item().get().asItem()).toList();
	}
}
