package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.platform.SlotAccess;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.registry.VBItems;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.BrewKettleBlock;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.BrewKettleItem;
import jexxme.vineandbarrel.kettle.BrewKettleMenu;
import jexxme.vineandbarrel.kettle.BrewingRecipe;
import jexxme.vineandbarrel.kettle.KettleContents;
import jexxme.vineandbarrel.kettle.KettleRecipeInput;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.kettle.KettleSupport;
import jexxme.vineandbarrel.kettle.KettleTags;
import jexxme.vineandbarrel.advancement.VBAdvancementGenerator;
import net.minecraft.nbt.NbtOps;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

/**
 * M2 Brew Kettle GameTests: brewing on heat (and only on heat), servings into buckets and bottles, hopper automation,
 * item conservation, and the interactions K1-K9 and D3-D4. Run headless with {@code ./gradlew runGameTestServer}.
 */
public final class KettleGameTests
{
	private static final Identifier SMALL = VineAndBarrel.id("gametest/empty_5x5");
	private static final DeferredRegister<java.util.function.Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, VineAndBarrel.MODID);
	private static final List<ResourceKey<Consumer<GameTestHelper>>> TESTS = new ArrayList<>();
	private static final Map<ResourceKey<Consumer<GameTestHelper>>, Integer> TIMEOUTS = new HashMap<>();
	private static final ResourceKey<Recipe<?>> SWEET_BERRY_JUICE_RECIPE = ResourceKey.create(Registries.RECIPE, VineAndBarrel.id("brewing/sweet_berry_juice"));

	private static final BlockPos HEAT = new BlockPos(2, 1, 2);
	private static final BlockPos KETTLE = HEAT.above();

	static {
		test("kettle_brews_on_campfire", 400, KettleGameTests::brewsOnCampfire);
		test("kettle_brews_through_heat_conductor", 400, KettleGameTests::brewsThroughConductor);
		test("kettle_needs_heat", 400, KettleGameTests::needsHeat);
		test("kettle_heat_sources", 100, KettleGameTests::heatSources);
		test("kettle_bucket_takes_four_servings", 100, KettleGameTests::bucketTakesFourServings);
		test("kettle_bottles_take_one_serving_each", 100, KettleGameTests::bottlesTakeOneEach);
		test("kettle_container_slot_fills_output", 100, KettleGameTests::containerSlotFillsOutput);
		test("kettle_conservation_full_batch", 600, KettleGameTests::conservationFullBatch);
		test("kettle_conservation_random_actions", 400, KettleGameTests::conservationRandomActions);
		test("kettle_hopper_automation", 800, KettleGameTests::hopperAutomation);
		test("kettle_hopper_sides", 100, KettleGameTests::hopperSides);
		test("kettle_container_click", 100, KettleGameTests::containerClick);
		test("kettle_keeps_contents_when_broken", 100, KettleGameTests::keepsContentsWhenBroken);
		test("kettle_save_load_roundtrip", 100, KettleGameTests::saveLoadRoundtrip);
		test("kettle_comparator_output", 100, KettleGameTests::comparatorOutput);
		test("kettle_support_states", 100, KettleGameTests::supportStates);
		test("kettle_lid_opens_with_screen", 100, KettleGameTests::lidOpensWithScreen);
		test("kettle_stale_menu_after_break_cannot_dupe", 100, KettleGameTests::staleMenuCannotDupe);
		test("kettle_weathering_keeps_contents", 100, KettleGameTests::weatheringKeepsContents);
		test("kettle_wax_and_scrape", 100, KettleGameTests::waxAndScrape);
		test("kettle_lightning_cleans_copper", 100, KettleGameTests::lightningCleansCopper);
		test("kettle_recipe_and_book", 100, KettleGameTests::recipeAndBook);
		test("juice_bottle_drinks_like_honey", 100, KettleGameTests::juiceBottleDrinks);
		test("base_bucket_never_places_fluid", 100, KettleGameTests::bucketNeverPlacesFluid);
		test("base_items_and_tags", 100, KettleGameTests::baseItemsAndTags);
		// Cross-milestone: the kettle brews M1's crops
		test("kettle_brews_grape_juice", 400, h -> brewsCropRecipe(h, List.of(VBItems.RED_GRAPES.get(), VBItems.RED_GRAPES.get(),
			VBItems.RED_GRAPES.get(), VBItems.RED_GRAPES.get(), Items.SUGAR), BaseItems.GRAPE_JUICE.get(), BaseItems.GRAPE_JUICE_BUCKET.get()));
		test("kettle_brews_barley_wort", 400, h -> brewsCropRecipe(h, List.of(VBItems.BARLEY.get(), VBItems.BARLEY.get(),
			VBItems.BARLEY.get(), VBItems.HOPS.get()), BaseItems.BARLEY_WORT_BOTTLE.get(), BaseItems.BARLEY_WORT_BUCKET.get()));
		test("kettle_brews_wheat_wort", 400, h -> brewsCropRecipe(h, List.of(Items.WHEAT, Items.WHEAT,
			Items.WHEAT, VBItems.HOPS.get()), BaseItems.WHEAT_WORT_BOTTLE.get(), BaseItems.WHEAT_WORT_BUCKET.get()));
		// New drinks: white grape juice, apple juice (our own cider), honey must (mead)
		test("kettle_brews_white_grape_juice", 400, h -> brewsCropRecipe(h, List.of(VBItems.WHITE_GRAPES.get(), VBItems.WHITE_GRAPES.get(),
			VBItems.WHITE_GRAPES.get(), VBItems.WHITE_GRAPES.get(), Items.SUGAR), BaseItems.WHITE_GRAPE_JUICE.get(), BaseItems.WHITE_GRAPE_JUICE_BUCKET.get()));
		test("kettle_brews_apple_juice", 400, h -> brewsCropRecipe(h, List.of(Items.APPLE, Items.APPLE, Items.APPLE, Items.APPLE, Items.SUGAR),
			BaseItems.APPLE_JUICE.get(), BaseItems.APPLE_JUICE_BUCKET.get()));
		test("kettle_brews_honey_must_and_returns_bottles", 400, KettleGameTests::brewsHoneyMust);
		// Review fixes: recipe book placement, pooled matching (hopper feeding), serving cap, comparator, shift-click
		test("kettle_recipe_book_conserves_items", 100, KettleGameTests::recipeBookConservesItems);
		test("kettle_hopper_feeds_juice_from_scratch", 600, h -> hopperFeedsFromScratch(h,
			List.of(new ItemStack(Items.SWEET_BERRIES, 4), new ItemStack(Items.SUGAR)), BaseItems.SWEET_BERRY_JUICE.get()));
		test("kettle_hopper_feeds_wort_from_scratch", 600, h -> hopperFeedsFromScratch(h,
			List.of(new ItemStack(VBItems.BARLEY.get(), 3), new ItemStack(VBItems.HOPS.get())), BaseItems.BARLEY_WORT_BOTTLE.get()));
		test("kettle_pooled_batches", 700, KettleGameTests::pooledBatches);
		test("kettle_pooled_matching_rules", 100, KettleGameTests::pooledMatchingRules);
		test("kettle_refuses_other_base", 400, KettleGameTests::refusesOtherBase);
		test("kettle_serving_cap", 1000, KettleGameTests::servingCap);
		test("kettle_comparator_caps_at_15", 100, KettleGameTests::comparatorCapsAt15);
		test("kettle_quick_move_conserves", 100, KettleGameTests::quickMoveConserves);
		test("kettle_old_slots_are_buffers", 200, KettleGameTests::oldSlotsAreBuffers);
		// K5 right-click filling, and the screen through its real click paths
		test("kettle_right_click_fills", 1000, KettleGameTests::rightClickFills);
		test("kettle_right_click_creative", 100, KettleGameTests::rightClickCreative);
		test("kettle_right_click_passes", 100, KettleGameTests::rightClickPasses);
		test("kettle_right_click_sneaking", 100, KettleGameTests::rightClickSneaking);
		test("kettle_menu_click_paths", 400, KettleGameTests::menuClickPaths);
		test("kettle_screen_knows_a_grid_that_makes_nothing", 100, KettleGameTests::gridMakesNothing);
		// The waiting brew shows under the raised lid, at four fill levels
		test("kettle_fill_level_follows_servings", 200, KettleGameTests::fillLevelFollowsServings);
		test("kettle_lid_opens_when_brew_done", 900, KettleGameTests::lidOpensWhenBrewDone);
		// Steam (K3): only a kettle with a brew or a batch steams, and no campfire smoke comes up through one
		test("kettle_stays_quiet_without_a_batch", 100, KettleGameTests::staysQuietWithoutBatch);
		test("kettle_hides_campfire_smoke", 100, KettleGameTests::hidesCampfireSmoke);
		// The kettle keeps its last recipe match while its ingredient slots stay the same: any change, however it is made,
		// matches again
		test("kettle_quiet_slot_change_matches_again", 400, KettleGameTests::quietSlotChangeMatchesAgain);
		test("kettle_stack_grown_in_place_matches_again", 400, KettleGameTests::stackGrownInPlaceMatchesAgain);
	}

	private KettleGameTests() {
	}

	private static void test(String name, int timeout, Consumer<GameTestHelper> function) {
		FUNCTIONS.register(name, () -> function);
		ResourceKey<Consumer<GameTestHelper>> key = ResourceKey.create(Registries.TEST_FUNCTION, VineAndBarrel.id(name));
		TESTS.add(key);
		TIMEOUTS.put(key, timeout);
	}

	public static void register() {
		FUNCTIONS.register();
		jexxme.vineandbarrel.platform.Services.PLATFORM.addGameTests(KettleGameTests::registerTests);
	}

	private static void registerTests(GameTestRegistrar event) {
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(VineAndBarrel.id("kettle"));
		for (ResourceKey<Consumer<GameTestHelper>> function : TESTS) {
			event.registerTest(function.identifier(), new FunctionGameTestInstance(function, new TestData<>(environment, SMALL, TIMEOUTS.get(function), 0, true)));
		}
	}

	// ------------------------------------------------------------------ helpers

	private static BlockState litCampfire() {
		return Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
	}

	private static BrewKettleBlockEntity kettle(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, BrewKettleBlockEntity.class);
	}

	/** Puts a heat source down and a kettle on it, with the support the kettle gets when placed there (K1). */
	private static BrewKettleBlockEntity placeKettle(GameTestHelper helper, BlockPos pos, BlockState heat) {
		helper.setBlock(pos.below(), heat);
		helper.setBlock(pos, KettleRegistry.kettle().defaultBlockState()
			.setValue(BrewKettleBlock.SUPPORT, BrewKettleBlock.trayState(helper.getLevel(), helper.absolutePos(pos))));
		return kettle(helper, pos);
	}

	/** One batch of sweet berry juice: four berries in four slots, sugar in the fifth. */
	private static void loadBatch(BrewKettleBlockEntity kettle, int batches) {
		for (int i = 0; i < 4; i++) {
			kettle.setStack(i, new ItemStack(Items.SWEET_BERRIES, batches));
		}
		kettle.setStack(4, new ItemStack(Items.SUGAR, batches));
	}

	/** A kettle holding a finished brew of {@code servings} sweet berry juice, as if just brewed. */
	private static BrewKettleBlockEntity kettleWithBrew(GameTestHelper helper, BlockPos pos, int servings) {
		helper.setBlock(pos, KettleRegistry.kettle());
		BrewKettleBlockEntity kettle = kettle(helper, pos);
		ItemStack item = new ItemStack(KettleRegistry.kettleItem());
		item.set(KettleRegistry.KETTLE_CONTENTS.get(), new KettleContents(new ItemStack(BaseItems.SWEET_BERRY_JUICE.get(), servings),
			new ItemStack(BaseItems.SWEET_BERRY_JUICE_BUCKET.get())));
		kettle.applyComponentsFromItemStack(item);
		return kettle;
	}

	/** Puts a finished brew of {@code servings} into a kettle that is already placed, as the kettle item would. */
	private static void fillBrew(BrewKettleBlockEntity kettle, Item bottle, Item bucket, int servings) {
		ItemStack item = new ItemStack(KettleRegistry.kettleItem());
		item.set(KettleRegistry.KETTLE_CONTENTS.get(), new KettleContents(new ItemStack(bottle, servings), new ItemStack(bucket)));
		kettle.applyComponentsFromItemStack(item);
	}

	private static Player playerHolding(GameTestHelper helper, ItemStack stack) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		return player;
	}

	private static int countInInventory(Player player, Item item) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) count += stack.getCount();
		}
		return count;
	}

	private static List<ItemEntity> dropsAround(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(2.0D));
	}

	// ------------------------------------------------------------------ brewing and heat

	/** On a lit campfire the kettle stands on its tray and brews 4 berries + sugar into 4 servings of Sweet Berry Juice. */
	private static void brewsOnCampfire(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, litCampfire());
		helper.assertValueEqual(helper.getBlockState(KETTLE).getValue(BrewKettleBlock.SUPPORT), KettleSupport.TRAY, "support over a campfire");
		loadBatch(kettle, 1);
		helper.assertTrue(kettle.isHeated(), "A kettle on a lit campfire is heated");
		helper.runAfterDelay(150, () -> helper.assertTrue(kettle.getBrew().isEmpty(), "Brewing takes 200 ticks, not 150"));
		helper.succeedWhen(() -> {
			helper.assertTrue(kettle.getBrew().is(BaseItems.SWEET_BERRY_JUICE.get()), "Expected Sweet Berry Juice, got " + kettle.getBrew());
			helper.assertValueEqual(kettle.getServings(), 4, "servings from one batch");
			helper.assertTrue(kettle.getBucketForm().is(BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), "bucket form of the brew");
			for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
				helper.assertTrue(kettle.stackIn(i).isEmpty(), "Ingredient slot " + i + " should be used up");
			}
		});
	}

	/**
	 * Four berries and no sugar don't brew; sugar put into the fifth slot without any change notification (as a mod writing
	 * the slot directly would) makes the recipe complete, and the kettle brews it.
	 */
	private static void quietSlotChangeMatchesAgain(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, litCampfire());
		for (int i = 0; i < 4; i++) {
			kettle.setStack(i, new ItemStack(Items.SWEET_BERRIES));
		}
		helper.runAfterDelay(40, () -> {
			helper.assertValueEqual(kettle.getBrewTime(), 0, "brew time without sugar");
			kettle.getInventory().setQuietly(4, new ItemStack(Items.SUGAR));
		});
		helper.runAfterDelay(60, () -> helper.assertTrue(kettle.getBrewTime() > 0, "brewing once the sugar is there"));
		helper.succeedWhen(() -> {
			helper.assertTrue(kettle.getBrew().is(BaseItems.SWEET_BERRY_JUICE.get()), "Expected Sweet Berry Juice, got " + kettle.getBrew());
			helper.assertValueEqual(kettle.getServings(), 4, "servings from one batch");
		});
	}

	/** Three berries and sugar (pooled: one berry short) don't brew; the slot's own stack grown by one in place completes it. */
	private static void stackGrownInPlaceMatchesAgain(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, litCampfire());
		kettle.setStack(0, new ItemStack(Items.SWEET_BERRIES, 3));
		kettle.setStack(1, new ItemStack(Items.SUGAR));
		helper.runAfterDelay(40, () -> {
			helper.assertValueEqual(kettle.getBrewTime(), 0, "brew time one berry short");
			kettle.getInventory().getStack(0).grow(1);
		});
		helper.runAfterDelay(60, () -> helper.assertTrue(kettle.getBrewTime() > 0, "brewing once the fourth berry is there"));
		helper.succeedWhen(() -> {
			helper.assertTrue(kettle.getBrew().is(BaseItems.SWEET_BERRY_JUICE.get()), "Expected Sweet Berry Juice, got " + kettle.getBrew());
			helper.assertValueEqual(kettle.getServings(), 4, "servings from one batch");
			helper.assertTrue(kettle.stackIn(0).isEmpty() && kettle.stackIn(1).isEmpty(), "berries and sugar used up");
		});
	}

	/** One batch of a crop recipe on a lit campfire: every ingredient used up, 4 servings of the base, the right bucket form. */
	private static void brewsCropRecipe(GameTestHelper helper, List<Item> ingredients, Item bottle, Item bucket) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, litCampfire());
		for (int i = 0; i < ingredients.size(); i++) {
			kettle.setStack(i, new ItemStack(ingredients.get(i)));
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(kettle.getBrew().is(bottle), "Expected " + bottle + ", got " + kettle.getBrew());
			helper.assertValueEqual(kettle.getServings(), 4, "servings from one batch");
			helper.assertTrue(kettle.getBucketForm().is(bucket), "bucket form of the brew");
			for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
				helper.assertTrue(kettle.stackIn(i).isEmpty(), "Ingredient slot " + i + " should be used up");
			}
		});
	}

	/**
	 * Mead's must: 3 honey bottles (pooled in one slot, as a hopper would leave them) brew 4 servings, and the kettle
	 * hands the 3 glass bottles back, like FD's cooking pot does with containers: nothing is lost.
	 */
	private static void brewsHoneyMust(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, litCampfire());
		kettle.setStack(0, new ItemStack(Items.HONEY_BOTTLE, 3));
		helper.succeedWhen(() -> {
			helper.assertTrue(kettle.getBrew().is(BaseItems.HONEY_MUST_BOTTLE.get()), "Expected honey must, got " + kettle.getBrew());
			helper.assertValueEqual(kettle.getServings(), 4, "servings from one batch");
			helper.assertTrue(kettle.getBucketForm().is(BaseItems.HONEY_MUST_BUCKET.get()), "bucket form of the brew");
			helper.assertTrue(kettle.stackIn(0).isEmpty(), "the honey is used up");
			int bottles = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new net.minecraft.world.phys.AABB(helper.absolutePos(KETTLE)).inflate(3.0)).stream()
				.filter(entity -> entity.getItem().is(Items.GLASS_BOTTLE)).mapToInt(entity -> entity.getItem().getCount()).sum();
			helper.assertValueEqual(bottles, 3, "glass bottles handed back");
		});
	}

	/** FD's conductor rule: magma block -> hopper -> kettle brews; the hopper doesn't give the tray. */
	private static void brewsThroughConductor(GameTestHelper helper) {
		helper.setBlock(HEAT.below(), Blocks.MAGMA_BLOCK);
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
		helper.assertValueEqual(helper.getBlockState(KETTLE).getValue(BrewKettleBlock.SUPPORT), KettleSupport.NONE, "support over a hopper");
		helper.assertTrue(kettle.isHeated(), "Heat passes through a hopper");
		loadBatch(kettle, 1);
		helper.succeedWhen(() -> helper.assertValueEqual(kettle.getServings(), 4, "servings brewed through a hopper"));
	}

	/** On stone, on an unlit campfire, and over a hopper with nothing hot below it, nothing brews and nothing is used up. */
	private static void needsHeat(GameTestHelper helper) {
		BlockPos a = new BlockPos(0, 2, 0);
		BlockPos b = new BlockPos(2, 2, 0);
		BlockPos c = new BlockPos(4, 2, 0);
		BrewKettleBlockEntity onStone = placeKettle(helper, a, Blocks.STONE.defaultBlockState());
		BrewKettleBlockEntity onUnlit = placeKettle(helper, b, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		helper.setBlock(c.below(2), Blocks.STONE);
		BrewKettleBlockEntity overHopper = placeKettle(helper, c, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
		for (BrewKettleBlockEntity kettle : List.of(onStone, onUnlit, overHopper)) {
			loadBatch(kettle, 1);
			helper.assertFalse(kettle.isHeated(), "Kettle should not be heated");
		}
		helper.runAfterDelay(260, () -> {
			for (BrewKettleBlockEntity kettle : List.of(onStone, onUnlit, overHopper)) {
				helper.assertTrue(kettle.getBrew().isEmpty(), "Nothing brews without heat, got " + kettle.getBrew());
				helper.assertValueEqual(kettle.stackIn(0).getCount(), 1, "berries left");
				helper.assertValueEqual(kettle.stackIn(4).getCount(), 1, "sugar left");
			}
			helper.succeed();
		});
	}

	/** Every source from the heat tag heats directly; the tray only shows over campfire, fire and lava (FD's tray rule). */
	private static void heatSources(GameTestHelper helper) {
		record Case(BlockState heat, boolean tray) {
		}
		List<Case> cases = List.of(new Case(litCampfire(), true), new Case(Blocks.SOUL_CAMPFIRE.defaultBlockState(), true),
			new Case(Blocks.MAGMA_BLOCK.defaultBlockState(), false), new Case(Blocks.LAVA_CAULDRON.defaultBlockState(), false),
			new Case(Blocks.LAVA.defaultBlockState(), true));
		int x = 0;
		for (Case c : cases) {
			BlockPos pos = new BlockPos(x, 2, 2);
			BrewKettleBlockEntity kettle = placeKettle(helper, pos, c.heat());
			helper.assertTrue(kettle.isHeated(), c.heat() + " should heat the kettle");
			helper.assertValueEqual(helper.getBlockState(pos).getValue(BrewKettleBlock.SUPPORT), c.tray() ? KettleSupport.TRAY : KettleSupport.NONE, "support over " + c.heat());
			x++;
		}
		helper.assertTrue(Blocks.FIRE.defaultBlockState().is(KettleTags.HEAT_SOURCES), "Fire is a heat source");
		helper.assertTrue(Blocks.FIRE.defaultBlockState().is(KettleTags.TRAY_HEAT_SOURCES), "Fire gets the tray");
		helper.assertTrue(Blocks.HOPPER.defaultBlockState().is(KettleTags.HEAT_CONDUCTORS), "A hopper conducts heat");
		helper.succeed();
	}

	// ------------------------------------------------------------------ servings: bucket = 4, bottle = 1

	private static void bucketTakesFourServings(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = kettleWithBrew(helper, KETTLE, 7);
		ItemStack buckets = new ItemStack(Items.BUCKET, 2);
		ItemStack first = kettle.serveInto(buckets, null);
		helper.assertTrue(first.is(BaseItems.SWEET_BERRY_JUICE_BUCKET.get()) && first.getCount() == 1, "First bucket: " + first);
		helper.assertValueEqual(kettle.getServings(), 3, "servings left after one bucket");
		helper.assertValueEqual(buckets.getCount(), 1, "empty buckets left");
		// 3 servings aren't a whole batch: the bucket is refused and nothing changes
		ItemStack second = kettle.serveInto(buckets, null);
		helper.assertTrue(second.isEmpty(), "A bucket needs 4 servings, got " + second);
		helper.assertValueEqual(kettle.getServings(), 3, "servings after a refused bucket");
		helper.assertValueEqual(buckets.getCount(), 1, "the refused bucket is not used up");
		helper.assertTrue(kettle.getBucketForm().is(BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), "the brew still knows its bucket form");
		helper.succeed();
	}

	private static void bottlesTakeOneEach(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = kettleWithBrew(helper, KETTLE, 4);
		ItemStack bottles = new ItemStack(Items.GLASS_BOTTLE, 6);
		int served = 0;
		for (int i = 0; i < 6; i++) {
			ItemStack juice = kettle.serveInto(bottles, null);
			if (!juice.isEmpty()) {
				helper.assertTrue(juice.is(BaseItems.SWEET_BERRY_JUICE.get()) && juice.getCount() == 1, "Each bottle holds one serving: " + juice);
				served++;
			}
		}
		helper.assertValueEqual(served, 4, "bottles filled from 4 servings");
		helper.assertValueEqual(bottles.getCount(), 2, "empty bottles left");
		helper.assertValueEqual(kettle.getServings(), 0, "servings left");
		helper.assertTrue(kettle.getBucketForm().isEmpty(), "An empty kettle forgets the brew's bucket form");
		helper.assertTrue(kettle.serveInto(new ItemStack(Items.BUCKET), null).isEmpty(), "Nothing left to serve");
		// Other containers never take the brew
		BrewKettleBlockEntity other = kettleWithBrew(helper, KETTLE.east(), 4);
		helper.assertTrue(other.serveInto(new ItemStack(Items.BOWL), null).isEmpty(), "A bowl doesn't take juice");
		helper.assertValueEqual(other.getServings(), 4, "servings after a bowl");
		helper.succeed();
	}

	/** Containers waiting in the container slot fill into the output on their own (FD's bowl slot). */
	private static void containerSlotFillsOutput(GameTestHelper helper) {
		BrewKettleBlockEntity buckets = kettleWithBrew(helper, new BlockPos(1, 2, 2), 9);
		buckets.setStack(BrewKettleBlockEntity.CONTAINER_SLOT, new ItemStack(Items.BUCKET, 3));
		BrewKettleBlockEntity bottles = kettleWithBrew(helper, new BlockPos(3, 2, 2), 5);
		bottles.setStack(BrewKettleBlockEntity.CONTAINER_SLOT, new ItemStack(Items.GLASS_BOTTLE, 8));
		helper.succeedWhen(() -> {
			// buckets don't stack: one filled bucket waits in the output, the rest wait until it is taken
			helper.assertTrue(buckets.stackIn(BrewKettleBlockEntity.OUTPUT_SLOT).is(BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), "bucket output");
			helper.assertValueEqual(buckets.stackIn(BrewKettleBlockEntity.OUTPUT_SLOT).getCount(), 1, "filled buckets in the output");
			helper.assertValueEqual(buckets.getServings(), 5, "servings after one bucket");
			helper.assertValueEqual(buckets.stackIn(BrewKettleBlockEntity.CONTAINER_SLOT).getCount(), 2, "empty buckets waiting");
			helper.assertValueEqual(bottles.stackIn(BrewKettleBlockEntity.OUTPUT_SLOT).getCount(), 5, "filled bottles in the output");
			helper.assertValueEqual(bottles.getServings(), 0, "servings after bottling");
			helper.assertValueEqual(bottles.stackIn(BrewKettleBlockEntity.CONTAINER_SLOT).getCount(), 3, "empty bottles left");
		});
	}

	// ------------------------------------------------------------------ item conservation

	/**
	 * Two batches: 8 berries + 2 sugar make exactly 8 servings. 1 bucket takes 4, then 5 bottles go in and exactly 4 come
	 * out filled. Breaking the kettle drops exactly what is left. Nothing is duplicated or lost.
	 */
	private static void conservationFullBatch(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, litCampfire());
		loadBatch(kettle, 2);
		helper.startSequence().thenWaitUntil(() -> helper.assertValueEqual(kettle.getServings(), 8, "servings from 8 berries and 2 sugar")).thenExecute(() -> {
			for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
				helper.assertTrue(kettle.stackIn(i).isEmpty(), "ingredient slot " + i + " used up exactly");
			}
			ItemStack bucket = new ItemStack(Items.BUCKET);
			helper.assertTrue(kettle.serveInto(bucket, null).is(BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), "bucket filled");
			helper.assertTrue(bucket.isEmpty(), "the empty bucket was used");
			helper.assertValueEqual(kettle.getServings(), 4, "servings after the bucket");
			kettle.setStack(BrewKettleBlockEntity.CONTAINER_SLOT, new ItemStack(Items.GLASS_BOTTLE, 5));
			BrewKettleBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(KETTLE), helper.getBlockState(KETTLE), kettle);
			helper.assertValueEqual(kettle.stackIn(BrewKettleBlockEntity.OUTPUT_SLOT).getCount(), 4, "filled bottles");
			helper.assertValueEqual(kettle.stackIn(BrewKettleBlockEntity.CONTAINER_SLOT).getCount(), 1, "empty bottles left");
			helper.assertValueEqual(kettle.getServings(), 0, "servings left");

			helper.setBlock(HEAT, Blocks.STONE); // nothing may burn on the campfire
			helper.getLevel().destroyBlock(helper.absolutePos(KETTLE), true);
			int juice = 0, glass = 0, kettles = 0;
			for (ItemEntity entity : dropsAround(helper, KETTLE)) {
				ItemStack stack = entity.getItem();
				if (stack.is(BaseItems.SWEET_BERRY_JUICE.get())) juice += stack.getCount();
				else if (stack.is(Items.GLASS_BOTTLE)) glass += stack.getCount();
				else if (KettleRegistry.allKettleItems().contains(stack.getItem())) { // it may weather while it brews (random ticks)
					kettles += stack.getCount();
					helper.assertTrue(BrewKettleItem.contentsOf(stack).isEmpty(), "An emptied kettle drops without contents");
				} else helper.fail("Unexpected drop " + stack);
			}
			helper.assertValueEqual(juice, 4, "juice bottles dropped");
			helper.assertValueEqual(glass, 1, "glass bottles dropped");
			helper.assertValueEqual(kettles, 1, "kettles dropped");
		}).thenSucceed();
	}

	/**
	 * Random inserts and extracts through every side, aborted transactions, hand serving and save/load on an unheated
	 * kettle (FD's conservation property test). After every step: put in - taken out == inside. After breaking: inside == dropped.
	 */
	private static void conservationRandomActions(GameTestHelper helper) {
		Direction[] sides = {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
		List<Item> pool = List.of(Items.SWEET_BERRIES, Items.SUGAR, Items.GLASS_BOTTLE, Items.BUCKET, Items.WHEAT, Items.STONE);
		helper.setBlock(KETTLE, KettleRegistry.kettle());
		BlockPos absolute = helper.absolutePos(KETTLE);
		Map<Item, Integer> expected = new HashMap<>();
		Random random = new Random(20261106L);
		for (int step = 0; step < 300; step++) {
			Direction side = sides[random.nextInt(sides.length)];
			SlotAccess handler = Services.PLATFORM.itemAccess(helper.getLevel(), absolute, side);
			helper.assertTrue(handler != null, "kettle exposes an item handler on side " + side);
			Item item = pool.get(random.nextInt(pool.size()));
			int amount = 1 + random.nextInt(random.nextBoolean() ? 3 : 70);
			int action = random.nextInt(10);
			if (action < 4) {
				try (SlotAccess.Tx tx = Services.PLATFORM.openTransaction()) {
					int inserted = insertAnywhere(handler, SlotAccess.of(item), amount, tx);
					tx.commit();
					expected.merge(item, inserted, Integer::sum);
				}
			} else if (action < 7) {
				try (SlotAccess.Tx tx = Services.PLATFORM.openTransaction()) {
					int extracted = extractAnywhere(handler, SlotAccess.of(item), amount, tx);
					tx.commit();
					expected.merge(item, -extracted, Integer::sum);
				}
			} else if (action < 8) {
				try (SlotAccess.Tx tx = Services.PLATFORM.openTransaction()) {
					insertAnywhere(handler, SlotAccess.of(item), amount, tx);
					extractAnywhere(handler, SlotAccess.of(pool.get(random.nextInt(pool.size()))), amount, tx);
					// not committed: nothing may change
				}
			} else if (action < 9) {
				ItemStack held = new ItemStack(random.nextBoolean() ? Items.GLASS_BOTTLE : Items.BUCKET, 4);
				ItemStack served = kettle(helper, KETTLE).serveInto(held, null);
				helper.assertTrue(served.isEmpty() && held.getCount() == 4, "An unbrewed kettle serves nothing");
			} else {
				// save and load into a fresh block entity
				BrewKettleBlockEntity old = kettle(helper, KETTLE);
				var tag = old.saveWithFullMetadata(helper.getLevel().registryAccess());
				old.loadWithComponents(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
			}
			Map<Item, Integer> inside = contents(kettle(helper, KETTLE));
			for (Item poolItem : pool) {
				helper.assertValueEqual(inside.getOrDefault(poolItem, 0), expected.getOrDefault(poolItem, 0), "step " + step + " " + poolItem);
			}
		}
		Map<Item, Integer> inside = contents(kettle(helper, KETTLE));
		helper.getLevel().destroyBlock(absolute, true);
		Map<Item, Integer> dropped = new HashMap<>();
		for (ItemEntity entity : dropsAround(helper, KETTLE)) {
			dropped.merge(entity.getItem().getItem(), entity.getItem().getCount(), Integer::sum);
		}
		for (Item poolItem : pool) {
			helper.assertValueEqual(dropped.getOrDefault(poolItem, 0), inside.getOrDefault(poolItem, 0), "dropped " + poolItem);
		}
		helper.succeed();
	}

	private static int insertAnywhere(SlotAccess handler, ItemStack resource, int amount, SlotAccess.Tx tx) {
		int done = 0;
		for (int i = 0; i < handler.size() && done < amount; i++) {
			done += handler.insert(i, resource, amount - done, tx);
		}
		return done;
	}

	private static int extractAnywhere(SlotAccess handler, ItemStack resource, int amount, SlotAccess.Tx tx) {
		int done = 0;
		for (int i = 0; i < handler.size() && done < amount; i++) {
			done += handler.extract(i, resource, amount - done, tx);
		}
		return done;
	}

	private static Map<Item, Integer> contents(BrewKettleBlockEntity kettle) {
		Map<Item, Integer> map = new HashMap<>();
		for (int i = 0; i < BrewKettleBlockEntity.INVENTORY_SIZE; i++) {
			ItemStack stack = kettle.stackIn(i);
			if (!stack.isEmpty()) map.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		return map;
	}

	// ------------------------------------------------------------------ hoppers

	/**
	 * Fully automatic, like FD's pot: magma -> hopper (conductor, pulls the output) -> kettle; a hopper above feeds
	 * ingredients, a hopper at the side feeds glass bottles. The hopper below ends up with 4 bottles of juice.
	 */
	private static void hopperAutomation(GameTestHelper helper) {
		BlockPos below = new BlockPos(2, 2, 2);
		BlockPos kettlePos = below.above();
		helper.setBlock(below.below(), Blocks.MAGMA_BLOCK);
		helper.setBlock(below, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
		helper.setBlock(kettlePos, KettleRegistry.kettle());
		helper.setBlock(kettlePos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		helper.setBlock(kettlePos.east(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
		BrewKettleBlockEntity kettle = kettle(helper, kettlePos);
		// Hoppers stack identical items into one slot (= one ingredient): preload three berries, the hopper adds the 4th and the sugar
		for (int i = 1; i < 4; i++) {
			kettle.setStack(i, new ItemStack(Items.SWEET_BERRIES));
		}
		HopperBlockEntity top = helper.getBlockEntity(kettlePos.above(), HopperBlockEntity.class);
		top.setItem(0, new ItemStack(Items.SWEET_BERRIES));
		top.setItem(1, new ItemStack(Items.SUGAR));
		helper.getBlockEntity(kettlePos.east(), HopperBlockEntity.class).setItem(0, new ItemStack(Items.GLASS_BOTTLE, 4));
		helper.succeedWhen(() -> {
			HopperBlockEntity bottom = helper.getBlockEntity(below, HopperBlockEntity.class);
			int juice = 0;
			for (int i = 0; i < bottom.getContainerSize(); i++) {
				ItemStack stack = bottom.getItem(i);
				helper.assertTrue(stack.isEmpty() || stack.is(BaseItems.SWEET_BERRY_JUICE.get()), "Only juice is pulled out below, got " + stack);
				juice += stack.getCount();
			}
			helper.assertValueEqual(juice, 4, "bottles of juice pulled out below");
			helper.assertValueEqual(kettle.getServings(), 0, "servings left");
		});
	}

	/**
	 * Side rules: the top takes ingredients into the grid and empty containers into the buffer, the sides take empty
	 * containers only, the bottom gives only the filled ones. Nothing pulls ingredients, empty containers or the brew.
	 */
	private static void hopperSides(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = kettleWithBrew(helper, KETTLE, 4);
		kettle.setStack(0, new ItemStack(Items.SWEET_BERRIES, 3));
		kettle.setStack(BrewKettleBlockEntity.OUTPUT_SLOT, new ItemStack(BaseItems.SWEET_BERRY_JUICE.get(), 2));
		BlockPos absolute = helper.absolutePos(KETTLE);
		SlotAccess top = Services.PLATFORM.itemAccess(helper.getLevel(), absolute, Direction.UP);
		SlotAccess side = Services.PLATFORM.itemAccess(helper.getLevel(), absolute, Direction.EAST);
		SlotAccess bottom = Services.PLATFORM.itemAccess(helper.getLevel(), absolute, Direction.DOWN);
		SlotAccess unsided = Services.PLATFORM.itemAccess(helper.getLevel(), absolute, null);
		ItemStack juice = SlotAccess.of(BaseItems.SWEET_BERRY_JUICE.get());
		try (SlotAccess.Tx tx = Services.PLATFORM.openTransaction()) {
			helper.assertValueEqual(insertAnywhere(side, SlotAccess.of(Items.GLASS_BOTTLE), 1, tx), 1, "a bottle into a side");
			helper.assertValueEqual(insertAnywhere(top, SlotAccess.of(Items.GLASS_BOTTLE), 1, tx), 1, "a bottle into the top");
			helper.assertValueEqual(kettle.getInventory().getAmount(BrewKettleBlockEntity.CONTAINER_SLOT), 2, "both bottles wait in the buffer");
			for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
				helper.assertFalse(kettle.getInventory().getStack(i).is(Items.GLASS_BOTTLE), "no bottle in the grid");
			}
			helper.assertValueEqual(insertAnywhere(top, SlotAccess.of(Items.SUGAR), 1, tx), 1, "sugar into the top goes to the grid");
			helper.assertValueEqual(insertAnywhere(side, SlotAccess.of(Items.SWEET_BERRIES), 1, tx), 0, "berries into a side");
			helper.assertValueEqual(insertAnywhere(side, SlotAccess.of(Items.STONE), 1, tx), 0, "stone into a side");
			helper.assertValueEqual(insertAnywhere(bottom, SlotAccess.of(Items.GLASS_BOTTLE), 1, tx), 0, "a bottle into the bottom");
			helper.assertValueEqual(insertAnywhere(bottom, SlotAccess.of(Items.SUGAR), 1, tx), 0, "sugar into the bottom");
			helper.assertValueEqual(insertAnywhere(unsided, SlotAccess.of(Items.BUCKET), 1, tx), 0, "a bucket can't join waiting bottles");

			helper.assertValueEqual(extractAnywhere(side, juice, 64, tx), 0, "juice out of a side");
			helper.assertValueEqual(extractAnywhere(top, juice, 64, tx), 0, "juice out of the top");
			helper.assertValueEqual(extractAnywhere(top, SlotAccess.of(Items.SWEET_BERRIES), 64, tx), 0, "ingredients out of the top");
			helper.assertValueEqual(extractAnywhere(unsided, SlotAccess.of(Items.SWEET_BERRIES), 64, tx), 0, "ingredients out without a side");
			helper.assertValueEqual(extractAnywhere(side, SlotAccess.of(Items.GLASS_BOTTLE), 64, tx), 0, "empty bottles out of a side");
			helper.assertValueEqual(extractAnywhere(bottom, SlotAccess.of(Items.GLASS_BOTTLE), 64, tx), 0, "empty bottles out of the bottom");
			helper.assertValueEqual(extractAnywhere(bottom, SlotAccess.of(Items.SWEET_BERRIES), 64, tx), 0, "ingredients out of the bottom");
			helper.assertValueEqual(extractAnywhere(bottom, juice, 64, tx), 2, "the filled bottles out of the bottom (not the brew)");
		}
		helper.assertValueEqual(kettle.getServings(), 4, "the brew itself never leaves through a hopper");
		helper.succeed();
	}

	/**
	 * Old worlds: containers and filled bottles that waited in the screen's old container and output slots are the
	 * hopper buffers now. They keep filling, a hopper below pulls them out, and breaking the kettle drops them.
	 */
	private static void oldSlotsAreBuffers(GameTestHelper helper) {
		BlockPos below = new BlockPos(2, 1, 2);
		helper.setBlock(below, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
		BrewKettleBlockEntity kettle = kettleWithBrew(helper, below.above(), 4);
		kettle.setStack(BrewKettleBlockEntity.CONTAINER_SLOT, new ItemStack(Items.GLASS_BOTTLE, 2));
		kettle.setStack(BrewKettleBlockEntity.OUTPUT_SLOT, new ItemStack(BaseItems.SWEET_BERRY_JUICE.get(), 3));
		helper.succeedWhen(() -> {
			HopperBlockEntity hopper = helper.getBlockEntity(below, HopperBlockEntity.class);
			int juice = 0;
			for (int i = 0; i < hopper.getContainerSize(); i++) {
				juice += hopper.getItem(i).is(BaseItems.SWEET_BERRY_JUICE.get()) ? hopper.getItem(i).getCount() : 0;
			}
			helper.assertValueEqual(juice, 5, "the old output and the two waiting bottles, filled, in the hopper");
			helper.assertValueEqual(kettle.getServings(), 2, "servings left");
			helper.assertTrue(kettle.stackIn(BrewKettleBlockEntity.CONTAINER_SLOT).isEmpty(), "no bottles waiting");
		});
	}

	// ------------------------------------------------------------------ K5 container click

	private static void containerClick(GameTestHelper helper) {
		kettleWithBrew(helper, KETTLE, 5);
		Player player = playerHolding(helper, new ItemStack(Items.GLASS_BOTTLE, 2));
		helper.useBlock(KETTLE, player);
		helper.assertValueEqual(countInInventory(player, BaseItems.SWEET_BERRY_JUICE.get()), 1, "juice bottles after one bottle click");
		helper.assertValueEqual(countInInventory(player, Items.GLASS_BOTTLE), 1, "empty bottles left");
		helper.assertValueEqual(kettle(helper, KETTLE).getServings(), 4, "servings after a bottle click");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
		helper.useBlock(KETTLE, player);
		helper.assertValueEqual(countInInventory(player, BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), 1, "juice buckets after a bucket click");
		helper.assertValueEqual(countInInventory(player, Items.BUCKET), 0, "empty buckets left");
		helper.assertValueEqual(kettle(helper, KETTLE).getServings(), 0, "servings after a bucket click");
		// with nothing left a bucket is kept (the screen opens instead)
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
		helper.useBlock(KETTLE, player);
		helper.assertValueEqual(countInInventory(player, Items.BUCKET), 1, "the bucket is kept when there is nothing to take");
		helper.succeed();
	}

	// ------------------------------------------------------------------ K6 keeps contents, save/load

	private static void keepsContentsWhenBroken(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = kettleWithBrew(helper, KETTLE, 6);
		kettle.setStack(0, new ItemStack(Items.SUGAR, 3));
		helper.getLevel().destroyBlock(helper.absolutePos(KETTLE), true);
		ItemStack kettleItem = ItemStack.EMPTY;
		int sugar = 0;
		for (ItemEntity entity : dropsAround(helper, KETTLE)) {
			if (entity.getItem().is(KettleRegistry.kettleItem())) kettleItem = entity.getItem().copy();
			if (entity.getItem().is(Items.SUGAR)) sugar += entity.getItem().getCount();
		}
		helper.assertFalse(kettleItem.isEmpty(), "The kettle drops itself");
		helper.assertValueEqual(sugar, 3, "ingredients drop next to it");
		KettleContents contents = BrewKettleItem.contentsOf(kettleItem);
		helper.assertTrue(contents.brew().is(BaseItems.SWEET_BERRY_JUICE.get()), "kettle item keeps the brew: " + contents);
		helper.assertValueEqual(contents.servings(), 6, "servings kept in the item");
		helper.assertTrue(contents.bucket().is(BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), "bucket form kept in the item");
		helper.assertTrue(kettleItem.getItem().isBarVisible(kettleItem), "kettle item shows its fill bar");
		// place it again: the brew is back and can still be served into a bucket
		BlockPos again = KETTLE.east();
		helper.setBlock(again, KettleRegistry.kettle());
		BrewKettleBlockEntity placed = kettle(helper, again);
		placed.applyComponentsFromItemStack(kettleItem);
		helper.assertValueEqual(placed.getServings(), 6, "servings after placing it again");
		helper.assertTrue(placed.serveInto(new ItemStack(Items.BUCKET), null).is(BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), "bucket from a re-placed kettle");
		helper.succeed();
	}

	private static void saveLoadRoundtrip(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = kettleWithBrew(helper, KETTLE, 5);
		kettle.setStack(1, new ItemStack(Items.SWEET_BERRIES, 12));
		kettle.setStack(BrewKettleBlockEntity.CONTAINER_SLOT, new ItemStack(Items.GLASS_BOTTLE, 3));
		var tag = kettle.saveWithFullMetadata(helper.getLevel().registryAccess());
		helper.setBlock(KETTLE.east(), KettleRegistry.kettle());
		BrewKettleBlockEntity copy = kettle(helper, KETTLE.east());
		copy.loadWithComponents(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
		helper.assertValueEqual(copy.getServings(), 5, "servings after load");
		helper.assertTrue(copy.getBucketForm().is(BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), "bucket form after load");
		helper.assertValueEqual(copy.stackIn(1).getCount(), 12, "ingredients after load");
		helper.assertValueEqual(copy.stackIn(BrewKettleBlockEntity.CONTAINER_SLOT).getCount(), 3, "containers after load");
		helper.succeed();
	}

	// ------------------------------------------------------------------ K7 comparator

	private static void comparatorOutput(GameTestHelper helper) {
		helper.setBlock(KETTLE, KettleRegistry.kettle());
		BlockPos comparator = KETTLE.north();
		helper.setBlock(comparator.below(), Blocks.STONE);
		helper.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.SOUTH));
		BrewKettleBlockEntity kettle = kettle(helper, KETTLE);
		helper.assertValueEqual(signal(helper), 0, "empty kettle");
		kettle.setStack(0, new ItemStack(Items.SWEET_BERRIES, 64));
		helper.assertValueEqual(signal(helper), 2, "one full slot of nine");
		for (int i = 1; i < 6; i++) {
			kettle.setStack(i, new ItemStack(Items.SUGAR, 64));
		}
		helper.assertValueEqual(signal(helper), 10, "six full slots of nine");
		helper.succeedWhen(() -> {
			ComparatorBlockEntity be = helper.getBlockEntity(comparator, ComparatorBlockEntity.class);
			helper.assertValueEqual(be.getOutputSignal(), 10, "a real comparator reads the kettle");
		});
	}

	private static int signal(GameTestHelper helper) {
		BlockPos absolute = helper.absolutePos(KETTLE);
		return helper.getLevel().getBlockState(absolute).getAnalogOutputSignal(helper.getLevel(), absolute, Direction.NORTH);
	}

	// ------------------------------------------------------------------ K1 support states, K2 lid

	private static void supportStates(GameTestHelper helper) {
		helper.setBlock(HEAT, litCampfire());
		helper.placeAt(playerHolding(helper, new ItemStack(KettleRegistry.kettleItem())), new ItemStack(KettleRegistry.kettleItem()), HEAT, Direction.UP);
		BrewKettleBlockEntity kettle = kettle(helper, KETTLE);
		helper.assertValueEqual(helper.getBlockState(KETTLE).getValue(BrewKettleBlock.SUPPORT), KettleSupport.TRAY, "tray over a campfire");
		Player player = playerHolding(helper, ItemStack.EMPTY);
		player.setShiftKeyDown(true);
		helper.useBlock(KETTLE, player);
		helper.assertValueEqual(helper.getBlockState(KETTLE).getValue(BrewKettleBlock.SUPPORT), KettleSupport.HANDLE, "sneak + empty hand hangs it");
		helper.useBlock(KETTLE, player);
		helper.assertValueEqual(helper.getBlockState(KETTLE).getValue(BrewKettleBlock.SUPPORT), KettleSupport.TRAY, "again puts it back on the tray");
		helper.setBlock(HEAT, Blocks.STONE);
		helper.assertValueEqual(helper.getBlockState(KETTLE).getValue(BrewKettleBlock.SUPPORT), KettleSupport.NONE, "no tray once the campfire is gone");
		helper.assertTrue(kettle.getBrew().isEmpty(), "toggling never touches the contents");
		// placing it against a ceiling hangs it
		BlockPos hanging = new BlockPos(0, 3, 0);
		helper.setBlock(hanging.above(), Blocks.OAK_PLANKS);
		Player placer = playerHolding(helper, new ItemStack(KettleRegistry.kettleItem()));
		helper.placeAt(placer, new ItemStack(KettleRegistry.kettleItem()), hanging.above(), Direction.DOWN);
		helper.assertValueEqual(helper.getBlockState(hanging).getValue(BrewKettleBlock.SUPPORT), KettleSupport.HANDLE, "placed under a block it hangs");
		helper.succeed();
	}

	private static void lidOpensWithScreen(GameTestHelper helper) {
		helper.setBlock(KETTLE, KettleRegistry.kettle());
		BrewKettleBlockEntity kettle = kettle(helper, KETTLE);
		@SuppressWarnings("removal")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			// not the block's own path: the mock connection can't carry NeoForge's extra-data screen payload
			Services.PLATFORM.openMenuForGameTest(player, kettle, kettle.getBlockPos());
			helper.assertTrue(player.containerMenu instanceof BrewKettleMenu, "the kettle screen opens");
			helper.assertTrue(helper.getBlockState(KETTLE).getValue(BrewKettleBlock.OPEN), "the lid lifts while the screen is open");
			helper.assertValueEqual(kettle.getOpenCount(), 1, "viewers");
			player.closeContainer();
			helper.assertFalse(helper.getBlockState(KETTLE).getValue(BrewKettleBlock.OPEN), "the lid closes with the screen");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/**
	 * Review fix: a screen left open on a kettle that another player breaks and replaces must stop working. Breaking
	 * drops the items once; the old screen can't take them a second time, and the new kettle starts empty.
	 */
	private static void staleMenuCannotDupe(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = kettleWithBrew(helper, KETTLE, 4);
		kettle.setStack(0, new ItemStack(Items.SUGAR, 5));
		kettle.setStack(BrewKettleBlockEntity.CONTAINER_SLOT, new ItemStack(Items.GLASS_BOTTLE, 2));
		kettle.setStack(BrewKettleBlockEntity.OUTPUT_SLOT, new ItemStack(BaseItems.SWEET_BERRY_JUICE.get(), 3));
		BlockPos absolute = helper.absolutePos(KETTLE);
		@SuppressWarnings("removal")
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		try {
			viewer.snapTo(Vec3.atBottomCenterOf(absolute).add(1.5D, 0.0D, 0.0D));
			Services.PLATFORM.openMenuForGameTest(viewer, kettle, kettle.getBlockPos());
			helper.assertTrue(viewer.containerMenu instanceof BrewKettleMenu, "the kettle screen opens");
			BrewKettleMenu menu = (BrewKettleMenu) viewer.containerMenu;
			helper.assertTrue(menu.stillValid(viewer), "the screen is valid while its kettle stands");

			helper.getLevel().destroyBlock(absolute, true);
			helper.setBlock(KETTLE, KettleRegistry.kettle());
			helper.assertFalse(menu.stillValid(viewer), "the old screen is invalid once its kettle is broken and replaced");

			// whatever the stale screen still tries, nothing comes out of it
			helper.assertTrue(menu.quickMoveStack(viewer, 0).isEmpty(), "shift-click on the old ingredients");
			helper.assertTrue(kettle.serveInto(new ItemStack(Items.BUCKET), viewer).isEmpty(), "the old kettle serves no brew");
			helper.assertValueEqual(countInInventory(viewer, Items.SUGAR), 0, "sugar taken through the old screen");
			helper.assertValueEqual(countInInventory(viewer, Items.GLASS_BOTTLE), 0, "bottles taken through the old screen");
			helper.assertValueEqual(countInInventory(viewer, BaseItems.SWEET_BERRY_JUICE.get()), 0, "juice taken through the old screen");
			helper.assertValueEqual(countInInventory(viewer, BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), 0, "brew taken through the old screen");

			// the break dropped everything exactly once, and the brew rides in the kettle item
			int sugar = 0, glass = 0, juice = 0, servings = 0;
			for (ItemEntity entity : dropsAround(helper, KETTLE)) {
				ItemStack stack = entity.getItem();
				if (stack.is(Items.SUGAR)) sugar += stack.getCount();
				else if (stack.is(Items.GLASS_BOTTLE)) glass += stack.getCount();
				else if (stack.is(BaseItems.SWEET_BERRY_JUICE.get())) juice += stack.getCount();
				else if (stack.is(KettleRegistry.kettleItem())) servings += BrewKettleItem.contentsOf(stack).servings();
			}
			helper.assertValueEqual(sugar, 5, "sugar dropped");
			helper.assertValueEqual(glass, 2, "bottles dropped");
			helper.assertValueEqual(juice, 3, "juice dropped");
			helper.assertValueEqual(servings, 4, "servings kept in the dropped kettle");
			helper.assertTrue(contents(kettle(helper, KETTLE)).isEmpty(), "the new kettle starts empty");
			viewer.doTick();
			helper.assertFalse(viewer.containerMenu instanceof BrewKettleMenu, "the server closes the stale screen");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(viewer);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ K8 copper

	private static void weatheringKeepsContents(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = kettleWithBrew(helper, KETTLE, 4);
		helper.setBlock(KETTLE, helper.getBlockState(KETTLE).setValue(BrewKettleBlock.FACING, Direction.EAST));
		WeatherState[] order = WeatherState.values();
		for (int i = 0; i < order.length - 1; i++) {
			BlockState state = helper.getBlockState(KETTLE);
			helper.assertTrue(state.is(KettleRegistry.KETTLES.get(order[i]).get()), "stage " + order[i]);
			helper.assertTrue(state.isRandomlyTicking(), order[i] + " kettle weathers over time");
			Optional<BlockState> next = ((WeatheringCopper) state.getBlock()).getNext(state);
			helper.assertTrue(next.isPresent(), order[i] + " has a next stage");
			helper.getLevel().setBlockAndUpdate(helper.absolutePos(KETTLE), next.get());
			helper.assertTrue(helper.getBlockEntity(KETTLE, BrewKettleBlockEntity.class) == kettle, "the same block entity stays");
		}
		BlockState oxidized = helper.getBlockState(KETTLE);
		helper.assertTrue(oxidized.is(KettleRegistry.KETTLES.get(WeatherState.OXIDIZED).get()), "fully oxidized");
		helper.assertFalse(oxidized.isRandomlyTicking(), "oxidized is the last stage");
		helper.assertValueEqual(oxidized.getValue(BrewKettleBlock.FACING), Direction.EAST, "facing kept through weathering");
		helper.assertValueEqual(kettle.getServings(), 4, "brew kept through weathering");
		helper.assertFalse(KettleRegistry.WAXED_KETTLES.get(WeatherState.EXPOSED).get().defaultBlockState().isRandomlyTicking(), "waxed kettles don't weather");
		helper.succeed();
	}

	private static void waxAndScrape(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = kettleWithBrew(helper, KETTLE, 4);
		helper.setBlock(KETTLE, KettleRegistry.KETTLES.get(WeatherState.WEATHERED).get().withPropertiesOf(helper.getBlockState(KETTLE))
			.setValue(BrewKettleBlock.FACING, Direction.WEST));
		kettle = kettle(helper, KETTLE);
		Player player = playerHolding(helper, new ItemStack(Items.HONEYCOMB, 2));
		helper.useBlock(KETTLE, player);
		helper.assertTrue(helper.getBlockState(KETTLE).is(KettleRegistry.WAXED_KETTLES.get(WeatherState.WEATHERED).get()), "honeycomb waxes it");
		helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "one honeycomb used");
		helper.useBlock(KETTLE, player);
		helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "a waxed kettle takes no more wax");

		ItemStack axe = new ItemStack(Items.IRON_AXE);
		player.setItemInHand(InteractionHand.MAIN_HAND, axe);
		helper.useBlock(KETTLE, player);
		helper.assertTrue(helper.getBlockState(KETTLE).is(KettleRegistry.KETTLES.get(WeatherState.WEATHERED).get()), "the axe takes the wax off first");
		helper.useBlock(KETTLE, player);
		helper.assertTrue(helper.getBlockState(KETTLE).is(KettleRegistry.KETTLES.get(WeatherState.EXPOSED).get()), "then scrapes one stage");
		helper.useBlock(KETTLE, player);
		helper.assertTrue(helper.getBlockState(KETTLE).is(KettleRegistry.kettle()), "down to fresh copper");
		helper.assertValueEqual(axe.getDamageValue(), 3, "axe damage");
		helper.assertValueEqual(helper.getBlockState(KETTLE).getValue(BrewKettleBlock.FACING), Direction.WEST, "facing kept");
		helper.assertValueEqual(kettle(helper, KETTLE).getServings(), 4, "brew kept through waxing and scraping");
		helper.assertTrue(kettle(helper, KETTLE) == kettle, "the same block entity stays");
		// a fresh kettle has nothing to scrape: the axe click opens the screen instead and the axe isn't used
		helper.useBlock(KETTLE, player);
		helper.assertValueEqual(axe.getDamageValue(), 3, "no damage when there is nothing to scrape");
		helper.succeed();
	}

	private static void lightningCleansCopper(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = kettleWithBrew(helper, KETTLE, 3);
		helper.setBlock(KETTLE, KettleRegistry.KETTLES.get(WeatherState.OXIDIZED).get().withPropertiesOf(helper.getBlockState(KETTLE)));
		LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(bolt != null, "lightning bolt");
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(KETTLE)).add(0.0D, 0.5D, 0.0D);
		bolt.snapTo(at);
		bolt.setVisualOnly(false);
		helper.getLevel().addFreshEntity(bolt);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(KETTLE).is(KettleRegistry.kettle()), "lightning cleans the kettle back to fresh copper, got " + helper.getBlockState(KETTLE));
			helper.assertValueEqual(kettle(helper, KETTLE).getServings(), 3, "brew kept after lightning");
		});
	}

	// ------------------------------------------------------------------ recipe, recipe book (K9), bases (D3, D4)

	private static void recipeAndBook(GameTestHelper helper) {
		Optional<RecipeHolder<?>> holder = helper.getLevel().recipeAccess().byKey(SWEET_BERRY_JUICE_RECIPE);
		helper.assertTrue(holder.isPresent(), "the sweet berry juice recipe is loaded");
		helper.assertTrue(holder.get().value() instanceof BrewingRecipe, "it is a brewing recipe");
		BrewingRecipe recipe = (BrewingRecipe) holder.get().value();
		helper.assertValueEqual(recipe.servings(), 4, "servings per batch");
		helper.assertValueEqual(recipe.brewingTime(), 200, "brewing time");
		helper.assertTrue(recipe.bucket().is(BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), "bucket form");
		helper.assertTrue(recipe.recipeBookCategory() == KettleRegistry.BREWING_JUICES.get(), "recipe book tab: juices");
		helper.assertFalse(recipe.placementInfo().isImpossibleToPlace(), "the recipe book can place it");
		// The kettle's own book type, with its own settings that are saved and copied apart from the furnace's
		RecipeBookType type = Services.PLATFORM.brewingRecipeBookType();
		helper.assertValueEqual(type.name(), "VINEANDBARREL_BREWING", "the kettle's recipe book type");
		RecipeBookSettings settings = new RecipeBookSettings();
		settings.setOpen(type, true);
		settings.setFiltering(type, true);
		helper.assertTrue(settings.isOpen(type) && settings.isFiltering(type), "the kettle book keeps its settings");
		helper.assertFalse(settings.isOpen(RecipeBookType.FURNACE) || settings.isFiltering(RecipeBookType.FURNACE), "apart from the furnace book's");
		RecipeBookSettings copy = settings.copy();
		helper.assertTrue(copy.isOpen(type) && copy.isFiltering(type), "a copy keeps the kettle book's settings");
		RecipeBookSettings loaded = RecipeBookSettings.MAP_CODEC.codec().parse(NbtOps.INSTANCE,
				RecipeBookSettings.MAP_CODEC.codec().encodeStart(NbtOps.INSTANCE, settings).getOrThrow()).getOrThrow();
		helper.assertTrue(loaded.isOpen(type) && loaded.isFiltering(type), "the kettle book's settings are saved with the player");
		helper.assertTrue(helper.getLevel().recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, VineAndBarrel.id("brew_kettle"))).isPresent(), "the kettle crafting recipe is loaded");
		helper.succeed();
	}

	/** D3: drinks like a honey bottle (vanilla drink sound and animation, light food, always drinkable) and gives the bottle back. */
	private static void juiceBottleDrinks(GameTestHelper helper) {
		for (Item juice : List.of(BaseItems.SWEET_BERRY_JUICE.get(), BaseItems.GRAPE_JUICE.get())) {
			ItemStack stack = new ItemStack(juice);
			Consumable consumable = stack.get(DataComponents.CONSUMABLE);
			helper.assertTrue(consumable != null, juice + " is drinkable");
			helper.assertValueEqual(consumable.animation(), ItemUseAnimation.DRINK, "drink animation");
			helper.assertTrue(consumable.sound().value() == SoundEvents.GENERIC_DRINK.value(), "vanilla drink sound");
			helper.assertTrue(stack.get(DataComponents.FOOD) != null && stack.get(DataComponents.FOOD).canAlwaysEat(), "light food, always drinkable");
			helper.assertValueEqual(stack.getMaxStackSize(), 16, "stack size");

			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.getFoodData().setFoodLevel(10);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			ItemStack result = stack.finishUsingItem(helper.getLevel(), player);
			helper.assertTrue(result.is(Items.GLASS_BOTTLE), juice + " gives its glass bottle back, got " + result);
			helper.assertValueEqual(player.getFoodData().getFoodLevel(), 12, "food after one juice");
		}
		for (Item wort : List.of(BaseItems.BARLEY_WORT_BOTTLE.get(), BaseItems.WHEAT_WORT_BOTTLE.get())) {
			helper.assertTrue(new ItemStack(wort).get(DataComponents.CONSUMABLE) == null, wort + " is not drinkable (only juices are)");
		}
		helper.succeed();
	}

	/** D4: like the milk bucket, a bucket of juice or wort never places a fluid. */
	private static void bucketNeverPlacesFluid(GameTestHelper helper) {
		BlockPos floor = new BlockPos(2, 1, 2);
		helper.setBlock(floor, Blocks.STONE);
		for (Item bucket : List.of(BaseItems.GRAPE_JUICE_BUCKET.get(), BaseItems.SWEET_BERRY_JUICE_BUCKET.get(), BaseItems.BARLEY_WORT_BUCKET.get(), BaseItems.WHEAT_WORT_BUCKET.get())) {
			Player player = playerHolding(helper, new ItemStack(bucket));
			BlockPos absolute = helper.absolutePos(floor);
			InteractionResult result = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.5, 0), Direction.UP, absolute, false)));
			helper.assertFalse(result.consumesAction(), bucket + " must not be placed");
			helper.assertTrue(helper.getBlockState(floor.above()).isAir(), "nothing placed above the floor by " + bucket);
			helper.assertTrue(player.getMainHandItem().is(bucket), bucket + " stays in the hand");
			helper.assertValueEqual(new ItemStack(bucket).getMaxStackSize(), 1, "buckets stack to 1");
		}
		helper.succeed();
	}

	private static void baseItemsAndTags(GameTestHelper helper) {
		TagKey<Item> drinks = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("farmersdelight", "drinks"));
		helper.assertTrue(new ItemStack(BaseItems.GRAPE_JUICE.get()).is(drinks), "grape juice is an FD drink");
		helper.assertTrue(new ItemStack(BaseItems.SWEET_BERRY_JUICE.get()).is(drinks), "sweet berry juice is an FD drink");
		helper.assertTrue(new ItemStack(BaseItems.WHITE_GRAPE_JUICE.get()).is(drinks), "white grape juice is an FD drink");
		helper.assertTrue(new ItemStack(BaseItems.APPLE_JUICE.get()).is(drinks), "apple juice is an FD drink");
		helper.assertFalse(new ItemStack(BaseItems.BARLEY_WORT_BOTTLE.get()).is(drinks), "wort is not a drink");
		helper.assertFalse(new ItemStack(BaseItems.HONEY_MUST_BOTTLE.get()).is(drinks), "honey must is not a drink");
		helper.assertTrue(new ItemStack(BaseItems.APPLE_JUICE.get()).has(net.minecraft.core.component.DataComponents.FOOD), "juices drink like a honey bottle");
		helper.assertFalse(new ItemStack(BaseItems.HONEY_MUST_BOTTLE.get()).has(net.minecraft.core.component.DataComponents.FOOD), "must is not drunk, like wort");
		for (Item bottle : List.of(BaseItems.GRAPE_JUICE.get(), BaseItems.SWEET_BERRY_JUICE.get(), BaseItems.BARLEY_WORT_BOTTLE.get(), BaseItems.WHEAT_WORT_BOTTLE.get(),
				BaseItems.WHITE_GRAPE_JUICE.get(), BaseItems.APPLE_JUICE.get(), BaseItems.HONEY_MUST_BOTTLE.get())) {
			ItemStack stack = new ItemStack(bottle);
			helper.assertTrue(stack.getItem().getCraftingRemainder() != null && stack.getItem().getCraftingRemainder().item().value() == Items.GLASS_BOTTLE, bottle + " leaves a glass bottle");
		}
		for (Block block : KettleRegistry.allKettleBlocks()) {
			helper.assertTrue(block.defaultBlockState().is(KettleTags.BREW_KETTLES), block + " is tagged as a kettle");
			helper.assertTrue(KettleRegistry.BREW_KETTLE_BE.get().isValid(block.defaultBlockState()), block + " has the kettle block entity");
		}
		helper.assertTrue(new ItemStack(Items.GLASS_BOTTLE).is(KettleTags.KETTLE_CONTAINERS) && new ItemStack(Items.BUCKET).is(KettleTags.KETTLE_CONTAINERS), "kettle containers");
		helper.succeed();
	}

	// ------------------------------------------------------------------ review fixes

	private static int countIn(Player player, BrewKettleBlockEntity kettle, Item item) {
		int count = countInInventory(player, item);
		for (int i = 0; i < BrewKettleBlockEntity.INVENTORY_SIZE; i++) {
			ItemStack stack = kettle.stackIn(i);
			if (stack.is(item)) count += stack.getCount();
		}
		return count;
	}

	/**
	 * Recipe book placement never loses or makes items: split berry stacks (30 + 64) can't fill a slot from one stack,
	 * which is where vanilla's placer grows the slot's stack in place. Placing again (max and one more) changes nothing in total.
	 */
	private static void recipeBookConservesItems(GameTestHelper helper) {
		helper.setBlock(KETTLE, KettleRegistry.kettle());
		BrewKettleBlockEntity kettle = kettle(helper, KETTLE);
		RecipeHolder<?> recipe = helper.getLevel().recipeAccess().byKey(SWEET_BERRY_JUICE_RECIPE).orElseThrow();
		@SuppressWarnings("removal")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(Items.SWEET_BERRIES, 30));
			player.getInventory().setItem(1, new ItemStack(Items.SWEET_BERRIES, 64));
			player.getInventory().setItem(2, new ItemStack(Items.SUGAR, 64));
			Services.PLATFORM.openMenuForGameTest(player, kettle, kettle.getBlockPos());
			helper.assertTrue(player.containerMenu instanceof BrewKettleMenu, "the kettle screen opens");
			BrewKettleMenu menu = (BrewKettleMenu) player.containerMenu;

			menu.handlePlacement(true, false, recipe, helper.getLevel(), player.getInventory());
			helper.assertValueEqual(countIn(player, kettle, Items.SWEET_BERRIES), 94, "berries after placing with max items");
			helper.assertValueEqual(countIn(player, kettle, Items.SUGAR), 64, "sugar after placing with max items");
			for (int i = 0; i < 4; i++) {
				helper.assertTrue(kettle.stackIn(i).is(Items.SWEET_BERRIES), "berries placed in slot " + i);
				helper.assertValueEqual(kettle.stackIn(i).getCount(), 23, "berries in slot " + i + " (94 berries make 23 batches)");
			}
			helper.assertValueEqual(kettle.stackIn(4).getCount(), 23, "sugar placed");

			menu.handlePlacement(true, false, recipe, helper.getLevel(), player.getInventory());
			helper.assertValueEqual(countIn(player, kettle, Items.SWEET_BERRIES), 94, "berries after placing again with max items");
			helper.assertValueEqual(countIn(player, kettle, Items.SUGAR), 64, "sugar after placing again with max items");

			// from a part-filled grid: one more batch at a time, then max again
			for (int click = 0; click < 3; click++) {
				menu.handlePlacement(false, false, recipe, helper.getLevel(), player.getInventory());
				helper.assertValueEqual(countIn(player, kettle, Items.SWEET_BERRIES), 94, "berries after a single placement " + click);
				helper.assertValueEqual(countIn(player, kettle, Items.SUGAR), 64, "sugar after a single placement " + click);
			}
			menu.handlePlacement(true, false, recipe, helper.getLevel(), player.getInventory());
			helper.assertValueEqual(countIn(player, kettle, Items.SWEET_BERRIES), 94, "berries after the last placement");
			helper.assertValueEqual(countIn(player, kettle, Items.SUGAR), 64, "sugar after the last placement");
			player.closeContainer();
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/** Nothing preloaded: a hopper above stacks the repeated ingredient into one slot, and the kettle still brews it. */
	private static void hopperFeedsFromScratch(GameTestHelper helper, List<ItemStack> feed, Item bottle) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, litCampfire());
		helper.setBlock(KETTLE.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		HopperBlockEntity hopper = helper.getBlockEntity(KETTLE.above(), HopperBlockEntity.class);
		for (int i = 0; i < feed.size(); i++) {
			hopper.setItem(i, feed.get(i).copy());
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(hopper.isEmpty(), "the hopper fed everything");
			helper.assertTrue(kettle.getBrew().is(bottle), "Expected " + bottle + ", got " + kettle.getBrew());
			helper.assertValueEqual(kettle.getServings(), 4, "servings from a hopper-fed batch");
			for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
				helper.assertTrue(kettle.stackIn(i).isEmpty(), "Ingredient slot " + i + " should be used up");
			}
		});
	}

	/** 8 berries in one slot and 2 sugar in another are exactly two batches: 8 servings, nothing left over. */
	private static void pooledBatches(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, litCampfire());
		kettle.setStack(0, new ItemStack(Items.SWEET_BERRIES, 8));
		kettle.setStack(1, new ItemStack(Items.SUGAR, 2));
		helper.succeedWhen(() -> {
			helper.assertValueEqual(kettle.getServings(), 8, "servings from 8 berries and 2 sugar in two slots");
			for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
				helper.assertTrue(kettle.stackIn(i).isEmpty(), "Ingredient slot " + i + " should be used up");
			}
		});
	}

	private record PoolCase(List<ItemStack> slots, int @Nullable [] taken) {
	}

	/** What one batch takes from each slot: FD's layout one per slot, a pooled stack all at once, no match with leftovers or too few. */
	private static void pooledMatchingRules(GameTestHelper helper) {
		helper.setBlock(KETTLE, KettleRegistry.kettle());
		BrewKettleBlockEntity kettle = kettle(helper, KETTLE);
		BrewingRecipe recipe = (BrewingRecipe) helper.getLevel().recipeAccess().byKey(SWEET_BERRY_JUICE_RECIPE).orElseThrow().value();
		ItemStack none = ItemStack.EMPTY;
		List<PoolCase> cases = List.of(
			// FD's layout, 3 batches: one item from every slot
			new PoolCase(List.of(new ItemStack(Items.SWEET_BERRIES, 3), new ItemStack(Items.SWEET_BERRIES, 3), new ItemStack(Items.SWEET_BERRIES, 3),
				new ItemStack(Items.SWEET_BERRIES, 3), new ItemStack(Items.SUGAR, 3), none), new int[]{1, 1, 1, 1, 1, 0}),
			// a hopper's layout
			new PoolCase(List.of(new ItemStack(Items.SWEET_BERRIES, 4), new ItemStack(Items.SUGAR), none, none, none, none), new int[]{4, 1, 0, 0, 0, 0}),
			// berries split over two slots, sugar in between
			new PoolCase(List.of(new ItemStack(Items.SWEET_BERRIES, 5), new ItemStack(Items.SUGAR, 2), new ItemStack(Items.SWEET_BERRIES, 2), none, none, none),
				new int[]{2, 1, 2, 0, 0, 0}),
			// too few berries, no sugar, a foreign leftover, a leftover from another recipe
			new PoolCase(List.of(new ItemStack(Items.SWEET_BERRIES, 3), new ItemStack(Items.SUGAR, 5), none, none, none, none), null),
			new PoolCase(List.of(new ItemStack(Items.SWEET_BERRIES, 64), none, none, none, none, none), null),
			new PoolCase(List.of(new ItemStack(Items.SWEET_BERRIES, 4), new ItemStack(Items.SUGAR), new ItemStack(Items.STONE), none, none, none), null),
			new PoolCase(List.of(new ItemStack(Items.SWEET_BERRIES, 4), new ItemStack(Items.SUGAR), new ItemStack(VBItems.RED_GRAPES.get()), none, none, none), null));
		for (PoolCase c : cases) {
			for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
				kettle.setStack(i, c.slots().get(i).copy());
			}
			KettleRecipeInput input = new KettleRecipeInput(kettle.getInventory());
			int[] taken = recipe.consumption(input);
			helper.assertTrue(Arrays.equals(taken, c.taken()), "consumption of " + c.slots() + ": expected "
				+ Arrays.toString(c.taken()) + ", got " + Arrays.toString(taken));
			helper.assertValueEqual(recipe.matches(input, helper.getLevel()), c.taken() != null, "matches " + c.slots());
			if (taken != null) {
				helper.assertValueEqual(Arrays.stream(taken).sum(), recipe.ingredients().size(), "items one batch takes");
			}
		}
		helper.succeed();
	}

	/** A kettle holding grape juice never brews sweet berry juice on top of it, and keeps the ingredients. */
	private static void refusesOtherBase(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, litCampfire());
		fillBrew(kettle, BaseItems.GRAPE_JUICE.get(), BaseItems.GRAPE_JUICE_BUCKET.get(), 4);
		loadBatch(kettle, 1);
		helper.runAfterDelay(260, () -> {
			helper.assertTrue(kettle.getBrew().is(BaseItems.GRAPE_JUICE.get()), "the grape juice stays, got " + kettle.getBrew());
			helper.assertValueEqual(kettle.getServings(), 4, "grape juice servings");
			helper.assertTrue(kettle.getBucketForm().is(BaseItems.GRAPE_JUICE_BUCKET.get()), "grape juice bucket form");
			for (int i = 0; i < 5; i++) {
				helper.assertValueEqual(kettle.stackIn(i).getCount(), 1, "ingredient slot " + i + " untouched");
			}
			helper.succeed();
		});
	}

	/** 60 servings plus two batches: the first batch fills to 64, the second waits with its ingredients untouched. */
	private static void servingCap(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, litCampfire());
		fillBrew(kettle, BaseItems.SWEET_BERRY_JUICE.get(), BaseItems.SWEET_BERRY_JUICE_BUCKET.get(), 60);
		loadBatch(kettle, 2);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(kettle.getServings(), 64, "servings up to the cap"))
			.thenIdle(260)
			.thenExecute(() -> {
				helper.assertValueEqual(kettle.getServings(), BrewKettleBlockEntity.MAX_SERVINGS, "never more than 64 servings");
				for (int i = 0; i < 5; i++) {
					helper.assertValueEqual(kettle.stackIn(i).getCount(), 1, "the second batch waits in slot " + i);
				}
			})
			.thenSucceed();
	}

	/** Every slot full, the brew at 64 servings: the comparator reads 15, never more. */
	private static void comparatorCapsAt15(GameTestHelper helper) {
		helper.setBlock(KETTLE, KettleRegistry.kettle());
		BrewKettleBlockEntity kettle = kettle(helper, KETTLE);
		fillBrew(kettle, BaseItems.SWEET_BERRY_JUICE.get(), BaseItems.SWEET_BERRY_JUICE_BUCKET.get(), BrewKettleBlockEntity.MAX_SERVINGS);
		for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
			kettle.setStack(i, new ItemStack(Items.SWEET_BERRIES, 64));
		}
		kettle.setStack(BrewKettleBlockEntity.CONTAINER_SLOT, new ItemStack(Items.GLASS_BOTTLE, 64));
		kettle.setStack(BrewKettleBlockEntity.OUTPUT_SLOT, new ItemStack(BaseItems.SWEET_BERRY_JUICE.get(), 16));
		helper.assertValueEqual(kettle.getServings(), BrewKettleBlockEntity.MAX_SERVINGS, "servings");
		helper.assertValueEqual(kettle.comparatorSignal(), 15, "a full kettle");
		helper.assertValueEqual(signal(helper), 15, "a full kettle through the block");
		// the brew slot alone, full: one full slot of nine
		BrewKettleBlockEntity brewOnly = kettleWithBrew(helper, KETTLE.east(), BrewKettleBlockEntity.MAX_SERVINGS);
		helper.assertValueEqual(brewOnly.comparatorSignal(), 2, "a full brew slot is one full slot");
		helper.succeed();
	}

	/**
	 * The screen is only the grid and the player's inventory. Shift-clicking an ingredient out moves what fits and
	 * leaves the rest; shift-clicking from the inventory fills the grid. Nothing is lost or made.
	 */
	private static void quickMoveConserves(GameTestHelper helper) {
		helper.setBlock(KETTLE, KettleRegistry.kettle());
		BrewKettleBlockEntity kettle = kettle(helper, KETTLE);
		kettle.setStack(0, new ItemStack(Items.SUGAR, 5));
		@SuppressWarnings("removal")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.getInventory().clearContent();
			for (int i = 0; i < 36; i++) {
				player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
			}
			player.getInventory().setItem(9, new ItemStack(Items.SUGAR, 62));
			Services.PLATFORM.openMenuForGameTest(player, kettle, kettle.getBlockPos());
			helper.assertTrue(player.containerMenu instanceof BrewKettleMenu, "the kettle screen opens");
			BrewKettleMenu menu = (BrewKettleMenu) player.containerMenu;
			helper.assertValueEqual(menu.slots.size(), BrewKettleBlockEntity.INPUT_SLOTS + 36, "the grid and the player's inventory, nothing else");

			menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
			helper.assertValueEqual(countInInventory(player, Items.SUGAR), 64, "sugar that fit into the full inventory");
			helper.assertValueEqual(kettle.stackIn(0).getCount(), 3, "sugar left in the grid");
			helper.assertValueEqual(countInInventory(player, Items.STONE), 35 * 64, "stone untouched");

			player.getInventory().setItem(20, ItemStack.EMPTY);
			menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
			helper.assertValueEqual(countInInventory(player, Items.SUGAR), 67, "all sugar in the inventory");
			helper.assertTrue(kettle.stackIn(0).isEmpty(), "grid slot emptied");

			// and back: the inventory's sugar fills the grid
			int sugarSlot = BrewKettleMenu.INDEX_INVENTORY + 9 - 9;
			for (int i = BrewKettleMenu.INDEX_INVENTORY; i < menu.slots.size(); i++) {
				if (menu.getSlot(i).getItem().is(Items.SUGAR) && menu.getSlot(i).getItem().getCount() == 64) {
					sugarSlot = i;
				}
			}
			menu.clicked(sugarSlot, 0, ContainerInput.QUICK_MOVE, player);
			int inGrid = 0;
			for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
				inGrid += kettle.stackIn(i).is(Items.SUGAR) ? kettle.stackIn(i).getCount() : 0;
			}
			helper.assertValueEqual(inGrid + countInInventory(player, Items.SUGAR), 67, "sugar is conserved moving into the grid");
			helper.assertValueEqual(inGrid, 64, "a stack moved into the grid");
			player.closeContainer();
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ K5 right-click filling, like a cauldron

	/** A right-click the way the server runs it: the block first (unless sneaking with something in hand), then the item. */
	private static InteractionResult rightClick(GameTestHelper helper, ServerPlayer player, ItemStack held) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		BlockPos absolute = helper.absolutePos(KETTLE);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
		return player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
	}

	/** The block's own answer to a right-click with {@code held}, without opening anything. */
	private static InteractionResult blockAnswer(GameTestHelper helper, ItemStack held) {
		Player player = playerHolding(helper, held);
		BlockPos absolute = helper.absolutePos(KETTLE);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
		return helper.getBlockState(KETTLE).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
	}

	@SuppressWarnings("removal")
	private static ServerPlayer playerAtKettle(GameTestHelper helper, GameType type) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(type);
		player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(KETTLE)).add(1.5D, 0.0D, 0.0D));
		player.getInventory().clearContent();
		return player;
	}

	/**
	 * A real brew, then right-clicks: a bottle takes 1 serving, a bucket takes 4. The empty is used up, the filled one
	 * comes back, and the player gets what taking from the output gives: the recipe, its experience and First Brew.
	 */
	private static void rightClickFills(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, litCampfire());
		loadBatch(kettle, 3);
		helper.startSequence()
			// three batches: 3 x 0.35 experience, so at least one orb for sure
			.thenWaitUntil(() -> helper.assertValueEqual(kettle.getServings(), 12, "servings from three batches"))
			.thenExecute(() -> {
				ServerPlayer player = playerAtKettle(helper, GameType.SURVIVAL);
				try {
					helper.assertTrue(rightClick(helper, player, new ItemStack(Items.GLASS_BOTTLE, 3)).consumesAction(), "a bottle click is used");
					helper.assertValueEqual(kettle.getServings(), 11, "a bottle takes one serving");
					helper.assertValueEqual(countInInventory(player, Items.GLASS_BOTTLE), 2, "one empty bottle used up");
					helper.assertValueEqual(countInInventory(player, BaseItems.SWEET_BERRY_JUICE.get()), 1, "a filled bottle back");
					helper.assertTrue(player.containerMenu == player.inventoryMenu, "filling doesn't open the screen");
					helper.assertTrue(player.getRecipeBook().contains(SWEET_BERRY_JUICE_RECIPE), "the brewed recipe is awarded");
					helper.assertTrue(player.getAdvancements().getOrStartProgress(helper.getLevel().getServer().getAdvancements()
						.get(VBAdvancementGenerator.id("first_brew"))).isDone(), "First Brew from a right-click");
					helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(helper.absolutePos(KETTLE)).inflate(3.0D)).isEmpty(),
						"the brewing experience drops");

					rightClick(helper, player, new ItemStack(Items.BUCKET));
					helper.assertValueEqual(kettle.getServings(), 7, "a bucket takes four servings");
					// the bucket goes in the hand when it was the last one
					rightClick(helper, player, new ItemStack(Items.BUCKET));
					helper.assertValueEqual(kettle.getServings(), 3, "a bucket takes four servings");
					helper.assertTrue(player.getMainHandItem().is(BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), "the filled bucket is in the hand");

					// three servings are not a bucket: the bucket is kept and the click passes on to the screen
					helper.assertTrue(blockAnswer(helper, new ItemStack(Items.BUCKET)) instanceof InteractionResult.TryEmptyHandInteraction,
						"a bucket with too little brew passes");
					// (a plain player: the mock connection can't carry NeoForge's screen payload)
					Player other = playerHolding(helper, new ItemStack(Items.BUCKET));
					helper.useBlock(KETTLE, other);
					helper.assertValueEqual(kettle.getServings(), 3, "servings after a bucket that didn't fit");
					helper.assertValueEqual(countInInventory(other, Items.BUCKET), 1, "the bucket is kept");
					// but bottles still take the rest, one each
					for (int i = 0; i < 3; i++) {
						rightClick(helper, player, new ItemStack(Items.GLASS_BOTTLE));
					}
					helper.assertTrue(kettle.getBrew().isEmpty(), "three bottles empty the kettle");
					helper.assertTrue(kettle.getBucketForm().isEmpty(), "no bucket form once the brew is gone");
				} finally {
					helper.getLevel().getServer().getPlayerList().remove(player);
				}
			})
			.thenSucceed();
	}

	/** In creative the empty container stays, and the filled one is given once (vanilla's cauldron rule). */
	private static void rightClickCreative(GameTestHelper helper) {
		kettleWithBrew(helper, KETTLE, 8);
		ServerPlayer player = playerAtKettle(helper, GameType.CREATIVE);
		try {
			rightClick(helper, player, new ItemStack(Items.GLASS_BOTTLE));
			helper.assertValueEqual(kettle(helper, KETTLE).getServings(), 7, "servings after a creative bottle");
			helper.assertTrue(player.getMainHandItem().is(Items.GLASS_BOTTLE), "the empty bottle stays in creative");
			helper.assertValueEqual(countInInventory(player, BaseItems.SWEET_BERRY_JUICE.get()), 1, "the filled bottle is given");
			rightClick(helper, player, new ItemStack(Items.BUCKET));
			helper.assertValueEqual(kettle(helper, KETTLE).getServings(), 3, "servings after a creative bucket");
			helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "the empty bucket stays in creative");
			helper.assertValueEqual(countInInventory(player, BaseItems.SWEET_BERRY_JUICE_BUCKET.get()), 1, "the filled bucket is given");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/** No brew, or a container that doesn't take it: the block passes, so the empty-hand use opens the screen. */
	private static void rightClickPasses(GameTestHelper helper) {
		helper.setBlock(KETTLE, KettleRegistry.kettle());
		helper.assertTrue(blockAnswer(helper, new ItemStack(Items.GLASS_BOTTLE)) instanceof InteractionResult.TryEmptyHandInteraction,
			"a bottle on an empty kettle passes");
		helper.assertTrue(blockAnswer(helper, new ItemStack(Items.BUCKET)) instanceof InteractionResult.TryEmptyHandInteraction,
			"a bucket on an empty kettle passes");
		kettleWithBrew(helper, KETTLE, 8);
		helper.assertTrue(blockAnswer(helper, new ItemStack(Items.BOWL)) instanceof InteractionResult.TryEmptyHandInteraction,
			"a bowl passes");
		helper.assertTrue(blockAnswer(helper, new ItemStack(Items.STICK)) instanceof InteractionResult.TryEmptyHandInteraction,
			"any other item passes");
		helper.assertValueEqual(kettle(helper, KETTLE).getServings(), 8, "nothing served");
		Player player = playerHolding(helper, ItemStack.EMPTY);
		BlockPos absolute = helper.absolutePos(KETTLE);
		helper.assertTrue(helper.getBlockState(KETTLE).useWithoutItem(helper.getLevel(), player,
			new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)).consumesAction(), "the empty-hand use takes the click (the screen)");
		helper.succeed();
	}

	/** Sneaking with a container skips the kettle, as vanilla skips any block: nothing is served and no screen opens. */
	private static void rightClickSneaking(GameTestHelper helper) {
		kettleWithBrew(helper, KETTLE, 8);
		ServerPlayer player = playerAtKettle(helper, GameType.SURVIVAL);
		try {
			player.setShiftKeyDown(true);
			rightClick(helper, player, new ItemStack(Items.GLASS_BOTTLE, 2));
			rightClick(helper, player, new ItemStack(Items.BUCKET));
			helper.assertValueEqual(kettle(helper, KETTLE).getServings(), 8, "nothing served while sneaking");
			helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "the bucket is kept");
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "no screen while sneaking");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/** Records what the server sends a client about the menu's data, the way the gauge gets its servings. */
	private static final class DataRecorder implements ContainerSynchronizer
	{
		final int[] data = new int[BrewKettleBlockEntity.DATA_COUNT];

		@Override
		public void sendInitialData(AbstractContainerMenu menu, List<ItemStack> items, ItemStack carried, int[] dataSlots) {
			System.arraycopy(dataSlots, 0, data, 0, Math.min(dataSlots.length, data.length));
		}

		@Override
		public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
		}

		@Override
		public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) {
		}

		@Override
		public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
			data[id] = value;
		}

		@Override
		public RemoteSlot createSlot() {
			return RemoteSlot.PLACEHOLDER;
		}
	}

	/**
	 * A leftover that makes nothing (a single sugar after a batch, barley with hops) greys the screen's arrow, hot or cold;
	 * an empty grid or one that brews doesn't. The flag reaches the client as menu data (the client has no recipes).
	 */
	private static void gridMakesNothing(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, Blocks.STONE.defaultBlockState());
		ServerPlayer player = playerAtKettle(helper, GameType.SURVIVAL);
		try {
			Services.PLATFORM.openMenuForGameTest(player, kettle, kettle.getBlockPos());
			BrewKettleMenu menu = (BrewKettleMenu) player.containerMenu;
			DataRecorder client = new DataRecorder();
			menu.setSynchronizer(client);
			menu.broadcastChanges();
			helper.assertValueEqual(client.data[BrewKettleBlockEntity.DATA_MAKES_NOTHING], 0, "an empty grid is no leftover");
			kettle.setStack(0, new ItemStack(Items.SUGAR));
			menu.broadcastChanges();
			helper.assertValueEqual(client.data[BrewKettleBlockEntity.DATA_MAKES_NOTHING], 1, "a single sugar makes nothing, even cold");
			helper.assertTrue(menu.ingredientsMakeNothing(), "the menu says so");
			kettle.setStack(0, ItemStack.EMPTY);
			loadBatch(kettle, 1);
			menu.broadcastChanges();
			helper.assertValueEqual(client.data[BrewKettleBlockEntity.DATA_MAKES_NOTHING], 0, "a batch that brews is no leftover");
		} finally {
			player.closeContainer();
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/**
	 * The screen through its real click paths: ingredients clicked into the grid brew with no container anywhere, the
	 * gauge's servings reach the client as menu data, a right-click then fills a bottle and the gauge follows.
	 */
	private static void menuClickPaths(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, litCampfire());
		ServerPlayer player = playerAtKettle(helper, GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(Items.SWEET_BERRIES, 4));
		player.getInventory().setItem(1, new ItemStack(Items.SUGAR, 1));
		Services.PLATFORM.openMenuForGameTest(player, kettle, kettle.getBlockPos());
		helper.assertTrue(player.containerMenu instanceof BrewKettleMenu, "the kettle screen opens");
		BrewKettleMenu menu = (BrewKettleMenu) player.containerMenu;
		DataRecorder client = new DataRecorder();
		menu.setSynchronizer(client);
		int hotbar0 = BrewKettleMenu.INDEX_INVENTORY + 27;

		// berries one by one into four slots (right-click places one), the sugar shift-clicked
		menu.clicked(hotbar0, 0, ContainerInput.PICKUP, player);
		for (int i = 0; i < 4; i++) {
			menu.clicked(i, 1, ContainerInput.PICKUP, player);
		}
		helper.assertTrue(menu.getCarried().isEmpty(), "all four berries placed");
		menu.clicked(hotbar0 + 1, 0, ContainerInput.QUICK_MOVE, player);
		helper.assertValueEqual(countInInventory(player, Items.SUGAR), 0, "sugar shift-clicked into the grid");
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(kettle.getServings(), 4, "the brew is done without any container"))
			.thenExecute(() -> {
				menu.broadcastChanges();
				helper.assertValueEqual(client.data[BrewKettleBlockEntity.DATA_SERVINGS], 4, "the gauge's servings synced to the client");
				helper.assertValueEqual(menu.getServings(), 4, "the menu's gauge");
				helper.assertTrue(menu.getBrew().is(BaseItems.SWEET_BERRY_JUICE.get()), "the gauge shows the juice");
				for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
					helper.assertTrue(kettle.stackIn(i).isEmpty(), "ingredients used up");
				}
				player.closeContainer();
				rightClick(helper, player, new ItemStack(Items.GLASS_BOTTLE));
				helper.assertValueEqual(countInInventory(player, BaseItems.SWEET_BERRY_JUICE.get()), 1, "a bottle filled by hand");
				menu.broadcastChanges();
				helper.assertValueEqual(client.data[BrewKettleBlockEntity.DATA_SERVINGS], 3, "the gauge follows the servings");
				helper.getLevel().getServer().getPlayerList().remove(player);
			})
			.thenSucceed();
	}

	// ------------------------------------------------------------------ the waiting brew under the raised lid

	private static int fill(GameTestHelper helper) {
		return helper.getBlockState(KETTLE).getValue(BrewKettleBlock.FILL);
	}

	/** The fill level is the brew's share of the 64 servings a kettle holds, in four steps, never 0 while any is left. */
	private static void fillLevelFollowsServings(GameTestHelper helper) {
		int[][] expected = {{0, 0}, {1, 1}, {4, 1}, {16, 1}, {17, 2}, {32, 2}, {33, 3}, {48, 3}, {49, 4}, {64, 4}};
		for (int[] pair : expected) {
			helper.assertValueEqual(BrewKettleBlockEntity.fillLevel(pair[0]), pair[1], "fill level for " + pair[0] + " servings");
		}
		BrewKettleBlockEntity kettle = kettleWithBrew(helper, KETTLE, 64);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(fill(helper), 4, "a full kettle"))
			.thenExecute(() -> {
				for (int i = 0; i < 24; i++) {
					kettle.serveInto(new ItemStack(Items.GLASS_BOTTLE), null);
				}
			})
			.thenWaitUntil(() -> helper.assertValueEqual(fill(helper), 3, "40 servings"))
			.thenExecute(() -> kettle.serveInto(new ItemStack(Items.BUCKET), null))
			.thenExecute(() -> {
				for (int i = 0; i < 5; i++) {
					kettle.serveInto(new ItemStack(Items.BUCKET), null);
				}
			})
			.thenWaitUntil(() -> helper.assertValueEqual(fill(helper), 1, "16 servings"))
			.thenExecute(() -> {
				for (int i = 0; i < 15; i++) {
					kettle.serveInto(new ItemStack(Items.GLASS_BOTTLE), null);
				}
			})
			.thenIdle(2)
			.thenExecute(() -> helper.assertValueEqual(fill(helper), 1, "one serving left still shows"))
			.thenExecute(() -> kettle.serveInto(new ItemStack(Items.GLASS_BOTTLE), null))
			.thenWaitUntil(() -> helper.assertValueEqual(fill(helper), 0, "an empty kettle"))
			.thenExecute(() -> helper.assertFalse(helper.getBlockState(KETTLE).getValue(BrewKettleBlock.OPEN), "no screen lid either"))
			.thenSucceed();
	}

	/** The lid stays down while a batch brews, comes up with the finished brew and goes down once the last serving is taken. */
	private static void lidOpensWhenBrewDone(GameTestHelper helper) {
		BrewKettleBlockEntity kettle = placeKettle(helper, KETTLE, litCampfire());
		loadBatch(kettle, 1);
		helper.runAfterDelay(100, () -> helper.assertValueEqual(fill(helper), 0, "the lid is down while brewing"));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(kettle.getServings(), 4, "the brew is done"))
			.thenWaitUntil(() -> helper.assertValueEqual(fill(helper), 1, "the finished brew shows"))
			.thenExecute(() -> {
				// another batch on top: the lid goes down again while it brews
				loadBatch(kettle, 1);
			})
			.thenWaitUntil(() -> helper.assertValueEqual(fill(helper), 0, "the lid is down while the next batch brews"))
			.thenWaitUntil(() -> helper.assertValueEqual(kettle.getServings(), 8, "the second batch is done"))
			.thenWaitUntil(() -> helper.assertValueEqual(fill(helper), 1, "eight servings show"))
			.thenExecute(() -> {
				// the container slot takes the brew, the lid closes when nothing is left
				kettle.setStack(BrewKettleBlockEntity.CONTAINER_SLOT, new ItemStack(Items.GLASS_BOTTLE, 8));
			})
			.thenWaitUntil(() -> helper.assertTrue(kettle.getBrew().isEmpty(), "bottled up"))
			.thenWaitUntil(() -> helper.assertValueEqual(fill(helper), 0, "the lid closes on an empty kettle"))
			.thenSucceed();
	}

	/**
	 * On a lit campfire an empty kettle, one with ingredients that make no brew and one with only empty bottles waiting
	 * stay quiet; a batch that brews and a finished brew steam.
	 */
	private static void staysQuietWithoutBatch(GameTestHelper helper) {
		helper.assertTrue(BrewKettleBlockEntity.staysQuiet(false, false, 0), "empty");
		helper.assertTrue(BrewKettleBlockEntity.staysQuiet(true, false, 0), "ingredients that match no recipe");
		helper.assertFalse(BrewKettleBlockEntity.staysQuiet(true, true, 0), "a matching batch");
		helper.assertFalse(BrewKettleBlockEntity.staysQuiet(false, false, 4), "a brew alone");
		helper.assertFalse(BrewKettleBlockEntity.staysQuiet(true, false, 4), "a brew under odd ingredients");

		BrewKettleBlockEntity empty = placeKettle(helper, new BlockPos(0, 2, 0), litCampfire());
		BrewKettleBlockEntity noRecipe = placeKettle(helper, new BlockPos(2, 2, 0), litCampfire());
		noRecipe.setStack(0, new ItemStack(Items.COBBLESTONE));
		BrewKettleBlockEntity batch = placeKettle(helper, new BlockPos(4, 2, 0), litCampfire());
		loadBatch(batch, 1);
		BrewKettleBlockEntity brew = placeKettle(helper, new BlockPos(0, 2, 4), litCampfire());
		fillBrew(brew, BaseItems.SWEET_BERRY_JUICE.get(), BaseItems.SWEET_BERRY_JUICE_BUCKET.get(), 4);
		BrewKettleBlockEntity containers = placeKettle(helper, new BlockPos(4, 2, 4), litCampfire());
		containers.setStack(BrewKettleBlockEntity.CONTAINER_SLOT, new ItemStack(Items.GLASS_BOTTLE, 3));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(empty.isHeated() && noRecipe.isHeated() && batch.isHeated(), "the kettles are on the fire");
			helper.assertTrue(empty.staysQuiet(), "an empty kettle stays quiet");
			helper.assertTrue(noRecipe.staysQuiet(), "ingredients that make no brew stay quiet");
			helper.assertFalse(batch.staysQuiet(), "a batch that brews steams");
			helper.assertFalse(brew.staysQuiet(), "a finished brew steams");
			helper.assertTrue(containers.staysQuiet(), "empty bottles alone stay quiet");
			// the batch taken out again: quiet on the next tick
			for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
				batch.setStack(i, ItemStack.EMPTY);
			}
			batch.setStack(0, new ItemStack(Items.COBBLESTONE));
		});
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(batch.staysQuiet(), "the batch swapped for cobblestone goes quiet");
			helper.succeed();
		});
	}

	/**
	 * The campfire smoke rule behind CampfireUnderKettleMixin: hidden under a kettle right above, or one above a hopper
	 * (a heat conductor); not under a kettle with a gap or a plain block between, not without a kettle, and never for a
	 * signal fire.
	 */
	private static void hidesCampfireSmoke(GameTestHelper helper) {
		BlockState hopper = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH);
		BlockState kettle = KettleRegistry.kettle().defaultBlockState();
		BlockPos direct = new BlockPos(0, 1, 0);
		BlockPos viaHopper = new BlockPos(2, 1, 0);
		BlockPos viaStone = new BlockPos(4, 1, 0);
		BlockPos alone = new BlockPos(0, 1, 2);
		BlockPos hopperOnly = new BlockPos(2, 1, 2);
		BlockPos gap = new BlockPos(4, 1, 2);
		for (BlockPos fire : List.of(direct, viaHopper, viaStone, alone, hopperOnly, gap)) {
			helper.setBlock(fire, litCampfire());
		}
		helper.setBlock(direct.above(), kettle);
		helper.setBlock(viaHopper.above(), hopper);
		helper.setBlock(viaHopper.above(2), kettle);
		helper.setBlock(viaStone.above(), Blocks.STONE);
		helper.setBlock(viaStone.above(2), kettle);
		helper.setBlock(hopperOnly.above(), hopper);
		helper.setBlock(gap.above(2), kettle);

		var level = helper.getLevel();
		helper.assertTrue(KettleTags.hidesCampfireSmoke(level, helper.absolutePos(direct), false), "a kettle right above");
		helper.assertTrue(KettleTags.hidesCampfireSmoke(level, helper.absolutePos(viaHopper), false), "a kettle above a hopper");
		helper.assertFalse(KettleTags.hidesCampfireSmoke(level, helper.absolutePos(viaStone), false), "stone between");
		helper.assertFalse(KettleTags.hidesCampfireSmoke(level, helper.absolutePos(alone), false), "no kettle");
		helper.assertFalse(KettleTags.hidesCampfireSmoke(level, helper.absolutePos(hopperOnly), false), "a hopper and no kettle");
		helper.assertFalse(KettleTags.hidesCampfireSmoke(level, helper.absolutePos(gap), false), "air between");
		helper.assertFalse(KettleTags.hidesCampfireSmoke(level, helper.absolutePos(direct), true), "a signal fire keeps its column");
		helper.assertFalse(KettleTags.hidesCampfireSmoke(level, helper.absolutePos(viaHopper), true), "a signal fire through a hopper too");
		helper.succeed();
	}
}
