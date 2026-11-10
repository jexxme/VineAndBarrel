package jexxme.vineandbarrel.world.village;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
		Services.PLATFORM.addVillagerTrades(VillagerProfession.FARMER, 1, List.copyOf(FARMER_LEVEL_1));
		Services.PLATFORM.addVillagerTrades(VillagerProfession.FARMER, 2, List.copyOf(FARMER_LEVEL_2));
		Services.PLATFORM.addWanderingTraderTrades(List.copyOf(WANDERING_TRADER_COMMON));
	}

	private static Trade buys(String path, Supplier<? extends ItemLike> item, int count, int maxUses, int xp) {
		return new Trade(VineAndBarrel.id(path), true, item, count, maxUses, xp, 0.05F);
	}

	private static Trade sells(String path, Supplier<? extends ItemLike> item, int count, int maxUses, int xp) {
		return new Trade(VineAndBarrel.id(path), false, item, count, maxUses, xp, 0.05F);
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

	/** The tavern chest (DESIGN 8): seeds, mugs and glasses, and a few drinks of the region, ready to start brewing. */
	static LootTable.Builder tavernLoot(VillageType type) {
		Entry ale = e(DrinkItems.BARLEY_ALE, 3, 1, 2);
		Entry wheatBeer = e(DrinkItems.WHEAT_BEER, 3, 1, 2);
		Entry redWine = e(DrinkItems.RED_WINE, 3, 1, 1);
		Entry berryWine = e(DrinkItems.SWEET_BERRY_WINE, 3, 1, 1);
		List<Entry> drinks = switch (type) {
			case PLAINS -> List.of(ale, wheatBeer, redWine, e(DrinkItems.SWEET_BERRY_WINE, 1, 1, 1), e(DrinkItems.AGED_BARLEY_ALE, 1, 1, 1));
			case DESERT -> List.of(redWine, wheatBeer, ale, e(DrinkItems.AGED_RED_WINE, 1, 1, 1));
			case SAVANNA -> List.of(redWine, ale, berryWine, e(DrinkItems.AGED_RED_WINE, 1, 1, 1));
			case SNOWY -> List.of(ale, wheatBeer, berryWine, e(DrinkItems.AGED_BARLEY_ALE, 1, 1, 1));
			case TAIGA -> List.of(berryWine, ale, wheatBeer, e(DrinkItems.AGED_SWEET_BERRY_WINE, 1, 1, 1));
		};
		return LootTable.lootTable()
			.withPool(pool(2, 3, e(VBItems.BARLEY_SEEDS, 3, 2, 6), e(VBItems.RED_GRAPES, 2, 2, 4), e(VBItems.WHITE_GRAPES, 1, 2, 4), e(VBItems.HOPS, 2, 2, 4),
				e(VBItems.BARLEY, 2, 2, 5)))
			.withPool(pool(1, 2, e(DrinkItems.MUG, 3, 1, 3), e(DrinkItems.WINE_GLASS, 2, 1, 2)))
			.withPool(pool(1, 3, drinks.toArray(Entry[]::new)));
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
			}
		}
	}

	/** For tests: every item our village house chests of this type can gain. */
	public static List<Item> houseLootItems(VillageType type) {
		return houseEntries(type).stream().map(entry -> entry.item().get().asItem()).toList();
	}
}
