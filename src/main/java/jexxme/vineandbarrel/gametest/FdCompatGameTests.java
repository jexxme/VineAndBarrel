package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.block.RopeClimbing;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

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
		test("fd_optional_dependency", 20, FdCompatGameTests::optionalDependency);
		test("fd_stove_heats_kettle", 400, FdCompatGameTests::stoveHeatsKettle);
		test("fd_vines_climb_real_rope", 100, FdCompatGameTests::vinesClimbRealRope);
		test("fd_piston_keeps_the_vines_rope", 100, FdCompatGameTests::pistonKeepsRope);
		test("fd_rich_soil_speeds_crops", 100, FdCompatGameTests::richSoilSpeedsCrops);
		test("fd_apple_cider_becomes_hard_cider", 20, h -> fdDrinkInBarrel(h, "apple_cider", DrinkItems.HARD_CIDER.get(), DrinkItems.AGED_HARD_CIDER.get(), DrinkItems.MUG.get(), DrinkItems.WINE_GLASS.get()));
		test("fd_melon_juice_becomes_melon_wine", 20, h -> fdDrinkInBarrel(h, "melon_juice", DrinkItems.MELON_WINE.get(), DrinkItems.AGED_MELON_WINE.get(), DrinkItems.WINE_GLASS.get(), DrinkItems.MUG.get()));
		test("fd_drinks_tag", 20, FdCompatGameTests::drinksTag);
		test("fd_wild_crops_tags", 20, FdCompatGameTests::wildCropsTags);
		test("fd_bale_signal_smoke", 20, FdCompatGameTests::baleSignalSmoke);
		test("fd_aged_drinks_give_no_comfort", 20, FdCompatGameTests::noComfort);
		test("fd_meals_eat_like_fd_meals", 20, FdCompatGameTests::mealsEatLikeFdMeals);
		test("fd_cooking_pot_beer_battered_fish", 400, h -> cookingPot(h, "beer_battered_fish",
			List.of(new ItemStack(Items.COD), new ItemStack(DrinkItems.BARLEY_ALE.get()), new ItemStack(Items.WHEAT)), FDCompat.BEER_BATTERED_FISH.get(), DrinkItems.MUG.get()));
		test("fd_cooking_pot_wine_braised_beef", 400, h -> cookingPot(h, "wine_braised_beef",
			List.of(new ItemStack(Items.BEEF), new ItemStack(DrinkItems.AGED_RED_WINE.get()), fdStack("onion"), new ItemStack(Items.CARROT)), FDCompat.WINE_BRAISED_BEEF.get(), DrinkItems.WINE_GLASS.get()));
	}

	private FdCompatGameTests() {
	}

	private static void test(String name, int timeout, Consumer<GameTestHelper> function) {
		FUNCTIONS.register(name, () -> function);
		ResourceKey<Consumer<GameTestHelper>> key = ResourceKey.create(Registries.TEST_FUNCTION, VineAndBarrel.id(name));
		TESTS.add(key);
		TIMEOUTS.put(key, timeout);
	}

	public static void register(IEventBus modEventBus) {
		FUNCTIONS.register(modEventBus);
		modEventBus.addListener(FdCompatGameTests::registerTests);
	}

	private static void registerTests(RegisterGameTestsEvent event) {
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
		helper.assertValueEqual(ModList.get().isLoaded("farmersdelight"), Boolean.getBoolean("vineandbarrel.expectFd"), "FD loaded (runGameTestServerFd sets vineandbarrel.expectFd)");
		for (String id : List.of("stove", "rope", "rich_soil", "rich_soil_farmland", "cooking_pot", "straw_bale")) {
			assertFdThing(helper, fdBlock(id), "farmersdelight:" + id);
		}
		for (String id : List.of("apple_cider", "melon_juice", "onion")) {
			assertFdThing(helper, fdItem(id), "farmersdelight:" + id);
		}
		helper.succeed();
	}

	/** neoforge.mods.toml declares FD as an optional dependency, loaded before us when present. */
	private static void optionalDependency(GameTestHelper helper) {
		IModInfo info = ModList.get().getModContainerById(VineAndBarrel.MODID).orElseThrow().getModInfo();
		IModInfo.ModVersion dependency = info.getDependencies().stream().filter(d -> d.getModId().equals(FDCompat.MODID)).findFirst()
			.orElseThrow(() -> new IllegalStateException("no farmersdelight dependency in neoforge.mods.toml"));
		helper.assertValueEqual(dependency.getType(), IModInfo.DependencyType.OPTIONAL, "FD dependency type");
		helper.assertValueEqual(dependency.getOrdering(), IModInfo.Ordering.AFTER, "we load after FD");
		ModList.get().getModContainerById(FDCompat.MODID).ifPresent(fd -> helper.assertTrue(
			dependency.getVersionRange().containsVersion(fd.getModInfo().getVersion()), "the installed FD " + fd.getModInfo().getVersion() + " is in range " + dependency.getVersionRange()));
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
	 * goes. Without FD there is no FD rope and a ripe bush under any other block stays a bush.
	 */
	private static void vinesClimbRealRope(GameTestHelper helper) {
		BlockPos bush = new BlockPos(2, 1, 2);
		helper.setBlock(bush.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(bush, VBBlocks.GRAPE_BUSH.get().defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
		Optional<Block> fdRope = fdBlock("rope");
		assertFdThing(helper, fdRope, "FD's rope");
		if (fdRope.isEmpty()) {
			helper.setBlock(bush.above(), Blocks.OAK_FENCE);
			for (int i = 0; i < 300; i++) {
				helper.randomTick(bush);
			}
			helper.assertBlockPresent(Blocks.OAK_FENCE, bush.above());
			helper.succeed();
			return;
		}
		Block rope = fdRope.get();
		helper.assertTrue(rope.defaultBlockState().is(VBTags.FD_ROPES), "FD's rope is in #farmersdelight:ropes");
		helper.assertTrue(RopeClimbing.ropeState(helper.getLevel(), helper.absolutePos(bush.above())).is(rope), "the rope a vine leaves behind is FD's");
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

	/** A piston moves a grown vine whole, like FD's tomatoes on rope; off its bush it withers and leaves the rope. */
	private static void pistonKeepsRope(GameTestHelper helper) {
		Optional<Block> fdRope = fdBlock("rope");
		assertFdThing(helper, fdRope, "FD's rope");
		helper.assertValueEqual(VBBlocks.GRAPE_VINE_ON_ROPE.get().defaultBlockState().getPistonPushReaction(), PushReaction.NORMAL, "grape vine push reaction");
		helper.assertValueEqual(VBBlocks.HOP_VINE_ON_ROPE.get().defaultBlockState().getPistonPushReaction(), PushReaction.NORMAL, "hop vine push reaction");
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
		helper.setBlock(grapeSoil.above(), VBBlocks.GRAPE_BUSH.get());
		helper.setBlock(hopSoil.above(), VBBlocks.HOP_BUSH.get());
		helper.setBlock(barleySoil.above(), VBBlocks.BARLEY.get());
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

	/** Every drink and both juices are in {@code farmersdelight:drinks}; with FD the tag merges with FD's own drinks. */
	private static void drinksTag(GameTestHelper helper) {
		TagKey<Item> drinks = TagKey.create(Registries.ITEM, FDCompat.id("drinks"));
		List<Item> ours = new ArrayList<>(DrinkItems.allDrinks());
		ours.add(BaseItems.GRAPE_JUICE.get());
		ours.add(BaseItems.SWEET_BERRY_JUICE.get());
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
		for (Block wild : List.of(VBBlocks.WILD_GRAPES.get(), VBBlocks.WILD_HOPS.get(), VBBlocks.WILD_BARLEY.get())) {
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
	 * The two meals always exist (stable worlds without FD) and eat like FD's bowl meals: FD's food values for a
	 * comparable meal, stack to 16, the bowl comes back, in {@code farmersdelight:meals}; with FD they also give FD's
	 * Nourishment and show it in the tooltip, without FD they give no effect and show none.
	 */
	private static void mealsEatLikeFdMeals(GameTestHelper helper) {
		TagKey<Item> meals = TagKey.create(Registries.ITEM, FDCompat.id("meals"));
		record Expected(MealItem meal, int nutrition, float saturation, int nourishment) {
		}
		for (Expected e : List.of(new Expected(FDCompat.BEER_BATTERED_FISH.get(), 12, 0.8F, FDCompat.MEDIUM_DURATION),
			new Expected(FDCompat.WINE_BRAISED_BEEF.get(), 14, 0.75F, FDCompat.LONG_DURATION))) {
			ItemStack stack = new ItemStack(e.meal());
			FoodProperties food = stack.get(DataComponents.FOOD);
			helper.assertTrue(food != null && food.nutrition() == e.nutrition() && Math.abs(food.saturation() - e.nutrition() * e.saturation() * 2.0F) < 0.01F,
				e.meal() + " food values: " + food);
			helper.assertValueEqual(stack.getMaxStackSize(), 16, e.meal() + " stacks to 16");
			helper.assertTrue(stack.getCraftingRemainder() != null && stack.getCraftingRemainder().item().value() == Items.BOWL, e.meal() + " leaves a bowl in recipes");
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
	 * a lit stove, fed like a hopper would (ingredients from the top, a bowl from the side, the meal out of the bottom),
	 * and every ingredient is accounted for: all used up, and the drink's vessel pops out as the ingredient remainder.
	 */
	private static void cookingPot(GameTestHelper helper, String name, List<ItemStack> ingredients, MealItem meal, Item vessel) {
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
		ResourceHandler<ItemResource> top = helper.getLevel().getCapability(Capabilities.Item.BLOCK, absolute, Direction.UP);
		ResourceHandler<ItemResource> side = helper.getLevel().getCapability(Capabilities.Item.BLOCK, absolute, Direction.NORTH);
		helper.assertTrue(top != null && side != null, "FD's cooking pot exposes its item handlers");
		try (Transaction tx = Transaction.openRoot()) {
			for (int i = 0; i < ingredients.size(); i++) {
				ItemStack ingredient = ingredients.get(i);
				helper.assertTrue(!ingredient.isEmpty(), "ingredient " + i + " exists");
				helper.assertValueEqual(top.insert(i, ItemResource.of(ingredient), 1, tx), 1, "inserted " + ingredient);
			}
			int bowls = 0;
			for (int i = 0; i < side.size() && bowls == 0; i++) {
				bowls = side.insert(i, ItemResource.of(Items.BOWL), 1, tx);
			}
			helper.assertValueEqual(bowls, 1, "a bowl goes into the container slot from the side");
			tx.commit();
		}
		ResourceHandler<ItemResource> bottom = helper.getLevel().getCapability(Capabilities.Item.BLOCK, absolute, Direction.DOWN);
		helper.succeedWhen(() -> {
			int served;
			try (Transaction tx = Transaction.openRoot()) {
				served = 0;
				for (int i = 0; i < bottom.size(); i++) {
					served += bottom.extract(i, ItemResource.of(meal), 64, tx);
				}
			}
			helper.assertValueEqual(served, 1, "one " + meal + " served into the bowl");
			for (int i = 0; i < ingredients.size(); i++) {
				helper.assertTrue(top.getResource(i).isEmpty(), "ingredient slot " + i + " used up");
			}
			helper.assertValueEqual(countAround(helper, pot, vessel), 1, "the " + vessel + " comes back out of the pot");
		});
	}
}
