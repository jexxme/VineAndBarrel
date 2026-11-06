package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.VineAndBarrel;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

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
	private static final Identifier TALL = VineAndBarrel.id("gametest/empty_16x14x16");
	private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, VineAndBarrel.MODID);
	private static final List<Entry> TESTS = new ArrayList<>();

	/** Where the tavern goes in its test area (room for the street side in front of it). */
	private static final BlockPos TAVERN_AT = new BlockPos(2, 0, 2);

	private record Entry(ResourceKey<Consumer<GameTestHelper>> function, Identifier structure, int maxTicks, boolean skyAccess) {
	}

	static {
		// under the open sky, so canSurvive judges the soil and not the light
		register("world_farm_processors_grow_our_crops", LARGE, 100, true, WorldGameTests::farmProcessors);
		register("world_trades_sell_our_seeds", LARGE, 100, WorldGameTests::trades);
		register("world_village_chests_gain_our_loot", LARGE, 100, WorldGameTests::houseLoot);
		register("world_tavern_chest_loot", LARGE, 100, WorldGameTests::tavernLoot);
		register("world_taverns_in_village_pools", LARGE, 100, WorldGameTests::tavernsInPools);
		register("world_village_apply_is_idempotent", LARGE, 100, WorldGameTests::applyIsIdempotent);
		for (VillageType type : VillageType.values()) {
			register("world_tavern_" + type.id() + "_places_and_barrels_tap", TALL, 200, helper -> tavernPlacesAndTaps(helper, type));
			register("world_tavern_" + type.id() + "_clears_solid_terrain", TALL, 100, helper -> tavernClearsTerrain(helper, type));
		}
	}

	private WorldGameTests() {
	}

	private static void register(String name, Identifier structure, int timeout, Consumer<GameTestHelper> function) {
		register(name, structure, timeout, false, function);
	}

	private static void register(String name, Identifier structure, int timeout, boolean skyAccess, Consumer<GameTestHelper> function) {
		FUNCTIONS.register(name, () -> function);
		TESTS.add(new Entry(ResourceKey.create(Registries.TEST_FUNCTION, VineAndBarrel.id(name)), structure, timeout, skyAccess));
	}

	public static void register(IEventBus modEventBus) {
		FUNCTIONS.register(modEventBus);
		modEventBus.addListener(WorldGameTests::registerTests);
	}

	private static void registerTests(RegisterGameTestsEvent event) {
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(VineAndBarrel.id("world"));
		for (Entry test : TESTS) {
			event.registerTest(test.function().identifier(), new FunctionGameTestInstance(test.function(), new TestData<>(environment,
				Level.OVERWORLD, test.structure(), test.maxTicks(), 0, true, Rotation.NONE, false, 1, 1, test.skyAccess(), 0)));
		}
	}

	// ------------------------------------------------------------------ 1. village farms

	/**
	 * Each village type's farm processor list carries our processor, first. Vanilla's large plains farm placed with that
	 * list, 16 times at different spots (each block draws by its position, like 16 farms in 16 villages), grows exactly
	 * that type's crops, each in a fair share, on farmland they can live on; vanilla's own crops still grow there too,
	 * and with Farmer's Delight installed its crops as well (neither crowds the other out). Checks the lists as the
	 * server start left them: nothing here adds the processor itself.
	 */
	private static void farmProcessors(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Registry<StructureProcessorList> lists = level.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST);
		StructureTemplate farm = level.getStructureTemplateManager().get(Identifier.withDefaultNamespace("village/plains/houses/plains_large_farm_1"))
			.orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(1, 0, 1));
		boolean fd = ModList.get().isLoaded("farmersdelight");
		Set<Block> ours = Set.of(VBBlocks.BARLEY.get(), VBBlocks.GRAPE_BUSH.get(), VBBlocks.HOP_BUSH.get());
		Set<Block> vanilla = Set.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS, Blocks.MELON_STEM, Blocks.PUMPKIN_STEM);
		for (VillageType type : VillageType.values()) {
			StructureProcessorList list = lists.getValue(type.farmProcessors());
			helper.assertTrue(list != null, "processor list " + type.farmProcessors());
			long count = list.list().stream().filter(VillageStructures::isOurFarmProcessor).count();
			helper.assertValueEqual((int) count, 1, "our farm processors in " + type.farmProcessors());
			helper.assertTrue(VillageStructures.isOurFarmProcessor(list.list().getFirst()), "ours runs first in " + type.farmProcessors());

			Map<Block, Integer> found = new HashMap<>();
			int wheatSpots = 0;
			int vanillaCrops = 0;
			int fdCrops = 0;
			for (int i = 0; i < 16; i++) {
				BlockPos at = origin.offset((i % 4) * 4, 0, (i / 4) * 5);
				StructurePlaceSettings settings = new StructurePlaceSettings().setKnownShape(true); // as a village places its pieces
				list.list().forEach(settings::addProcessor);
				farm.placeInWorld(level, at, at, settings, RandomSource.create(1L), Block.UPDATE_CLIENTS);
				for (BlockPos pos : BlockPos.betweenClosed(at, at.offset(farm.getSize()).offset(-1, -1, -1))) {
					BlockState state = level.getBlockState(pos);
					if (!level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
						continue;
					}
					wheatSpots++;
					if (ours.contains(state.getBlock())) {
						found.merge(state.getBlock(), 1, Integer::sum);
						helper.assertTrue(state.canSurvive(level, pos), state + " can't live on farmland in a " + type.id() + " farm");
					} else if (vanilla.contains(state.getBlock())) {
						vanillaCrops++;
					} else if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("farmersdelight")) {
						fdCrops++;
					}
				}
			}
			Set<Block> expected = new HashSet<>();
			VillageStructures.FARM_CROPS.get(type).forEach(crop -> expected.add(crop.crop().get()));
			helper.assertValueEqual(found.keySet(), expected, "our crops in 16 " + type.id() + " farms (" + found + ")");
			// expected share of each rule: its chance of the wheat its earlier rules left (no less than half of that here)
			float left = 1.0F;
			for (VillageStructures.FarmCrop crop : VillageStructures.FARM_CROPS.get(type)) {
				int min = (int) (wheatSpots * left * crop.chance() * 0.5F);
				helper.assertTrue(found.getOrDefault(crop.crop().get(), 0) >= min,
					crop.crop().get() + " in 16 " + type.id() + " farms: " + found + ", expected at least " + min + " of " + wheatSpots);
				left *= 1.0F - crop.chance();
			}
			// we take our share and no more: the farm's own crops (vanilla's, and FD's when installed) keep most of it
			int ourTotal = found.values().stream().mapToInt(Integer::intValue).sum();
			helper.assertTrue(ourTotal <= wheatSpots * (1.0F - left) * 1.5F, "our crops stay within their share of " + type.id() + " farms: "
				+ ourTotal + " of " + wheatSpots);
			helper.assertTrue(vanillaCrops + fdCrops > wheatSpots / 2, "the farm's own crops keep most of a " + type.id() + " farm: vanilla "
				+ vanillaCrops + ", FD " + fdCrops + " of " + wheatSpots);
			if (fd) {
				helper.assertTrue(fdCrops > 0, "Farmer's Delight's crops grow in " + type.id() + " farms too");
			}
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ 2. trades

	/** The trades are in the trade sets villagers draw from, and each gives what it says for one emerald. */
	private static void trades(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Registry<TradeSet> sets = level.registryAccess().lookupOrThrow(Registries.TRADE_SET);
		assertInSet(helper, sets, TradeSets.FARMER_LEVEL_1, VillageData.FARMER_1_BARLEY_SEEDS);
		assertInSet(helper, sets, TradeSets.FARMER_LEVEL_2, VillageData.FARMER_2_RED_GRAPES);
		assertInSet(helper, sets, TradeSets.FARMER_LEVEL_2, VillageData.FARMER_2_HOPS);
		assertInSet(helper, sets, TradeSets.WANDERING_TRADER_COMMON, VillageData.WANDERING_TRADER_RED_GRAPES);
		assertInSet(helper, sets, TradeSets.WANDERING_TRADER_COMMON, VillageData.WANDERING_TRADER_HOPS);
		assertInSet(helper, sets, TradeSets.WANDERING_TRADER_COMMON, VillageData.WANDERING_TRADER_BARLEY_SEEDS);

		Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
		LootContext context = new LootContext.Builder(new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, villager.position())
			.withParameter(LootContextParams.THIS_ENTITY, villager)
			.withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
			.create(LootContextParamSets.VILLAGER_TRADE)).create(Optional.empty());
		assertOffer(helper, context, VillageData.FARMER_1_BARLEY_SEEDS, VBItems.BARLEY_SEEDS.get(), 6, 16);
		assertOffer(helper, context, VillageData.FARMER_2_RED_GRAPES, VBItems.RED_GRAPES.get(), 3, 16);
		assertOffer(helper, context, VillageData.FARMER_2_HOPS, VBItems.HOPS.get(), 3, 16);
		assertOffer(helper, context, VillageData.WANDERING_TRADER_RED_GRAPES, VBItems.RED_GRAPES.get(), 1, 12);
		assertOffer(helper, context, VillageData.WANDERING_TRADER_HOPS, VBItems.HOPS.get(), 1, 12);
		assertOffer(helper, context, VillageData.WANDERING_TRADER_BARLEY_SEEDS, VBItems.BARLEY_SEEDS.get(), 1, 12);
		long ours = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).listElementIds()
			.filter(key -> key.identifier().getNamespace().equals(VineAndBarrel.MODID)).count();
		helper.assertValueEqual((int) ours, 6, "our villager trades");
		helper.succeed();
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
			tavernLoot(helper, helper.getLevel().getServer().reloadableRegistries().getLootTable(type.tavernLoot()));
		}
		helper.succeed();
	}

	private static void tavernLoot(GameTestHelper helper, LootTable table) {
		Set<Item> seeds = Set.of(VBItems.BARLEY_SEEDS.get(), VBItems.RED_GRAPES.get(), VBItems.HOPS.get(), VBItems.BARLEY.get());
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

	private static SinglePoolElement tavernElement(GameTestHelper helper, VillageType type) {
		StructureTemplatePool pool = helper.getLevel().registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getValue(type.housesPool());
		helper.assertTrue(pool != null, "pool " + type.housesPool());
		for (var entry : pool.rawTemplates) {
			if (VillageStructures.isTemplate(entry.getFirst(), type.tavern())) {
				return (SinglePoolElement) entry.getFirst();
			}
		}
		throw helper.assertionException("the " + type.id() + " tavern is not in " + type.housesPool());
	}

	/** Applying again (a second server start in the same JVM) changes no pool and no farm processor list. */
	private static void applyIsIdempotent(GameTestHelper helper) {
		var access = helper.getLevel().registryAccess();
		Map<VillageType, List<Object>> before = new HashMap<>();
		for (VillageType type : VillageType.values()) {
			before.put(type, List.of(List.copyOf(access.lookupOrThrow(Registries.TEMPLATE_POOL).getValue(type.housesPool()).rawTemplates),
				access.lookupOrThrow(Registries.TEMPLATE_POOL).getValue(type.housesPool()).templates.size(),
				List.copyOf(access.lookupOrThrow(Registries.PROCESSOR_LIST).getValue(type.farmProcessors()).list())));
		}
		VillageStructures.apply(access);
		for (VillageType type : VillageType.values()) {
			List<Object> after = List.of(List.copyOf(access.lookupOrThrow(Registries.TEMPLATE_POOL).getValue(type.housesPool()).rawTemplates),
				access.lookupOrThrow(Registries.TEMPLATE_POOL).getValue(type.housesPool()).templates.size(),
				List.copyOf(access.lookupOrThrow(Registries.PROCESSOR_LIST).getValue(type.farmProcessors()).list()));
			helper.assertValueEqual(after, before.get(type), type.id() + " pool and farm processors after applying again");
		}
		helper.succeed();
	}

	/**
	 * Each tavern is in its village's house pool once, with its weight, rigid like the vanilla houses; its template loads.
	 * Checks the pools as the server start left them: nothing here adds the taverns itself.
	 */
	private static void tavernsInPools(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (VillageType type : VillageType.values()) {
			StructureTemplatePool pool = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getValue(type.housesPool());
			helper.assertTrue(pool != null, "pool " + type.housesPool());
			long entries = pool.rawTemplates.stream().filter(e -> VillageStructures.isTemplate(e.getFirst(), type.tavern())).count();
			helper.assertValueEqual((int) entries, 1, "tavern entries in " + type.housesPool());
			int weight = pool.rawTemplates.stream().filter(e -> VillageStructures.isTemplate(e.getFirst(), type.tavern())).mapToInt(e -> e.getSecond()).sum();
			helper.assertValueEqual(weight, VillageStructures.TAVERN_WEIGHT, type.id() + " tavern weight");
			long picks = pool.templates.stream().filter(e -> VillageStructures.isTemplate(e, type.tavern())).count();
			helper.assertValueEqual((int) picks, VillageStructures.TAVERN_WEIGHT, type.id() + " tavern picks in the pool's draw list");
			helper.assertTrue(tavernElement(helper, type).getProjection() == StructureTemplatePool.Projection.RIGID, "rigid like the vanilla houses");
			StructureTemplate template = level.getStructureTemplateManager().get(type.tavern()).orElse(null);
			helper.assertTrue(template != null, "the " + type.id() + " tavern template loads");
			helper.assertValueEqual(template.getSize(), new net.minecraft.core.Vec3i(11, 11, 11), type.id() + " tavern size");
		}
		helper.succeed();
	}

	/**
	 * Placed the way a village places it (the pool element with its processors, jigsaws replaced), a tavern has its
	 * kettle on a lit campfire, a chest with its tavern loot, drinks on the tables and the bar, its hanging sign, and
	 * four filled barrels (W1), one of them aged, that pour real drinks into a player's mug or glass. Its floor is flush
	 * with the path: the street joint sits at the door's height, one above the floor.
	 */
	private static void tavernPlacesAndTaps(GameTestHelper helper, VillageType type) {
		ServerLevel level = helper.getLevel();
		SinglePoolElement element = tavernElement(helper, type);
		StructureTemplate template = level.getStructureTemplateManager().get(type.tavern()).orElseThrow();
		BlockPos entrance = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW).stream()
			.filter(info -> info.nbt() != null && "minecraft:building_entrance".equals(info.nbt().getStringOr("name", ""))).findFirst().orElseThrow().pos();
		BlockPos origin = helper.absolutePos(TAVERN_AT);
		boolean placed = element.place(level.getStructureTemplateManager(), level, level.structureManager(), level.getChunkSource().getGenerator(),
			origin, origin, Rotation.NONE, BoundingBox.fromCorners(origin.offset(-2, -2, -2), origin.offset(14, 13, 14)), RandomSource.create(5L),
			LiquidSettings.APPLY_WATERLOGGING, false);
		helper.assertTrue(placed, "the " + type.id() + " tavern places");
		BlockState lowerDoor = level.getBlockState(at(helper, 5, 2, 1));
		helper.assertTrue(lowerDoor.getBlock() instanceof DoorBlock && entrance.getY() == 2 && entrance.getZ() == 0,
			"the street joint at the door's height (" + entrance + ", door " + lowerDoor + ")");

		Map<Block, Integer> counts = new HashMap<>();
		BlockPos.betweenClosedStream(BoundingBox.fromCorners(origin, origin.offset(10, 10, 10)))
			.forEach(pos -> counts.merge(level.getBlockState(pos).getBlock(), 1, Integer::sum));
		helper.assertValueEqual(counts.getOrDefault(Blocks.JIGSAW, 0), 0, "jigsaws left in the tavern");
		helper.assertValueEqual(counts.getOrDefault(BarrelRegistry.AGING_BARREL.get(), 0), 4, "aging barrels");
		helper.assertValueEqual(counts.getOrDefault(BarrelRegistry.PLACED_DRINKS.get(), 0), 4, "placed drinks blocks");
		helper.assertValueEqual(counts.getOrDefault(Blocks.CHEST, 0), 1, "chests");
		helper.assertValueEqual(counts.getOrDefault(Blocks.CAMPFIRE, 0), 1, "campfires");
		helper.assertTrue(level.getBlockState(at(helper, 6, 4, 0)).getBlock() instanceof WallHangingSignBlock, "the hanging sign");
		helper.assertTrue(level.getBlockState(at(helper, 5, 1, 4)).equals(level.getBlockState(at(helper, 5, 1, 5))), "the villager jigsaw left floor like its neighbours");

		// the kettle sits on its tray over a lit campfire
		BlockState kettle = level.getBlockState(at(helper, 1, 3, 8));
		helper.assertTrue(kettle.getBlock() instanceof BrewKettleBlock && kettle.getValue(BrewKettleBlock.SUPPORT) == KettleSupport.TRAY, "kettle on a tray: " + kettle);
		BlockState campfire = level.getBlockState(at(helper, 1, 2, 8));
		helper.assertTrue(campfire.is(Blocks.CAMPFIRE) && campfire.getValue(CampfireBlock.LIT), "lit campfire under the kettle: " + campfire);

		// the chest holds the tavern loot
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(at(helper, 8, 2, 8));
		helper.assertTrue(chest != null && type.tavernLoot().equals(chest.getLootTable()), type.id() + " tavern chest loot table");
		chest.unpackLootTable(null);
		boolean vessel = false;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			vessel |= chest.getItem(i).is(DrinkItems.MUG.get()) || chest.getItem(i).is(DrinkItems.WINE_GLASS.get());
		}
		helper.assertTrue(vessel, "a mug or glass in the tavern chest");

		// the sign, and drinks on every placed-drinks block, each spot showing what stands there
		SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(at(helper, 6, 4, 0));
		helper.assertTrue(sign != null && sign.getText(SignTextSlot.FRONT).getMessages(false).stream().anyMatch(c -> c.getString().equals("Tavern")),
			"the sign says Tavern");
		for (BlockPos pos : List.of(at(helper, 2, 3, 3), at(helper, 8, 3, 3), at(helper, 3, 3, 6), at(helper, 7, 3, 6))) {
			PlacedDrinksBlockEntity drinks = (PlacedDrinksBlockEntity) level.getBlockEntity(pos);
			helper.assertTrue(drinks != null && drinks.count() > 0, "drinks at " + pos);
			for (int spot = 0; spot < 4; spot++) {
				PlacedDrink shown = level.getBlockState(pos).getValue(PlacedDrinksBlock.SPOT.get(spot));
				PlacedDrink held = drinks.getItem(spot).isEmpty() ? PlacedDrink.NONE : PlacedDrink.of(drinks.getItem(spot));
				helper.assertValueEqual(shown, held, "what spot " + spot + " at " + pos + " shows");
			}
		}

		// W1: after a tick the barrels know their age; tap each by hand
		List<BlockPos> barrels = List.of(TAVERN_AT.offset(3, 2, 8), TAVERN_AT.offset(4, 2, 8), TAVERN_AT.offset(5, 2, 8), TAVERN_AT.offset(4, 3, 8));
		helper.runAfterDelay(3, () -> {
			int aged = 0;
			for (BlockPos barrel : barrels) {
				aged += tapsByHand(helper, barrel) ? 1 : 0;
			}
			helper.assertValueEqual(aged, 1, "aged barrels in the " + type.id() + " tavern");
			BlockState rack = level.getBlockState(at(helper, 4, 2, 8));
			helper.assertTrue(rack.getValue(AgingBarrelBlock.LEFT) && rack.getValue(AgingBarrelBlock.RIGHT), "the middle barrel joins the rack");
			helper.assertTrue(level.getBlockState(at(helper, 4, 3, 8)).getValue(AgingBarrelBlock.STACKED), "the top barrel rests on chocks");
			// they keep aging from where the template left them: the saved age is a start, not a frozen value
			Map<BlockPos, Long> ages = new HashMap<>();
			Map<BlockPos, Long> toNext = new HashMap<>();
			for (BlockPos barrel : barrels) {
				AgingBarrelBlockEntity be = helper.getBlockEntity(barrel, AgingBarrelBlockEntity.class);
				ages.put(barrel, be.age());
				toNext.put(barrel, be.ticksToNextStage());
			}
			long start = level.getGameTime();
			helper.runAfterDelay(20, () -> {
				long waited = level.getGameTime() - start;
				for (BlockPos barrel : barrels) {
					AgingBarrelBlockEntity be = helper.getBlockEntity(barrel, AgingBarrelBlockEntity.class);
					helper.assertTrue(be.age() >= ages.get(barrel) + waited, "the barrel at " + barrel + " aged " + (be.age() - ages.get(barrel))
						+ " ticks in " + waited);
					if (be.stage() == BarrelStage.YOUNG) {
						helper.assertTrue(be.ticksToNextStage() <= toNext.get(barrel) - waited, "the young barrel at " + barrel + " comes closer to aged");
					}
				}
				helper.succeed();
			});
		});
	}

	/**
	 * Placed into solid stone (a village on rising ground), a tavern clears its own room: every block the template
	 * leaves as air is air, the doorway and the space in front of the door are open, the floor is walkable.
	 */
	private static void tavernClearsTerrain(GameTestHelper helper, VillageType type) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(TAVERN_AT);
		for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(10, 10, 10))) {
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		SinglePoolElement element = tavernElement(helper, type);
		helper.assertTrue(element.place(level.getStructureTemplateManager(), level, level.structureManager(), level.getChunkSource().getGenerator(),
			origin, origin, Rotation.NONE, BoundingBox.fromCorners(origin.offset(-2, -2, -2), origin.offset(14, 13, 14)), RandomSource.create(5L),
			LiquidSettings.APPLY_WATERLOGGING, false), "the " + type.id() + " tavern places");
		StructureTemplate template = level.getStructureTemplateManager().get(type.tavern()).orElseThrow();
		List<StructureTemplate.StructureBlockInfo> air = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.AIR);
		helper.assertTrue(air.size() > 200, "the " + type.id() + " template keeps its air: " + air.size());
		for (StructureTemplate.StructureBlockInfo info : air) {
			helper.assertTrue(level.getBlockState(origin.offset(info.pos())).isAir(), "stone left at " + info.pos() + " in the " + type.id() + " tavern");
		}
		// the room people walk in, and the doorway with the step in front of it
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(1, 2, 2), origin.offset(9, 4, 5))) {
			BlockState state = level.getBlockState(pos);
			helper.assertTrue(!state.is(Blocks.STONE), "stone in the " + type.id() + " tavern's room at " + pos.subtract(origin));
		}
		for (int y = 2; y <= 3; y++) {
			helper.assertTrue(level.getBlockState(origin.offset(5, y, 0)).isAir(), "the way in at y " + y + " in front of the " + type.id() + " tavern");
			helper.assertTrue(level.getBlockState(origin.offset(5, y, 1)).getBlock() instanceof DoorBlock, "the " + type.id() + " tavern's door");
			helper.assertTrue(level.getBlockState(origin.offset(5, y, 2)).isAir(), "inside the " + type.id() + " tavern's door at y " + y);
		}
		helper.succeed();
	}

	private static BlockPos at(GameTestHelper helper, int x, int y, int z) {
		return helper.absolutePos(TAVERN_AT.offset(x, y, z));
	}

	/** Taps one serving into the vessel the barrel's drink needs, as a player would. Returns whether it was aged. */
	private static boolean tapsByHand(GameTestHelper helper, BlockPos relative) {
		AgingBarrelBlockEntity barrel = helper.getBlockEntity(relative, AgingBarrelBlockEntity.class);
		int servings = barrel.servings();
		BarrelStage stage = barrel.stage();
		ItemStack drink = barrel.drink();
		helper.assertTrue(servings >= 2, "a few servings in the barrel at " + relative + ": " + servings);
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
