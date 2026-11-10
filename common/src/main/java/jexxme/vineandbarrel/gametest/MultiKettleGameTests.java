package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.BrewKettleBlock;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.BrewKettleItem;
import jexxme.vineandbarrel.kettle.BrewKettlePartBlock;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.kettle.KettleChimneyBlock;
import jexxme.vineandbarrel.kettle.KettleContents;
import jexxme.vineandbarrel.kettle.KettleMultiblock;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.kettle.KettleShape;
import jexxme.vineandbarrel.kettle.KettleSupport;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * WP4: the Big Kettle (the Great Kettle is dormant: 27 kettles make no Great Kettle). Joining by placing a cube (and not when sneaking, or with different brews), bigger
 * batches and the per-fire speed, splitting with the brew shared out, use from any block, one copper vessel, the
 * sight glass, the copper-ingot chimney, more than 99 servings saved, and a kettle saved by 1.0 loading unchanged.
 */
public final class MultiKettleGameTests
{
	private static final ResourceLocation ROOM = VineAndBarrel.id("gametest/empty_16x14x16");
	private static final List<Entry> TESTS = new ArrayList<>();

	private record Entry(String name, int timeout, ResourceLocation structure, Consumer<GameTestHelper> function) {
	}

	/** The kettles' bottom corner: fires go one block below. */
	private static final BlockPos BASE = new BlockPos(3, 2, 3);

	static {
		test("multikettle_joins_big_cube", 100, MultiKettleGameTests::joinsBigCube);
		test("multikettle_joins_when_sneaking", 100, MultiKettleGameTests::joinsWhenSneaking);
		test("multikettle_27_kettles_make_no_great_kettle", 100, MultiKettleGameTests::noGreatKettle);
		test("multikettle_different_brews_dont_join", 100, MultiKettleGameTests::differentBrewsDontJoin);
		test("multikettle_join_pools_the_brew", 100, MultiKettleGameTests::joinPoolsTheBrew);
		test("multikettle_big_batch_on_four_fires", 600, MultiKettleGameTests::bigBatchOnFourFires);
		test("multikettle_one_fire_is_slower", 600, MultiKettleGameTests::oneFireIsSlower);
		test("multikettle_break_shares_the_brew", 100, MultiKettleGameTests::breakSharesTheBrew);
		test("multikettle_removed_part_keeps_the_brew", 100, MultiKettleGameTests::removedPartKeepsTheBrew);
		test("multikettle_serves_from_any_block", 100, MultiKettleGameTests::servesFromAnyBlock);
		test("multikettle_hopper_feeds_any_block", 400, MultiKettleGameTests::hopperFeedsAnyBlock);
		test("multikettle_waxes_and_scrapes_as_one", 100, MultiKettleGameTests::waxesAndScrapesAsOne);
		test("multikettle_weathers_as_one", 100, MultiKettleGameTests::weathersAsOne);
		test("multikettle_sight_glass_and_capacity", 100, MultiKettleGameTests::sightGlassAndCapacity);
		test("multikettle_saves_more_than_a_stack", 100, MultiKettleGameTests::savesMoreThanAStack);
		test("kettle_chimney_grows_with_copper", 100, MultiKettleGameTests::chimneyGrowsWithCopper);
		test("kettle_chimney_falls_and_drops_ingots", 100, MultiKettleGameTests::chimneyFallsAndDropsIngots);
		test("kettle_chimney_weathers_with_the_kettle", 100, MultiKettleGameTests::chimneyWeathersWithKettle);
		test("kettle_chimney_on_big_kettle", 100, MultiKettleGameTests::chimneyOnBigKettle);
		test("kettle_chimney_breaks_through_the_roof", 100, MultiKettleGameTests::chimneyBreaksThroughRoof);
		test("multikettle_breaking_the_controller_splits", 100, MultiKettleGameTests::breakingTheControllerSplits);
		test("multikettle_removed_controller_keeps_the_brew", 100, MultiKettleGameTests::removedControllerKeepsTheBrew);
		test("multikettle_copper_change_on_one_part_turns_all", 100, MultiKettleGameTests::copperChangeOnOnePartTurnsAll);
		test("kettle_chimney_stops_at_obsidian", 100, MultiKettleGameTests::chimneyStopsAtObsidian);
		test("kettle_chimney_keeps_a_trellis", 100, MultiKettleGameTests::chimneyKeepsTrellis);
		test("kettle_chimney_through_a_roof_in_one_click", 100, MultiKettleGameTests::chimneyThroughRoofInOneClick);
		test("kettle_chimney_through_stone_drops_it", 100, MultiKettleGameTests::chimneyThroughStoneDrops);
		test("multikettle_removed_full_kettle_never_overfills", 100, MultiKettleGameTests::removedFullKettleNeverOverfills);
		test("kettle_chimney_grows_from_the_chimney", 100, MultiKettleGameTests::chimneyGrowsFromChimney);
		test("kettle_sight_glass_shows_the_brew", 100, MultiKettleGameTests::smallSightGlass);
		test("kettle_saved_by_1_0_loads_unchanged", 100, MultiKettleGameTests::savedBy10LoadsUnchanged);
	}

	private MultiKettleGameTests() {
	}

	private static void test(String name, int timeout, Consumer<GameTestHelper> function) {
		test(name, timeout, ROOM, function);
	}

