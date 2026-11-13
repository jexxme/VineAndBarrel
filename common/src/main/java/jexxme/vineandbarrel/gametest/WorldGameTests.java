package jexxme.vineandbarrel.gametest;

import com.mojang.serialization.JsonOps;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.world.village.FarmCropProcessor;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.barrel.BarrelStage;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.drink.PlacedDrink;
import jexxme.vineandbarrel.drink.PlacedDrinksBlock;
import jexxme.vineandbarrel.drink.PlacedDrinksBlockEntity;
import jexxme.vineandbarrel.kettle.BrewKettleBlock;
import jexxme.vineandbarrel.kettle.KettleSupport;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import jexxme.vineandbarrel.world.village.VillageData;
import jexxme.vineandbarrel.world.village.VillageStructures;
import jexxme.vineandbarrel.world.village.VillageType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import java.util.function.Predicate;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * M6 World GameTests: village farms grow our crops, farmers and the wandering trader sell our seeds, village chests
 * hold our loot, and each village type's tavern sits in its house pool, places like a village piece and its barrels
 * (W1) can really be tapped.
 */
public final class WorldGameTests
{
	private static final Identifier LARGE = VineAndBarrel.id("gametest/empty_32x6x32");
	private static final Identifier TALL = VineAndBarrel.id("gametest/empty_20x22x20");
	private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, VineAndBarrel.MODID);
	private static final List<Entry> TESTS = new ArrayList<>();

	/** Where the tavern goes in its test area (room for the street side in front of it). */
	private static final BlockPos TAVERN_AT = new BlockPos(2, VillageStructures.CELLAR_HEIGHT, 2);
	/**
	 * The floor's layer in every tavern template, its lowest: a village places a house inside its street's bounding box,
	 * which never reaches below the street. The street joint is one above. The cellar is a piece of its own under the
	 * floor (see {@link VillageStructures.TavernLayout#cellarId}).
	 */
	private static final int TAVERN_FLOOR = 0;

	private record Entry(ResourceKey<Consumer<GameTestHelper>> function, Identifier structure, int maxTicks, boolean skyAccess) {
	}

	static {
		// under the open sky, so canSurvive judges the soil and not the light
		register("world_farm_processors_grow_our_crops", LARGE, 100, true, WorldGameTests::farmProcessors);
		register("world_zombie_village_farms_grow_our_crops", LARGE, 100, true, WorldGameTests::zombieFarmProcessors);
		register("world_trades_sell_our_seeds", LARGE, 100, WorldGameTests::trades);
		register("world_village_chests_gain_our_loot", LARGE, 100, WorldGameTests::houseLoot);
		register("world_tavern_chest_loot", LARGE, 100, WorldGameTests::tavernLoot);
		register("world_taverns_in_village_pools", LARGE, 100, WorldGameTests::tavernsInPools);
		register("world_taverns_one_village_in_three", LARGE, 200, WorldGameTests::taverns_oneVillageInThree);
		register("world_tavern_cellar_casks_are_the_taverns", TALL, 100, WorldGameTests::cellarCasksAreTheTaverns);
		register("world_tavern_keeper_restocks_his_own_casks", TALL, 100, WorldGameTests::keeperRestocks);
		register("world_village_apply_is_idempotent", LARGE, 100, WorldGameTests::applyIsIdempotent);
		register("world_legacy_taverns_resolve_and_count", TALL, 100, WorldGameTests::legacyTavernsResolveAndCount);
		for (VillageStructures.TavernLayout layout : VillageStructures.TAVERNS) {
			String n = "world_tavern_" + layout.name();
			register(n + "_places_and_barrels_tap", TALL, 200, helper -> tavernPlacesAndTaps(helper, layout));
			register(n + "_clears_solid_terrain", TALL, 100, helper -> tavernClearsTerrain(helper, layout));
			register(n + "_villagers_find_their_way", TALL, 100, helper -> villagersFindTheirWay(helper, layout));
			register(n + "_is_lit", TALL, 100, helper -> tavernIsLit(helper, layout));
			register(n + "_has_room_to_walk", TALL, 100, helper -> tavernHasRoomToWalk(helper, layout));
			register(n + "_is_built_whole", TALL, 100, helper -> tavernIsBuiltWhole(helper, layout));
			register(n + "_closes_its_stairwell_without_room_for_a_cellar", TALL, 100, helper -> tavernWithoutCellar(helper, layout));
			register(n + "_takes_a_village_plot", LARGE, 100, helper -> tavernTakesAVillagePlot(helper, layout));
			register(n + "_holds_as_much_drink_as_before", LARGE, 100, helper -> tavernServings(helper, layout));
		}
	}

	private WorldGameTests() {
	}

	/**
	 * The test-relative position of an absolute one: the inverse of {@link GameTestHelper#absolutePos}. Vanilla's own
	 * {@code relativePos} turns by the wrong rotation (180 degrees too many; NeoForge patches it, Fabric doesn't), so
	 * our tests use this on both loaders.
	 */
	static BlockPos relativePos(GameTestHelper helper, BlockPos absolute) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		Rotation rotation = helper.getTestRotation();
		// Turning back: the same rotation three more times makes a full turn
		Rotation inverse = rotation.getRotated(rotation).getRotated(rotation);
		return StructureTemplate.transform(absolute, net.minecraft.world.level.block.Mirror.NONE, inverse, origin).subtract(origin);
	}

	private static void register(String name, Identifier structure, int timeout, Consumer<GameTestHelper> function) {
		register(name, structure, timeout, false, function);
	}

	private static void register(String name, Identifier structure, int timeout, boolean skyAccess, Consumer<GameTestHelper> function) {
		FUNCTIONS.register(name, () -> function);
		TESTS.add(new Entry(ResourceKey.create(Registries.TEST_FUNCTION, VineAndBarrel.id(name)), structure, timeout, skyAccess));
	}

	public static void register() {
		FUNCTIONS.register();
		jexxme.vineandbarrel.platform.Services.PLATFORM.addGameTests(WorldGameTests::registerTests);
	}

	private static void registerTests(GameTestRegistrar event) {
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(VineAndBarrel.id("world"));
		for (Entry test : TESTS) {
			event.registerTest(test.function().identifier(), new FunctionGameTestInstance(test.function(), new TestData<>(environment,
				Level.OVERWORLD, test.structure(), test.maxTicks(), 0, true, Rotation.NONE, false, 1, 1, test.skyAccess(), 0)));
		}
	}

	// ------------------------------------------------------------------ 1. village farms

	/**
	 * Each village type's farm processor list carries our processor, once and first. Vanilla's large plains farm placed
	 * with that list, 16 times at different spots, grows exactly what our rule picks for each of its wheat blocks and
	 * nothing of ours anywhere else; with Farmer's Delight installed its farm rules stay in the list after ours. Checks
	 * the lists as the server start left them: nothing here adds the processor itself.
	 * <p>
	 * Nothing here counts on a draw: our rule's pick is fixed by a block's position, so every wheat block is checked
	 * against it one by one, wherever the GameTest framework happens to put this test; the shares are checked on a fixed
	 * stretch of positions that is the same in every run ({@link #farmShares}).
	 */
	private static void farmProcessors(GameTestHelper helper) {
		farms(helper, VillageType::farmProcessors, true);
	}

	/**
	 * The same for abandoned villages: their farms are placed with the type's zombie processor list, which carries
	 * vanilla's farm rules too, so it gets our processor, first, and grows the same crops in the same shares. Farmer's
	 * Delight adds its crops only to living villages' farms, so its rules aren't expected here.
	 */
	private static void zombieFarmProcessors(GameTestHelper helper) {
		farms(helper, VillageType::zombieProcessors, false);
	}

	private static void farms(GameTestHelper helper, java.util.function.Function<VillageType, Identifier> listOf, boolean fdToo) {
		ServerLevel level = helper.getLevel();
		Registry<StructureProcessorList> lists = level.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST);
		StructureTemplate farm = level.getStructureTemplateManager().get(Identifier.withDefaultNamespace("village/plains/houses/plains_large_farm_1"))
			.orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(1, 0, 1));
		boolean fd = jexxme.vineandbarrel.platform.Services.PLATFORM.isModLoaded("farmersdelight");
		Set<Block> ours = Set.of(VBBlocks.BARLEY.get(), VBBlocks.GRAPE_BUSH.get(), VBBlocks.HOP_BUSH.get(), VBBlocks.WHITE_GRAPE_BUSH.get());
		for (VillageType type : VillageType.values()) {
			Identifier listId = listOf.apply(type);
			StructureProcessorList list = lists.getValue(listId);
			helper.assertTrue(list != null, "processor list " + listId);
			long count = list.list().stream().filter(VillageStructures::isOurFarmProcessor).count();
			helper.assertValueEqual((int) count, 1, "our farm processors in " + listId);
			helper.assertTrue(VillageStructures.isOurFarmProcessor(list.list().getFirst()), "ours runs first in " + listId);
			// Farmer's Delight's farm rules stay in a living village's list next to ours (its crops, in its codec)
			if (fd && fdToo) {
				helper.assertTrue(list.list().stream().anyMatch(processor -> StructureProcessorType.SINGLE_CODEC.encodeStart(JsonOps.INSTANCE, processor)
					.result().map(json -> json.toString().contains("\"farmersdelight:")).orElse(false)), "Farmer's Delight's farm rules are in " + listId);
			}

			// Our rule, built on its own from the crop table: the test's reference for every wheat block
			FarmCropProcessor rule = (FarmCropProcessor) VillageStructures.farmCropProcessor(VillageStructures.FARM_CROPS.get(type));
			int checked = 0;
			for (int i = 0; i < 16; i++) {
				BlockPos at = origin.offset((i % 4) * 4, 0, (i / 4) * 5);
				StructurePlaceSettings settings = new StructurePlaceSettings().setKnownShape(true); // as a village places its pieces
				list.list().forEach(settings::addProcessor);
				farm.placeInWorld(level, at, at, settings, RandomSource.create(1L), Block.UPDATE_CLIENTS);
				// the template's wheat, where it landed: our crop exactly where our rule picks one, nothing of ours elsewhere
				for (StructureTemplate.StructureBlockInfo wheat : farm.filterBlocks(at, new StructurePlaceSettings(), Blocks.WHEAT)) {
					BlockState placed = level.getBlockState(wheat.pos());
					BlockState pick = rule.pick(wheat.pos());
					if (pick != null) {
						helper.assertTrue(placed.is(pick.getBlock()), "our rule picks " + pick.getBlock() + " at " + wheat.pos() + " in a " + listId + " farm, it grew " + placed);
						helper.assertTrue(placed.canSurvive(level, wheat.pos()), placed + " can't live on farmland in a " + listId + " farm");
					} else {
						helper.assertFalse(ours.contains(placed.getBlock()), "nothing of ours where our rule picks nothing: " + placed + " at " + wheat.pos()
							+ " in a " + listId + " farm");
					}
					checked++;
				}
			}
			helper.assertTrue(checked > 0, "the farm has wheat to check");
			farmShares(helper, type, rule);
		}
		helper.succeed();
	}

	/**
	 * Our rule's shares on a fixed stretch of 20,000 positions, the same in every run: each crop gets its chance of the
	 * wheat the earlier rules left, all of them together no more than their share, so the farm's own crops keep the rest.
	 */
	private static void farmShares(GameTestHelper helper, VillageType type, FarmCropProcessor rule) {
		Map<Block, Integer> found = new HashMap<>();
		int spots = 0;
		for (int x = 0; x < 200; x++) {
			for (int z = 0; z < 100; z++) {
				BlockState pick = rule.pick(new BlockPos(x, 64, z));
				if (pick != null) {
					found.merge(pick.getBlock(), 1, Integer::sum);
				}
				spots++;
			}
		}
		float left = 1.0F;
		for (VillageStructures.FarmCrop crop : VillageStructures.FARM_CROPS.get(type)) {
			double mean = spots * left * crop.chance();
			int got = found.getOrDefault(crop.crop().get(), 0);
			helper.assertTrue(got >= mean * 0.85 && got <= mean * 1.15, crop.crop().get() + " in " + type.id() + " farms: " + got + " of " + spots
				+ ", expected about " + Math.round(mean));
			left *= 1.0F - crop.chance();
		}
		int ourTotal = found.values().stream().mapToInt(Integer::intValue).sum();
		helper.assertTrue(ourTotal <= spots * (1.0F - left) * 1.1F, "our crops stay within their share of " + type.id() + " farms: " + ourTotal + " of " + spots);
	}

	// ------------------------------------------------------------------ 2. trades

	/** The trades are in the trade sets villagers draw from, and each gives what it says for one emerald. */
	private static void trades(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Registry<TradeSet> sets = level.registryAccess().lookupOrThrow(Registries.TRADE_SET);
		assertInSet(helper, sets, TradeSets.FARMER_LEVEL_1, VillageData.FARMER_1_BARLEY_SEEDS);
		assertInSet(helper, sets, TradeSets.FARMER_LEVEL_2, VillageData.FARMER_2_RED_GRAPES);
		assertInSet(helper, sets, TradeSets.FARMER_LEVEL_2, VillageData.FARMER_2_HOPS);
		assertInSet(helper, sets, TradeSets.FARMER_LEVEL_2, VillageData.FARMER_2_WHITE_GRAPES);
		assertInSet(helper, sets, TradeSets.WANDERING_TRADER_COMMON, VillageData.WANDERING_TRADER_RED_GRAPES);
		assertInSet(helper, sets, TradeSets.WANDERING_TRADER_COMMON, VillageData.WANDERING_TRADER_WHITE_GRAPES);
		assertInSet(helper, sets, TradeSets.WANDERING_TRADER_COMMON, VillageData.WANDERING_TRADER_HOPS);
		assertInSet(helper, sets, TradeSets.WANDERING_TRADER_COMMON, VillageData.WANDERING_TRADER_BARLEY_SEEDS);
		assertInSet(helper, sets, TradeSets.FARMER_LEVEL_1, VillageData.FARMER_1_BARLEY_EMERALD);
		assertInSet(helper, sets, TradeSets.FARMER_LEVEL_2, VillageData.FARMER_2_RED_GRAPES_EMERALD);
		Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
		LootContext context = new LootContext.Builder(new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, villager.position())
			.withParameter(LootContextParams.THIS_ENTITY, villager)
			.withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
			.create(LootContextParamSets.VILLAGER_TRADE)).create(Optional.empty());
		// Every vanilla trade a farmer is sure to offer stays sure: a level that draws as many trades as vanilla gives it
		// (or more) gets nothing of ours, and a level that already draws at random gets at most one of our buying trades
		for (ResourceKey<TradeSet> farmerLevel : List.of(TradeSets.FARMER_LEVEL_1, TradeSets.FARMER_LEVEL_2, TradeSets.FARMER_LEVEL_3, TradeSets.FARMER_LEVEL_4,
				TradeSets.FARMER_LEVEL_5)) {
			TradeSet set = sets.getValue(farmerLevel);
			long vanilla = set.trades().stream().filter(h -> h.unwrapKey().map(k -> k.identifier().getNamespace().equals("minecraft")).orElse(false)).count();
			long ours = set.trades().stream().filter(h -> h.unwrapKey().map(k -> k.identifier().getNamespace().equals(VineAndBarrel.MODID)).orElse(false)).count();
			long buys = set.trades().stream().filter(h -> h.unwrapKey().map(k -> k.identifier().getNamespace().equals(VineAndBarrel.MODID)
				&& k.identifier().getPath().endsWith("_emerald")).orElse(false)).count();
			int drawn = set.calculateNumberOfTrades(context);
			if (vanilla <= drawn) {
				helper.assertValueEqual((int) ours, 0, farmerLevel.identifier() + " offers all " + vanilla + " of its vanilla trades, so none of ours");
			}
			helper.assertTrue(buys <= 1, farmerLevel.identifier() + " has " + buys + " of our buying trades");
		}
		assertOffer(helper, context, VillageData.FARMER_1_BARLEY_SEEDS, VBItems.BARLEY_SEEDS.get(), 6, 16);
		assertOffer(helper, context, VillageData.FARMER_2_RED_GRAPES, VBItems.RED_GRAPES.get(), 3, 16);
		assertOffer(helper, context, VillageData.FARMER_2_HOPS, VBItems.HOPS.get(), 3, 16);
		assertOffer(helper, context, VillageData.WANDERING_TRADER_RED_GRAPES, VBItems.RED_GRAPES.get(), 1, 12);
		assertOffer(helper, context, VillageData.WANDERING_TRADER_HOPS, VBItems.HOPS.get(), 1, 12);
		assertOffer(helper, context, VillageData.WANDERING_TRADER_BARLEY_SEEDS, VBItems.BARLEY_SEEDS.get(), 1, 12);
		assertOffer(helper, context, VillageData.FARMER_2_WHITE_GRAPES, VBItems.WHITE_GRAPES.get(), 3, 16);
		assertOffer(helper, context, VillageData.WANDERING_TRADER_WHITE_GRAPES, VBItems.WHITE_GRAPES.get(), 1, 12);
		// Farmers buy the harvest like vanilla's wheat and FD's crops: 20 for an emerald
		assertBuys(helper, context, VillageData.FARMER_1_BARLEY_EMERALD, VBItems.BARLEY.get(), 20, 16);
		assertBuys(helper, context, VillageData.FARMER_2_RED_GRAPES_EMERALD, VBItems.RED_GRAPES.get(), 20, 16);
		long ours = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).listElementIds()
			.filter(key -> key.identifier().getNamespace().equals(VineAndBarrel.MODID) && !key.identifier().getPath().startsWith("tavern_keeper/")).count();
		helper.assertValueEqual((int) ours, 10, "our villager trades for farmers and the wandering trader");
		helper.succeed();
	}

	/** A trade where the villager takes {@code count} of {@code wants} for one emerald. */
	private static void assertBuys(GameTestHelper helper, LootContext context, ResourceKey<VillagerTrade> key, Item wants, int count, int maxUses) {
		VillagerTrade trade = helper.getLevel().registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).getValue(key);
		helper.assertTrue(trade != null, "trade " + key.identifier());
		MerchantOffer offer = trade.getOffer(context);
		helper.assertTrue(offer != null, "an offer from " + key.identifier());
		helper.assertTrue(offer.getCostA().is(wants) && offer.getCostA().getCount() == count && offer.getCostB().isEmpty(),
			key.identifier() + " wants " + offer.getCostA() + " + " + offer.getCostB());
		helper.assertTrue(offer.getResult().is(Items.EMERALD) && offer.getResult().getCount() == 1, key.identifier() + " gives " + offer.getResult());
		helper.assertValueEqual(offer.getMaxUses(), maxUses, key.identifier() + " uses");
	}

	private static void assertInSet(GameTestHelper helper, Registry<TradeSet> sets, ResourceKey<TradeSet> set, ResourceKey<VillagerTrade> trade) {
		TradeSet tradeSet = sets.getValue(set);
		helper.assertTrue(tradeSet != null && tradeSet.trades().stream().anyMatch(h -> h.is(trade)), trade.identifier() + " is not in " + set.identifier());
	}

	private static void assertOffer(GameTestHelper helper, LootContext context, ResourceKey<VillagerTrade> key, Item gives, int count, int maxUses) {
		VillagerTrade trade = helper.getLevel().registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).getValue(key);
		helper.assertTrue(trade != null, "trade " + key.identifier());
		MerchantOffer offer = trade.getOffer(context);
		helper.assertTrue(offer != null, "an offer from " + key.identifier());
		helper.assertTrue(offer.getResult().is(gives) && offer.getResult().getCount() == count, key.identifier() + " gives " + offer.getResult());
		helper.assertTrue(offer.getCostA().is(Items.EMERALD) && offer.getCostA().getCount() == 1 && offer.getCostB().isEmpty(),
			key.identifier() + " costs " + offer.getCostA() + " + " + offer.getCostB());
		helper.assertValueEqual(offer.getMaxUses(), maxUses, key.identifier() + " uses");
	}

	// ------------------------------------------------------------------ 3. chest loot

	/** Every vanilla village house chest now always holds some of our loot, and over many chests all of it turns up. */
	private static void houseLoot(GameTestHelper helper) {
		for (VillageType type : VillageType.values()) {
			LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(type.vanillaHouseLoot());
			Set<Item> ours = Set.copyOf(VillageData.houseLootItems(type));
			Set<Item> seen = new HashSet<>();
			for (int i = 0; i < 300; i++) {
				List<ItemStack> loot = table.getRandomItems(chestParams(helper), RandomSource.create(i * 7919L + type.ordinal()));
				List<Item> ourLoot = loot.stream().map(ItemStack::getItem).filter(ours::contains).toList();
				helper.assertTrue(!ourLoot.isEmpty(), type.id() + " house chest without our loot: " + loot);
				seen.addAll(ourLoot);
			}
			helper.assertValueEqual(seen, ours, "everything our " + type.id() + " house loot can add");
		}
		helper.succeed();
	}

	/** The tavern chest: seeds, mugs or glasses and a few drinks, every time. */
	private static void tavernLoot(GameTestHelper helper) {
		for (VillageType type : VillageStructures.TAVERN_TYPES) {
			LootTable bar = helper.getLevel().getServer().reloadableRegistries().getLootTable(type.tavernLoot());
			tavernLoot(helper, bar);
			// the bar's chest holds what a house chest of the village holds too (1.2: "why only VB loot?")
			boolean vanilla = false;
			for (int i = 0; i < 100 && !vanilla; i++) {
				vanilla = bar.getRandomItems(chestParams(helper), RandomSource.create(i)).stream()
					.anyMatch(s -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("minecraft"));
			}
			helper.assertTrue(vanilla, type.id() + " tavern chest never holds anything of vanilla's village loot");
			// the cellar's chest: always seeds or harvest and a drink, now and then an aged one
			LootTable cellar = helper.getLevel().getServer().reloadableRegistries().getLootTable(type.tavernCellarLoot());
			Set<Item> crops = Set.of(VBItems.BARLEY_SEEDS.get(), VBItems.RED_GRAPES.get(), VBItems.WHITE_GRAPES.get(), VBItems.HOPS.get(), VBItems.BARLEY.get());
			boolean aged = false;
			for (int i = 0; i < 100; i++) {
				List<ItemStack> loot = cellar.getRandomItems(chestParams(helper), RandomSource.create(i));
				helper.assertTrue(loot.stream().anyMatch(s -> crops.contains(s.getItem())), type.id() + " cellar chest without seeds: " + loot);
				helper.assertTrue(loot.stream().anyMatch(s -> s.getItem() instanceof jexxme.vineandbarrel.drink.DrinkItem), type.id() + " cellar chest without a drink: " + loot);
				aged |= loot.stream().anyMatch(s -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().startsWith("aged_"));
			}
			helper.assertTrue(aged, type.id() + " cellar chest never holds an aged drink");
		}
		helper.succeed();
	}

	private static void tavernLoot(GameTestHelper helper, LootTable table) {
		Set<Item> seeds = Set.of(VBItems.BARLEY_SEEDS.get(), VBItems.RED_GRAPES.get(), VBItems.WHITE_GRAPES.get(), VBItems.HOPS.get(), VBItems.BARLEY.get());
		Set<Item> vessels = Set.of(DrinkItems.MUG.get(), DrinkItems.WINE_GLASS.get());
		for (int i = 0; i < 100; i++) {
			List<ItemStack> loot = table.getRandomItems(chestParams(helper), RandomSource.create(i));
			helper.assertTrue(loot.stream().anyMatch(s -> seeds.contains(s.getItem())), "tavern chest without seeds: " + loot);
			helper.assertTrue(loot.stream().anyMatch(s -> vessels.contains(s.getItem())), "tavern chest without a mug or glass: " + loot);
			helper.assertTrue(loot.stream().anyMatch(s -> s.getItem() instanceof jexxme.vineandbarrel.drink.DrinkItem), "tavern chest without a drink: " + loot);
		}
	}

	private static LootParams chestParams(GameTestHelper helper) {
		return new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
			.create(LootContextParamSets.CHEST);
	}

	// ------------------------------------------------------------------ 4. the taverns

	private static SinglePoolElement tavernElement(GameTestHelper helper, VillageStructures.TavernLayout layout) {
		StructureTemplatePool pool = helper.getLevel().registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getValue(layout.type().housesPool());
		helper.assertTrue(pool != null, "pool " + layout.type().housesPool());
		for (var entry : pool.rawTemplates) {
			if (VillageStructures.isTemplate(entry.getFirst(), layout.id())) {
				return (SinglePoolElement) entry.getFirst();
			}
		}
		throw helper.assertionException(layout.name() + " is not in " + layout.type().housesPool());
	}

	/** Applying again (a second server start in the same JVM) changes no pool and no farm processor list, a zombie village's included. */
	private static void applyIsIdempotent(GameTestHelper helper) {
		var access = helper.getLevel().registryAccess();
		Map<VillageType, List<Object>> before = new HashMap<>();
		for (VillageType type : VillageType.values()) {
			before.put(type, List.of(List.copyOf(access.lookupOrThrow(Registries.TEMPLATE_POOL).getValue(type.housesPool()).rawTemplates),
				access.lookupOrThrow(Registries.TEMPLATE_POOL).getValue(type.housesPool()).templates.size(),
				List.copyOf(access.lookupOrThrow(Registries.PROCESSOR_LIST).getValue(type.farmProcessors()).list()),
				List.copyOf(access.lookupOrThrow(Registries.PROCESSOR_LIST).getValue(type.zombieProcessors()).list())));
		}
		VillageStructures.apply(access);
		for (VillageType type : VillageType.values()) {
			List<Object> after = List.of(List.copyOf(access.lookupOrThrow(Registries.TEMPLATE_POOL).getValue(type.housesPool()).rawTemplates),
				access.lookupOrThrow(Registries.TEMPLATE_POOL).getValue(type.housesPool()).templates.size(),
				List.copyOf(access.lookupOrThrow(Registries.PROCESSOR_LIST).getValue(type.farmProcessors()).list()),
				List.copyOf(access.lookupOrThrow(Registries.PROCESSOR_LIST).getValue(type.zombieProcessors()).list()));
			helper.assertValueEqual(after, before.get(type), type.id() + " pool and farm processors after applying again");
		}
		helper.succeed();
	}

	/**
	 * Each tavern layout is in its village's house pool once, with its weight, rigid like the vanilla houses, dressed by
	 * our processor after the pool's own; its template loads and fits a village plot (at most 15 by 15), its floor five
	 * up from its bottom. Every village type has two or more layouts. Checks the pools as the server start left them:
	 * nothing here adds the taverns itself.
	 */
	private static void tavernsInPools(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (VillageType type : VillageType.values()) {
			helper.assertTrue(type.taverns().size() >= 2, "two or more tavern layouts for " + type.id());
		}
		for (VillageStructures.TavernLayout layout : VillageStructures.TAVERNS) {
			VillageType type = layout.type();
			StructureTemplatePool pool = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getValue(type.housesPool());
			helper.assertTrue(pool != null, "pool " + type.housesPool());
			long entries = pool.rawTemplates.stream().filter(e -> VillageStructures.isTemplate(e.getFirst(), layout.id())).count();
			helper.assertValueEqual((int) entries, 1, layout.name() + " entries in " + type.housesPool());
			int weight = pool.rawTemplates.stream().filter(e -> VillageStructures.isTemplate(e.getFirst(), layout.id())).mapToInt(e -> e.getSecond()).sum();
			helper.assertValueEqual(weight, layout.weight(), layout.name() + " weight");
			long picks = pool.templates.stream().filter(e -> VillageStructures.isTemplate(e, layout.id())).count();
			helper.assertValueEqual((int) picks, layout.weight(), layout.name() + " picks in the pool's draw list");
			SinglePoolElement element = tavernElement(helper, layout);
			helper.assertTrue(element.getProjection() == StructureTemplatePool.Projection.RIGID, "rigid like the vanilla houses");
			String encoded = net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement.CODEC.encodeStart(
				net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, level.registryAccess()), element).getOrThrow().toString();
			helper.assertTrue(encoded.contains(VineAndBarrel.id("tavern_dressing").toString()), layout.name() + " is dressed: " + encoded);
			StructureTemplate template = level.getStructureTemplateManager().get(layout.id()).orElse(null);
			helper.assertTrue(template != null, layout.name() + " template loads");
			helper.assertTrue(template.getSize().getX() <= 15 && template.getSize().getZ() <= 15, layout.name() + " fits a village plot: " + template.getSize());
			helper.assertValueEqual(entranceOf(template).getY(), TAVERN_FLOOR + 1, layout.name() + " street joint one above the floor");
			// the cellar: its own template, which the dressing lays under the floor
			helper.assertTrue(encoded.contains(layout.cellarId().toString()), layout.name() + " lays its cellar: " + encoded);
			StructureTemplate cellar = level.getStructureTemplateManager().get(layout.cellarId()).orElse(null);
			helper.assertTrue(cellar != null && cellar.getSize().getY() == VillageStructures.CELLAR_HEIGHT, layout.name() + " cellar template loads, " + VillageStructures.CELLAR_HEIGHT + " high");
			helper.assertTrue(cellar.getSize().getX() == template.getSize().getX() && cellar.getSize().getZ() == template.getSize().getZ(),
				layout.name() + " cellar " + cellar.getSize() + " under the whole house " + template.getSize());
		}
		helper.succeed();
	}

	/**
	 * About one village in three gets a tavern, in every village type: villages laid out the way the game lays them out
	 * (as {@link #tavernTakesAVillagePlot}, on deep flat ground, at fixed places and seeds, so the same villages every
	 * run) with the pools as they ship. Taverns fit flat ground more often than real terrain, where the weights aim for one
	 * village in three, so between 25% and 65% of these have a tavern, and every layout turns up. Retune
	 * {@link VillageStructures#TAVERNS} against real worlds when this fails. No village has two taverns, however many of
	 * its plots one would fit (see {@link VillageStructures#oneTavernPerVillage}).
	 */
	private static void taverns_oneVillageInThree(GameTestHelper helper) {
		int villages = 120;
		StringBuilder report = new StringBuilder();
		for (VillageType type : VillageType.values()) {
			int with = 0;
			Map<Identifier, Integer> byLayout = new HashMap<>();
			for (int v = 0; v < villages; v++) {
				var stub = layOutVillage(helper, type, new BlockPos(4096 + v * 304, 1, 4096), 7919L * v + type.ordinal());
				int taverns = 0;
				if (stub.isPresent()) {
					for (var piece : stub.get().getPiecesBuilder().build().pieces()) {
						if (piece instanceof PoolElementStructurePiece poolPiece && VillageStructures.isTavern(poolPiece.getElement(), type)) {
							taverns++;
							byLayout.merge(((SinglePoolElement) poolPiece.getElement()).getTemplateLocation(), 1, Integer::sum);
						}
					}
				}
				helper.assertTrue(taverns <= 1, "a " + type.id() + " village with " + taverns + " taverns (village " + v + ")");
				with += taverns > 0 ? 1 : 0;
			}
			double share = with / (double) villages;
			report.append(' ').append(type.id()).append(' ').append(Math.round(share * 100)).append("% ").append(byLayout.values());
			helper.assertTrue(share >= 0.25 && share <= 0.65, type.id() + " villages with a tavern: " + Math.round(share * 100) + "% of " + villages);
			for (Identifier id : type.taverns()) {
				helper.assertTrue(byLayout.getOrDefault(id, 0) >= 3, id.getPath() + " laid out in only " + byLayout.getOrDefault(id, 0) + " of " + villages + " villages");
			}
		}
		VineAndBarrel.LOGGER.info("Villages with a tavern:{}", report);
		helper.succeed();
	}

	/**
	 * A village of this type laid out the way the game does (its town centres, streets and houses, depth 6, 80 from the
	 * centre), on deep flat ground with the surface at y 0 (the GameTest world's own ground is only four blocks over the
	 * bottom of the world, too shallow for a house with a cellar). No blocks are placed.
	 */
	private static Optional<Structure.GenerationStub> layOutVillage(GameTestHelper helper, VillageType type, BlockPos centre, long seed) {
		ServerLevel level = helper.getLevel();
		var pools = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		var centres = pools.get(ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.withDefaultNamespace("village/" + type.id() + "/town_centers"))).orElseThrow();
		var generator = deepFlatGenerator(level);
		var randomState = level.getChunkSource().randomState();
		var context = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
			randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED), randomState, level.getStructureTemplateManager(), seed,
			ChunkPos.containing(centre), level, biome -> true);
		return JigsawPlacement.addPieces(context, centres, Optional.empty(), 6, centre, true, Optional.empty(),
			new JigsawStructure.MaxDistance(80), PoolAliasLookup.EMPTY, JigsawStructure.DEFAULT_DIMENSION_PADDING, JigsawStructure.DEFAULT_LIQUID_SETTINGS);
	}

	private static net.minecraft.world.level.chunk.ChunkGenerator deepFlat;

	/** A flat generator with 64 blocks of ground under its surface (y 0), like a world with room for cellars. */
	private static net.minecraft.world.level.chunk.ChunkGenerator deepFlatGenerator(ServerLevel level) {
		if (deepFlat == null) {
			var access = level.registryAccess();
			var biomes = access.lookupOrThrow(Registries.BIOME);
			var settings = net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings.getDefault(biomes, access.lookupOrThrow(Registries.STRUCTURE_SET),
				access.lookupOrThrow(Registries.PLACED_FEATURE)).withBiomeAndLayers(List.of(
					new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(1, Blocks.BEDROCK), new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(60, Blocks.STONE),
					new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(3, Blocks.DIRT), new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(1, Blocks.GRASS_BLOCK)),
				Optional.of(net.minecraft.core.HolderSet.direct()), biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS));
			deepFlat = new net.minecraft.world.level.levelgen.FlatLevelSource(settings);
		}
		return deepFlat;
	}

	/** A tavern standing in the test, placed the way a village places it: its pool element and processors, jigsaws replaced. */
	private record Placed(ServerLevel level, BlockPos origin, StructureTemplate template, BlockPos joint) {
		/** The tavern and its cellar under it. */
		BoundingBox box() {
			return BoundingBox.fromCorners(origin.below(VillageStructures.CELLAR_HEIGHT), origin.offset(template.getSize()).offset(-1, -1, -1));
		}

		/** Every position in the tavern whose state passes the test. */
		List<BlockPos> find(Predicate<BlockState> test) {
			List<BlockPos> found = new ArrayList<>();
			BoundingBox box = box();
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (test.test(level.getBlockState(pos))) {
					found.add(pos.immutable());
				}
			}
			return found;
		}

		/** The street joint's cell, now open: where a villager comes in from the street. */
		BlockPos street() {
			return origin.offset(joint);
		}
	}

	/** Whether something stands in the way at this spot (a block with any collision). */
	private static boolean blocks(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private static BlockPos entranceOf(StructureTemplate template) {
		return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW).stream()
			.filter(info -> info.nbt() != null && "minecraft:building_entrance".equals(info.nbt().getStringOr("name", ""))).findFirst().orElseThrow().pos();
	}

	private static Placed placeTavern(GameTestHelper helper, VillageStructures.TavernLayout layout) {
		ServerLevel level = helper.getLevel();
		SinglePoolElement element = tavernElement(helper, layout);
		StructureTemplate template = level.getStructureTemplateManager().get(layout.id()).orElseThrow();
		BlockPos origin = helper.absolutePos(TAVERN_AT);
		// the clip box reaches down to the cellar, as a chunk's does in a world
		boolean placed = element.place(level.getStructureTemplateManager(), level, level.structureManager(), level.getChunkSource().getGenerator(),
			origin, origin, Rotation.NONE, BoundingBox.fromCorners(origin.offset(-2, -2 - VillageStructures.CELLAR_HEIGHT, -2), origin.offset(template.getSize()).offset(1, 1, 1)),
			RandomSource.create(5L), LiquidSettings.APPLY_WATERLOGGING, false);
		helper.assertTrue(placed, layout.name() + " places");
		return new Placed(level, origin, template, entranceOf(template));
	}

	/**
	 * The way in: from the street joint (on the street edge, at door height) straight to the door, two blocks of
	 * headroom over a floor you can walk on, so the floor is flush with the path and nothing blocks it. Returns the door.
	 */
	private static BlockPos wayIn(GameTestHelper helper, Placed t, String type) {
		helper.assertValueEqual(t.joint().getZ(), 0, type + " street joint on the street edge");
		BlockPos pos = t.street();
		for (int step = 0; step < t.template().getSize().getZ(); step++, pos = pos.south()) {
			BlockState state = t.level().getBlockState(pos);
			if (state.getBlock() instanceof DoorBlock) {
				helper.assertTrue(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER, "the " + type + " door's lower half at the joint's height");
				helper.assertTrue(state.is(BlockTags.WOODEN_DOORS), "villagers can open the " + type + " door");
				helper.assertTrue(t.level().getBlockState(pos.below()).isFaceSturdy(t.level(), pos.below(), Direction.UP), "a floor under the " + type + " door");
				return pos;
			}
			helper.assertTrue(!blocks(t.level(), pos) && !blocks(t.level(), pos.above()),
				"the way to the " + type + " door is open at " + pos.subtract(t.origin()));
			// something to walk on (a dirt path is a little lower than a full block, so not "sturdy")
			helper.assertTrue(blocks(t.level(), pos.below()),
				"a path under the way to the " + type + " door at " + pos.below().subtract(t.origin()));
		}
		throw helper.assertionException("no door in line with the " + type + " tavern's street joint");
	}

	/**
	 * Placed the way a village places it, a tavern has its way in from the street, its hanging sign, the kettle on a
	 * lit campfire, a hearth whose smoke rises up an open chimney, one chest with its tavern loot, drinks on the tables
	 * and the bar, and tavern stock (W1): racked and stacked aging barrels, a few of them aged, none of them home-brewed
	 * (so they earn On Tap, never Patience Pays), that pour real drinks into a player's mug or glass and keep aging.
	 */
	private static void tavernPlacesAndTaps(GameTestHelper helper, VillageStructures.TavernLayout layout) {
		ServerLevel level = helper.getLevel();
		Placed t = placeTavern(helper, layout);
		VillageType type = layout.type();
		String name = layout.name();
		helper.assertValueEqual(t.joint().getY(), TAVERN_FLOOR + 1, name + " street joint one above the floor");
		wayIn(helper, t, name);
		helper.assertValueEqual(t.find(s -> s.is(Blocks.JIGSAW)).size(), 0, "jigsaws left in the " + name + " tavern");
		// one job site, the kettle behind the bar: the keeper's (1.2); no vanilla job block, so no other villager moves in
		List<BlockPos> sites = t.find(s -> net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(s).filter(poi -> poi.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE)).isPresent());
		helper.assertValueEqual(sites.size(), 1, "job sites in the " + name + " tavern");
		helper.assertTrue(jexxme.vineandbarrel.world.village.TavernKeeper.isJobSite(level.getBlockState(sites.getFirst())), "the " + name + " job site is the keeper's kettle");
		// and the tavern brings its keeper, once, standing by the kettle
		List<net.minecraft.world.entity.npc.villager.Villager> keepers = level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
			net.minecraft.world.phys.AABB.of(t.box()).inflate(1), jexxme.vineandbarrel.world.village.TavernKeeper::isKeeper);
		helper.assertValueEqual(keepers.size(), 1, name + " keepers");
		helper.assertTrue(keepers.getFirst().getVillagerXp() > 0, name + " keeper keeps his trade until he reaches his kettle");
		helper.assertTrue(keepers.getFirst().blockPosition().closerThan(sites.getFirst(), 3), name + " keeper stands by his kettle: " + keepers.getFirst().blockPosition().subtract(t.origin()));
		// every dirt path stays one (a block on top would turn it to dirt)
		List<BlockPos> sunk = t.find(s -> s.is(Blocks.DIRT_PATH)).stream().filter(pos -> !level.getBlockState(pos).canSurvive(level, pos))
			.map(pos -> pos.subtract(t.origin())).toList();
		helper.assertTrue(sunk.isEmpty(), "dirt paths in the " + name + " tavern that turn to dirt: " + sunk);

		// the hanging sign, in the reader's language (audit 2026-11-08), "Tavern" where the key is unknown
		List<BlockPos> signs = t.find(s -> s.getBlock() instanceof CeilingHangingSignBlock || s.getBlock() instanceof WallHangingSignBlock);
		helper.assertTrue(signs.stream().anyMatch(pos -> level.getBlockEntity(pos) instanceof SignBlockEntity sign
			&& sign.getText(SignTextSlot.FRONT).getMessages(false).stream().anyMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tr
				&& tr.getKey().equals("sign.vineandbarrel.tavern") && "Tavern".equals(tr.getFallback()))), "a " + name + " hanging sign saying Tavern in every language");
		// hops climb the trellis (no Farmer's Delight needed), their bushes drawn as the base of the vine
		for (BlockPos pos : t.find(s -> s.getBlock() instanceof jexxme.vineandbarrel.block.ClimbingCropBlock)) {
			BlockState plant = level.getBlockState(pos);
			helper.assertFalse(plant.getBlock() instanceof jexxme.vineandbarrel.block.VineOnRopeBlock, name + " grows a vine on a rope at " + pos.subtract(t.origin()));
			if (plant.getBlock() instanceof jexxme.vineandbarrel.block.ClimbingBushBlock bush) {
				helper.assertValueEqual(plant.getValue(jexxme.vineandbarrel.block.ClimbingBushBlock.SUPPORT),
					jexxme.vineandbarrel.block.BushSupport.of(bush, level.getBlockState(pos.above())), name + " bush support at " + pos.subtract(t.origin()));
			}
		}

		// the kettle sits on its tray over a lit campfire
		List<BlockPos> kettles = t.find(s -> s.getBlock() instanceof BrewKettleBlock);
		helper.assertValueEqual(kettles.size(), 1, name + " kettles");
		BlockState kettle = level.getBlockState(kettles.getFirst());
		helper.assertTrue(kettle.getValue(BrewKettleBlock.SUPPORT) == KettleSupport.TRAY, "kettle on a tray: " + kettle);
		BlockState under = level.getBlockState(kettles.getFirst().below());
		helper.assertTrue(under.is(Blocks.CAMPFIRE) && under.getValue(CampfireBlock.LIT), "lit campfire under the kettle: " + under);

		// the hearth: a lit signal fire with nothing but air above it, up through the chimney and out over the roof
		List<BlockPos> hearths = t.find(s -> s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.LIT) && s.getValue(CampfireBlock.SIGNAL_FIRE));
		helper.assertTrue(hearths.stream().anyMatch(pos -> {
			for (BlockPos above = pos.above(); above.getY() <= t.box().maxY(); above = above.above()) {
				if (!level.getBlockState(above).isAir()) {
					return false;
				}
			}
			return true;
		}), "the " + name + " hearth smokes out of an open chimney: " + hearths);

		// two chests: the bar's, with the tavern loot, and the one hidden in the cellar (1.2)
		List<BlockPos> chests = t.find(s -> s.is(Blocks.CHEST));
		helper.assertValueEqual(chests.size(), 2, name + " chests");
		List<BlockPos> cellarChests = chests.stream().filter(pos -> level.getBlockEntity(pos) instanceof ChestBlockEntity c && type.tavernCellarLoot().equals(c.getLootTable())).toList();
		helper.assertValueEqual(cellarChests.size(), 1, name + " cellar chests");
		helper.assertTrue(cellarChests.getFirst().getY() < t.origin().getY(), name + " cellar chest below the floor");
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chests.stream().filter(pos -> !cellarChests.contains(pos)).findFirst().orElseThrow());
		helper.assertTrue(chest != null && type.tavernLoot().equals(chest.getLootTable()), name + " tavern chest loot table");
		chest.unpackLootTable(null);
		boolean vessel = false;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			vessel |= chest.getItem(i).is(DrinkItems.MUG.get()) || chest.getItem(i).is(DrinkItems.WINE_GLASS.get());
		}
		helper.assertTrue(vessel, "a mug or glass in the " + name + " tavern chest");

		// drinks on every placed-drinks block, each spot showing what stands there
		List<BlockPos> tables = t.find(s -> s.is(BarrelRegistry.PLACED_DRINKS.get()));
		helper.assertTrue(tables.size() >= 4, "drinks on the " + name + " tables and the bar: " + tables.size());
		for (BlockPos pos : tables) {
			PlacedDrinksBlockEntity drinks = (PlacedDrinksBlockEntity) level.getBlockEntity(pos);
			helper.assertTrue(drinks != null && drinks.count() > 0, "drinks at " + pos.subtract(t.origin()));
			for (int spot = 0; spot < 4; spot++) {
				PlacedDrink shown = level.getBlockState(pos).getValue(PlacedDrinksBlock.SPOT.get(spot));
				PlacedDrink held = drinks.getItem(spot).isEmpty() ? PlacedDrink.NONE : PlacedDrink.of(drinks.getItem(spot));
				helper.assertValueEqual(shown, held, "what spot " + spot + " at " + pos.subtract(t.origin()) + " shows");
			}
		}

		// the barrels: racked and stacked, the tavern's own stock, most of it down in the cellar below the floor
		List<BlockPos> barrels = t.find(s -> s.is(BarrelRegistry.AGING_BARREL.get()));
		int floorY = t.street().getY() - 1;
		helper.assertTrue(barrels.stream().filter(pos -> pos.getY() < floorY).count() >= 6, "casks in the " + name + " cellar below the floor: "
			+ barrels.stream().filter(pos -> pos.getY() < floorY).count());
		helper.assertTrue(barrels.stream().anyMatch(pos -> level.getBlockState(pos).getValue(AgingBarrelBlock.STACKED)), "a barrel stack in the " + name + " tavern");
		helper.assertTrue(barrels.stream().anyMatch(pos -> level.getBlockState(pos).getValue(AgingBarrelBlock.LEFT) && level.getBlockState(pos).getValue(AgingBarrelBlock.RIGHT)),
			"a barrel rack in the " + name + " tavern");
		// W1: after a tick the barrels know their age. A tavern has been serving: half its young casks are drunk dry (which
		// ones comes from each cask's position), the rest hold one or two servings, and its aged casks are never dry, so
		// every tavern has stock. The rule is checked cask by cask, nothing here counts on a draw.
		helper.runAfterDelay(3, () -> {
			List<BlockPos> stock = new java.util.ArrayList<>();
			for (BlockPos pos : barrels) {
				AgingBarrelBlockEntity be = (AgingBarrelBlockEntity) level.getBlockEntity(pos);
				boolean waxed = level.getBlockState(pos).getValue(AgingBarrelBlock.BUNG) == jexxme.vineandbarrel.barrel.BungState.WAX;
				helper.assertTrue(be.servings() <= 2, "at most two servings left in the cask at " + relativePos(helper, pos) + ": " + be.servings());
				helper.assertTrue(!waxed || be.servings() >= 1, "the aged cask at " + relativePos(helper, pos) + " isn't drunk dry");
				if (be.servings() > 0) {
					stock.add(relativePos(helper, pos));
				}
			}
			for (BlockPos barrel : stock) {
				helper.assertFalse(helper.getBlockEntity(barrel, AgingBarrelBlockEntity.class).isHomeBrewed(), "tavern stock is not home-brewed at " + barrel);
			}
			// they keep aging from where the template left them: the saved age is a start, not a frozen value (every cask of
			// stock, before any is tapped dry)
			Map<BlockPos, Long> ages = new HashMap<>();
			Map<BlockPos, Long> toNext = new HashMap<>();
			for (BlockPos barrel : stock) {
				AgingBarrelBlockEntity be = helper.getBlockEntity(barrel, AgingBarrelBlockEntity.class);
				ages.put(barrel, be.age());
				toNext.put(barrel, be.ticksToNextStage());
			}
			long start = level.getGameTime();
			helper.runAfterDelay(20, () -> {
				long waited = level.getGameTime() - start;
				for (BlockPos barrel : stock) {
					AgingBarrelBlockEntity be = helper.getBlockEntity(barrel, AgingBarrelBlockEntity.class);
					helper.assertTrue(be.age() >= ages.get(barrel) + waited, "the barrel at " + barrel + " aged " + (be.age() - ages.get(barrel))
						+ " ticks in " + waited);
					if (be.stage() == BarrelStage.YOUNG) {
						helper.assertTrue(be.ticksToNextStage() <= toNext.get(barrel) - waited, "the young barrel at " + barrel + " comes closer to aged");
					}
				}
				// then tap each by hand
				int aged = 0;
				for (BlockPos barrel : stock) {
					aged += tapsByHand(helper, barrel) ? 1 : 0;
				}
				// a few, most of them in the cellar's find (1.2)
				helper.assertTrue(aged >= 1 && aged <= 7, "a few aged casks in the " + name + " tavern: " + aged);
				helper.succeed();
			});
		});
	}

	/**
	 * Placed into solid stone (a village on rising ground), a tavern clears its own room: every block the template
	 * leaves as air is air, and the way in from the street to the door is open over a walkable floor.
	 */
	private static void tavernClearsTerrain(GameTestHelper helper, VillageStructures.TavernLayout layout) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(TAVERN_AT);
		StructureTemplate template = level.getStructureTemplateManager().get(layout.id()).orElseThrow();
		for (BlockPos pos : BlockPos.betweenClosed(origin.below(VillageStructures.CELLAR_HEIGHT), origin.offset(template.getSize()).offset(-1, -1, -1))) {
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		Placed t = placeTavern(helper, layout);
		StructureTemplate cellar = level.getStructureTemplateManager().get(layout.cellarId()).orElseThrow();
		for (StructureTemplate.StructureBlockInfo info : cellar.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.AIR)) {
			helper.assertTrue(level.getBlockState(origin.below(VillageStructures.CELLAR_HEIGHT).offset(info.pos())).isAir(),
				"stone left at " + info.pos() + " in the cellar of " + layout.name());
		}
		List<StructureTemplate.StructureBlockInfo> air = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.AIR);
		helper.assertTrue(air.size() > 500, layout.name() + " template keeps its air: " + air.size());
		for (StructureTemplate.StructureBlockInfo info : air) {
			helper.assertTrue(level.getBlockState(origin.offset(info.pos())).isAir(), "stone left at " + info.pos() + " in " + layout.name());
		}
		wayIn(helper, t, layout.name());
		helper.succeed();
	}

	/**
	 * Room to walk ({@link TavernWalk}): from the door a way two wide to the bar counter, and one at least one wide to
	 * every seat, cask, the kettle and the chest, and down into the cellar, two blocks high all the way.
	 */
	/**
	 * Each layout's servings before 1.2, as the dressing deals them on average: an aged cask keeps one or two (1.5), a
	 * young one is drunk dry half the time and else holds one or two (0.75). Measured on the 1.1 templates, 2026-11-13.
	 */
	private static final Map<String, Double> SERVINGS_BEFORE = Map.of("plains_tavern_1", 18.8, "plains_tavern_2", 15.0, "desert_tavern_1", 20.2,
		"desert_tavern_2", 20.2, "savanna_tavern_1", 18.8, "savanna_tavern_2", 17.2, "snowy_tavern_1", 18.0, "snowy_tavern_2", 16.5,
		"taiga_tavern_1", 18.8, "taiga_tavern_2", 18.8);

	/**
	 * The 1.2 interiors move drink about (the cellar's find holds aged casks) but don't add any (Jexxme: "keep total loot
	 * balanced"): a tavern and its cellar together hold today's servings, within 10%.
	 */
	private static void tavernServings(GameTestHelper helper, VillageStructures.TavernLayout layout) {
		var manager = helper.getLevel().getStructureTemplateManager();
		double expected = 0;
		for (net.minecraft.resources.Identifier id : List.of(layout.id(), layout.cellarId())) {
			for (var info : manager.get(id).orElseThrow().filterBlocks(BlockPos.ZERO, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
				jexxme.vineandbarrel.barrel.BarrelRegistry.AGING_BARREL.get())) {
				var nbt = info.nbt();
				if (nbt == null || !(nbt.contains("Batch") || nbt.contains(jexxme.vineandbarrel.world.village.TavernDressingProcessor.CHOICES))) {
					continue;
				}
				expected += info.state().getValue(jexxme.vineandbarrel.barrel.AgingBarrelBlock.BUNG) == jexxme.vineandbarrel.barrel.BungState.WAX
					|| nbt.getBooleanOr(jexxme.vineandbarrel.world.village.TavernDressingProcessor.FULL, false) ? 1.5 : 0.75;
			}
		}
		double before = SERVINGS_BEFORE.get(layout.name());
		// and the aged casks as many as before (most of them now in the cellar's find)
		int aged = 0;
		for (net.minecraft.resources.Identifier id : List.of(layout.id(), layout.cellarId())) {
			aged += (int) manager.get(id).orElseThrow().filterBlocks(BlockPos.ZERO, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
				jexxme.vineandbarrel.barrel.BarrelRegistry.AGING_BARREL.get()).stream()
				.filter(info -> info.state().getValue(jexxme.vineandbarrel.barrel.AgingBarrelBlock.BUNG) == jexxme.vineandbarrel.barrel.BungState.WAX).count();
		}
		helper.assertValueEqual(aged, layout.name().equals("plains_tavern_2") ? 1 : 2, layout.name() + " aged casks, as before 1.2");
		helper.assertTrue(Math.abs(expected - before) <= before * 0.1, layout.name() + " holds " + expected + " servings on average, " + before + " before 1.2");
		helper.succeed();
	}

	private static void tavernHasRoomToWalk(GameTestHelper helper, VillageStructures.TavernLayout layout) {
		Placed t = placeTavern(helper, layout);
		TavernWalk.Result walk = TavernWalk.check(t.level(), t.box(), t.street(), t.street().getY() - 1);
		helper.assertTrue(walk.ok(), layout.name() + " has no room to walk: " + walk.problems() + "\n" + walk.map());
		helper.succeed();
	}

	/**
	 * Built whole (Jexxme's review, 2026-11-09). Every table stands: a table top on its own, or a row of them, rests on a
	 * leg (an upside-down stair) instead of hanging over the floor. The hearth burns inside, walled on every side but its
	 * mouth, its chimney closed round the smoke all the way up and standing on stone, never on the roof's stairs or over
	 * open air. No bare ground shows in the cellar.
	 */
	private static void tavernIsBuiltWhole(GameTestHelper helper, VillageStructures.TavernLayout layout) {
		ServerLevel level = helper.getLevel();
		Placed t = placeTavern(helper, layout);
		String name = layout.name();
		BoundingBox house = BoundingBox.fromCorners(t.origin(), t.origin().offset(t.template().getSize()).offset(-1, -1, -1));

		// the tables (a trapdoor up under the eaves, with nothing under it, is trim, not a table)
		for (BlockPos top : t.find(WorldGameTests::isTableTop)) {
			if (level.getBlockState(top.below()).isFaceSturdy(level, top.below(), Direction.UP)) {
				helper.assertTrue(tableStands(level, top), "the " + name + " table at " + top.subtract(t.origin()) + " floats: no leg under it or its row");
			}
		}

		// the hearth and its chimney
		List<BlockPos> hearths = t.find(s -> s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.SIGNAL_FIRE));
		helper.assertFalse(hearths.isEmpty(), name + " has a hearth");
		for (BlockPos fire : hearths) {
			BlockPos at = fire.subtract(t.origin());
			long open = Direction.Plane.HORIZONTAL.stream().filter(d -> !blocks(level, fire.relative(d))).count();
			helper.assertTrue(open <= 1, "the " + name + " hearth at " + at + " is open on " + open + " sides, not just its mouth");
			// the chimney's top: the highest layer where anything still stands beside the smoke (snow lying on it doesn't)
			int top = fire.getY();
			for (BlockPos p = fire.above(); p.getY() <= house.maxY(); p = p.above()) {
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if (blocks(level, p.relative(d))) {
						top = p.getY();
					}
				}
			}
			// above the mouth and its lintel, walled round all the way up
			for (BlockPos p = fire.above(2); p.getY() <= top; p = p.above()) {
				for (Direction d : Direction.Plane.HORIZONTAL) {
					helper.assertFalse(level.getBlockState(p.relative(d)).isAir(),
						"the " + name + " chimney is open beside the smoke at " + p.relative(d).subtract(t.origin()));
				}
			}
			// its stones stand on stone: a full block, a corbel or a top slab under each, or air only inside the house
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int y = fire.getY() + 3; y <= top + 1; y++) {
						BlockPos p = new BlockPos(fire.getX() + dx, y, fire.getZ() + dz);
						BlockState stone = level.getBlockState(p);
						if ((dx == 0 && dz == 0) || !stone.is(BlockTags.MINEABLE_WITH_PICKAXE) || !stone.isCollisionShapeFullBlock(level, p)) {
							continue;
						}
						BlockPos under = p.below();
						BlockState below = level.getBlockState(under);
						boolean holds = below.isCollisionShapeFullBlock(level, under)
							|| (below.getBlock() instanceof StairBlock && below.getValue(StairBlock.HALF) == Half.TOP)
							|| (below.getBlock() instanceof SlabBlock && below.getValue(SlabBlock.TYPE) != SlabType.BOTTOM)
							|| (below.isAir() && !openToTheOutside(level, under, house));
						helper.assertTrue(holds, "the " + name + " chimney's stone at " + p.subtract(t.origin()) + " stands on " + below + ", seen from outside");
					}
				}
			}
		}

		// the cellar: no bare ground beside its room
		StructureTemplate cellar = level.getStructureTemplateManager().get(layout.cellarId()).orElseThrow();
		BlockPos cellarAt = t.origin().below(VillageStructures.CELLAR_HEIGHT);
		for (StructureTemplate.StructureBlockInfo info : cellar.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.AIR)) {
			for (Direction d : Direction.values()) {
				BlockPos next = cellarAt.offset(info.pos()).relative(d);
				BlockState wall = level.getBlockState(next);
				// rooted dirt is the root cellar's ceiling, with roots hanging from it (1.2), not bare ground
				helper.assertFalse((wall.is(BlockTags.DIRT) && !wall.is(Blocks.ROOTED_DIRT)) || wall.is(BlockTags.SAND) || wall.is(Blocks.GRAVEL),
					"bare " + BuiltInRegistries.BLOCK.getKey(wall.getBlock()).getPath() + " in the " + name + " cellar wall at " + next.subtract(cellarAt));
			}
		}
		helper.succeed();
	}

	/** A table top: a closed trapdoor in the top half of its block. */
	private static boolean isTableTop(BlockState state) {
		return state.getBlock() instanceof TrapDoorBlock && !state.getValue(TrapDoorBlock.OPEN)
			&& state.getValue(TrapDoorBlock.HALF) == Half.TOP;
	}

	/** Whether this table top, or the row of table tops it is in, has a leg: an upside-down stair at the same height. */
	private static boolean tableStands(ServerLevel level, BlockPos top) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (BlockPos p = top.relative(d); ; p = p.relative(d)) {
				BlockState s = level.getBlockState(p);
				if (s.getBlock() instanceof StairBlock) {
					if (s.getValue(StairBlock.HALF) == Half.TOP) {
						return true;
					}
					break;
				}
				if (!isTableTop(s)) {
					break;
				}
			}
		}
		return false;
	}

	/** Whether open air runs straight out from this spot, along any side, past the house's walls. */
	private static boolean openToTheOutside(ServerLevel level, BlockPos pos, BoundingBox house) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = pos;
			while (house.isInside(p) && level.getBlockState(p).isAir()) {
				p = p.relative(d);
			}
			if (!house.isInside(p)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * No villager gets trapped and the door works for them: a villager coming in from the street finds a path through
	 * the door to the bar and into the barrel cellar, and one in the cellar finds its way back out.
	 */
	private static void villagersFindTheirWay(GameTestHelper helper, VillageStructures.TavernLayout layout) {
		ServerLevel level = helper.getLevel();
		Placed t = placeTavern(helper, layout);
		String name = layout.name();
		// the bar: in front of the tavern chest behind the counter; the cellar: in front of a cask on the cellar floor, down
		// the stairs below the hall, that a villager can stand in front of (full or drunk dry: the way there is the same)
		BlockPos chest = t.find(s -> s.is(Blocks.CHEST)).stream().filter(pos -> level.getBlockEntity(pos) instanceof ChestBlockEntity c
			&& layout.type().tavernLoot().equals(c.getLootTable())).findFirst().orElseThrow();
		BlockPos bar = chest.relative(level.getBlockState(chest).getValue(ChestBlock.FACING));
		BlockPos cellar = t.find(s -> s.is(BarrelRegistry.AGING_BARREL.get())).stream()
			// the cellar room's floor (the finds below it are reached by ladder, and kept for players)
			.filter(pos -> pos.getY() == t.origin().getY() - 4 && !level.getBlockState(pos.below()).is(BarrelRegistry.AGING_BARREL.get()))
			.sorted(java.util.Comparator.<BlockPos>comparingInt(pos -> -pos.getX()).thenComparingInt(pos -> -pos.getZ()))
			.map(pos -> pos.relative(level.getBlockState(pos).getValue(AgingBarrelBlock.FACING)))
			.filter(front -> level.getBlockState(front).isAir() && level.getBlockState(front.above()).isAir())
			.findFirst().orElseThrow();
		Villager villager = helper.spawn(EntityTypes.VILLAGER, relativePos(helper, t.street()));
		// a mob plans paths only while it stands on the ground; it has not fallen onto it yet on its first tick
		villager.setOnGround(true);
		for (BlockPos target : List.of(bar, cellar)) {
			Path path = villager.getNavigation().createPath(target, 0);
			helper.assertTrue(path != null && path.canReach(), "a villager from the " + name + " street reaches " + target.subtract(t.origin()) + ": " + describe(path, t.origin()));
		}
		villager.teleportTo(cellar.getX() + 0.5, cellar.getY(), cellar.getZ() + 0.5);
		villager.setOnGround(true);
		Path out = villager.getNavigation().createPath(t.street(), 0);
		helper.assertTrue(out != null && out.canReach(), "a villager in the " + name + " cellar finds its way out: " + out);
		helper.succeed();
	}

	/**
	 * The tavern fits the plots real villages offer. Villages are grown the way the game grows them (the jigsaw layout
	 * of this type's town centres, streets and houses, depth 6, 80 from the centre), only with the tavern favoured, and
	 * at least one tavern must be laid out. A house that reaches below its street's ground layer, or is too big for the
	 * plots beside the streets, never is: it would sit in the pool and never appear.
	 */
	private static void tavernTakesAVillagePlot(GameTestHelper helper, VillageStructures.TavernLayout layout) {
		ServerLevel level = helper.getLevel();
		VillageType type = layout.type();
		var pools = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		StructureTemplatePool houses = pools.getValue(type.housesPool());
		SinglePoolElement tavern = tavernElement(helper, layout);
		int favour = 30;
		for (int i = 0; i < favour; i++) {
			houses.templates.add(tavern);
		}
		try {
			// A fixed place, not the test's own: the layout depends on the seed and the chunk only, so the same villages
			// are laid out every run
			BlockPos centre = new BlockPos(8, 1, 8);
			int taverns = 0;
			for (int seed = 0; seed < 32 && taverns == 0; seed++) {
				var stub = layOutVillage(helper, type, centre, seed);
				if (stub.isPresent()) {
					for (var piece : stub.get().getPiecesBuilder().build().pieces()) {
						if (piece instanceof PoolElementStructurePiece poolPiece && VillageStructures.isTemplate(poolPiece.getElement(), layout.id())) {
							taverns++;
						}
					}
				}
			}
			helper.assertTrue(taverns > 0, "no " + type.id() + " village laid out " + layout.name());
		} finally {
			for (int i = 0; i < favour; i++) {
				houses.templates.remove(houses.templates.size() - 1);
			}
		}
		helper.succeed();
	}

	/**
	 * No monster spawns inside: every spot a mob could stand on under the tavern's roofs (the hall, the cellar, under
	 * the eaves and the pergola) has block light.
	 */
	private static void tavernIsLit(GameTestHelper helper, VillageStructures.TavernLayout layout) {
		ServerLevel level = helper.getLevel();
		Placed t = placeTavern(helper, layout);
		VillageType type = layout.type();
		helper.runAfterDelay(20, () -> {
			BoundingBox box = t.box();
			// light is worked out on another thread: a busy server can reach tick 20 before the tavern and its cellar are
			// lit, so wait for the light engine itself (in real time) before reading the light
			List<BlockPos> corners = new ArrayList<>();
			for (int x = box.minX(); x <= box.maxX() + 15; x += 16) {
				for (int z = box.minZ(); z <= box.maxZ() + 15; z += 16) {
					corners.add(new BlockPos(Math.min(x, box.maxX()), box.minY(), Math.min(z, box.maxZ())));
				}
			}
			VBCropTests.settleLightAt(helper, corners);
			List<BlockPos> dark = new ArrayList<>();
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY() + 1, box.minZ(), box.maxX(), box.maxY() - 1, box.maxZ())) {
				BlockPos below = pos.below();
				if (blocks(level, pos) || blocks(level, pos.above()) || !level.getBlockState(pos).getFluidState().isEmpty()
					|| !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
					continue;
				}
				boolean roofed = false;
				for (BlockPos above = pos.above(2); above.getY() <= box.maxY() && !roofed; above = above.above()) {
					roofed = !level.getBlockState(above).isAir();
				}
				if (roofed && level.getBrightness(LightLayer.BLOCK, pos) == 0) {
					dark.add(pos.subtract(t.origin()));
				}
			}
			helper.assertTrue(dark.isEmpty(), "dark spots a monster could spawn on in " + layout.name() + ": " + dark);
			helper.succeed();
		});
	}

	/**
	 * A tavern's cellar casks are the tavern's, though they lie under the tavern piece's box: tapping one in a village
	 * earns On Tap, and the blackout's tavern ending counts them among the tavern's barrels.
	 */
	private static void cellarCasksAreTheTaverns(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageStructures.TavernLayout layout = VillageStructures.taverns(VillageType.PLAINS).getFirst();
		Placed t = placeTavern(helper, layout);
		// the piece as a village records it: the tavern's own box, from its floor up
		BoundingBox piece = BoundingBox.fromCorners(t.origin(), t.origin().offset(t.template().getSize()).offset(-1, -1, -1));
		BlockPos cask = t.find(s -> s.is(BarrelRegistry.AGING_BARREL.get())).stream()
			.filter(pos -> pos.getY() < t.origin().getY() && ((AgingBarrelBlockEntity) level.getBlockEntity(pos)).servings() > 0)
			.findFirst().orElseThrow(() -> helper.assertionException("no full cask in the cellar"));
		helper.assertFalse(piece.isInside(cask), "the cellar cask lies under the piece's box");
		BlackoutGameTests.inVillage(helper, piece, () -> {
			helper.assertTrue(VillageStructures.villageAt(level, cask).isValid(), "the cellar cask is in the village");
			helper.assertTrue(jexxme.vineandbarrel.tipsy.BlackoutSpots.inTavern(level, cask), "the cellar cask is in the tavern");
			helper.assertTrue(jexxme.vineandbarrel.tipsy.BlackoutSpots.tavernBarrels(level, cask.above(2), 16).contains(cask), "the blackout counts the cellar cask");
			@SuppressWarnings("removal")
			net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
			try {
				AgingBarrelBlockEntity barrel = (AgingBarrelBlockEntity) level.getBlockEntity(cask);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AgingBarrelBlockEntity.vesselOf(barrel.drink()), 1));
				helper.useBlock(relativePos(helper, cask), player);
				var onTap = level.getServer().getAdvancements().get(jexxme.vineandbarrel.advancement.VBAdvancementGenerator.id("on_tap"));
				helper.assertTrue(onTap != null && player.getAdvancements().getOrStartProgress(onTap).isDone(), "On Tap from a cellar cask");
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
		});
		helper.succeed();
	}

	/**
	 * The keeper's daily restock picks an empty cask of his own tavern that he can walk to, never a player's cask beside
	 * the tavern, and fills it as the tavern's stock (not home-brewed).
	 */
	private static void keeperRestocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageStructures.TavernLayout layout = VillageStructures.taverns(VillageType.PLAINS).getFirst();
		Placed t = placeTavern(helper, layout);
		BoundingBox piece = BoundingBox.fromCorners(t.origin(), t.origin().offset(t.template().getSize()).offset(-1, -1, -1));
		BlockPos kettle = t.find(s -> s.getBlock() instanceof BrewKettleBlock).getFirst();
		net.minecraft.world.entity.npc.villager.Villager keeper = level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
			net.minecraft.world.phys.AABB.of(t.box()).inflate(1), jexxme.vineandbarrel.world.village.TavernKeeper::isKeeper).getFirst();
		// every cask of the tavern full but one in the cellar, drunk dry; a player's empty cask outside the tavern
		List<BlockPos> casks = t.find(s -> s.is(BarrelRegistry.AGING_BARREL.get()));
		BlockPos dry = casks.stream().filter(pos -> pos.getY() == t.origin().getY() - 4).findFirst().orElseThrow();
		for (BlockPos pos : casks) {
			BlockState state = level.getBlockState(pos);
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(pos, state.setValue(AgingBarrelBlock.FILL, 0));
			if (!pos.equals(dry)) {
				jexxme.vineandbarrel.world.village.TavernKeeper.fill(level, pos, DrinkItems.BARLEY_ALE.get());
			}
		}
		BlockPos players = t.origin().offset(-2, 1, 2);
		level.setBlockAndUpdate(players.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(players, BarrelRegistry.AGING_BARREL.get().defaultBlockState());
		BlackoutGameTests.inVillage(helper, piece, () -> {
			helper.assertFalse(jexxme.vineandbarrel.tipsy.BlackoutSpots.inTavern(level, players), "the player's cask is not the tavern's");
			// a mob plans paths only while it stands on the ground; it has not fallen onto it yet on its first tick
			keeper.setOnGround(true);
			BlockPos cask = jexxme.vineandbarrel.world.village.TavernKeeper.restockCask(level, keeper, kettle);
			var path = keeper.getNavigation().createPath(dry, 1);
			helper.assertTrue(dry.equals(cask), "the cask he restocks: " + cask + " (tavern casks " + jexxme.vineandbarrel.tipsy.BlackoutSpots.tavernBarrels(level, kettle, 16).size()
				+ ", path to the dry one " + (path == null ? "none" : path.canReach() + " " + path.getNodeCount() + " to " + path.getTarget()) + ", keeper at "
				+ keeper.blockPosition().subtract(t.origin()) + ", dry at " + dry.subtract(t.origin()) + ")");
			helper.assertTrue(jexxme.vineandbarrel.world.village.TavernKeeper.fill(level, cask, DrinkItems.BARLEY_ALE.get()), "filled");
			AgingBarrelBlockEntity barrel = (AgingBarrelBlockEntity) level.getBlockEntity(cask);
			helper.assertFalse(barrel.isHomeBrewed(), "the tavern's stock, not home-brewed");
			helper.assertTrue(((AgingBarrelBlockEntity) level.getBlockEntity(players)).contents().isEmpty(), "the player's cask left alone");
		});
		helper.succeed();
	}

	/**
	 * Audit m1 (2026-11-08): the 0.9.0 taverns keep their templates under their old names, so a village 0.9.0 laid out
	 * builds them as they were in the chunks it hadn't generated yet. They are never chosen for a new village, and they
	 * still count as taverns: their casks are the tavern's for On Tap and the blackout's tavern ending.
	 */
	private static void legacyTavernsResolveAndCount(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (VillageType type : VillageType.values()) {
			Identifier legacy = type.legacyTavern();
			helper.assertFalse(type.taverns().contains(legacy), legacy + " is not one of the layouts");
			StructureTemplate template = level.getStructureTemplateManager().get(legacy).orElse(null);
			helper.assertTrue(template != null, legacy + " still loads");
			helper.assertTrue(template.getSize().equals(new net.minecraft.core.Vec3i(11, 11, 11)), legacy + " is the 0.9.0 tavern: " + template.getSize());
			StructureTemplatePool pool = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getValue(type.housesPool());
			helper.assertFalse(pool.rawTemplates.stream().anyMatch(e -> VillageStructures.isTemplate(e.getFirst(), legacy)), legacy + " is not in " + type.housesPool());
			helper.assertFalse(pool.templates.stream().anyMatch(e -> VillageStructures.isTemplate(e, legacy)), legacy + " is never drawn");
			SinglePoolElement element = legacyTavernElement(level, type);
			helper.assertTrue(VillageStructures.isTavern(element, type), legacy + " counts as a " + type.id() + " tavern");
			helper.assertTrue(VillageStructures.isAnyTavern(element), legacy + " counts as a tavern");
		}
		// A 0.9.0 plains tavern, placed the way its saved piece places it, in a village
		SinglePoolElement element = legacyTavernElement(level, VillageType.PLAINS);
		StructureTemplate template = level.getStructureTemplateManager().get(VillageType.PLAINS.legacyTavern()).orElseThrow();
		BlockPos origin = helper.absolutePos(TAVERN_AT);
		boolean placed = element.place(level.getStructureTemplateManager(), level, level.structureManager(), level.getChunkSource().getGenerator(),
			origin, origin, Rotation.NONE, BoundingBox.fromCorners(origin.offset(-2, -2, -2), origin.offset(template.getSize()).offset(1, 1, 1)),
			RandomSource.create(5L), LiquidSettings.APPLY_WATERLOGGING, false);
		helper.assertTrue(placed, "the 0.9.0 tavern places");
		BoundingBox box = element.getBoundingBox(level.getStructureTemplateManager(), origin, Rotation.NONE);
		BlockPos cask = BlockPos.betweenClosedStream(box).filter(pos -> level.getBlockEntity(pos) instanceof AgingBarrelBlockEntity barrel && barrel.servings() > 0)
			.map(BlockPos::immutable).findFirst().orElseThrow(() -> helper.assertionException("no full cask in the 0.9.0 tavern"));
		BlackoutGameTests.inVillage(helper, box, element, () -> {
			helper.assertTrue(jexxme.vineandbarrel.tipsy.BlackoutSpots.inTavern(level, cask), "the 0.9.0 cask is in the tavern");
			helper.assertTrue(jexxme.vineandbarrel.tipsy.BlackoutSpots.tavernBarrels(level, cask.above(2), 16).contains(cask), "the blackout counts the 0.9.0 cask");
			@SuppressWarnings("removal")
			net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
			try {
				AgingBarrelBlockEntity barrel = (AgingBarrelBlockEntity) level.getBlockEntity(cask);
				helper.assertFalse(barrel.isHomeBrewed(), "the 0.9.0 cask holds tavern stock");
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AgingBarrelBlockEntity.vesselOf(barrel.drink()), 1));
				helper.useBlock(relativePos(helper, cask), player);
				var onTap = level.getServer().getAdvancements().get(jexxme.vineandbarrel.advancement.VBAdvancementGenerator.id("on_tap"));
				helper.assertTrue(onTap != null && player.getAdvancements().getOrStartProgress(onTap).isDone(), "On Tap from a 0.9.0 tavern's cask");
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
		});
		helper.succeed();
	}

	/** A 0.9.0 tavern's pool element, as a 0.9.0 village saved it: its template and the plain processor list, no dressing. */
	private static SinglePoolElement legacyTavernElement(ServerLevel level, VillageType type) {
		var processors = level.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST)
			.getOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.withDefaultNamespace("empty")));
		return (SinglePoolElement) SinglePoolElement.single(type.legacyTavern().toString(), processors).apply(StructureTemplatePool.Projection.RIGID);
	}

	/**
	 * Where the ground is too shallow for the cellar (the bottom of a superflat world), the tavern goes without one and
	 * its floor is closed where the stairs would go down: no hole, no crawlspace, no casks underground.
	 */
	private static void tavernWithoutCellar(GameTestHelper helper, VillageStructures.TavernLayout layout) {
		ServerLevel level = helper.getLevel();
		SinglePoolElement element = tavernElement(helper, layout);
		StructureTemplate template = level.getStructureTemplateManager().get(layout.id()).orElseThrow();
		BlockPos test = helper.absolutePos(TAVERN_AT);
		BlockPos origin = new BlockPos(test.getX(), level.getMinY() + 2, test.getZ());
		boolean placed = element.place(level.getStructureTemplateManager(), level, level.structureManager(), level.getChunkSource().getGenerator(),
			origin, origin, Rotation.NONE, BoundingBox.fromCorners(new BlockPos(origin.getX() - 2, level.getMinY(), origin.getZ() - 2), origin.offset(template.getSize()).offset(1, 1, 1)),
			RandomSource.create(5L), LiquidSettings.APPLY_WATERLOGGING, false);
		helper.assertTrue(placed, layout.name() + " places at the bottom of the world");
		for (BlockPos pos : BlockPos.betweenClosed(origin.getX(), level.getMinY(), origin.getZ(), origin.getX() + template.getSize().getX() - 1, origin.getY() - 1,
			origin.getZ() + template.getSize().getZ() - 1)) {
			helper.assertFalse(level.getBlockState(pos).is(BarrelRegistry.AGING_BARREL.get()), "a cask under " + layout.name() + " without room for its cellar");
		}
		List<BlockPos> holes = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(template.getSize().getX() - 1, 0, template.getSize().getZ() - 1))) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.getBlock() instanceof StairBlock) {
				holes.add(pos.subtract(origin));
			}
		}
		helper.assertTrue(holes.isEmpty(), layout.name() + " floor left open without a cellar under it: " + holes);
		helper.succeed();
	}

	/** A path's nodes relative to the tavern, for a failure message. */
	private static String describe(Path path, BlockPos origin) {
		if (path == null) {
			return "no path";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < path.getNodeCount(); i++) {
			out.append(' ').append(path.getNode(i).asBlockPos().subtract(origin).toShortString().replace(" ", ""));
		}
		return "nodes" + out;
	}

	/** Taps one serving into the vessel the barrel's drink needs, as a player would. Returns whether it was aged. */
	private static boolean tapsByHand(GameTestHelper helper, BlockPos relative) {
		AgingBarrelBlockEntity barrel = helper.getBlockEntity(relative, AgingBarrelBlockEntity.class);
		int servings = barrel.servings();
		BarrelStage stage = barrel.stage();
		ItemStack drink = barrel.drink();
		helper.assertTrue(servings >= 1, "a serving or more in the barrel at " + relative + ": " + servings);
		helper.assertTrue(stage == BarrelStage.YOUNG || stage == BarrelStage.AGED, "ready to tap at " + relative + ": " + stage);
		helper.assertValueEqual(helper.getBlockState(relative).getValue(AgingBarrelBlock.FILL), servings, "fill gauge at " + relative);
		helper.assertValueEqual(helper.getBlockState(relative).getValue(AgingBarrelBlock.BUNG), stage.bung(), "bung at " + relative);
		Item vessel = AgingBarrelBlockEntity.vesselOf(drink);
		helper.assertTrue(vessel != null, "a vessel for " + drink);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(vessel, 2));
		helper.useBlock(relative, player);
		helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "one " + vessel + " used at " + relative);
		helper.assertTrue(player.getInventory().contains(drink.copyWithCount(1)), "tapped a " + drink.getItem() + " at " + relative);
		helper.assertValueEqual(barrel.servings(), servings - 1, "servings after tapping at " + relative);
		return stage == BarrelStage.AGED;
	}
}
