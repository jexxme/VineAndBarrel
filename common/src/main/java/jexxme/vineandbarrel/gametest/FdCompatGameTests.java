package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.platform.SlotAccess;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.block.VineClimbing;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.compat.farmersdelight.FeastBlock;
import jexxme.vineandbarrel.compat.farmersdelight.MealItem;
import jexxme.vineandbarrel.drink.DrinkItem;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.BrewKettleBlock;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.kettle.KettleTags;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import jexxme.vineandbarrel.tag.VBTags;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * M5 Farmer's Delight integration (DESIGN §7). Every test runs in both GameTest runs and checks the hook from both
 * sides: with FD ({@code ./gradlew runGameTestServerFd}) it works against FD's real blocks, items and recipes; without
 * FD ({@code ./gradlew runGameTestServer}) it degrades cleanly (nothing FD loads, our side still works on its own).
 * FD is never compiled against: its things are looked up by id.
 */
public final class FdCompatGameTests
{
	private static final Identifier SMALL = VineAndBarrel.id("gametest/empty_5x5");
	private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, VineAndBarrel.MODID);
	private static final List<ResourceKey<Consumer<GameTestHelper>>> TESTS = new ArrayList<>();
	private static final Map<ResourceKey<Consumer<GameTestHelper>>, Integer> TIMEOUTS = new HashMap<>();

	private static final int FERMENT = AgingRecipe.DEFAULT_FERMENTING_TIME;
	private static final int AGE = AgingRecipe.DEFAULT_AGING_TIME;

	static {
		test("fd_run_has_the_right_mods", 20, FdCompatGameTests::rightMods);
		loaderTest("fd_optional_dependency", 20);
		test("fd_stove_heats_kettle", 400, FdCompatGameTests::stoveHeatsKettle);
		test("fd_vines_climb_real_rope", 100, FdCompatGameTests::vinesClimbRealRope);
		test("fd_piston_keeps_the_vines_rope", 100, FdCompatGameTests::pistonKeepsRope);
		test("fd_ropes_and_trellises_together", 100, FdCompatGameTests::ropesAndTrellises);
		test("fd_rich_soil_speeds_crops", 100, FdCompatGameTests::richSoilSpeedsCrops);
		test("fd_apple_cider_becomes_hard_cider", 20, h -> fdDrinkInBarrel(h, "apple_cider", DrinkItems.HARD_CIDER.get(), DrinkItems.AGED_HARD_CIDER.get(), DrinkItems.MUG.get(), DrinkItems.WINE_GLASS.get()));
		test("fd_melon_juice_becomes_melon_wine", 20, h -> fdDrinkInBarrel(h, "melon_juice", DrinkItems.MELON_WINE.get(), DrinkItems.AGED_MELON_WINE.get(), DrinkItems.WINE_GLASS.get(), DrinkItems.MUG.get()));
		test("fd_drinks_tag", 20, FdCompatGameTests::drinksTag);
		test("fd_cider_two_ways_one_drink", 20, FdCompatGameTests::ciderTwoWaysOneDrink);
		test("fd_wild_crops_tags", 20, FdCompatGameTests::wildCropsTags);
		test("fd_bale_signal_smoke", 20, FdCompatGameTests::baleSignalSmoke);
		test("fd_aged_drinks_give_no_comfort", 20, FdCompatGameTests::noComfort);
		test("fd_meals_eat_like_fd_meals", 20, FdCompatGameTests::mealsEatLikeFdMeals);
		test("fd_cooking_pot_beer_battered_fish", 400, h -> cookingPot(h, "beer_battered_fish",
			List.of(new ItemStack(Items.COD), new ItemStack(DrinkItems.BARLEY_ALE.get()), new ItemStack(Items.WHEAT)), FDCompat.BEER_BATTERED_FISH.get(), Items.BOWL, List.of(DrinkItems.MUG.get())));
		test("fd_cooking_pot_wine_braised_beef", 400, h -> cookingPot(h, "wine_braised_beef",
			List.of(new ItemStack(Items.BEEF), new ItemStack(DrinkItems.AGED_RED_WINE.get()), fdStack("onion"), new ItemStack(Items.CARROT)), FDCompat.WINE_BRAISED_BEEF.get(), Items.BOWL, List.of(DrinkItems.WINE_GLASS.get())));
		test("fd_cooking_pot_cider_braised_pork", 400, h -> cookingPot(h, "cider_braised_pork",
			List.of(new ItemStack(Items.PORKCHOP), new ItemStack(DrinkItems.HARD_CIDER.get()), new ItemStack(Items.APPLE), fdStack("onion")), FDCompat.CIDER_BRAISED_PORK.get(), Items.BOWL, List.of(DrinkItems.MUG.get())));
		test("fd_cooking_pot_barley_mutton_broth", 400, h -> cookingPot(h, "barley_mutton_broth",
			List.of(new ItemStack(Items.MUTTON), new ItemStack(VBItems.BARLEY.get()), new ItemStack(Items.CARROT), fdStack("onion")), FDCompat.BARLEY_MUTTON_BROTH.get(), Items.BOWL, List.of()));
		// the bone broth's bowl comes back out of the pot, like a drink's vessel
		test("fd_cooking_pot_white_wine_risotto", 400, h -> cookingPot(h, "white_wine_risotto",
			List.of(fdStack("rice"), new ItemStack(DrinkItems.AGED_WHITE_WINE.get()), fdStack("onion"), fdStack("bone_broth")), FDCompat.WHITE_WINE_RISOTTO.get(), Items.BOWL, List.of(DrinkItems.WINE_GLASS.get(), Items.BOWL)));
		// the feast needs no bowl: it comes out of the pot as the placeable pot of coq au vin
		test("fd_cooking_pot_coq_au_vin_block", 700, h -> cookingPot(h, "coq_au_vin_block",
			List.of(new ItemStack(Items.CHICKEN), new ItemStack(Items.CHICKEN), new ItemStack(DrinkItems.RED_WINE.get()), new ItemStack(Items.BROWN_MUSHROOM), fdStack("onion"), fdStack("bacon")),
			FDCompat.COQ_AU_VIN_BLOCK_ITEM.get(), null, List.of(DrinkItems.WINE_GLASS.get())));
		test("fd_feast_serves_four_bowls", 40, FdCompatGameTests::feastServesFourBowls);
		test("fd_feast_drops", 40, FdCompatGameTests::feastDrops);
		test("fd_feast_real_clicks", 40, FdCompatGameTests::feastRealClicks);
		test("fd_feast_comparator", 40, FdCompatGameTests::feastComparator);
		test("fd_beer_bread", 20, FdCompatGameTests::beerBread);
		test("fd_knife_cuts_straw_from_barley", 20, FdCompatGameTests::knifeCutsStraw);
		test("fd_cutting_board_wild_barley", 100, FdCompatGameTests::cuttingBoardWildBarley);
		test("fd_cutting_board_wild_grapes_and_hops", 200, FdCompatGameTests::cuttingBoardWildGrapesAndHops);
		test("fd_drinks_sober_by_one", 20, FdCompatGameTests::fdDrinksSoberByOne);
		test("fd_recipes_have_unlocks", 20, FdCompatGameTests::recipesHaveUnlocks);
	}

	private FdCompatGameTests() {
	}

	/** A test the loader writes with its own API ({@code Platform#loaderGameTest}); left out on a loader that has none. */
	private static void loaderTest(String name, int timeout) {
		Consumer<GameTestHelper> function = jexxme.vineandbarrel.platform.Services.PLATFORM.loaderGameTest(name);
		if (function != null) {
			test(name, timeout, function);
		}
	}

	private static void test(String name, int timeout, Consumer<GameTestHelper> function) {
		FUNCTIONS.register(name, () -> function);
		ResourceKey<Consumer<GameTestHelper>> key = ResourceKey.create(Registries.TEST_FUNCTION, VineAndBarrel.id(name));
		TESTS.add(key);
		TIMEOUTS.put(key, timeout);
	}

	public static void register() {
		FUNCTIONS.register();
		jexxme.vineandbarrel.platform.Services.PLATFORM.addGameTests(FdCompatGameTests::registerTests);
	}

	private static void registerTests(GameTestRegistrar event) {
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(VineAndBarrel.id("farmersdelight"));
		for (ResourceKey<Consumer<GameTestHelper>> function : TESTS) {
			event.registerTest(function.identifier(), new FunctionGameTestInstance(function, new TestData<>(environment, SMALL, TIMEOUTS.get(function), 0, true)));
		}
	}

	// ------------------------------------------------------------------ helpers

	private static boolean fd() {
		return FDCompat.isLoaded();
	}

	private static Optional<Block> fdBlock(String path) {
		return BuiltInRegistries.BLOCK.getOptional(FDCompat.id(path));
	}

	private static Optional<Item> fdItem(String path) {
		return BuiltInRegistries.ITEM.getOptional(FDCompat.id(path));
	}

	/** An FD item by id; only called by tests that need FD (the stack is empty without it). */
	private static ItemStack fdStack(String path) {
		return fdItem(path).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	/** Without FD nothing of FD's may exist; with FD the thing must be there. */
	private static void assertFdThing(GameTestHelper helper, Optional<?> thing, String what) {
		helper.assertValueEqual(thing.isPresent(), fd(), what + " exists exactly when FD is installed");
	}

	private static Optional<RecipeHolder<?>> recipe(GameTestHelper helper, String path) {
		return helper.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, VineAndBarrel.id(path)));
	}

	private static BlockState litStove() {
		return fdBlock("stove").orElseThrow().defaultBlockState().setValue(BlockStateProperties.LIT, true);
	}

	private static Player player(GameTestHelper helper) {
		return helper.makeMockPlayer(GameType.SURVIVAL);
	}

	private static int countAround(GameTestHelper helper, BlockPos pos, Item item) {
		int n = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(3.0D))) {
			if (entity.getItem().is(item)) n += entity.getItem().getCount();
		}
		return n;
	}

	private static List<Component> tooltip(GameTestHelper helper, ItemStack stack) {
		List<Component> lines = new ArrayList<>();
		stack.getItem().appendHoverText(stack, Item.TooltipContext.of(helper.getLevel()), TooltipDisplay.DEFAULT, lines::add, TooltipFlag.NORMAL);
		return lines;
	}

	/** The translation keys and the text of a tooltip line and everything inside it. */
	private static String text(Component line) {
		StringBuilder out = new StringBuilder(line.getString());
		collectKeys(line, out);
		return out.toString();
	}

	private static void collectKeys(Component component, StringBuilder out) {
		if (component.getContents() instanceof TranslatableContents t) {
			out.append(' ').append(t.getKey());
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component c) collectKeys(c, out);
			}
		}
		component.getSiblings().forEach(sibling -> collectKeys(sibling, out));
	}

	// ------------------------------------------------------------------ the runs themselves

	/** The FD run really has FD (and its real blocks); the normal run really has none of it. */
	private static void rightMods(GameTestHelper helper) {
		helper.assertValueEqual(jexxme.vineandbarrel.platform.Services.PLATFORM.isModLoaded("farmersdelight"), Boolean.getBoolean("vineandbarrel.expectFd"), "FD loaded (runGameTestServerFd sets vineandbarrel.expectFd)");
		for (String id : List.of("stove", "rope", "rich_soil", "rich_soil_farmland", "cooking_pot", "straw_bale")) {
			assertFdThing(helper, fdBlock(id), "farmersdelight:" + id);
		}
		for (String id : List.of("apple_cider", "melon_juice", "onion")) {
			assertFdThing(helper, fdItem(id), "farmersdelight:" + id);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ §7 rows

	/**
	 * FD's stove heats the kettle through our heat tag's optional {@code #farmersdelight:heat_sources} entry, only while
	 * lit, and the kettle really brews on it. Without FD the optional entry is simply skipped and the vanilla sources work.
	 */
	private static void stoveHeatsKettle(GameTestHelper helper) {
		BlockPos heat = new BlockPos(1, 1, 2);
		BlockPos kettlePos = heat.above();
		if (!fd()) {
			helper.assertTrue(Blocks.MAGMA_BLOCK.defaultBlockState().is(KettleTags.HEAT_SOURCES), "the heat tag still loads its vanilla entries");
			helper.setBlock(heat, Blocks.MAGMA_BLOCK);
			helper.setBlock(kettlePos, KettleRegistry.kettle());
			helper.assertTrue(helper.getBlockEntity(kettlePos, BrewKettleBlockEntity.class).isHeated(), "magma heats the kettle without FD");
			helper.succeed();
			return;
		}
		BlockState stove = litStove();
		helper.assertTrue(stove.is(KettleTags.HEAT_SOURCES), "FD's stove is in #vineandbarrel:heat_sources");
		// an unlit stove next to it heats nothing
		BlockPos coldHeat = new BlockPos(3, 1, 2);
		helper.setBlock(coldHeat, stove.setValue(BlockStateProperties.LIT, false));
		helper.setBlock(coldHeat.above(), KettleRegistry.kettle());
		helper.assertFalse(helper.getBlockEntity(coldHeat.above(), BrewKettleBlockEntity.class).isHeated(), "an unlit stove heats nothing");
		helper.setBlock(heat, stove);
		helper.setBlock(kettlePos, KettleRegistry.kettle().defaultBlockState()
			.setValue(BrewKettleBlock.SUPPORT, BrewKettleBlock.trayState(helper.getLevel(), helper.absolutePos(kettlePos))));
		BrewKettleBlockEntity kettle = helper.getBlockEntity(kettlePos, BrewKettleBlockEntity.class);
		helper.assertTrue(kettle.isHeated(), "a kettle on a lit FD stove is heated");
		for (int i = 0; i < 4; i++) {
			kettle.setStack(i, new ItemStack(Items.SWEET_BERRIES));
		}
		kettle.setStack(4, new ItemStack(Items.SUGAR));
		helper.succeedWhen(() -> {
			helper.assertTrue(kettle.getBrew().is(BaseItems.SWEET_BERRY_JUICE.get()), "brewed on the stove: " + kettle.getBrew());
			helper.assertValueEqual(kettle.getServings(), 4, "servings");
		});
	}

	/**
	 * With FD our bushes climb FD's real rope (in {@code #farmersdelight:ropes}), and FD's rope comes back when the vine
	 * goes. Without FD there is no FD rope, and a ripe bush under a block that holds no vine (a glass pane) stays a bush.
	 */
	private static void vinesClimbRealRope(GameTestHelper helper) {
		BlockPos bush = new BlockPos(2, 1, 2);
		helper.setBlock(bush.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(bush, VBBlocks.GRAPE_BUSH.get().defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
		Optional<Block> fdRope = fdBlock("rope");
		assertFdThing(helper, fdRope, "FD's rope");
		if (fdRope.isEmpty()) {
			helper.setBlock(bush.above(), Blocks.GLASS_PANE);
			for (int i = 0; i < 300; i++) {
				helper.randomTick(bush);
			}
			helper.assertBlockPresent(Blocks.GLASS_PANE, bush.above());
			helper.succeed();
			return;
		}
		Block rope = fdRope.get();
		helper.assertTrue(rope.defaultBlockState().is(VBTags.FD_ROPES), "FD's rope is in #farmersdelight:ropes");
		helper.assertTrue(VineClimbing.ropeState(helper.getLevel(), helper.absolutePos(bush.above())).is(rope), "the rope a vine leaves behind is FD's");
		helper.setBlock(bush.above(), rope);
		helper.setBlock(bush.above(2), rope);
		for (int i = 0; i < 2000 && !helper.getBlockState(bush.above()).is(VBBlocks.GRAPE_VINE_ON_ROPE.get()); i++) {
			helper.randomTick(bush);
		}
		helper.assertBlockPresent(VBBlocks.GRAPE_VINE_ON_ROPE.get(), bush.above());
		helper.assertBlockPresent(rope, bush.above(2));
		// the vine goes, FD's rope comes back
		helper.getLevel().destroyBlock(helper.absolutePos(bush.above()), false);
		helper.succeedWhen(() -> helper.assertBlockPresent(rope, bush.above()));
	}

	/**
	 * FD's ropes next to our Trellis: FD's rope is a vine support through the optional {@code #farmersdelight:ropes} entry;
	 * a bush under it still grows the vine on a rope, and that vine climbs on onto a trellis above (one plant, one height
	 * limit). When the bush goes, each vine gives back its own support. Without FD the trellis alone is a support.
	 */
	private static void ropesAndTrellises(GameTestHelper helper) {
		Optional<Block> fdRope = fdBlock("rope");
		assertFdThing(helper, fdRope, "FD's rope");
		BlockState trellis = VBBlocks.TRELLIS.get().defaultBlockState().setValue(jexxme.vineandbarrel.block.TrellisBlock.AXIS, Direction.Axis.Z);
		helper.assertTrue(trellis.is(VBTags.VINE_SUPPORTS), "the Trellis is a vine support");
		if (fdRope.isEmpty()) {
			helper.succeed();
			return;
		}
		Block rope = fdRope.get();
		helper.assertTrue(rope.defaultBlockState().is(VBTags.VINE_SUPPORTS), "FD's rope is a vine support");
		BlockPos bush = new BlockPos(2, 1, 2);
		helper.setBlock(bush.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(bush, VBBlocks.GRAPE_BUSH.get().defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
		helper.setBlock(bush.above(), rope);
		helper.setBlock(bush.above(2), trellis);
		for (int i = 0; i < 10; i++) {
			net.minecraft.world.item.BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), helper.getLevel(), helper.absolutePos(bush));
		}
		helper.assertBlockPresent(VBBlocks.GRAPE_VINE_ON_ROPE.get(), bush.above());
		helper.assertBlockPresent(VBBlocks.GRAPE_VINE_ON_TRELLIS.get(), bush.above(2));
		helper.getLevel().destroyBlock(helper.absolutePos(bush), false);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(rope, bush.above());
			helper.assertBlockState(bush.above(2), trellis);
		});
	}

	/** A piston moves a grown vine whole, like FD's tomatoes on rope; off its bush it withers and leaves the rope. */
	private static void pistonKeepsRope(GameTestHelper helper) {
		Optional<Block> fdRope = fdBlock("rope");
		assertFdThing(helper, fdRope, "FD's rope");
		helper.assertValueEqual(VBBlocks.GRAPE_VINE_ON_ROPE.get().defaultBlockState().getPistonPushReaction(), PushReaction.PUSH_PULL, "grape vine push reaction");
		helper.assertValueEqual(VBBlocks.HOP_VINE_ON_ROPE.get().defaultBlockState().getPistonPushReaction(), PushReaction.PUSH_PULL, "hop vine push reaction");
		if (fdRope.isEmpty()) {
			helper.succeed();
			return;
		}
		BlockPos bush = new BlockPos(2, 1, 2);
		BlockPos vine = bush.above();
		helper.setBlock(bush.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(bush, VBBlocks.GRAPE_BUSH.get().defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
		helper.setBlock(vine, VBBlocks.GRAPE_VINE_ON_ROPE.get().defaultBlockState());
		helper.setBlock(vine.west(), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		helper.setBlock(vine.west(2), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> helper.assertBlockPresent(fdRope.get(), vine.east()));
	}

	/**
	 * FD's rich soil	/**
	 * FD's rich soil (and rich soil farmland) boost our crops with no code of ours: only the soil gets random ticks here,
	 * so any growth is FD's boost. Without FD there is no rich soil, and our crops stay boostable (not in FD's
	 * {@code unaffected_by_rich_soil}).
	 */
	private static void richSoilSpeedsCrops(GameTestHelper helper) {
		TagKey<Block> unaffected = TagKey.create(Registries.BLOCK, FDCompat.id("unaffected_by_rich_soil"));
		for (Block crop : List.of(VBBlocks.GRAPE_BUSH.get(), VBBlocks.HOP_BUSH.get(), VBBlocks.BARLEY.get())) {
			helper.assertFalse(crop.defaultBlockState().is(unaffected), crop + " is boostable by rich soil");
		}
		Optional<Block> richSoil = fdBlock("rich_soil");
		Optional<Block> richFarmland = fdBlock("rich_soil_farmland");
		assertFdThing(helper, richSoil, "rich soil");
		assertFdThing(helper, richFarmland, "rich soil farmland");
		if (!fd()) {
			helper.succeed();
			return;
		}
		BlockPos grapeSoil = new BlockPos(0, 1, 0);
		BlockPos hopSoil = new BlockPos(0, 1, 2);
		BlockPos barleySoil = new BlockPos(2, 1, 2);
		helper.setBlock(grapeSoil, richSoil.get());
		helper.setBlock(hopSoil, richSoil.get());
		helper.setBlock(barleySoil, richFarmland.get().defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
		helper.setBlock(new BlockPos(4, 1, 2), Blocks.WATER); // keeps the farmland wet, so it boosts
		// planted by a player, as in a real field: grapes and hops on rich soil, barley seeds on rich soil farmland
		Player farmer = player(helper);
		for (var planting : List.of(Map.entry(grapeSoil, VBItems.RED_GRAPES.get()), Map.entry(hopSoil, VBItems.HOPS.get()), Map.entry(barleySoil, VBItems.BARLEY_SEEDS.get()))) {
			ItemStack stack = new ItemStack(planting.getValue());
			farmer.setItemInHand(InteractionHand.MAIN_HAND, stack);
			helper.placeAt(farmer, stack, planting.getKey(), Direction.UP);
			helper.assertTrue(stack.isEmpty(), planting.getValue() + " is planted on " + helper.getBlockState(planting.getKey()).getBlock());
		}
		helper.assertBlockPresent(VBBlocks.GRAPE_BUSH.get(), grapeSoil.above());
		helper.assertBlockPresent(VBBlocks.HOP_BUSH.get(), hopSoil.above());
		helper.assertBlockPresent(VBBlocks.BARLEY.get(), barleySoil.above());
		for (int i = 0; i < 300; i++) {
			helper.randomTick(grapeSoil);
			helper.randomTick(hopSoil);
			helper.randomTick(barleySoil);
		}
		helper.assertValueEqual(helper.getBlockState(grapeSoil.above()).getValue(ClimbingCropBlock.AGE), 3, "rich soil grew the grape bush to ripe");
		helper.assertValueEqual(helper.getBlockState(hopSoil.above()).getValue(ClimbingCropBlock.AGE), 3, "rich soil grew the hop bush to ripe");
		helper.assertValueEqual(helper.getBlockState(barleySoil.above()).getValue(CropBlock.AGE), CropBlock.MAX_AGE, "rich soil farmland grew the barley");
		helper.succeed();
	}

	/**
	 * FD's apple cider and melon juice bottles go into the barrel at 1 serving each (the glass bottle comes back),
	 * ferment into hard cider / melon wine and age on, tapped into the right vessel only. Without FD the recipes are not
	 * loaded and the items still exist.
	 */
	private static void fdDrinkInBarrel(GameTestHelper helper, String fdBase, DrinkItem young, DrinkItem aged, Item vessel, Item wrongVessel) {
		String recipeId = "aging/" + BuiltInRegistries.ITEM.getKey(young).getPath();
		helper.assertValueEqual(recipe(helper, recipeId).isPresent(), fd(), recipeId + " loads exactly when FD is installed");
		helper.assertTrue(BuiltInRegistries.ITEM.getKey(young).getNamespace().equals(VineAndBarrel.MODID) && aged.isAged(), "the drinks always exist");
		Optional<Item> base = fdItem(fdBase);
		assertFdThing(helper, base, "farmersdelight:" + fdBase);
		if (base.isEmpty()) {
			helper.succeed();
			return;
		}
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, BarrelRegistry.AGING_BARREL.get().defaultBlockState().setValue(AgingBarrelBlock.FACING, Direction.NORTH));
		AgingBarrelBlockEntity barrel = helper.getBlockEntity(pos, AgingBarrelBlockEntity.class);
		for (int i = 1; i <= 2; i++) {
			ItemStack bottle = new ItemStack(base.get());
			ItemStack left = barrel.pour(bottle, null);
			helper.assertTrue(left != null && left.is(Items.GLASS_BOTTLE) && bottle.isEmpty(), fdBase + " pours and leaves its glass bottle: " + left);
			helper.assertValueEqual(barrel.servings(), i, fdBase + ": 1 serving per bottle");
		}
		helper.assertTrue(barrel.drink().is(young), fdBase + " becomes " + young);
		barrel.setAge(FERMENT);
		helper.assertFalse(barrel.canTap(new ItemStack(wrongVessel)), young + " is not tapped into " + wrongVessel);
		helper.assertTrue(barrel.tap(new ItemStack(vessel), null).is(young), "tapped " + young + " after a day");
		barrel.setAge(FERMENT + AGE);
		helper.assertTrue(barrel.tap(new ItemStack(vessel), null).is(aged), "tapped " + aged + " after 3 more days");
		helper.assertValueEqual(barrel.servings(), 0, "both servings tapped");
		helper.succeed();
	}

	/**
	 * One cider, two ways in (Jexxme): our apple juice makes Hard Cider in every run, FD's apple cider makes the same
	 * Hard Cider with FD. They stay two bases, so a barrel of one refuses the other (one batch, one base) and nothing mixes.
	 */
	private static void ciderTwoWaysOneDrink(GameTestHelper helper) {
		var ours = AgingBarrelBlockEntity.recipeFor(helper.getLevel(), new ItemStack(BaseItems.APPLE_JUICE.get()));
		helper.assertTrue(ours != null && ours.young().is(DrinkItems.HARD_CIDER.get()) && ours.aged().is(DrinkItems.AGED_HARD_CIDER.get()),
			"apple juice becomes hard cider with and without FD");
		Optional<Item> fdCider = fdItem("apple_cider");
		assertFdThing(helper, fdCider, "farmersdelight:apple_cider");
		if (fdCider.isPresent()) {
			var theirs = AgingBarrelBlockEntity.recipeFor(helper.getLevel(), new ItemStack(fdCider.get()));
			helper.assertTrue(theirs != null && theirs.young().is(DrinkItems.HARD_CIDER.get()), "FD's apple cider becomes the same hard cider");
			BlockPos pos = new BlockPos(2, 1, 2);
			helper.setBlock(pos, BarrelRegistry.AGING_BARREL.get().defaultBlockState().setValue(AgingBarrelBlock.FACING, Direction.NORTH));
			AgingBarrelBlockEntity barrel = helper.getBlockEntity(pos, AgingBarrelBlockEntity.class);
			helper.assertTrue(barrel.pour(new ItemStack(BaseItems.APPLE_JUICE.get()), null) != null, "apple juice pours in");
			ItemStack fdBottle = new ItemStack(fdCider.get());
			helper.assertTrue(barrel.pour(fdBottle, null) == null && fdBottle.getCount() == 1, "FD's apple cider does not top up apple juice");
			helper.assertValueEqual(barrel.servings(), 1, "servings after the refused top-up");
		}
		helper.succeed();
	}

	/** Every drink and both juices are in {@code farmersdelight:drinks}; with FD the tag merges with FD's own drinks. */
	private static void drinksTag(GameTestHelper helper) {
		TagKey<Item> drinks = TagKey.create(Registries.ITEM, FDCompat.id("drinks"));
		List<Item> ours = new ArrayList<>(DrinkItems.allDrinks());
		ours.add(BaseItems.GRAPE_JUICE.get());
		ours.add(BaseItems.SWEET_BERRY_JUICE.get());
		ours.add(BaseItems.WHITE_GRAPE_JUICE.get());
		ours.add(BaseItems.APPLE_JUICE.get());
		for (Item item : ours) {
			helper.assertTrue(new ItemStack(item).is(drinks), item + " is in farmersdelight:drinks");
		}
		for (String fdDrink : List.of("apple_cider", "melon_juice", "hot_cocoa", "milk_bottle")) {
			fdItem(fdDrink).ifPresent(item -> helper.assertTrue(new ItemStack(item).is(drinks), "FD's own " + fdDrink + " is still in the tag"));
		}
		int size = helper.getLevel().registryAccess().lookupOrThrow(Registries.ITEM).get(drinks).map(set -> set.size()).orElse(0);
		if (fd()) {
			helper.assertTrue(size > ours.size(), "with FD the tag also holds FD's drinks (" + size + ")");
		} else {
			helper.assertValueEqual(size, ours.size(), "without FD the tag holds only ours");
		}
		helper.succeed();
	}

	/** Our wild crops are in FD's wild crop tags (blocks and items), next to FD's own when FD is installed. */
	private static void wildCropsTags(GameTestHelper helper) {
		for (Block wild : List.of(VBBlocks.WILD_GRAPES.get(), VBBlocks.WILD_WHITE_GRAPES.get(), VBBlocks.WILD_HOPS.get(), VBBlocks.WILD_BARLEY.get())) {
			helper.assertTrue(wild.defaultBlockState().is(VBTags.FD_WILD_CROPS), wild + " is in farmersdelight:wild_crops");
			helper.assertTrue(new ItemStack(wild).is(VBTags.FD_WILD_CROPS_ITEM), wild + " item is in farmersdelight:wild_crops");
		}
		fdBlock("wild_cabbages").ifPresent(cabbage -> helper.assertTrue(cabbage.defaultBlockState().is(VBTags.FD_WILD_CROPS), "FD's own wild crops stay in the tag"));
		helper.succeed();
	}

	/**
	 * A campfire on the barley bale sends signal smoke in both runs (our mixin and FD's tag entry), and with FD a
	 * campfire on FD's straw bale still does too (FD's own rule is untouched by ours).
	 */
	private static void baleSignalSmoke(GameTestHelper helper) {
		helper.assertTrue(VBBlocks.BARLEY_BALE.get().defaultBlockState().is(VBTags.FD_CAMPFIRE_SIGNAL_SMOKE), "the bale is in farmersdelight:campfire_signal_smoke");
		List<Block> grounds = new ArrayList<>(List.of(VBBlocks.BARLEY_BALE.get(), Blocks.STONE));
		fdBlock("straw_bale").ifPresent(grounds::add);
		for (int i = 0; i < grounds.size(); i++) {
			BlockPos ground = new BlockPos(i * 2 % 6, 1, i < 3 ? 1 : 3);
			helper.setBlock(ground.above(), Blocks.CAMPFIRE);
			helper.setBlock(ground, grounds.get(i));
		}
		helper.assertTrue(helper.getBlockState(new BlockPos(0, 2, 1)).getValue(CampfireBlock.SIGNAL_FIRE), "signal smoke over the bale");
		helper.assertFalse(helper.getBlockState(new BlockPos(2, 2, 1)).getValue(CampfireBlock.SIGNAL_FIRE), "control: none over stone");
		if (fd()) {
			helper.assertTrue(helper.getBlockState(new BlockPos(4, 2, 1)).getValue(CampfireBlock.SIGNAL_FIRE), "signal smoke over FD's straw bale");
		}
		helper.succeed();
	}

	/**
	 * No FD Comfort (DESIGN log, M3 decisions): an aged drink gives its own buff, longer and stronger, and nothing else,
	 * with FD (where the Comfort effect is registered) and without; its tooltip names no Comfort either.
	 */
	private static void noComfort(GameTestHelper helper) {
		assertFdThing(helper, BuiltInRegistries.MOB_EFFECT.getOptional(FDCompat.COMFORT), "FD's Comfort effect");
		for (DrinkItem drink : List.of(DrinkItems.AGED_RED_WINE.get(), DrinkItems.AGED_BARLEY_ALE.get(), DrinkItems.AGED_HARD_CIDER.get())) {
			Player player = player(helper);
			ItemStack stack = new ItemStack(drink);
			stack.finishUsingItem(helper.getLevel(), player);
			MobEffectInstance buff = player.getEffect(drink.buff().getEffect());
			// at least its own duration (drinking the next one right after counts as a cheers, which lengthens it)
			helper.assertTrue(buff != null && buff.getDuration() >= drink.buff().getDuration(), drink + " gives its own buff: " + buff);
			BuiltInRegistries.MOB_EFFECT.get(FDCompat.COMFORT).ifPresent(comfort -> helper.assertFalse(player.hasEffect(comfort), drink + " gives no Comfort"));
			for (Component line : tooltip(helper, new ItemStack(drink))) {
				helper.assertFalse(text(line).toLowerCase(java.util.Locale.ROOT).contains("comfort"), drink + " tooltip names Comfort: " + text(line));
			}
		}
		helper.assertTrue(DrinkItems.AGED_RED_WINE.get().buff().getDuration() > DrinkItems.RED_WINE.get().buff().getDuration(), "aged lasts longer");
		helper.assertTrue(DrinkItems.AGED_RED_WINE.get().buff().getAmplifier() > DrinkItems.RED_WINE.get().buff().getAmplifier(), "aged is stronger");
		helper.assertTrue(Boolean.TRUE.equals(new ItemStack(DrinkItems.AGED_RED_WINE.get()).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "aged = glint");
		helper.succeed();
	}

	/**
	 * The meals always exist (stable worlds without FD) and eat like FD's bowl meals: FD's food values for a
	 * comparable meal, stack to 16, the bowl comes back, in {@code farmersdelight:meals}; with FD they also give FD's
	 * Nourishment and show it in the tooltip, without FD they give no effect and show none.
	 */
	private static void mealsEatLikeFdMeals(GameTestHelper helper) {
		TagKey<Item> meals = TagKey.create(Registries.ITEM, FDCompat.id("meals"));
		record Expected(MealItem meal, int nutrition, float saturation, int nourishment) {
		}
		for (Expected e : List.of(new Expected(FDCompat.BEER_BATTERED_FISH.get(), 12, 0.8F, FDCompat.MEDIUM_DURATION),
			new Expected(FDCompat.WINE_BRAISED_BEEF.get(), 14, 0.75F, FDCompat.LONG_DURATION),
			new Expected(FDCompat.CIDER_BRAISED_PORK.get(), 14, 0.75F, FDCompat.LONG_DURATION),
			new Expected(FDCompat.BARLEY_MUTTON_BROTH.get(), 14, 0.75F, FDCompat.LONG_DURATION),
			new Expected(FDCompat.WHITE_WINE_RISOTTO.get(), 14, 0.75F, FDCompat.LONG_DURATION),
			new Expected(FDCompat.COQ_AU_VIN.get(), 14, 0.75F, FDCompat.LONG_DURATION))) {
			ItemStack stack = new ItemStack(e.meal());
			FoodProperties food = stack.get(DataComponents.FOOD);
			helper.assertTrue(food != null && food.nutrition() == e.nutrition() && Math.abs(food.saturation() - e.nutrition() * e.saturation() * 2.0F) < 0.01F,
				e.meal() + " food values: " + food);
			helper.assertValueEqual(stack.getMaxStackSize(), 16, e.meal() + " stacks to 16");
			helper.assertTrue(stack.getItem().getCraftingRemainder() != null && stack.getItem().getCraftingRemainder().item().value() == Items.BOWL, e.meal() + " leaves a bowl in recipes");
			helper.assertTrue(stack.is(meals), e.meal() + " is in farmersdelight:meals");
			Player player = player(helper);
			player.getFoodData().setFoodLevel(0);
			ItemStack left = stack.finishUsingItem(helper.getLevel(), player);
			helper.assertTrue(left.is(Items.BOWL), e.meal() + " gives its bowl back: " + left);
			helper.assertValueEqual(player.getFoodData().getFoodLevel(), e.nutrition(), e.meal() + " fills " + e.nutrition());
			Optional<Holder.Reference<net.minecraft.world.effect.MobEffect>> nourishment = FDCompat.nourishment();
			assertFdThing(helper, nourishment, "FD's Nourishment");
			nourishment.ifPresent(effect -> {
				MobEffectInstance given = player.getEffect(effect);
				helper.assertTrue(given != null && given.getDuration() == e.nourishment(), e.meal() + " gives Nourishment for " + e.nourishment() + ": " + given);
				// FD's flags (FoodValues.nourishment): not ambient, no particles, no HUD icon
				helper.assertTrue(!given.isAmbient() && !given.isVisible() && !given.showIcon(), e.meal() + " Nourishment flags match FD's: " + given);
			});
			if (nourishment.isEmpty()) {
				helper.assertTrue(player.getActiveEffects().isEmpty(), e.meal() + " gives no effect without FD");
			}
			helper.assertValueEqual(tooltip(helper, new ItemStack(e.meal())).size(), fd() ? 1 : 0, e.meal() + " tooltip lines");
		}
		helper.succeed();
	}

	/**
	 * The meal's {@code farmersdelight:cooking} recipe loads only with FD. With FD it cooks in FD's real cooking pot on
	 * a lit stove, fed like a hopper would (ingredients from the top, the container from the side, the meal out of the
	 * bottom), and every ingredient is accounted for: all used up, and each remainder (a drink's vessel, the broth's bowl)
	 * pops out of the pot. A feast has no container and comes out as is.
	 */
	private static void cookingPot(GameTestHelper helper, String name, List<ItemStack> ingredients, Item meal, Item container, List<Item> vessels) {
		Optional<RecipeHolder<?>> recipe = recipe(helper, "cooking/" + name);
		helper.assertValueEqual(recipe.isPresent(), fd(), "cooking/" + name + " loads exactly when FD is installed");
		if (!fd()) {
			helper.succeed();
			return;
		}
		Recipe<?> value = recipe.get().value();
		helper.assertValueEqual(BuiltInRegistries.RECIPE_TYPE.getKey(value.getType()), FDCompat.id("cooking"), "recipe type");
		BlockPos stove = new BlockPos(2, 1, 2);
		BlockPos pot = stove.above();
		helper.setBlock(stove, litStove());
		helper.setBlock(pot, fdBlock("cooking_pot").orElseThrow());
		BlockPos absolute = helper.absolutePos(pot);
		SlotAccess top = Services.PLATFORM.itemAccess(helper.getLevel(), absolute, Direction.UP);
		SlotAccess side = Services.PLATFORM.itemAccess(helper.getLevel(), absolute, Direction.NORTH);
		helper.assertTrue(top != null && side != null, "FD's cooking pot exposes its item handlers");
		try (SlotAccess.Tx tx = Services.PLATFORM.openTransaction()) {
			for (int i = 0; i < ingredients.size(); i++) {
				ItemStack ingredient = ingredients.get(i);
				helper.assertTrue(!ingredient.isEmpty(), "ingredient " + i + " exists");
				helper.assertValueEqual(top.insert(i, SlotAccess.of(ingredient), 1, tx), 1, "inserted " + ingredient);
			}
			// As a hopper does: into the side as a whole, the pot picks the slot (FD Refabricated's side rules apply only there)
			if (container != null) {
				helper.assertValueEqual(side.insertAny(SlotAccess.of(container), 1, tx), 1, "a " + container + " goes into the container slot from the side");
			}
			tx.commit();
		}
		SlotAccess bottom = Services.PLATFORM.itemAccess(helper.getLevel(), absolute, Direction.DOWN);
		helper.succeedWhen(() -> {
			int served;
			try (SlotAccess.Tx tx = Services.PLATFORM.openTransaction()) {
				served = bottom.extractAny(SlotAccess.of(meal), 64, tx);
			}
			helper.assertValueEqual(served, 1, "one " + meal + " served");
			for (int i = 0; i < ingredients.size(); i++) {
				helper.assertTrue(top.get(i).isEmpty(), "ingredient slot " + i + " used up");
			}
			for (Item vessel : new java.util.LinkedHashSet<>(vessels)) {
				long expected = vessels.stream().filter(v -> v == vessel).count();
				helper.assertValueEqual(countAround(helper, pot, vessel), (int) expected, "the " + vessel + " comes back out of the pot");
			}
		});
	}

	// ------------------------------------------------------------------ the feast, the bread, the straw, the unlocks

	private static BlockState feast() {
		return FDCompat.COQ_AU_VIN_BLOCK.get().defaultBlockState();
	}

	private static BlockHitResult hit(GameTestHelper helper, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		return new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
	}

	/**
	 * The Coq au Vin feast works with and without FD (the block always exists): four bowls, one serving each, the pot
	 * shows each one taken, then the leftovers, which the next click clears for a bone. No bowl, no serving.
	 */
	private static void feastServesFourBowls(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		helper.setBlock(pos, feast());
		helper.assertTrue(feast().is(VBTags.FD_FEASTS) && new ItemStack(FDCompat.COQ_AU_VIN_BLOCK_ITEM.get()).is(VBTags.FD_FEASTS_ITEM), "the feast is in farmersdelight:feasts");
		helper.assertValueEqual(new ItemStack(FDCompat.COQ_AU_VIN_BLOCK_ITEM.get()).getMaxStackSize(), 1, "the feast stacks to 1");
		Player player = player(helper);
		// empty hand or anything but a bowl: nothing is served
		helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit(helper, pos));
		helper.getBlockState(pos).useItemOn(new ItemStack(Items.STONE), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, pos));
		helper.assertValueEqual(helper.getBlockState(pos).getValue(FeastBlock.SERVINGS), FeastBlock.MAX_SERVINGS, "no serving without a bowl");
		ItemStack bowls = new ItemStack(Items.BOWL, 5);
		player.setItemInHand(InteractionHand.MAIN_HAND, bowls);
		for (int taken = 1; taken <= FeastBlock.MAX_SERVINGS; taken++) {
			InteractionResult result = helper.getBlockState(pos).useItemOn(bowls, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, pos));
			helper.assertTrue(result.consumesAction(), "serving " + taken + " takes the click");
			helper.assertValueEqual(helper.getBlockState(pos).getValue(FeastBlock.SERVINGS), FeastBlock.MAX_SERVINGS - taken, "servings left");
			helper.assertValueEqual(player.getInventory().countItem(FDCompat.COQ_AU_VIN.get()), taken, "bowls of coq au vin");
			helper.assertValueEqual(bowls.getCount(), 5 - taken, "one bowl per serving");
		}
		// the leftovers: the next click clears them, bowl in hand or not, and leaves a bone
		helper.getBlockState(pos).useItemOn(bowls, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, pos));
		helper.assertBlockNotPresent(FDCompat.COQ_AU_VIN_BLOCK.get(), pos);
		helper.assertValueEqual(bowls.getCount(), 1, "clearing the leftovers takes no bowl");
		helper.assertValueEqual(countAround(helper, pos, Items.BONE), 1, "the leftovers leave a bone");
		helper.assertValueEqual(countAround(helper, pos, FDCompat.COQ_AU_VIN_BLOCK_ITEM.get()), 0, "and not the pot");
		helper.succeed();
	}

	/** Broken whole, the feast drops itself; once served from, only a bone. It needs solid ground, like FD's feasts. */
	private static void feastDrops(GameTestHelper helper) {
		BlockPos whole = new BlockPos(0, 1, 0);
		BlockPos served = new BlockPos(4, 1, 4);
		BlockPos floating = new BlockPos(0, 1, 4);
		for (BlockPos pos : List.of(whole, served, floating)) {
			helper.setBlock(pos.below(), Blocks.STONE);
		}
		helper.setBlock(whole, feast());
		helper.setBlock(served, feast().setValue(FeastBlock.SERVINGS, 2));
		helper.setBlock(floating, feast());
		helper.getLevel().destroyBlock(helper.absolutePos(whole), true);
		helper.getLevel().destroyBlock(helper.absolutePos(served), true);
		helper.assertValueEqual(countAround(helper, whole, FDCompat.COQ_AU_VIN_BLOCK_ITEM.get()), 1, "a whole feast drops itself");
		helper.assertValueEqual(countAround(helper, whole, Items.BONE), 0, "a whole feast drops no bone");
		helper.assertValueEqual(countAround(helper, served, Items.BONE), 1, "a served feast drops a bone");
		helper.assertValueEqual(countAround(helper, served, FDCompat.COQ_AU_VIN_BLOCK_ITEM.get()), 0, "a served feast does not drop itself");
		// the ground goes, the feast goes with it
		helper.setBlock(floating.below(), Blocks.AIR);
		helper.assertBlockNotPresent(FDCompat.COQ_AU_VIN_BLOCK.get(), floating);
		helper.succeed();
	}

	/**
	 * Beer bread eats like a loaf (6, 0.6, stacks to 64, no effect) in every run; its recipe loads only with FD, and
	 * there 3 wheat and an ale or wheat beer make two loaves, the mug staying in the grid.
	 */
	private static void beerBread(GameTestHelper helper) {
		ItemStack bread = new ItemStack(FDCompat.BEER_BREAD.get());
		FoodProperties food = bread.get(DataComponents.FOOD);
		helper.assertTrue(food != null && food.nutrition() == 6 && Math.abs(food.saturation() - 7.2F) < 0.01F, "beer bread food: " + food);
		helper.assertValueEqual(bread.getMaxStackSize(), 64, "beer bread stacks to 64");
		helper.assertTrue(bread.is(jexxme.vineandbarrel.compat.Conventions.FOODS_BREAD), "beer bread is c:foods/bread");
		Player player = player(helper);
		player.getFoodData().setFoodLevel(0);
		bread.copy().finishUsingItem(helper.getLevel(), player);
		helper.assertValueEqual(player.getFoodData().getFoodLevel(), 6, "beer bread fills 6");
		helper.assertTrue(player.getActiveEffects().isEmpty(), "beer bread gives no effect");
		helper.assertValueEqual(recipe(helper, "beer_bread").isPresent(), fd(), "beer_bread loads exactly when FD is installed");
		if (!fd()) {
			helper.succeed();
			return;
		}
		for (Item beer : List.of(DrinkItems.BARLEY_ALE.get(), DrinkItems.AGED_BARLEY_ALE.get(), DrinkItems.WHEAT_BEER.get(), DrinkItems.AGED_WHEAT_BEER.get())) {
			CraftingInput input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT), new ItemStack(beer)));
			RecipeHolder<CraftingRecipe> holder = helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.orElseThrow(() -> new IllegalStateException("no beer bread from " + beer));
			ItemStack made = holder.value().assemble(input);
			helper.assertTrue(made.is(FDCompat.BEER_BREAD.get()) && made.getCount() == 2, "3 wheat + " + beer + " make 2 beer bread: " + made);
			helper.assertTrue(holder.value().getRemainingItems(input).stream().anyMatch(left -> left.is(DrinkItems.MUG.get())), "the mug stays in the grid");
		}
		CraftingInput wine = CraftingInput.of(2, 2, List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT), new ItemStack(DrinkItems.RED_WINE.get())));
		helper.assertTrue(helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, wine, helper.getLevel()).isEmpty(), "wine makes no beer bread");
		helper.succeed();
	}

	/**
	 * With FD, FD's knife cuts a straw from ripe barley, as FD's knife does from ripe wheat; not from unripe barley, and
	 * not with any other tool. Without FD the barley drops as always.
	 */
	private static void knifeCutsStraw(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 2);
		BlockState ripe = VBBlocks.BARLEY.get().defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE);
		BlockState young = ripe.setValue(CropBlock.AGE, 3);
		Optional<Item> straw = fdItem("straw");
		Optional<Item> knife = fdItem("flint_knife");
		assertFdThing(helper, straw, "farmersdelight:straw");
		assertFdThing(helper, knife, "farmersdelight:flint_knife");
		ItemStack tool = knife.map(ItemStack::new).orElse(new ItemStack(Items.IRON_SWORD));
		List<ItemStack> ripeDrops = drops(helper, ripe, pos, tool);
		helper.assertTrue(count(ripeDrops, VBItems.BARLEY.get()) > 0, "ripe barley still drops barley: " + ripeDrops);
		if (straw.isEmpty()) {
			helper.assertTrue(ripeDrops.stream().noneMatch(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("straw")), "no straw without FD: " + ripeDrops);
			helper.succeed();
			return;
		}
		helper.assertTrue(new ItemStack(knife.orElseThrow()).is(VBTags.STRAW_HARVESTERS), "FD's knife is a straw harvester");
		helper.assertValueEqual(count(ripeDrops, straw.get()), 1, "a knife cuts one straw from ripe barley");
		helper.assertValueEqual(count(drops(helper, young, pos, tool), straw.get()), 0, "none from unripe barley");
		helper.assertValueEqual(count(drops(helper, ripe, pos, new ItemStack(Items.IRON_HOE)), straw.get()), 0, "none with a hoe");
		helper.assertValueEqual(count(drops(helper, ripe, pos, ItemStack.EMPTY), straw.get()), 0, "none by hand");
		helper.succeed();
	}

	/**
	 * With FD, wild barley on FD's real cutting board, cut with FD's knife, gives its barley and a straw half the time,
	 * like FD's wild rice. The recipe loads only with FD.
	 */
	private static void cuttingBoardWildBarley(GameTestHelper helper) {
		helper.assertValueEqual(recipe(helper, "cutting/wild_barley").isPresent(), fd(), "cutting/wild_barley loads exactly when FD is installed");
		if (!fd()) {
			helper.succeed();
			return;
		}
		BlockPos board = new BlockPos(2, 1, 2);
		helper.setBlock(board.below(), Blocks.STONE);
		helper.setBlock(board, fdBlock("cutting_board").orElseThrow());
		// a real server player: FD Refabricated's board triggers its advancement criteria with one. The mock one is
		// always in creative, so the board takes a copy and the hand keeps its item
		@SuppressWarnings("removal")
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack knife = fdStack("flint_knife");
		int cuts = 16;
		for (int i = 0; i < cuts; i++) {
			// FD's board works with the main hand, as a player's click does
			ItemStack wild = new ItemStack(VBItems.WILD_BARLEY.get());
			player.setItemInHand(InteractionHand.MAIN_HAND, wild);
			helper.getBlockState(board).useItemOn(wild, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, board));
			player.setItemInHand(InteractionHand.MAIN_HAND, knife);
			helper.getBlockState(board).useItemOn(knife, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, board));
			helper.assertValueEqual(countAround(helper, board, VBItems.BARLEY.get()), i + 1, "barley after cut " + (i + 1));
		}
		int barley = countAround(helper, board, VBItems.BARLEY.get());
		int straw = countAround(helper, board, fdItem("straw").orElseThrow());
		helper.assertValueEqual(barley, cuts, "one barley per cut");
		// a straw half the time: 16 cuts with none or all would be a 1 in 32768 chance
		helper.assertTrue(straw > 0 && straw < cuts, "a straw about half the time: " + straw + " of " + cuts);
		helper.succeed();
	}

	/**
	 * Wild grapes, wild white grapes and wild hops on FD's cutting board, like FD's own wild cabbages, carrots and
	 * potatoes: the crop every cut, and two dyes of the plant's colour at 50% each (FD rolls every item of a chance stack).
	 */
	private static void cuttingBoardWildGrapesAndHops(GameTestHelper helper) {
		record Cut(String recipe, Item wild, Item crop, Item dye) {
		}
		List<Cut> cuts = List.of(new Cut("cutting/wild_grapes", VBItems.WILD_GRAPES.get(), VBItems.RED_GRAPES.get(), Items.DYE.pick(net.minecraft.world.item.DyeColor.PURPLE)),
			new Cut("cutting/wild_white_grapes", VBItems.WILD_WHITE_GRAPES.get(), VBItems.WHITE_GRAPES.get(), Items.DYE.pick(net.minecraft.world.item.DyeColor.YELLOW)),
			new Cut("cutting/wild_hops", VBItems.WILD_HOPS.get(), VBItems.HOPS.get(), Items.DYE.pick(net.minecraft.world.item.DyeColor.LIME)));
		for (Cut cut : cuts) {
			helper.assertValueEqual(recipe(helper, cut.recipe()).isPresent(), fd(), cut.recipe() + " loads exactly when FD is installed");
		}
		if (!fd()) {
			helper.succeed();
			return;
		}
		@SuppressWarnings("removal")
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack knife = fdStack("flint_knife");
		int times = 16;
		BlockPos board = new BlockPos(2, 1, 2);
		helper.setBlock(board.below(), Blocks.STONE);
		helper.setBlock(board, fdBlock("cutting_board").orElseThrow());
		for (Cut cut : cuts) {
			for (int i = 0; i < times; i++) {
				ItemStack wild = new ItemStack(cut.wild());
				player.setItemInHand(InteractionHand.MAIN_HAND, wild);
				helper.getBlockState(board).useItemOn(wild, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, board));
				player.setItemInHand(InteractionHand.MAIN_HAND, knife);
				helper.getBlockState(board).useItemOn(knife, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, board));
			}
			int crops = countAround(helper, board, cut.crop());
			int dyes = countAround(helper, board, cut.dye());
			helper.assertValueEqual(crops, times, cut.recipe() + ": one crop per cut");
			// 32 dyes at 50% each: none or all of them would be a 1 in 2^31 chance
			helper.assertTrue(dyes > 0 && dyes < 2 * times, cut.recipe() + ": each of the two dyes about half the time: " + dyes);
			// the next crop counts its own drops
			helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(board)).inflate(3.0D)).forEach(ItemEntity::discard);
		}
		helper.succeed();
	}

	/**
	 * FD's melon juice, apple cider and milk bottle lower Tipsy by exactly one level, like our juices and a water bottle.
	 * The milk bottle would otherwise take one random effect, which could be all of Tipsy: Tipsy is in FD's
	 * milk-bottle-ignored tag (FDN's and FD Refabricated's names), so only our one level goes.
	 */
	private static void fdDrinksSoberByOne(GameTestHelper helper) {
		List<String> drinks = List.of("melon_juice", "apple_cider", "milk_bottle");
		for (String id : drinks) {
			assertFdThing(helper, fdItem(id), "farmersdelight:" + id);
		}
		if (!fd()) {
			helper.succeed();
			return;
		}
		for (String id : drinks) {
			TipsyGameTests.TestPlayer player = TipsyGameTests.player(helper);
			jexxme.vineandbarrel.tipsy.Tipsy.setLevel(player, 3);
			helper.assertValueEqual(player.getActiveEffects().size(), 1, "only Tipsy before " + id);
			TipsyGameTests.drink(player, fdStack(id));
			helper.assertValueEqual(jexxme.vineandbarrel.tipsy.Tipsy.getLevel(player), 2, "Tipsy after " + id);
		}
		helper.succeed();
	}

	private static List<ItemStack> drops(GameTestHelper helper, BlockState state, BlockPos pos, ItemStack tool) {
		return Block.getDrops(state, helper.getLevel(), helper.absolutePos(pos), null, player(helper), tool);
	}

	private static int count(List<ItemStack> drops, Item item) {
		return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	/** Every recipe of ours that needs FD has its recipe-book unlock (M9), and both load exactly when FD is installed. */
	private static void recipesHaveUnlocks(GameTestHelper helper) {
		for (String path : List.of("cooking/beer_battered_fish", "cooking/wine_braised_beef", "cooking/cider_braised_pork", "cooking/barley_mutton_broth",
				"cooking/white_wine_risotto", "cooking/coq_au_vin_block", "beer_bread")) {
			helper.assertValueEqual(recipe(helper, path).isPresent(), fd(), path + " loads exactly when FD is installed");
			Identifier unlockId = VineAndBarrel.id("recipes/food/" + path.substring(path.lastIndexOf('/') + 1));
			AdvancementHolder unlock = helper.getLevel().getServer().getAdvancements().get(unlockId);
			helper.assertValueEqual(unlock != null, fd(), unlockId + " loads exactly when FD is installed");
			if (unlock != null) {
				List<ResourceKey<Recipe<?>>> rewards = unlock.value().rewards().recipes();
				helper.assertTrue(rewards.contains(ResourceKey.create(Registries.RECIPE, VineAndBarrel.id(path))), unlockId + " unlocks " + path + ": " + rewards);
			}
		}
		helper.succeed();
	}

	/**
	 * A survival player that goes through the server's real click handling (ServerPlayerGameMode#useItemOn): main hand
	 * first, then the off hand if the main hand did nothing, as the client sends them.
	 */
	private static net.minecraft.server.level.ServerPlayer survivalPlayer(GameTestHelper helper, Vec3 at) {
		net.minecraft.server.network.CommonListenerCookie cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "feast-test-player"), false);
		net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
			cookie.gameProfile(), cookie.clientInformation());
		GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player, cookie);
		Vec3 pos = helper.absoluteVec(at);
		player.setPos(pos.x, pos.y, pos.z);
		return player;
	}

	private static void click(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player, BlockHitResult hit) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.gameMode.useItemOn(player, helper.getLevel(), player.getItemInHand(hand), hand, hit).consumesAction()) {
				return;
			}
		}
	}

	/**
	 * The feast through the server's real click handling, as FD's feasts behave: a bowl in the off hand serves when the
	 * main hand is empty, a block in the main hand is placed against the pot instead, and with a full inventory the
	 * serving drops at the player.
	 */
	private static void feastRealClicks(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		helper.setBlock(pos, feast());
		BlockPos absolute = helper.absolutePos(pos);
		BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.1, 0), Direction.UP, absolute, false);
		net.minecraft.server.level.ServerPlayer player = survivalPlayer(helper, new Vec3(2.5, 1.0, 4.5));

		// an empty main hand and a bowl in the off hand: the off hand serves
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BOWL, 2));
		click(helper, player, top);
		helper.assertValueEqual(helper.getBlockState(pos).getValue(FeastBlock.SERVINGS), 3, "the off-hand bowl serves");
		helper.assertValueEqual(player.getOffhandItem().getCount(), 1, "one bowl used from the off hand");
		helper.assertValueEqual(player.getInventory().countItem(FDCompat.COQ_AU_VIN.get()), 1, "the serving is in the inventory");
		helper.assertValueEqual(player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(Items.BOWL)), 1, "the bowl counts as used");

		// a block in the main hand is placed against the pot, nothing is served
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 4));
		BlockHitResult side = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, -0.2, 0.37), Direction.SOUTH, absolute, false);
		click(helper, player, side);
		helper.assertBlockPresent(Blocks.OAK_PLANKS, pos.south());
		helper.assertValueEqual(helper.getBlockState(pos).getValue(FeastBlock.SERVINGS), 3, "placing a block serves nothing");
		helper.setBlock(pos.south(), Blocks.AIR);

		// a full inventory: the serving drops at the player, not into the pot
		player.getInventory().clearContent();
		for (int slot = 0; slot < 36; slot++) {
			player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		}
		// two bowls, so the used one doesn't free its slot (one bowl would leave room for the serving in the hand)
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL, 2));
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE, 64));
		click(helper, player, top);
		helper.assertValueEqual(helper.getBlockState(pos).getValue(FeastBlock.SERVINGS), 2, "a full inventory still takes a serving");
		List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2.0D),
			entity -> entity.getItem().is(FDCompat.COQ_AU_VIN.get()));
		helper.assertValueEqual(dropped.size(), 1, "the serving drops at the player");
		helper.succeed();
	}

	/** A comparator reads the servings left (FD's feasts do the same): 4 when full, down to 0 for the leftovers. */
	private static void feastComparator(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 2);
		BlockPos comparator = pos.south();
		helper.setBlock(pos.below(), Blocks.STONE);
		helper.setBlock(comparator.below(), Blocks.STONE);
		BlockState full = feast();
		helper.assertTrue(full.hasAnalogOutputSignal(), "the feast has a comparator output");
		for (int servings = 0; servings <= FeastBlock.MAX_SERVINGS; servings++) {
			helper.assertValueEqual(full.setValue(FeastBlock.SERVINGS, servings).getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(pos), Direction.SOUTH),
				servings, "comparator output with " + servings + " servings");
		}
		// facing north, the comparator reads the block north of it: the feast, set down after it so the comparator updates
		helper.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(net.minecraft.world.level.block.ComparatorBlock.FACING, Direction.NORTH));
		helper.setBlock(pos, full.setValue(FeastBlock.SERVINGS, 3));
		helper.succeedWhen(() -> helper.assertValueEqual(
			helper.getBlockEntity(comparator, net.minecraft.world.level.block.entity.ComparatorBlockEntity.class).getOutputSignal(), 3,
			"a real comparator reads 3 servings"));
	}
}