	private static void test(String name, int timeout, ResourceLocation structure, Consumer<GameTestHelper> function) {
		TESTS.add(new Entry(name, timeout, structure, function));
	}

	public static void register() {
		jexxme.vineandbarrel.platform.Services.PLATFORM.addGameTests(MultiKettleGameTests::registerTests);
	}

	private static void registerTests(GameTestRegistrar event) {
		for (Entry test : TESTS) {
			VBGameTests.add(event, "multikettle", test.name(), test.structure(), test.timeout(), test.function());
		}
	}

	// ------------------------------------------------------------------ helpers

	private static void fires(GameTestHelper helper, int size, int lit) {
		int n = 0;
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				BlockPos at = BASE.offset(x, -1, z);
				helper.setBlock(at, n++ < lit ? Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true) : Blocks.STONE.defaultBlockState());
			}
		}
	}

	private static Player playerHolding(GameTestHelper helper, ItemStack stack) {
		Player player = VBGameTests.makeMockPlayer(helper, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		return player;
	}

	/** A player places a kettle at {@code pos} (facing north, as a player looking south places it). */
	private static void place(GameTestHelper helper, BlockPos pos, boolean sneaking) {
		ItemStack kettle = new ItemStack(KettleRegistry.kettleItem());
		Player player = playerHolding(helper, kettle);
		player.setShiftKeyDown(sneaking);
		helper.placeAt(player, kettle, pos.below(), Direction.UP);
	}

	/** Places a cube of kettles block by block, the last one completing it. */
	private static void placeCube(GameTestHelper helper, int size) {
		for (int y = 0; y < size; y++) {
			for (int z = 0; z < size; z++) {
				for (int x = 0; x < size; x++) {
					place(helper, BASE.offset(x, y, z), false);
				}
			}
		}
	}

	/** A big kettle of {@code shape} put down directly (no placing), over {@code lit} fires. */
	private static BrewKettleBlockEntity bigKettle(GameTestHelper helper, KettleShape shape, int lit) {
		fires(helper, shape.size(), lit);
		for (int y = 0; y < shape.size(); y++) {
			for (int z = 0; z < shape.size(); z++) {
				for (int x = 0; x < shape.size(); x++) {
					helper.setBlock(BASE.offset(x, y, z), KettleRegistry.kettle());
				}
			}
		}
		KettleMultiblock.tryJoin(helper.getLevel(), helper.absolutePos(BASE));
		BrewKettleBlockEntity kettle = controller(helper);
		VBGameTests.assertValueEqual(helper, kettle.shape(), shape, "kettle shape");
		return kettle;
	}

	private static BrewKettleBlockEntity controller(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = KettleMultiblock.controller(helper.getLevel(), helper.absolutePos(BASE));
		helper.assertTrue(kettle != null, "a kettle at the base");
		return kettle;
	}

	private static void fillBrew(GameTestHelper helper, BrewKettleBlockEntity kettle, int servings) {
		ItemStack item = new ItemStack(KettleRegistry.kettleItem());
		// a kettle item's brew is an item stack saved in its tag (a count is a byte there): a Big Kettle's larger brew is
		// topped up in the kettle itself, where the count is only an int (as it also is in a component on 1.21.1)
		int inItem = Math.min(servings, BrewKettleBlockEntity.MAX_SERVINGS);
		BrewKettleItem.setContents(item, new KettleContents(new ItemStack(BaseItems.SWEET_BERRY_JUICE.get(), inItem),
			new ItemStack(BaseItems.SWEET_BERRY_JUICE_BUCKET.get())));
		applyItem(helper, kettle, item);
		if (servings > inItem) {
			ItemStack brew = kettle.getBrew();
			brew.setCount(servings);
			kettle.setStack(BrewKettleBlockEntity.BREW_SLOT, brew);
		}
	}

	/**
	 * The kettle item's name and brew go into the kettle (1.21.1: {@code kettle.applyComponentsFromItemStack(item)}).
	 * Asked of the block entity itself, as on 1.21.1: a Big Kettle's blocks are part blocks, which no item places.
	 */
	private static void applyItem(GameTestHelper helper, BrewKettleBlockEntity kettle, ItemStack item) {
		kettle.readItem(item);
	}

	private static void assertAllParts(GameTestHelper helper, int size, boolean parts) {
		for (int y = 0; y < size; y++) {
			for (int z = 0; z < size; z++) {
				for (int x = 0; x < size; x++) {
					Block block = helper.getBlockState(BASE.offset(x, y, z)).getBlock();
					helper.assertTrue(parts ? block instanceof BrewKettlePartBlock : block instanceof BrewKettleBlock,
						(parts ? "a part of the big kettle" : "a single kettle") + " at " + x + "," + y + "," + z);
				}
			}
		}
	}

	private static int servingsAround(GameTestHelper helper, int size) {
		int total = 0;
		for (int y = 0; y < size; y++) {
			for (int z = 0; z < size; z++) {
				for (int x = 0; x < size; x++) {
					if (helper.getLevel().getBlockEntity(helper.absolutePos(BASE.offset(x, y, z))) instanceof BrewKettleBlockEntity kettle) {
						total += kettle.getServings();
					}
				}
			}
		}
		return total;
	}

	private static int droppedServings(GameTestHelper helper) {
		int total = 0;
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BASE)).inflate(6))) {
			total += BrewKettleItem.contentsOf(item.getItem()).servings();
		}
		return total;
	}

	private static int dropped(GameTestHelper helper, Item item) {
		int total = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BASE)).inflate(12))) {
			if (entity.getItem().is(item)) total += entity.getItem().getCount();
		}
		return total;
	}

	// ------------------------------------------------------------------ joining

	private static void joinsBigCube(GameTestHelper helper) {
		fires(helper, 2, 4);
		for (int i = 0; i < 7; i++) {
			place(helper, BASE.offset(i & 1, (i >> 2) & 1, (i >> 1) & 1), false);
		}
		helper.assertTrue(helper.getBlockState(BASE).getBlock() instanceof BrewKettleBlock, "seven kettles stay single");
		place(helper, BASE.offset(1, 1, 1), false);
		helper.runAfterDelay(2, () -> {
			assertAllParts(helper, 2, true);
			VBGameTests.assertValueEqual(helper, controller(helper).shape(), KettleShape.BIG, "the eighth kettle completes a Big Kettle");
			VBGameTests.assertValueEqual(helper, controller(helper).capacity(), 8 * BrewKettleBlockEntity.MAX_SERVINGS, "a Big Kettle holds 64 per block");
			helper.succeed();
		});
	}

	/** Placing against a kettle needs sneaking (else its screen opens), so the cube joins all the same. */
	private static void joinsWhenSneaking(GameTestHelper helper) {
		fires(helper, 2, 4);
		for (int i = 0; i < 7; i++) {
			place(helper, BASE.offset(i & 1, (i >> 2) & 1, (i >> 1) & 1), true);
		}
		helper.assertTrue(helper.getBlockState(BASE).getBlock() instanceof BrewKettleBlock, "seven stay single");
		place(helper, BASE.offset(1, 1, 1), true);
		helper.runAfterDelay(2, () -> {
			assertAllParts(helper, 2, true);
			helper.succeed();
		});
	}

	/** The Great Kettle is dormant: a 3x3x3 cube of kettles makes Big Kettles at most, never a Great one. */
	private static void noGreatKettle(GameTestHelper helper) {
		fires(helper, 3, 9);
		placeCube(helper, 3);
		helper.runAfterDelay(2, () -> {
			for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) for (int x = 0; x < 3; x++) {
				BlockState state = helper.getBlockState(BASE.offset(x, y, z));
				helper.assertTrue(!(state.getBlock() instanceof BrewKettlePartBlock) || state.getValue(BrewKettlePartBlock.PART) < 8,
					"no Great Kettle part at " + x + "," + y + "," + z);
			}
			helper.succeed();
		});
	}

	private static void differentBrewsDontJoin(GameTestHelper helper) {
		fires(helper, 2, 4);
		for (int i = 0; i < 7; i++) {
			place(helper, BASE.offset(i & 1, (i >> 2) & 1, (i >> 1) & 1), false);
		}
		fillBrew(helper, VBGameTests.blockEntity(helper, BASE, BrewKettleBlockEntity.class), 4);
		ItemStack item = new ItemStack(KettleRegistry.kettleItem());
		BrewKettleItem.setContents(item, new KettleContents(new ItemStack(BaseItems.GRAPE_JUICE.get(), 4), new ItemStack(BaseItems.GRAPE_JUICE_BUCKET.get())));
		applyItem(helper, VBGameTests.blockEntity(helper, BASE.east(), BrewKettleBlockEntity.class), item);
		place(helper, BASE.offset(1, 1, 1), false);
		helper.runAfterDelay(2, () -> {
			assertAllParts(helper, 2, false);
			helper.succeed();
		});
	}

	private static void joinPoolsTheBrew(GameTestHelper helper) {
		fires(helper, 2, 4);
		for (int i = 0; i < 7; i++) {
			place(helper, BASE.offset(i & 1, (i >> 2) & 1, (i >> 1) & 1), false);
		}
		fillBrew(helper, VBGameTests.blockEntity(helper, BASE, BrewKettleBlockEntity.class), 5);
		fillBrew(helper, VBGameTests.blockEntity(helper, BASE.east(), BrewKettleBlockEntity.class), 3);
		VBGameTests.blockEntity(helper, BASE.south(), BrewKettleBlockEntity.class).setStack(0, new ItemStack(Items.SWEET_BERRIES, 10));
		VBGameTests.blockEntity(helper, BASE.south().east(), BrewKettleBlockEntity.class).setStack(2, new ItemStack(Items.SWEET_BERRIES, 6));
		place(helper, BASE.offset(1, 1, 1), false);
		helper.runAfterDelay(2, () -> joinPoolsTheBrewAfter(helper));
	}

	private static void joinPoolsTheBrewAfter(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = controller(helper);
		VBGameTests.assertValueEqual(helper, kettle.getServings(), 8, "the brews pooled");
		int berries = 0;
		for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
			if (kettle.stackIn(i).is(Items.SWEET_BERRIES)) berries += kettle.stackIn(i).getCount();
		}
		VBGameTests.assertValueEqual(helper, berries, 16, "the ingredients pooled into one grid");
		helper.succeed();
	}

	// ------------------------------------------------------------------ brewing: bigger batches, one fire per block's share

	private static void bigBatchOnFourFires(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = bigKettle(helper, KettleShape.BIG, 4);
		kettle.setStack(0, new ItemStack(Items.SWEET_BERRIES, 32));
		kettle.setStack(1, new ItemStack(Items.SUGAR, 8));
		// 8 sets of 200 ticks, 4 fires boil 4 sets at once: 400 ticks
		helper.runAtTickTime(350, () -> VBGameTests.assertValueEqual(helper, controller(helper).getServings(), 0, "not done after 350 ticks"));
		helper.succeedWhen(() -> {
			VBGameTests.assertValueEqual(helper, controller(helper).getServings(), 32, "one batch of a Big Kettle: 32 servings");
			helper.assertTrue(controller(helper).stackIn(0).isEmpty() && controller(helper).stackIn(1).isEmpty(), "all 8 sets taken");
		});
	}

	private static void oneFireIsSlower(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = bigKettle(helper, KettleShape.BIG, 1);
		kettle.setStack(0, new ItemStack(Items.SWEET_BERRIES, 8));
		kettle.setStack(1, new ItemStack(Items.SUGAR, 2));
		// a partial batch of 2 sets on one fire: 400 ticks (on four fires it would be 200)
		helper.runAtTickTime(300, () -> VBGameTests.assertValueEqual(helper, controller(helper).getServings(), 0, "one fire is slower"));
		helper.succeedWhen(() -> VBGameTests.assertValueEqual(helper, controller(helper).getServings(), 8, "a partial batch: 2 sets, 8 servings"));
	}

	// ------------------------------------------------------------------ breaking

	private static void breakSharesTheBrew(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = bigKettle(helper, KettleShape.BIG, 4);
		fillBrew(helper, kettle, 32);
		ServerPlayer player = VBGameTests.mockServerPlayerInLevel(helper);
		player.setGameMode(GameType.SURVIVAL);
		player.gameMode.destroyBlock(helper.absolutePos(BASE.offset(1, 1, 1)));
		helper.assertTrue(helper.getBlockState(BASE).getBlock() instanceof BrewKettleBlock, "the rest fell apart into single kettles");
		VBGameTests.assertValueEqual(helper, servingsAround(helper, 2), 28, "seven kettles keep seven shares");
		helper.succeedWhen(() -> VBGameTests.assertValueEqual(helper, droppedServings(helper), 4, "the broken block drops its share"));
	}

	/** A player breaks the controller block itself: no crash, one share drops, the rest keep theirs, no XP twice. */
	private static void breakingTheControllerSplits(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = bigKettle(helper, KettleShape.BIG, 4);
		fillBrew(helper, kettle, 32);
		ServerPlayer player = VBGameTests.mockServerPlayerInLevel(helper);
		player.setGameMode(GameType.SURVIVAL);
		player.gameMode.destroyBlock(helper.absolutePos(BASE));
		helper.assertTrue(helper.getBlockState(BASE.offset(1, 1, 1)).getBlock() instanceof BrewKettleBlock, "the rest fell apart into single kettles");
		VBGameTests.assertValueEqual(helper, servingsAround(helper, 2), 28, "seven kettles keep seven shares");
		helper.succeedWhen(() -> VBGameTests.assertValueEqual(helper, droppedServings(helper), 4, "the controller drops its share"));
	}

	/** The controller removed some other way (a command, an explosion): the rest keep the whole brew. */
	private static void removedControllerKeepsTheBrew(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = bigKettle(helper, KettleShape.BIG, 4);
		fillBrew(helper, kettle, 32);
		helper.setBlock(BASE, Blocks.AIR);
		helper.assertTrue(helper.getBlockState(BASE.offset(1, 1, 1)).getBlock() instanceof BrewKettleBlock, "the rest fell apart into single kettles");
		VBGameTests.assertValueEqual(helper, servingsAround(helper, 2), 32, "nothing lost");
		helper.succeed();
	}

	/** Vanilla's honeycomb (or another mod) changes one block's copper: the kettle stays whole, all of it turns. */
	private static void copperChangeOnOnePartTurnsAll(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = bigKettle(helper, KettleShape.BIG, 4);
		fillBrew(helper, kettle, 16);
		BlockPos at = BASE.offset(1, 1, 1);
		BlockState state = helper.getBlockState(at);
		helper.setBlock(at, KettleRegistry.partOf(KettleRegistry.waxedOf(KettleRegistry.singleOf(state.getBlock()))).withPropertiesOf(state));
		assertAllParts(helper, 2, true);
		VBGameTests.assertValueEqual(helper, controller(helper).getServings(), 16, "the brew stayed");
		helper.succeed();
	}

	private static void removedPartKeepsTheBrew(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = bigKettle(helper, KettleShape.BIG, 4);
		fillBrew(helper, kettle, 32);
		helper.setBlock(BASE.offset(1, 1, 1), Blocks.AIR);
		helper.assertTrue(helper.getBlockState(BASE).getBlock() instanceof BrewKettleBlock, "the rest fell apart into single kettles");
		VBGameTests.assertValueEqual(helper, servingsAround(helper, 2), 32, "nothing lost when a block is removed some other way");
		helper.setBlock(BASE, Blocks.AIR);
		VBGameTests.assertValueEqual(helper, servingsAround(helper, 2), 32 - 5, "a single kettle removed takes its own share, as in 1.0");
		helper.succeed();
	}

	// ------------------------------------------------------------------ use from any block

	private static void servesFromAnyBlock(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = bigKettle(helper, KettleShape.BIG, 4);
		fillBrew(helper, kettle, 5);
		Player player = playerHolding(helper, new ItemStack(Items.GLASS_BOTTLE));
		helper.useBlock(BASE.offset(1, 1, 1), player);
		helper.assertTrue(player.getMainHandItem().is(BaseItems.SWEET_BERRY_JUICE.get()), "a bottle filled from the top corner");
		VBGameTests.assertValueEqual(helper, controller(helper).getServings(), 4, "taken from the kettle's brew");
		helper.succeed();
	}

	private static void hopperFeedsAnyBlock(GameTestHelper helper) {
		bigKettle(helper, KettleShape.BIG, 4);
		BlockPos hopper = BASE.offset(1, 2, 1);
		helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		VBGameTests.blockEntity(helper, hopper, HopperBlockEntity.class).setItem(0, new ItemStack(Items.SWEET_BERRIES, 4));
		VBGameTests.blockEntity(helper, hopper, HopperBlockEntity.class).setItem(1, new ItemStack(Items.SUGAR, 1));
		helper.succeedWhen(() -> {
			int berries = 0;
			for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
				if (controller(helper).stackIn(i).is(Items.SWEET_BERRIES)) berries += controller(helper).stackIn(i).getCount();
			}
			helper.assertTrue(berries == 4 || controller(helper).getServings() > 0, "a hopper on a top corner feeds the kettle");
		});
	}

	// ------------------------------------------------------------------ one copper vessel

	private static void waxesAndScrapesAsOne(GameTestHelper helper) {
		bigKettle(helper, KettleShape.BIG, 4);
		Player player = playerHolding(helper, new ItemStack(Items.HONEYCOMB));
		helper.useBlock(BASE.offset(1, 1, 0), player);
		for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) {
			helper.assertTrue(!(helper.getBlockState(BASE.offset(x, y, z)).getBlock() instanceof jexxme.vineandbarrel.kettle.WeatheringBrewKettlePartBlock), "every block waxed by one honeycomb");
		}
		Player axe = playerHolding(helper, new ItemStack(Items.IRON_AXE));
		helper.useBlock(BASE.offset(0, 1, 1), axe);
		for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) {
			helper.assertTrue(helper.getBlockState(BASE.offset(x, y, z)).getBlock() instanceof jexxme.vineandbarrel.kettle.WeatheringBrewKettlePartBlock, "every block unwaxed by one scrape");
		}
		VBGameTests.assertValueEqual(helper, controller(helper).shape(), KettleShape.BIG, "still one kettle");
		helper.succeed();
	}

	private static void weathersAsOne(GameTestHelper helper) {
		bigKettle(helper, KettleShape.BIG, 4);
		for (int i = 0; i < 2000 && helper.getBlockState(BASE).getBlock() == KettleRegistry.PARTS.get(WeatherState.UNAFFECTED).get(); i++) {
			helper.randomTick(BASE);
		}
		Block exposed = KettleRegistry.PARTS.get(WeatherState.EXPOSED).get();
		for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) {
			VBGameTests.assertValueEqual(helper, helper.getBlockState(BASE.offset(x, y, z)).getBlock(), exposed, "every block weathered together");
		}
		helper.assertTrue(!helper.getBlockState(BASE.east()).isRandomlyTicking(), "only the controller takes random ticks");
		helper.succeed();
	}

	// ------------------------------------------------------------------ sight glass, capacity, saving

	private static void sightGlassAndCapacity(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = bigKettle(helper, KettleShape.BIG, 0);
		fillBrew(helper, kettle, 256);
		helper.succeedWhen(() -> {
			BlockPos gauge = KettleMultiblock.kettleAt(helper.getLevel(), helper.absolutePos(BASE)).gauge();
			VBGameTests.assertValueEqual(helper, helper.getLevel().getBlockState(gauge).getValue(BrewKettlePartBlock.LEVEL), 2, "half full shows half the glass");
			VBGameTests.assertValueEqual(helper, BrewKettleBlockEntity.gaugeLevel(1, 512), 1, "any brew shows in the glass");
			VBGameTests.assertValueEqual(helper, BrewKettleBlockEntity.gaugeLevel(512, 512), BrewKettlePartBlock.MAX_LEVEL, "a full kettle fills the glass");
		});
	}

	private static void savesMoreThanAStack(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = bigKettle(helper, KettleShape.BIG, 0);
		fillBrew(helper, kettle, 64);
		// pour in more than an item stack can save
		for (int i = 0; i < 4; i++) {
			ItemStack brew = kettle.getBrew();
			brew.grow(64);
			kettle.setStack(BrewKettleBlockEntity.BREW_SLOT, brew);
		}
		VBGameTests.assertValueEqual(helper, kettle.getServings(), 320, "a Big Kettle holds 320");
		var tag = kettle.saveWithFullMetadata();
		kettle.clearContent();
		kettle.load(tag);
		VBGameTests.assertValueEqual(helper, kettle.getServings(), 320, "320 servings after saving and loading");
		helper.assertTrue(kettle.getBucketForm().is(BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), "bucket form after loading");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the copper-ingot chimney

	private static void chimneyGrowsWithCopper(GameTestHelper helper) {
		fires(helper, 1, 1);
		place(helper, BASE, false);
		Player player = playerHolding(helper, new ItemStack(Items.COPPER_INGOT, 12));
		for (int i = 0; i < 3; i++) {
			helper.useBlock(BASE, player);
		}
		helper.assertTrue(helper.getBlockState(BASE).getValue(BrewKettleBlock.EXTENDED), "the kettle's chimney is extended");
		for (int i = 1; i <= 3; i++) {
			BlockState piece = helper.getBlockState(BASE.above(i));
			helper.assertTrue(piece.getBlock() instanceof KettleChimneyBlock, "a chimney piece at " + i);
			VBGameTests.assertValueEqual(helper, piece.getValue(KettleChimneyBlock.TOP), i == 3, "only the top piece has the cap");
		}
		VBGameTests.assertValueEqual(helper, player.getMainHandItem().getCount(), 9, "three ingots used");
		for (int i = 0; i < 9; i++) {
			helper.useBlock(BASE, player);
		}
		helper.assertTrue(helper.getBlockState(BASE.above(KettleChimneyBlock.MAX)).getBlock() instanceof KettleChimneyBlock, "eight pieces high");
		helper.assertTrue(helper.getBlockState(BASE.above(KettleChimneyBlock.MAX + 1)).isAir(), "never more than eight");
		VBGameTests.assertValueEqual(helper, player.getMainHandItem().getCount(), 4, "the ninth ingot isn't taken");
		// a hanging kettle can't be extended, and an extended one can't be hung
		Player sneaking = playerHolding(helper, ItemStack.EMPTY);
		sneaking.setShiftKeyDown(true);
		helper.useBlock(BASE, sneaking);
		helper.assertTrue(helper.getBlockState(BASE).getValue(BrewKettleBlock.SUPPORT) != KettleSupport.HANDLE, "an extended kettle doesn't hang");
		helper.succeed();
	}

	private static void chimneyFallsAndDropsIngots(GameTestHelper helper) {
		fires(helper, 1, 1);
		place(helper, BASE, false);
		Player player = playerHolding(helper, new ItemStack(Items.COPPER_INGOT, 4));
		for (int i = 0; i < 4; i++) {
			helper.useBlock(BASE, player);
		}
		helper.getLevel().destroyBlock(helper.absolutePos(BASE.above(2)), true);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(BASE.above(3)).isAir() && helper.getBlockState(BASE.above(4)).isAir(), "the pieces above fell");
			helper.assertTrue(helper.getBlockState(BASE.above(1)).getValue(KettleChimneyBlock.TOP), "the piece below is the new top");
			VBGameTests.assertValueEqual(helper, dropped(helper, Items.COPPER_INGOT), 3, "three ingots back");
			helper.assertTrue(helper.getBlockState(BASE).getValue(BrewKettleBlock.EXTENDED), "still extended by one piece");
		});
	}

	private static void chimneyWeathersWithKettle(GameTestHelper helper) {
		fires(helper, 1, 1);
		place(helper, BASE, false);
		Player player = playerHolding(helper, new ItemStack(Items.COPPER_INGOT, 2));
		helper.useBlock(BASE, player);
		helper.useBlock(BASE, player);
		helper.useBlock(BASE, playerHolding(helper, new ItemStack(Items.HONEYCOMB)));
		VBGameTests.assertValueEqual(helper, helper.getBlockState(BASE).getBlock(), KettleRegistry.WAXED_KETTLES.get(WeatherState.UNAFFECTED).get(), "the kettle is waxed");
		VBGameTests.assertValueEqual(helper, helper.getBlockState(BASE.above(2)).getBlock(), KettleRegistry.WAXED_CHIMNEYS.get(WeatherState.UNAFFECTED).get(), "its chimney too");
		helper.succeed();
	}

	private static void chimneyOnBigKettle(GameTestHelper helper) {
		bigKettle(helper, KettleShape.BIG, 4);
		Player player = playerHolding(helper, new ItemStack(Items.COPPER_INGOT, 2));
		helper.useBlock(BASE.offset(1, 0, 1), player);
		BlockPos chimney = KettleMultiblock.kettleAt(helper.getLevel(), helper.absolutePos(BASE)).chimney();
		BlockState piece = helper.getLevel().getBlockState(chimney.above());
		helper.assertTrue(piece.getBlock() instanceof KettleChimneyBlock, "a piece on the Big Kettle's chimney");
		VBGameTests.assertValueEqual(helper, piece.getValue(KettleChimneyBlock.SIZE), 2, "sized for the Big Kettle");
		// breaking the kettle apart takes its chimney down, ingot back
		helper.setBlock(BASE.offset(1, 1, 1), Blocks.AIR);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getBlockState(chimney.above()).isAir(), "the chimney fell with the kettle");
			VBGameTests.assertValueEqual(helper, dropped(helper, Items.COPPER_INGOT), 1, "its ingot back");
		});
	}

	private static void chimneyBreaksThroughRoof(GameTestHelper helper) {
		fires(helper, 1, 1);
		place(helper, BASE, false);
		// a plank roof two blocks up
		for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) helper.setBlock(BASE.offset(x, 3, z), Blocks.OAK_PLANKS);
		ServerPlayer player = VBGameTests.mockServerPlayerInLevel(helper);
		player.setGameMode(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_INGOT, 4));
		for (int i = 0; i < 4; i++) helper.useBlock(BASE, player);
		BlockState inRoof = helper.getBlockState(BASE.above(3));
		helper.assertTrue(inRoof.getBlock() instanceof KettleChimneyBlock, "the chimney went through the roof");
		helper.assertTrue(inRoof.getValue(KettleChimneyBlock.FLASHING), "with copper flashing where it goes through");
		helper.assertTrue(!helper.getBlockState(BASE.above(2)).getValue(KettleChimneyBlock.FLASHING), "no flashing under the roof");
		helper.assertTrue(helper.getBlockState(BASE.above(4)).getBlock() instanceof KettleChimneyBlock, "and on above it");
		helper.succeedWhen(() -> VBGameTests.assertValueEqual(helper, dropped(helper, Items.OAK_PLANKS), 1, "the roof block it broke drops"));
	}

	private static void chimneyStopsAtObsidian(GameTestHelper helper) {
		place(helper, BASE, false);
		helper.setBlock(BASE.above(2), Blocks.OBSIDIAN);
		ServerPlayer player = VBGameTests.mockServerPlayerInLevel(helper);
		player.setGameMode(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_INGOT, 4));
		for (int i = 0; i < 4; i++) helper.useBlock(BASE, player);
		helper.assertTrue(helper.getBlockState(BASE.above(2)).is(Blocks.OBSIDIAN), "obsidian stays");
		VBGameTests.assertValueEqual(helper, player.getMainHandItem().getCount(), 3, "only one ingot used");
		helper.succeed();
	}

	private static ServerPlayer survivorWithIngots(GameTestHelper helper) {
		ServerPlayer player = VBGameTests.mockServerPlayerInLevel(helper);
		player.setGameMode(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_INGOT, 4));
		return player;
	}

	/** A vine on a trellis over the kettle: breaking the vine leaves its trellis, which stops the chimney (and stays). */
	private static void chimneyKeepsTrellis(GameTestHelper helper) {
		place(helper, BASE, false);
		helper.runAfterDelay(2, () -> {
			BlockPos above = helper.absolutePos(BASE.above());
			helper.getLevel().setBlock(above, VBBlocks.GRAPE_VINE_ON_TRELLIS.get().defaultBlockState(), Block.UPDATE_CLIENTS);
			helper.assertTrue(helper.getBlockState(BASE.above()).is(VBBlocks.GRAPE_VINE_ON_TRELLIS.get()), "a vine on a trellis over the kettle");
			helper.useBlock(BASE, survivorWithIngots(helper));
			helper.assertTrue(helper.getBlockState(BASE.above()).is(VBBlocks.TRELLIS.get()), "the trellis stays");
			helper.assertTrue(!helper.getBlockState(BASE).getValue(BrewKettleBlock.EXTENDED), "the chimney didn't grow");
			helper.succeed();
		});
	}

	/** A roof right on the kettle: one click breaks it and puts the piece in its place, for one ingot. */
	private static void chimneyThroughRoofInOneClick(GameTestHelper helper) {
		place(helper, BASE, false);
		for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) helper.setBlock(BASE.offset(x, 1, z), Blocks.OAK_PLANKS);
		ServerPlayer player = survivorWithIngots(helper);
		helper.useBlock(BASE, player);
		BlockState piece = helper.getBlockState(BASE.above());
		helper.assertTrue(piece.getBlock() instanceof KettleChimneyBlock, "the chimney piece stands where the roof block was");
		helper.assertTrue(piece.getValue(KettleChimneyBlock.TOP) && piece.getValue(KettleChimneyBlock.FLASHING), "the top piece, flashed into the roof");
		helper.assertTrue(helper.getBlockState(BASE).getValue(BrewKettleBlock.EXTENDED), "the kettle's chimney carries on into it");
		VBGameTests.assertValueEqual(helper, player.getMainHandItem().getCount(), 3, "one ingot used");
		helper.succeedWhen(() -> VBGameTests.assertValueEqual(helper, dropped(helper, Items.OAK_PLANKS), 1, "the roof block drops"));
	}

	/** A copper ingot is no pickaxe, but the stone the chimney goes through drops all the same. */
	private static void chimneyThroughStoneDrops(GameTestHelper helper) {
		place(helper, BASE, false);
		helper.setBlock(BASE.above(), Blocks.STONE);
		helper.useBlock(BASE, survivorWithIngots(helper));
		helper.assertTrue(helper.getBlockState(BASE.above()).getBlock() instanceof KettleChimneyBlock, "the chimney went through the stone");
		helper.succeedWhen(() -> VBGameTests.assertValueEqual(helper, dropped(helper, Items.COBBLESTONE), 1, "the stone drops as mined"));
	}

	/** A full Big Kettle loses a block without a drop: no single kettle holds more than 64, the rest drops. */
	private static void removedFullKettleNeverOverfills(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = bigKettle(helper, KettleShape.BIG, 4);
		fillBrew(helper, kettle, 64);
		for (int i = 0; i < 7; i++) {
			ItemStack brew = kettle.getBrew();
			brew.grow(64);
			kettle.setStack(BrewKettleBlockEntity.BREW_SLOT, brew);
		}
		VBGameTests.assertValueEqual(helper, kettle.getServings(), 512, "a full Big Kettle");
		helper.setBlock(BASE.offset(1, 1, 1), Blocks.AIR);
		for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) {
			if (x == 1 && y == 1 && z == 1) continue;
			BrewKettleBlockEntity single = VBGameTests.blockEntity(helper, BASE.offset(x, y, z), BrewKettleBlockEntity.class);
			VBGameTests.assertValueEqual(helper, single.getServings(), BrewKettleBlockEntity.MAX_SERVINGS, "each kettle full, never over");
		}
		helper.succeedWhen(() -> VBGameTests.assertValueEqual(helper, dropped(helper, BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), 16, "the 64 left over drop as buckets"));
	}

	private static void chimneyGrowsFromChimney(GameTestHelper helper) {
		fires(helper, 1, 1);
		place(helper, BASE, false);
		Player player = playerHolding(helper, new ItemStack(Items.COPPER_INGOT, 3));
		helper.useBlock(BASE, player);
		helper.useBlock(BASE.above(1), player);
		helper.useBlock(BASE.above(2), player);
		helper.assertTrue(helper.getBlockState(BASE.above(3)).getBlock() instanceof KettleChimneyBlock, "clicking the chimney grows it");
		VBGameTests.assertValueEqual(helper, player.getMainHandItem().getCount(), 0, "three ingots used");
		helper.succeed();
	}

	private static void smallSightGlass(GameTestHelper helper) {
		fires(helper, 1, 0);
		place(helper, BASE, false);
		fillBrew(helper, VBGameTests.blockEntity(helper, BASE, BrewKettleBlockEntity.class), 33);
		helper.succeedWhen(() -> {
			VBGameTests.assertValueEqual(helper, helper.getBlockState(BASE).getValue(BrewKettleBlock.FILL), 3, "the Kettle's sight glass shows 33 of 64");
			helper.assertTrue(!helper.getBlockState(BASE).getValue(BrewKettleBlock.OPEN), "the lid stays shut: the glass shows the brew");
		});
	}

	// ------------------------------------------------------------------ 1.0 saves

	/**
	 * A Brew Kettle's block state and block entity exactly as Vine & Barrel 1.0 saves them (1.0.1 / 1.0.2 format; the item
	 * stacks inside as this Minecraft writes them: {@code Count} is a byte, where 1.21.1 has {@code count}).
	 */
	static final String STATE_1_0 = "vineandbarrel:brew_kettle[facing=north,support=tray,open=false,fill=1,waterlogged=false]";
	static final String DATA_1_0 = "{id:\"vineandbarrel:brew_kettle\",Inventory:{stacks:[{id:\"minecraft:sweet_berries\",Count:3b},{},{},{},{},{},"
		+ "{id:\"vineandbarrel:sweet_berry_juice\",Count:8b},{id:\"minecraft:glass_bottle\",Count:2b},{}]},BrewTime:50,BrewTimeTotal:200,"
		+ "BucketForm:{id:\"vineandbarrel:sweet_berry_juice_bucket\",Count:1b},RecipesUsed:{\"vineandbarrel:brewing/sweet_berry_juice\":2}}";

	/**
	 * A kettle in the 1.0 save format (its 1.0 block state properties and block entity data): 8 servings of sweet berry
	 * juice, berries in the grid, bottles waiting, a batch a quarter done, an empty 1.0 kettle beside it. It loads as a
	 * single kettle with everything kept and its new chimney not extended, and the two never join on their own.
	 */
	private static void savedBy10LoadsUnchanged(GameTestHelper helper) {
		BlockPos at = BASE;
		helper.setBlock(at.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		BlockState state;
		try {
			state = net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(helper.getLevel().holderLookup(Registries.BLOCK), STATE_1_0, false).blockState();
			helper.getLevel().setBlock(helper.absolutePos(at), state, Block.UPDATE_ALL);
			helper.getLevel().setBlock(helper.absolutePos(at.east()), state.setValue(BrewKettleBlock.FILL, 0), Block.UPDATE_ALL);
			var tag = net.minecraft.nbt.TagParser.parseTag(DATA_1_0);
			VBGameTests.blockEntity(helper, at, BrewKettleBlockEntity.class).load(tag);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new IllegalStateException(e);
		}
		state = helper.getBlockState(at);
		helper.assertTrue(state.getBlock() instanceof BrewKettleBlock, "still a single kettle");
		VBGameTests.assertValueEqual(helper, state.getValue(BrewKettleBlock.SUPPORT), KettleSupport.TRAY, "on its tray");
		helper.assertTrue(!state.getValue(BrewKettleBlock.EXTENDED), "chimney not extended");
		BrewKettleBlockEntity kettle = VBGameTests.blockEntity(helper, at, BrewKettleBlockEntity.class);
		VBGameTests.assertValueEqual(helper, kettle.getServings(), 8, "servings kept");
		helper.assertTrue(kettle.getBrew().is(BaseItems.SWEET_BERRY_JUICE.get()), "the brew kept");
		helper.assertTrue(kettle.getBucketForm().is(BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), "bucket form kept");
		VBGameTests.assertValueEqual(helper, kettle.stackIn(0).getCount(), 3, "ingredients kept");
		VBGameTests.assertValueEqual(helper, kettle.stackIn(BrewKettleBlockEntity.CONTAINER_SLOT).getCount(), 2, "waiting bottles kept");
		VBGameTests.assertValueEqual(helper, kettle.getBrewTime(), 50, "the batch's progress kept");
		VBGameTests.assertValueEqual(helper, kettle.shape(), KettleShape.SINGLE, "a 1x1");
		helper.assertTrue(helper.getBlockState(at.east()).getBlock() instanceof BrewKettleBlock, "its neighbour stays single");
		helper.succeed();
	}
}
