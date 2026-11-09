package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.block.VBFlammability;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.block.WildCropBlock;
import jexxme.vineandbarrel.entity.BeeGrowth;
import jexxme.vineandbarrel.entity.FoxPickGrapesGoal;
import jexxme.vineandbarrel.mixin.BeeAccessor;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import jexxme.vineandbarrel.tag.VBTags;
import jexxme.vineandbarrel.world.WildCropFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static jexxme.vineandbarrel.gametest.VBGameTests.LARGE;
import static jexxme.vineandbarrel.gametest.VBGameTests.test;

/**
 * M1 crops: planting, growth, harvesting, rope climbing, wild crops, worldgen and the farm interactions C1-C7.
 * Every test drives the real game code (random ticks, bone meal, block use, loot tables, mob AI goals and behaviours).
 */
final class VBCropTests
{
	private VBCropTests() {
	}

	static void registerAll() {
		test("crops_planting_from_items", VBCropTests::plantingFromItems);
		test("crops_grape_bush_grows_every_stage", h -> randomTickGrowth(h, VBBlocks.GRAPE_BUSH.get(), Blocks.GRASS_BLOCK, ClimbingCropBlock.AGE));
		test("crops_hop_bush_grows_every_stage", h -> randomTickGrowth(h, VBBlocks.HOP_BUSH.get(), Blocks.GRASS_BLOCK, ClimbingCropBlock.AGE));
		test("crops_white_grape_bush_grows_every_stage", h -> randomTickGrowth(h, VBBlocks.WHITE_GRAPE_BUSH.get(), Blocks.GRASS_BLOCK, ClimbingCropBlock.AGE));
		test("crops_barley_grows_every_stage", h -> randomTickGrowth(h, VBBlocks.BARLEY.get(), Blocks.FARMLAND, CropBlock.AGE));
		test("crops_bone_meal_grows_bushes_and_barley", VBCropTests::boneMealGrowth);
		test("crops_grape_bush_harvest", h -> bushHarvest(h, VBBlocks.GRAPE_BUSH.get(), VBItems.RED_GRAPES.get()));
		test("crops_hop_bush_harvest", h -> bushHarvest(h, VBBlocks.HOP_BUSH.get(), VBItems.HOPS.get()));
		test("crops_white_grape_bush_harvest", h -> bushHarvest(h, VBBlocks.WHITE_GRAPE_BUSH.get(), VBItems.WHITE_GRAPES.get()));
		test("crops_bush_break_drops", VBCropTests::bushBreakDrops);
		test("crops_barley_maturity_drops", VBCropTests::barleyDrops);
		test("crops_wild_crop_drops", VBCropTests::wildCropDrops);
		test("crops_wild_crops_stand_on_soil", VBCropTests::wildCropsStandOnSoil);
		test("crops_grape_climbs_rope", LARGE, 200, h -> ropeClimbing(h, VBBlocks.GRAPE_BUSH.get(), VBBlocks.GRAPE_VINE_ON_ROPE.get(), VBItems.RED_GRAPES.get()));
		test("crops_hop_climbs_rope", LARGE, 200, h -> ropeClimbing(h, VBBlocks.HOP_BUSH.get(), VBBlocks.HOP_VINE_ON_ROPE.get(), VBItems.HOPS.get()));
		test("crops_white_grape_climbs_rope", LARGE, 200,
				h -> ropeClimbing(h, VBBlocks.WHITE_GRAPE_BUSH.get(), VBBlocks.WHITE_GRAPE_VINE_ON_ROPE.get(), VBItems.WHITE_GRAPES.get()));
		test("crops_no_rope_no_climbing", VBCropTests::noRopeNoClimbing);
		test("crops_vine_broken_by_a_player_once", VBCropTests::vineBrokenOnce);
		test("crops_worldgen_wild_crops", LARGE, 100, true, VBCropTests::worldgenWildCrops);
		test("crops_worldgen_wild_white_grapes", LARGE, 100, true, VBCropTests::worldgenWildWhiteGrapes);
		test("farm_c2_c3_c4_tags", VBCropTests::farmTags);
		test("farm_c2_bees_grow_bushes_and_vines", VBCropTests::beesGrowBushesAndVines);
		test("crops_burn_like_vanilla", VBCropTests::burnLikeVanilla);
		test("farm_c3_cow_breeds_from_barley", LARGE, 600, VBCropTests::cowBreedsFromBarley);
		test("farm_c3_c7_horse_and_llama_eat", VBCropTests::horseAndLlamaEat);
		test("farm_c4_farmer_harvests_and_replants_barley", VBCropTests::farmerHarvestsBarley);
		test("farm_c5_fox_picks_ripe_grapes", h -> foxPicksGrapes(h, VBBlocks.GRAPE_BUSH.get(), VBBlocks.GRAPE_VINE_ON_ROPE.get(), VBItems.RED_GRAPES.get()));
		test("farm_c5_fox_picks_ripe_white_grapes",
				h -> foxPicksGrapes(h, VBBlocks.WHITE_GRAPE_BUSH.get(), VBBlocks.WHITE_GRAPE_VINE_ON_ROPE.get(), VBItems.WHITE_GRAPES.get()));
		test("farm_c6_composter_accepts_crops", VBCropTests::composterAcceptsCrops);
		test("farm_c7_storage_recipes_both_ways", VBCropTests::storageRecipes);
		test("farm_c7_bale_cuts_fall_damage", VBCropTests::baleFallDamage);
		test("farm_c7_bale_campfire_signal_smoke", VBCropTests::baleSignalSmoke);
	}

	// ------------------------------------------------------------------ helpers

	private static Player player(GameTestHelper helper) {
		return helper.makeMockPlayer(GameType.SURVIVAL);
	}

	private static BlockState state(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockState(pos);
	}

	/** Sums and removes every item entity near {@code pos}; fails if any of them is not {@code expected}. */
	private static int collect(GameTestHelper helper, BlockPos pos, Item expected) {
		AABB box = new AABB(helper.absolutePos(pos)).inflate(2.5);
		int count = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			helper.assertTrue(entity.getItem().is(expected), "Unexpected drop " + entity.getItem() + ", expected only " + expected);
			count += entity.getItem().getCount();
			entity.discard();
		}
		return count;
	}

	private static int count(List<ItemStack> drops, Item item) {
		return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	private static List<ItemStack> drops(GameTestHelper helper, BlockState state, BlockPos pos, ItemStack tool) {
		return Block.getDrops(state, helper.getLevel(), helper.absolutePos(pos), null, player(helper), tool);
	}

	private static boolean boneMeal(GameTestHelper helper, BlockPos pos) {
		return BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), helper.getLevel(), helper.absolutePos(pos));
	}

	// ------------------------------------------------------------------ planting

	/** Each item plants its crop where vanilla would allow it: grapes and hops on grass like sweet berries, seeds on farmland only. */
	private static void plantingFromItems(GameTestHelper helper) {
		Player player = player(helper);
		record Plant(Supplier<? extends Item> item, Block soil, Block crop, int x, int z) {
		}
		List<Plant> plants = List.of(
				new Plant(VBItems.RED_GRAPES, Blocks.GRASS_BLOCK, VBBlocks.GRAPE_BUSH.get(), 0, 0),
				new Plant(VBItems.HOPS, Blocks.DIRT, VBBlocks.HOP_BUSH.get(), 2, 0),
				new Plant(VBItems.BARLEY_SEEDS, Blocks.FARMLAND, VBBlocks.BARLEY.get(), 4, 0),
				new Plant(VBItems.WHITE_GRAPES, Blocks.GRASS_BLOCK, VBBlocks.WHITE_GRAPE_BUSH.get(), 2, 2));
		for (Plant plant : plants) {
			BlockPos soil = new BlockPos(plant.x(), 1, plant.z());
			helper.setBlock(soil, plant.soil());
			ItemStack stack = new ItemStack(plant.item().get(), 2);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack); // the use context reads the item from the hand
			helper.placeAt(player, stack, soil, Direction.UP);
			BlockState planted = state(helper, soil.above());
			helper.assertTrue(planted.is(plant.crop()), "Expected " + plant.crop() + " planted from " + plant.item().get() + ", got " + planted);
			IntegerProperty age = plant.crop() instanceof CropBlock ? CropBlock.AGE : ClimbingCropBlock.AGE;
			helper.assertValueEqual(planted.getValue(age), 0, "age of a freshly planted " + plant.crop());
			helper.assertValueEqual(stack.getCount(), 1, "items left after planting " + plant.item().get());
		}
		// Barley seeds do not take on grass (wheat rule)
		BlockPos grass = new BlockPos(0, 1, 3);
		helper.setBlock(grass, Blocks.GRASS_BLOCK);
		ItemStack seeds = new ItemStack(VBItems.BARLEY_SEEDS.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, seeds);
		helper.placeAt(player, seeds, grass, Direction.UP);
		helper.assertBlockPresent(Blocks.AIR, grass.above());
		helper.succeed();
	}

	// ------------------------------------------------------------------ growth

	/**
	 * Random ticks take the crop through every age, one at a time, from 0 to its maximum. First it is boxed in stone:
	 * below light 9 (vanilla's crop and berry bush rule) nothing grows.
	 */
	private static void randomTickGrowth(GameTestHelper helper, Block crop, Block soil, IntegerProperty age) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), soil);
		helper.setBlock(pos, crop.defaultBlockState());
		// a closed stone box around the plant and its soil: farmland is not a full block, so light would leak in through it
		List<BlockPos> box = new ArrayList<>(List.of(pos.above(), pos.below(2)));
		for (BlockPos layer : List.of(pos, pos.below())) {
			box.addAll(List.of(layer.north(), layer.south(), layer.east(), layer.west()));
		}
		box.forEach(wall -> helper.setBlock(wall, Blocks.STONE));
		// the crop rule reads the light at the plant, the bush rule one block above it
		BlockPos lightPos = crop instanceof CropBlock ? pos : pos.above();
		settleLight(helper, box);
		int dark = helper.getLevel().getRawBrightness(helper.absolutePos(lightPos), 0);
		helper.assertTrue(dark < 9, "the stone box makes it dark at the plant (below 9), but the light is " + dark);
		for (int i = 0; i < 500; i++) {
			helper.randomTick(pos);
		}
		helper.assertValueEqual(state(helper, pos).getValue(age), 0, "age of " + crop + " after 500 random ticks in the dark");
		box.forEach(wall -> helper.setBlock(wall, Blocks.AIR));
		settleLight(helper, box);
		int bright = helper.getLevel().getRawBrightness(helper.absolutePos(lightPos), 0);
		helper.assertTrue(bright >= 9, "without the box it is bright at the plant (9 or more), but the light is " + bright);
		// the box came off while the light still was the box's: barley popped off like vanilla wheat in the dark, replant it
		helper.setBlock(pos.below(), soil);
		helper.setBlock(pos, crop.defaultBlockState());
		growToMax(helper, crop, pos, age);
	}

	/**
	 * Waits until the light engine has done the light updates queued for the chunks of {@code changed} (relative
	 * positions). Light is worked out on another thread, and a test server ticks as fast as it can (100 ticks can be a
	 * few milliseconds), so a wait counted in ticks can run out while the light engine is still busy, e.g. lighting the
	 * chunks a new test batch loaded; the light then still reads 0. This waits in real time instead, with the server
	 * thread held, so no tick passes and nothing else changes meanwhile; it fails after 20 seconds.
	 */
	private static void settleLight(GameTestHelper helper, List<BlockPos> changed) {
		settleLightAt(helper, changed.stream().map(helper::absolutePos).toList());
	}

	/** {@link #settleLight} for absolute positions (the taverns' lighting test waits for the whole tavern this way). */
	static void settleLightAt(GameTestHelper helper, List<BlockPos> absolute) {
		ThreadedLevelLightEngine engine = helper.getLevel().getChunkSource().getLightEngine();
		CompletableFuture<Void> settled = CompletableFuture.allOf(absolute.stream().map(ChunkPos::containing).distinct()
			.map(chunk -> engine.waitForPendingTasks(chunk.x(), chunk.z())).toArray(CompletableFuture[]::new));
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		while (true) {
			// the server tick starts the light engine's next run; the tick is held here, so start it ourselves
			engine.tryScheduleUpdate();
			try {
				settled.get(1, TimeUnit.MILLISECONDS);
				return;
			} catch (TimeoutException e) {
				if (System.nanoTime() > deadline) {
					helper.fail("The light engine did not finish its light updates within 20 seconds");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting for the light engine", e);
			} catch (ExecutionException e) {
				throw new IllegalStateException("The light engine failed", e);
			}
		}
	}

	private static void growToMax(GameTestHelper helper, Block crop, BlockPos pos, IntegerProperty age) {
		int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElseThrow();
		int current = 0;
		List<Integer> seen = new ArrayList<>(List.of(0));
		for (int i = 0; i < 20000 && current < max; i++) {
			helper.randomTick(pos);
			BlockState state = state(helper, pos);
			helper.assertTrue(state.is(crop), crop + " disappeared while growing: " + state);
			int now = state.getValue(age);
			if (now != current) {
				helper.assertValueEqual(now, current + 1, "next age of " + crop);
				current = now;
				seen.add(now);
			}
		}
		helper.assertValueEqual(current, max, "final age of " + crop + " (ages seen " + seen + ")");
		helper.succeed();
	}

	/** Bone meal: a bush gains exactly one age per use like the sweet berry bush, and stops at ripe without a rope; barley grows like wheat. */
	private static void boneMealGrowth(GameTestHelper helper) {
		for (Block bush : List.of(VBBlocks.GRAPE_BUSH.get(), VBBlocks.HOP_BUSH.get())) {
			BlockPos pos = new BlockPos(bush == VBBlocks.GRAPE_BUSH.get() ? 1 : 3, 2, 1);
			helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
			helper.setBlock(pos, bush);
			for (int expected = 1; expected <= ClimbingCropBlock.MAX_AGE; expected++) {
				helper.assertTrue(boneMeal(helper, pos), "Bone meal should work on " + bush + " at age " + (expected - 1));
				helper.assertValueEqual(state(helper, pos).getValue(ClimbingCropBlock.AGE), expected, "age after bone meal");
			}
			helper.assertFalse(boneMeal(helper, pos), "Bone meal must not be used up on a ripe " + bush + " without a rope");
		}
		BlockPos barley = new BlockPos(2, 2, 3);
		helper.setBlock(barley.below(), Blocks.FARMLAND);
		helper.setBlock(barley, VBBlocks.BARLEY.get());
		for (int i = 0; i < 8 && !VBBlocks.BARLEY.get().isMaxAge(state(helper, barley)); i++) {
			helper.assertTrue(boneMeal(helper, barley), "Bone meal should grow barley");
		}
		helper.assertTrue(VBBlocks.BARLEY.get().isMaxAge(state(helper, barley)), "Barley should be mature after bone meal");
		helper.succeed();
	}

	// ------------------------------------------------------------------ harvesting (C1)

	/**
	 * C1: right-clicking a ripe bush picks 2-3 fruit (sweet berry bush yield at age 3), nothing else, and drops it back
	 * to age 1. Both yields show up over 32 harvests. Half-ripe (age 2) it gives 1-2 (VBTrellisTests); younger it gives
	 * nothing and keeps its age.
	 */
	private static void bushHarvest(GameTestHelper helper, Block bush, Item fruit) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
		Player player = player(helper);
		boolean sawTwo = false;
		boolean sawThree = false;
		for (int i = 0; i < 32; i++) {
			helper.setBlock(pos, bush.defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
			helper.useBlock(pos, player);
			helper.assertValueEqual(state(helper, pos).getValue(ClimbingCropBlock.AGE), ClimbingCropBlock.PICKED_AGE, "age after picking");
			int picked = collect(helper, pos, fruit);
			helper.assertTrue(picked == 2 || picked == 3, "A harvest should give 2 or 3 " + fruit + ", gave " + picked);
			sawTwo |= picked == 2;
			sawThree |= picked == 3;
		}
		helper.assertTrue(sawTwo && sawThree, "Over 32 harvests both 2 and 3 should appear");
		for (int age = 0; age < ClimbingCropBlock.HALF_RIPE_AGE; age++) {
			helper.setBlock(pos, bush.defaultBlockState().setValue(ClimbingCropBlock.AGE, age));
			helper.useBlock(pos, player);
			helper.assertValueEqual(state(helper, pos).getValue(ClimbingCropBlock.AGE), age, "age of an unripe bush after use");
			helper.assertValueEqual(collect(helper, pos, fruit), 0, "fruit from an unripe bush");
		}
		helper.succeed();
	}

	/** Breaking a bush drops its fruit like the sweet berry bush: 2-3 when ripe, 1-2 when half-ripe, nothing younger. */
	private static void bushBreakDrops(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
		for (Block bush : List.of(VBBlocks.GRAPE_BUSH.get(), VBBlocks.WHITE_GRAPE_BUSH.get(), VBBlocks.HOP_BUSH.get())) {
			Item fruit = ((ClimbingCropBlock) bush).produce();
			for (int age = 0; age <= 3; age++) {
				List<ItemStack> drops = drops(helper, bush.defaultBlockState().setValue(ClimbingCropBlock.AGE, age), pos, ItemStack.EMPTY);
				int fruitCount = count(drops, fruit);
				helper.assertValueEqual(drops.size() - (fruitCount > 0 ? 1 : 0), 0, "other drops of " + bush + " " + drops);
				if (age == 3) {
					helper.assertTrue(fruitCount == 2 || fruitCount == 3, "Ripe " + bush + " should drop 2-3, dropped " + fruitCount);
				} else if (age == 2) {
					helper.assertTrue(fruitCount == 1 || fruitCount == 2, "Half-ripe " + bush + " should drop 1-2, dropped " + fruitCount);
				} else {
					helper.assertValueEqual(fruitCount, 0, bush + " drops at age " + age);
				}
			}
		}
		helper.succeed();
	}

	/** Barley drops like wheat: mature gives exactly 1 barley and 1-4 seeds, immature exactly 1 seed. */
	private static void barleyDrops(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.FARMLAND);
		BlockState mature = VBBlocks.BARLEY.get().getStateForAge(7);
		for (int i = 0; i < 20; i++) {
			List<ItemStack> drops = drops(helper, mature, pos, ItemStack.EMPTY);
			helper.assertValueEqual(count(drops, VBItems.BARLEY.get()), 1, "barley from mature barley");
			int seeds = count(drops, VBItems.BARLEY_SEEDS.get());
			helper.assertTrue(seeds >= 1 && seeds <= 4, "Mature barley should drop 1-4 seeds, dropped " + seeds);
		}
		for (int age = 0; age < 7; age++) {
			List<ItemStack> drops = drops(helper, VBBlocks.BARLEY.get().getStateForAge(age), pos, ItemStack.EMPTY);
			helper.assertValueEqual(count(drops, VBItems.BARLEY.get()), 0, "barley from barley at age " + age);
			helper.assertValueEqual(count(drops, VBItems.BARLEY_SEEDS.get()), 1, "seeds from barley at age " + age);
		}
		helper.succeed();
	}

	/** Wild crops drop like FD's: shears (vanilla's, or any in #c:tools/shear) take the plant, otherwise exactly one crop (barley: one barley and one seed). */
	private static void wildCropDrops(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
		ItemStack shears = new ItemStack(Items.SHEARS);
		record Wild(Block block, Item crop) {
		}
		for (Wild wild : List.of(new Wild(VBBlocks.WILD_GRAPES.get(), VBItems.RED_GRAPES.get()),
				new Wild(VBBlocks.WILD_WHITE_GRAPES.get(), VBItems.WHITE_GRAPES.get()), new Wild(VBBlocks.WILD_HOPS.get(), VBItems.HOPS.get()))) {
			for (int i = 0; i < 10; i++) {
				List<ItemStack> drops = drops(helper, wild.block().defaultBlockState(), pos, ItemStack.EMPTY);
				helper.assertTrue(drops.size() == 1 && count(drops, wild.crop()) == 1, wild.block() + " should drop exactly one " + wild.crop() + ", dropped " + drops);
			}
			List<ItemStack> sheared = drops(helper, wild.block().defaultBlockState(), pos, shears);
			helper.assertTrue(sheared.size() == 1 && count(sheared, wild.block().asItem()) == 1, "Shears should take " + wild.block() + " itself, got " + sheared);
			// "Modded shears": the test data puts the brush into #c:tools/shear, the common tag such mods use
			List<ItemStack> modded = drops(helper, wild.block().defaultBlockState(), pos, new ItemStack(Items.BRUSH));
			helper.assertTrue(modded.size() == 1 && count(modded, wild.block().asItem()) == 1, "A tool in #c:tools/shear should take " + wild.block() + " itself, got " + modded);
		}
		Block wildBarley = VBBlocks.WILD_BARLEY.get();
		for (int i = 0; i < 10; i++) {
			List<ItemStack> drops = drops(helper, wildBarley.defaultBlockState(), pos, ItemStack.EMPTY);
			helper.assertValueEqual(count(drops, VBItems.BARLEY_SEEDS.get()), 1, "seeds from wild barley");
			helper.assertValueEqual(count(drops, VBItems.BARLEY.get()), 1, "barley from wild barley, every time");
		}
		List<ItemStack> sheared = drops(helper, wildBarley.defaultBlockState(), pos, shears);
		helper.assertTrue(sheared.size() == 1 && count(sheared, wildBarley.asItem()) == 1, "Shears should take wild barley itself, got " + sheared);
		helper.succeed();
	}

	/** Wild crops stand where FD's do (any natural soil or sand), not on stone; bone meal spreads them nearby (FD). */
	private static void wildCropsStandOnSoil(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Block> soils = List.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.PODZOL, Blocks.SAND, Blocks.COARSE_DIRT);
		for (Block wild : List.of(VBBlocks.WILD_GRAPES.get(), VBBlocks.WILD_WHITE_GRAPES.get(), VBBlocks.WILD_HOPS.get(), VBBlocks.WILD_BARLEY.get())) {
			for (int i = 0; i < soils.size(); i++) {
				BlockPos pos = new BlockPos(i, 2, 0);
				helper.setBlock(pos.below(), soils.get(i));
				helper.assertTrue(wild.defaultBlockState().canSurvive(level, helper.absolutePos(pos)), wild + " should stand on " + soils.get(i));
			}
			helper.setBlock(new BlockPos(0, 1, 4), Blocks.STONE);
			helper.assertFalse(wild.defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(0, 2, 4))), wild + " must not stand on stone");
		}
		for (int x = 0; x < 5; x++) {
			for (int z = 1; z < 4; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
		BlockPos center = new BlockPos(2, 2, 2);
		helper.setBlock(center, VBBlocks.WILD_GRAPES.get());
		for (int i = 0; i < 30; i++) {
			boneMeal(helper, center);
		}
		helper.assertTrue(countAround(helper, VBBlocks.WILD_GRAPES.get()) > 1, "Bone meal should spread wild grapes");
		helper.succeed();
	}

	// ------------------------------------------------------------------ FD rope climbing (tag only; FD's real rope in the FD run, the test data's stand-in otherwise)

	/**
	 * A player breaking a vine on a rope breaks it once: one destroy game event (the sound, particles and sculk signal of
	 * playerWillDestroy come with it), and the vine is gone.
	 */
	private static void vineBrokenOnce(GameTestHelper helper) {
		BlockPos bush = new BlockPos(2, 1, 2);
		BlockPos vine = bush.above();
		helper.setBlock(bush.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(bush, VBBlocks.GRAPE_BUSH.get().defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
		helper.setBlock(vine, VBBlocks.GRAPE_VINE_ON_ROPE.get().defaultBlockState().setValue(ClimbingCropBlock.AGE, 1));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos absolute = helper.absolutePos(vine);
		AtomicInteger destroyed = new AtomicInteger();
		GameEventListener listener = new GameEventListener()
		{
			@Override
			public PositionSource getListenerSource() {
				return new BlockPositionSource(absolute);
			}

			@Override
			public int getListenerRadius() {
				return 2;
			}

			@Override
			public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 at) {
				if (event.is(GameEvent.BLOCK_DESTROY) && context.sourceEntity() == player && BlockPos.containing(at).equals(absolute)) {
					destroyed.incrementAndGet();
				}
				return false;
			}
		};
		GameEventListenerRegistry listeners = helper.getLevel().getChunkAt(absolute).getListenerRegistry(SectionPos.blockToSectionCoord(absolute.getY()));
		listeners.register(listener);
		try {
			player.gameMode.destroyBlock(absolute);
		} finally {
			listeners.unregister(listener);
		}
		helper.assertBlockNotPresent(VBBlocks.GRAPE_VINE_ON_ROPE.get(), vine);
		helper.assertValueEqual(destroyed.get(), 1, "destroy game events for one break");
		helper.succeed();
	}

	/**
	 * A ripe bush with ropes above: a random tick grows the vine onto the first rope, bone meal grows it to 3 blocks
	 * (FD's limit, the third rope stays a rope), a ripe vine is picked like the bush, and when the bush goes the ropes
	 * come back instead of the vines (FD's rope permanence) without dropping rope items.
	 */
	private static void ropeClimbing(GameTestHelper helper, Block bush, Block vine, Item fruit) {
		BlockPos bushPos = new BlockPos(4, 1, 4);
		helper.setBlock(bushPos.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(bushPos, bush.defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
		// FD's real rope when FD is installed (the FD run), otherwise the test data's stand-in
		Block rope = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(VBTags.FD_ROPE).orElseGet(() -> helper.getLevel().registryAccess()
				.lookupOrThrow(Registries.BLOCK).getOrThrow(VBTags.FD_ROPES).stream()
				.findFirst().map(Holder::value).orElseThrow(() -> new IllegalStateException("The test data should put a stand-in rope into #farmersdelight:ropes")));
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(bushPos.above(y), rope);
		}
		// random ticks: the ripe bush climbs one block
		for (int i = 0; i < 2000 && !state(helper, bushPos.above()).is(vine); i++) {
			helper.randomTick(bushPos);
		}
		helper.assertTrue(state(helper, bushPos.above()).is(vine), "The ripe bush should climb the rope above it");
		helper.assertValueEqual(state(helper, bushPos.above()).getValue(ClimbingCropBlock.AGE), 0, "age of a fresh vine");
		helper.assertBlockPresent(rope, bushPos.above(2));
		// bone meal on the bush passes up to the vine and climbs on, up to 3 tall
		for (int i = 0; i < 40; i++) {
			boneMeal(helper, bushPos);
		}
		helper.assertTrue(state(helper, bushPos.above()).is(vine) && state(helper, bushPos.above(2)).is(vine), "Bone meal should grow the vine two ropes high");
		helper.assertBlockPresent(rope, bushPos.above(3));
		helper.assertFalse(boneMeal(helper, bushPos), "A fully grown 3-tall plant is no bone meal target");
		// a ripe vine is picked like the bush, a half-ripe one too (fewer)
		BlockPos top = bushPos.above(2);
		helper.setBlock(top, vine.defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
		helper.useBlock(top, player(helper));
		int picked = collect(helper, top, fruit);
		helper.assertTrue(picked == 2 || picked == 3, "Picking a ripe vine should give 2-3, gave " + picked);
		helper.assertValueEqual(state(helper, top).getValue(ClimbingCropBlock.AGE), ClimbingCropBlock.PICKED_AGE, "vine age after picking");
		helper.setBlock(top, vine.defaultBlockState().setValue(ClimbingCropBlock.AGE, 2));
		helper.useBlock(top, player(helper));
		int half = collect(helper, top, fruit);
		helper.assertTrue(half == 1 || half == 2, "Picking a half-ripe vine should give 1-2, gave " + half);
		helper.assertValueEqual(state(helper, top).getValue(ClimbingCropBlock.AGE), ClimbingCropBlock.PICKED_AGE, "vine age after picking it half-ripe");
		// remove the bush: the vines lose their support and the ropes come back
		helper.getLevel().destroyBlock(helper.absolutePos(bushPos), false);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(rope, bushPos.above());
			helper.assertBlockPresent(rope, bushPos.above(2));
			helper.assertBlockPresent(rope, bushPos.above(3));
			helper.assertItemEntityNotPresent(rope.asItem(), bushPos.above(), 3.0);
		});
	}

	/** Under a block that holds no vine (a glass pane: no rope, trellis, fence, wall, chain or bars) a ripe bush never climbs. */
	private static void noRopeNoClimbing(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(pos, VBBlocks.GRAPE_BUSH.get().defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
		helper.setBlock(pos.above(), Blocks.GLASS_PANE);
		for (int i = 0; i < 300; i++) {
			helper.randomTick(pos);
		}
		helper.assertBlockPresent(Blocks.GLASS_PANE, pos.above());
		helper.assertFalse(boneMeal(helper, pos), "Bone meal must not be used on a ripe bush under a glass pane");
		helper.succeed();
	}

	// ------------------------------------------------------------------ worldgen

	/**
	 * The biome modifiers load and pick the biomes from DESIGN 3 (tag + temperature), and the placed features really
	 * place wild crops: the full placement chain (minus the rarity roll and the biome check) run on a grass field.
	 */
	private static void worldgenWildCrops(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Registry<Biome> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
		record Expect(String crop, List<ResourceKey<Biome>> in, List<ResourceKey<Biome>> out) {
		}
		List<Expect> expectations = List.of(
				new Expect("grapes", List.of(Biomes.PLAINS, Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.DARK_FOREST),
						List.of(Biomes.DESERT, Biomes.TAIGA, Biomes.SNOWY_PLAINS, Biomes.SAVANNA, Biomes.MUSHROOM_FIELDS, Biomes.GROVE)),
				new Expect("hops", List.of(Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.RIVER),
						List.of(Biomes.PLAINS, Biomes.FROZEN_RIVER, Biomes.DESERT, Biomes.TAIGA)),
				new Expect("barley", List.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU),
						List.of(Biomes.FOREST, Biomes.DESERT, Biomes.SNOWY_PLAINS, Biomes.MUSHROOM_FIELDS)));
		for (Expect expect : expectations) {
			// The biome modifier files (NeoForge loads them as biome modifiers, Fabric through WildCropFilter)
			WildCropFilter modifier = WildCropFilter.load("wild_" + expect.crop());
			WildCropFilter.load("wild_" + expect.crop() + "_extra");
			ResourceKey<PlacedFeature> patchKey = ResourceKey.create(Registries.PLACED_FEATURE, jexxme.vineandbarrel.VineAndBarrel.id("patch_wild_" + expect.crop()));
			for (ResourceKey<Biome> key : expect.in()) {
				Holder<Biome> biome = biomes.getOrThrow(key);
				helper.assertTrue(modifier.accepts(biome), "wild " + expect.crop() + " should be chosen for " + key.identifier());
				boolean inSettings = biome.value().getGenerationSettings().features().stream()
						.anyMatch(set -> set.stream().anyMatch(feature -> feature.is(patchKey)));
				helper.assertTrue(inSettings, "patch_wild_" + expect.crop() + " should be in the generation settings of " + key.identifier());
			}
			for (ResourceKey<Biome> key : expect.out()) {
				Holder<Biome> biome = biomes.getOrThrow(key);
				boolean inSettings = biome.value().getGenerationSettings().features().stream()
						.anyMatch(set -> set.stream().anyMatch(feature -> feature.is(patchKey)));
				helper.assertFalse(inSettings, "patch_wild_" + expect.crop() + " must not generate in " + key.identifier());
			}
		}

		// Real placement on a 32x32 grass field
		for (int x = 0; x < 32; x++) {
			for (int z = 0; z < 32; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
			}
		}
		int placedTotal = 0;
		for (String crop : List.of("grapes", "hops", "barley")) {
			PlacedFeature patch = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
					.getValue(jexxme.vineandbarrel.VineAndBarrel.id("patch_wild_" + crop));
			helper.assertTrue(patch != null, "patch_wild_" + crop + " should load");
			List<PlacementModifier> placement = patch.placement().stream()
					.filter(modifier -> !(modifier instanceof RarityFilter) && !(modifier instanceof BiomeFilter)).toList();
			PlacedFeature unfiltered = new PlacedFeature(patch.feature(), placement);
			Block wild = switch (crop) {
				case "grapes" -> VBBlocks.WILD_GRAPES.get();
				case "hops" -> VBBlocks.WILD_HOPS.get();
				default -> VBBlocks.WILD_BARLEY.get();
			};
			BlockPos origin = helper.absolutePos(new BlockPos(8, 0, 8));
			unfiltered.place(level, level.getChunkSource().getGenerator(), level.getRandom(), origin);
			int placed = 0;
			for (int x = 0; x < 32; x++) {
				for (int z = 0; z < 32; z++) {
					BlockPos pos = new BlockPos(x, 1, z);
					if (state(helper, pos).is(wild)) {
						placed++;
						helper.assertBlockPresent(Blocks.GRASS_BLOCK, pos.below());
						helper.setBlock(pos, Blocks.AIR);
					}
				}
			}
			helper.assertTrue(placed >= 5, "The wild " + crop + " patch should place several plants on grass, placed " + placed
					+ " (surface height at 8,8: " + helper.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 8, 8)
					+ ", at 20,20: " + helper.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 20, 20)
					+ ", wild blocks anywhere: " + countAround(helper, wild) + ")");
			placedTotal += placed;
		}
		helper.assertTrue(placedTotal > 0, "nothing placed");
		helper.succeed();
	}

	/**
	 * Wild white grapes like it cooler than red ones (Jexxme): birch forests, meadows and cherry groves, not the warmer
	 * forests and plains where only red grapes grow, nor taiga, groves, snowy slopes or deserts. Birch forests (temperature
	 * exactly 0.6, the top of white's range and the bottom of red's) grow both on purpose: that's where they meet. The
	 * patch places plants on grass.
	 */
	private static void worldgenWildWhiteGrapes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Registry<Biome> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
		// The biome modifier files (NeoForge loads them as biome modifiers, Fabric through WildCropFilter)
		WildCropFilter.load("wild_white_grapes");
		WildCropFilter.load("wild_white_grapes_extra");
		ResourceKey<PlacedFeature> patchKey = ResourceKey.create(Registries.PLACED_FEATURE, jexxme.vineandbarrel.VineAndBarrel.id("patch_wild_white_grapes"));
		ResourceKey<PlacedFeature> redKey = ResourceKey.create(Registries.PLACED_FEATURE, jexxme.vineandbarrel.VineAndBarrel.id("patch_wild_grapes"));
		for (ResourceKey<Biome> key : List.of(Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.MEADOW, Biomes.CHERRY_GROVE)) {
			boolean inSettings = biomes.getOrThrow(key).value().getGenerationSettings().features().stream()
					.anyMatch(set -> set.stream().anyMatch(feature -> feature.is(patchKey)));
			helper.assertTrue(inSettings, "wild white grapes should grow in " + key.identifier());
			boolean red = biomes.getOrThrow(key).value().getGenerationSettings().features().stream()
					.anyMatch(set -> set.stream().anyMatch(feature -> feature.is(redKey)));
			boolean birch = key == Biomes.BIRCH_FOREST || key == Biomes.OLD_GROWTH_BIRCH_FOREST;
			helper.assertValueEqual(red, birch, "red grapes too in " + key.identifier() + " (only the birch forests, where both meet)");
		}
		for (ResourceKey<Biome> key : List.of(Biomes.FOREST, Biomes.PLAINS, Biomes.DARK_FOREST, Biomes.TAIGA, Biomes.GROVE, Biomes.SNOWY_SLOPES,
				Biomes.DESERT, Biomes.JUNGLE, Biomes.MUSHROOM_FIELDS)) {
			boolean inSettings = biomes.getOrThrow(key).value().getGenerationSettings().features().stream()
					.anyMatch(set -> set.stream().anyMatch(feature -> feature.is(patchKey)));
			helper.assertFalse(inSettings, "wild white grapes must not grow in " + key.identifier());
		}
		for (int x = 0; x < 32; x++) {
			for (int z = 0; z < 32; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
			}
		}
		PlacedFeature patch = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).getValue(patchKey.identifier());
		helper.assertTrue(patch != null, "patch_wild_white_grapes should load");
		List<PlacementModifier> placement = patch.placement().stream()
				.filter(modifier -> !(modifier instanceof RarityFilter) && !(modifier instanceof BiomeFilter)).toList();
		new PlacedFeature(patch.feature(), placement).place(level, level.getChunkSource().getGenerator(), level.getRandom(),
				helper.absolutePos(new BlockPos(8, 0, 8)));
		int placed = 0;
		for (int x = 0; x < 32; x++) {
			for (int z = 0; z < 32; z++) {
				BlockPos pos = new BlockPos(x, 1, z);
				if (state(helper, pos).is(VBBlocks.WILD_WHITE_GRAPES.get())) {
					placed++;
					helper.assertBlockPresent(Blocks.GRASS_BLOCK, pos.below());
				}
			}
		}
		helper.assertTrue(placed >= 5, "The wild white grape patch should place several plants on grass, placed " + placed);
		helper.succeed();
	}

	private static int countAround(GameTestHelper helper, Block block) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(-8, -8, -8)), helper.absolutePos(new BlockPos(40, 12, 40)))) {
			if (helper.getLevel().getBlockState(pos).is(block)) {
				n++;
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ farm interactions

	/** C2 bees, C3 animal food, C4 villagers, C5 foxes, seasons and rich soil: the tags are live with the right members. */
	private static void farmTags(GameTestHelper helper) {
		for (Block block : List.of(VBBlocks.GRAPE_BUSH.get(), VBBlocks.WHITE_GRAPE_BUSH.get(), VBBlocks.HOP_BUSH.get(), VBBlocks.GRAPE_VINE_ON_ROPE.get(),
				VBBlocks.WHITE_GRAPE_VINE_ON_ROPE.get(), VBBlocks.HOP_VINE_ON_ROPE.get(), VBBlocks.BARLEY.get())) {
			helper.assertTrue(block.defaultBlockState().is(BlockTags.BEE_GROWABLES), block + " should be a bee growable (C2)");
		}
		ItemStack barley = new ItemStack(VBItems.BARLEY.get());
		ItemStack seeds = new ItemStack(VBItems.BARLEY_SEEDS.get());
		for (var tag : List.of(ItemTags.COW_FOOD, ItemTags.SHEEP_FOOD, ItemTags.GOAT_FOOD, ItemTags.HORSE_FOOD, ItemTags.LLAMA_FOOD)) {
			helper.assertTrue(barley.is(tag), "Barley should be in " + tag.location() + " (C3)");
		}
		helper.assertTrue(seeds.is(ItemTags.CHICKEN_FOOD) && seeds.is(ItemTags.PARROT_FOOD), "Barley seeds should feed chickens and parrots (C3)");
		helper.assertTrue(new ItemStack(VBItems.BARLEY_BALE.get()).is(ItemTags.LLAMA_FOOD), "The bale should feed llamas (C7)");
		helper.assertTrue(seeds.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) && seeds.is(ItemTags.VILLAGER_PICKS_UP), "Farmers plant and pick up barley seeds (C4)");
		helper.assertTrue(barley.is(ItemTags.VILLAGER_PICKS_UP), "Farmers pick up the barley they harvest, like wheat (C4)");
		helper.assertTrue(new ItemStack(VBItems.RED_GRAPES.get()).is(ItemTags.FOX_FOOD), "Foxes love grapes (C5)");
		helper.assertTrue(new ItemStack(VBItems.WHITE_GRAPES.get()).is(ItemTags.FOX_FOOD), "Foxes love white grapes too (C5)");
		helper.assertTrue(VBBlocks.GRAPE_BUSH.get().defaultBlockState().is(VBTags.SEASONS_AUTUMN_CROPS), "Grapes are an autumn crop");
		helper.assertTrue(VBBlocks.WHITE_GRAPE_BUSH.get().defaultBlockState().is(VBTags.SEASONS_AUTUMN_CROPS)
				&& VBBlocks.WHITE_GRAPE_VINE_ON_ROPE.get().defaultBlockState().is(VBTags.SEASONS_AUTUMN_CROPS), "White grapes are an autumn crop");
		helper.assertTrue(VBBlocks.HOP_BUSH.get().defaultBlockState().is(VBTags.SEASONS_SUMMER_CROPS)
				&& VBBlocks.HOP_BUSH.get().defaultBlockState().is(VBTags.SEASONS_AUTUMN_CROPS), "Hops are a summer and autumn crop");
		helper.assertTrue(VBBlocks.BARLEY.get().defaultBlockState().is(VBTags.SEASONS_SPRING_CROPS)
				&& VBBlocks.BARLEY.get().defaultBlockState().is(VBTags.SEASONS_SUMMER_CROPS), "Barley is a spring and summer crop");
		var unaffected = net.minecraft.tags.TagKey.create(Registries.BLOCK, net.minecraft.resources.Identifier.fromNamespaceAndPath("farmersdelight", "unaffected_by_rich_soil"));
		for (Block block : List.of(VBBlocks.GRAPE_BUSH.get(), VBBlocks.WHITE_GRAPE_BUSH.get(), VBBlocks.HOP_BUSH.get(), VBBlocks.BARLEY.get())) {
			helper.assertFalse(block.defaultBlockState().is(unaffected), block + " must stay boostable by FD rich soil");
		}
		helper.succeed();
	}

	/**
	 * C2: a bee over our plants grows them one age like it grows a sweet berry bush, counted against its pollination;
	 * the vine on a rope too, and a ripe plant is left alone. Driving the bee's own growth goal proves the mixin hooks
	 * it: vanilla's goal alone never touches our blocks.
	 */
	private static void beesGrowBushesAndVines(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// a young bush with the bee one block above
		BlockPos bush = new BlockPos(0, 2, 0);
		helper.setBlock(bush.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(bush, VBBlocks.GRAPE_BUSH.get());
		Bee first = helper.spawn(EntityTypes.BEE, bush.above());
		helper.assertTrue(BeeGrowth.grow(first) != null, "The bee should grow the grape bush");
		helper.assertValueEqual(state(helper, bush).getValue(ClimbingCropBlock.AGE), 1, "bush age after a bee pass");
		helper.assertValueEqual(((BeeAccessor) first).vineandbarrel$cropsGrown(), 1, "crops the bee grew since pollinating");
		// a young vine on a rope over a ripe bush: the vine grows, the ripe bush does not
		BlockPos ripeBush = new BlockPos(2, 2, 0);
		helper.setBlock(ripeBush.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(ripeBush, VBBlocks.HOP_BUSH.get().defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
		helper.setBlock(ripeBush.above(), VBBlocks.HOP_VINE_ON_ROPE.get());
		Bee second = helper.spawn(EntityTypes.BEE, ripeBush.above(2));
		BeeGrowth.grow(second);
		helper.assertValueEqual(state(helper, ripeBush.above()).getValue(ClimbingCropBlock.AGE), 1, "vine age after a bee pass");
		helper.assertValueEqual(state(helper, ripeBush).getValue(ClimbingCropBlock.AGE), 3, "a ripe bush stays as it is");
		helper.assertValueEqual(((BeeAccessor) second).vineandbarrel$cropsGrown(), 1, "crops the second bee grew");
		// the mixin: tick the bee's own BeeGrowCropGoal until it grows a fresh bush (1 in 15 per tick, like vanilla)
		BlockPos third = new BlockPos(4, 2, 0);
		helper.setBlock(third.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(third, VBBlocks.HOP_BUSH.get());
		Bee bee = helper.spawn(EntityTypes.BEE, third.above());
		var growGoal = bee.goalSelector.getAvailableGoals().stream()
				.filter(wrapped -> wrapped.getGoal().getClass().getSimpleName().equals("BeeGrowCropGoal")).findFirst()
				.orElseThrow(() -> new IllegalStateException("Bees should have vanilla's crop growth goal")).getGoal();
		for (int i = 0; i < 1000 && state(helper, third).getValue(ClimbingCropBlock.AGE) == 0; i++) {
			bee.setPos(Vec3.atBottomCenterOf(helper.absolutePos(third.above())));
			growGoal.tick();
		}
		helper.assertValueEqual(state(helper, third).getValue(ClimbingCropBlock.AGE), 1, "hop bush age after the bee's growth goal ran");
		helper.assertTrue(level.getBlockState(helper.absolutePos(third)).is(BlockTags.BEE_GROWABLES), "hop bush is a bee growable");
		helper.succeed();
	}

	/**
	 * Fire: every bush, vine (on a rope or a Trellis) and wild crop burns like the sweet berry bush, the bale like a hay bale
	 * (copying properties copies no fire data). The plants are found in the registry, so a new one can't be forgotten. The
	 * bare Trellis burns like a fence (VBTrellisTests).
	 */
	private static void burnLikeVanilla(GameTestHelper helper) {
		BlockState berries = Blocks.SWEET_BERRY_BUSH.defaultBlockState();
		BlockState hay = Blocks.HAY_BLOCK.defaultBlockState();
		helper.assertTrue(VBFlammability.burnOdds(berries) > 0 && VBFlammability.burnOdds(hay) > 0, "vanilla controls burn");
		List<Block> plants = BuiltInRegistries.BLOCK.stream()
				.filter(block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(VineAndBarrel.MODID))
				.filter(block -> block instanceof ClimbingCropBlock || block instanceof WildCropBlock).toList();
		helper.assertTrue(plants.containsAll(List.of(VBBlocks.GRAPE_BUSH.get(), VBBlocks.WHITE_GRAPE_BUSH.get(), VBBlocks.GRAPE_VINE_ON_ROPE.get(),
				VBBlocks.WHITE_GRAPE_VINE_ON_ROPE.get(), VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS.get(), VBBlocks.WILD_WHITE_GRAPES.get(), VBBlocks.WILD_BARLEY.get())),
				"climbing and wild crops found: " + plants);
		for (Block plant : plants) {
			BlockState state = plant.defaultBlockState();
			helper.assertValueEqual(VBFlammability.burnOdds(state), VBFlammability.burnOdds(berries), plant + " flammability");
			helper.assertValueEqual(VBFlammability.igniteOdds(state), VBFlammability.igniteOdds(berries), plant + " fire spread");
		}
		BlockState bale = VBBlocks.BARLEY_BALE.get().defaultBlockState();
		helper.assertValueEqual(VBFlammability.burnOdds(bale), VBFlammability.burnOdds(hay), "bale flammability");
		helper.assertValueEqual(VBFlammability.igniteOdds(bale), VBFlammability.igniteOdds(hay), "bale fire spread");
		helper.succeed();
	}

	/** C3: two cows fed barley by a player fall in love and breed a calf. */
	private static void cowBreedsFromBarley(GameTestHelper helper) {
		for (int x = 10; x < 22; x++) {
			for (int z = 10; z < 22; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
			}
		}
		Cow first = helper.spawn(EntityTypes.COW, new BlockPos(15, 1, 15));
		Cow second = helper.spawn(EntityTypes.COW, new BlockPos(17, 1, 15));
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL); // Animal#mobInteract only breeds for server players
		for (Cow cow : List.of(first, second)) {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VBItems.BARLEY.get(), 2));
			cow.mobInteract(player, InteractionHand.MAIN_HAND);
			helper.assertTrue(cow.isInLove(), "A cow fed barley should be in love");
			helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "barley left after feeding one cow");
		}
		helper.succeedWhen(() -> helper.assertTrue(helper.getEntities(EntityTypes.COW).stream().anyMatch(Cow::isBaby), "Expected a calf"));
	}

	/** C3/C7: barley heals a horse like wheat; the bale heals a llama like a hay bale; both are used up. */
	private static void horseAndLlamaEat(GameTestHelper helper) {
		helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 0, 3), Blocks.STONE);
		Horse horse = helper.spawnWithNoFreeWill(EntityTypes.HORSE, new BlockPos(1, 1, 1));
		Llama llama = helper.spawnWithNoFreeWill(EntityTypes.LLAMA, new BlockPos(3, 1, 3));
		Player player = player(helper);
		horse.setHealth(5.0F);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VBItems.BARLEY.get(), 3));
		horse.mobInteract(player, InteractionHand.MAIN_HAND);
		helper.assertValueEqual(horse.getHealth(), 7.0F, "horse health after barley (wheat heals 2)");
		helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "barley left after feeding the horse");
		llama.setHealth(5.0F);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VBItems.BARLEY_BALE.get(), 2));
		llama.mobInteract(player, InteractionHand.MAIN_HAND);
		helper.assertValueEqual(llama.getHealth(), Math.min(llama.getMaxHealth(), 15.0F), "llama health after a bale (a hay bale heals 10)");
		helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "bales left after feeding the llama");
		helper.succeed();
	}

	/**
	 * C4: vanilla's farmer behaviour ({@code HarvestFarmland}) driven directly on a farmer standing in ripe barley: it
	 * harvests the barley (barley and seeds drop) and then replants it from the barley seeds in its inventory.
	 */
	private static void farmerHarvestsBarley(GameTestHelper helper) {
		BlockPos crop = new BlockPos(2, 2, 2);
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		helper.setBlock(crop.below(), Blocks.FARMLAND);
		helper.setBlock(crop, VBBlocks.BARLEY.get().getStateForAge(7));
		Villager farmer = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, crop);
		ServerLevel level = helper.getLevel();
		farmer.setVillagerData(farmer.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER));
		farmer.getInventory().addItem(new ItemStack(VBItems.BARLEY_SEEDS.get(), 3));
		farmer.getBrain().setMemory(MemoryModuleType.SECONDARY_JOB_SITE, List.of(net.minecraft.core.GlobalPos.of(level.dimension(), helper.absolutePos(crop.below()))));
		farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		farmer.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
		HarvestFarmland behaviour = new HarvestFarmland();
		long time = level.getGameTime() + 100;
		helper.assertTrue(behaviour.tryStart(level, farmer, time), "The farmer should want to harvest the ripe barley");
		behaviour.tickOrStop(level, farmer, time + 1);
		helper.assertBlockPresent(Blocks.AIR, crop);
		behaviour.tickOrStop(level, farmer, time + 2);
		helper.assertTrue(state(helper, crop).is(VBBlocks.BARLEY.get()) && state(helper, crop).getValue(CropBlock.AGE) == 0,
				"The farmer should replant barley from its seeds, found " + state(helper, crop));
		helper.assertValueEqual(farmer.getInventory().countItem(VBItems.BARLEY_SEEDS.get()), 2, "seeds left in the farmer's inventory");
		helper.assertItemEntityPresent(VBItems.BARLEY.get(), crop, 2.0);
		helper.succeed();
	}

	/**
	 * C5: foxes get the grape-picking goal when they join the level; it targets grape bushes with fruit only (half-ripe or
	 * ripe, like vanilla's berry goal), red or white (not younger ones, not hops, not grapes up on a rope or a trellis) and
	 * picks like vanilla's berry goal: one in the mouth, the rest dropped, bush to age 1.
	 */
	private static void foxPicksGrapes(GameTestHelper helper, Block grapeBush, Block grapeVine, Item grapes) {
		BlockPos bush = new BlockPos(2, 2, 2);
		helper.setBlock(bush.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(new BlockPos(0, 1, 0), Blocks.GRASS_BLOCK);
		Fox fox = helper.spawn(EntityTypes.FOX, new BlockPos(0, 2, 0)); // not spawnWithNoFreeWill: that clears every goal
		helper.assertTrue(fox.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof FoxPickGrapesGoal),
				"Foxes should get the grape-picking goal");
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(bush);
		helper.setBlock(bush, grapeBush.defaultBlockState().setValue(ClimbingCropBlock.AGE, 1));
		helper.assertFalse(FoxPickGrapesGoal.canPick(level, abs), "A grape bush without fruit is not a fox target");
		helper.setBlock(bush, grapeBush.defaultBlockState().setValue(ClimbingCropBlock.AGE, 2));
		helper.assertTrue(FoxPickGrapesGoal.canPick(level, abs), "Half-ripe grapes are a fox target, like a sweet berry bush at age 2");
		helper.setBlock(bush, VBBlocks.HOP_BUSH.get().defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
		helper.assertFalse(FoxPickGrapesGoal.canPick(level, abs), "Hops are not a fox target");
		helper.setBlock(bush, grapeVine.defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
		helper.assertFalse(FoxPickGrapesGoal.canPick(level, abs), "Grapes up on a rope are out of a fox's reach");
		Block onTrellis = ((ClimbingCropBlock) grapeBush).trellisVine();
		helper.setBlock(bush, onTrellis.defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
		helper.assertFalse(FoxPickGrapesGoal.canPick(level, abs), "Grapes up on a trellis are out of a fox's reach");
		BlockState ripe = grapeBush.defaultBlockState().setValue(ClimbingCropBlock.AGE, 3);
		helper.setBlock(bush, ripe);
		helper.assertTrue(FoxPickGrapesGoal.canPick(level, abs), "A ripe grape bush is a fox target");
		// run the fox's own goal: it finds the bush, waits 40 ticks next to it like vanilla, then picks
		fox.teleportTo(abs.getX() - 0.5, abs.getY(), abs.getZ() + 0.5);
		FoxPickGrapesGoal goal = (FoxPickGrapesGoal) fox.goalSelector.getAvailableGoals().stream()
				.filter(wrapped -> wrapped.getGoal() instanceof FoxPickGrapesGoal).findFirst().orElseThrow().getGoal();
		helper.assertTrue(goal.canUse(), "The fox should spot the ripe grape bush");
		goal.start();
		for (int i = 0; i < 60 && state(helper, bush).getValue(ClimbingCropBlock.AGE) == ClimbingCropBlock.MAX_AGE; i++) {
			goal.tick();
		}
		helper.assertTrue(fox.getItemBySlot(EquipmentSlot.MAINHAND).is(grapes), "The fox should hold a bunch of " + grapes);
		helper.assertValueEqual(state(helper, bush).getValue(ClimbingCropBlock.AGE), ClimbingCropBlock.PICKED_AGE, "bush age after the fox picked it");
		int dropped = collect(helper, bush, grapes);
		helper.assertTrue(dropped == 1 || dropped == 2, "The fox should drop 1-2 more grapes (2-3 picked), dropped " + dropped);
		// half-ripe: vanilla's 1-2, one of them into the empty mouth
		fox.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		BlockState halfRipe = grapeBush.defaultBlockState().setValue(ClimbingCropBlock.AGE, ClimbingCropBlock.HALF_RIPE_AGE);
		helper.setBlock(bush, halfRipe);
		goal.pickGrapes(level, abs, halfRipe);
		helper.assertTrue(fox.getItemBySlot(EquipmentSlot.MAINHAND).is(grapes), "The fox should hold a bunch from a half-ripe bush");
		int halfDropped = collect(helper, bush, grapes);
		helper.assertTrue(halfDropped == 0 || halfDropped == 1, "The fox should drop 0-1 more from a half-ripe bush (1-2 picked), dropped " + halfDropped);
		helper.assertValueEqual(state(helper, bush).getValue(ClimbingCropBlock.AGE), ClimbingCropBlock.PICKED_AGE, "bush age after the fox picked it half-ripe");
		helper.succeed();
	}

	/** C6: every crop item fills a real composter (vanilla chances: they all eventually add a layer); a non-compostable never does. */
	private static void composterAcceptsCrops(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 2);
		ServerLevel level = helper.getLevel();
		Player player = player(helper);
		List<Item> compostables = List.of(VBItems.RED_GRAPES.get(), VBItems.WHITE_GRAPES.get(), VBItems.HOPS.get(), VBItems.BARLEY.get(),
				VBItems.BARLEY_SEEDS.get(), VBItems.WILD_GRAPES.get(), VBItems.WILD_WHITE_GRAPES.get(), VBItems.WILD_HOPS.get(), VBItems.WILD_BARLEY.get(),
				VBItems.BARLEY_BALE.get());
		for (Item item : compostables) {
			helper.assertTrue(new ItemStack(item).has(DataComponents.COMPOSTABLE), item + " should be compostable");
			helper.setBlock(pos, Blocks.COMPOSTER);
			int tries = 0;
			while (state(helper, pos).getValue(ComposterBlock.LEVEL) == 0 && tries++ < 60) {
				ItemStack stack = new ItemStack(item);
				ComposterBlock.insertItem(player, state(helper, pos), level, stack, helper.absolutePos(pos));
				helper.assertTrue(stack.isEmpty(), "The composter should take " + item);
			}
			helper.assertTrue(state(helper, pos).getValue(ComposterBlock.LEVEL) > 0, item + " never added a compost layer in 60 tries");
		}
		helper.setBlock(pos, Blocks.COMPOSTER);
		ItemStack crate = new ItemStack(VBItems.GRAPE_CRATE.get());
		ComposterBlock.insertItem(player, state(helper, pos), level, crate, helper.absolutePos(pos));
		helper.assertValueEqual(crate.getCount(), 1, "crates are not compostable (like FD's)");
		helper.succeed();
	}

	/**
	 * C7: through the real crafting lookup, 9 produce in a full grid make exactly 1 storage block (8 make nothing), and
	 * that block alone makes exactly 9 produce again: nothing gained, nothing lost.
	 */
	private static void storageRecipes(GameTestHelper helper) {
		RecipeManager recipes = helper.getLevel().getServer().getRecipeManager();
		record Storage(Item produce, Item block, String packId, String unpackId) {
		}
		for (Storage storage : List.of(
				new Storage(VBItems.RED_GRAPES.get(), VBItems.GRAPE_CRATE.get(), "grape_crate", "red_grapes_from_crate"),
				new Storage(VBItems.WHITE_GRAPES.get(), VBItems.WHITE_GRAPE_CRATE.get(), "white_grape_crate", "white_grapes_from_crate"),
				new Storage(VBItems.HOPS.get(), VBItems.HOPS_CRATE.get(), "hops_crate", "hops_from_crate"),
				new Storage(VBItems.BARLEY.get(), VBItems.BARLEY_BALE.get(), "barley_bale", "barley_from_bale"))) {
			List<ItemStack> nine = new ArrayList<>();
			for (int i = 0; i < 9; i++) {
				nine.add(new ItemStack(storage.produce()));
			}
			CraftingInput full = CraftingInput.of(3, 3, nine);
			RecipeHolder<CraftingRecipe> pack = recipes.getRecipeFor(RecipeType.CRAFTING, full, helper.getLevel())
					.orElseThrow(() -> new IllegalStateException("No recipe packs 9 " + storage.produce()));
			helper.assertValueEqual(pack.id().identifier(), jexxme.vineandbarrel.VineAndBarrel.id(storage.packId()), "recipe for 9 " + storage.produce());
			ItemStack packed = pack.value().assemble(full);
			helper.assertTrue(packed.is(storage.block()) && packed.getCount() == 1, "9 " + storage.produce() + " should make exactly 1 " + storage.block() + ", made " + packed);

			List<ItemStack> eight = new ArrayList<>(nine);
			eight.set(8, ItemStack.EMPTY);
			helper.assertTrue(recipes.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, eight), helper.getLevel()).isEmpty(),
					"8 " + storage.produce() + " must not make a " + storage.block());

			CraftingInput single = CraftingInput.of(1, 1, List.of(packed));
			RecipeHolder<CraftingRecipe> unpack = recipes.getRecipeFor(RecipeType.CRAFTING, single, helper.getLevel())
					.orElseThrow(() -> new IllegalStateException("No recipe unpacks " + storage.block()));
			helper.assertValueEqual(unpack.id().identifier(), jexxme.vineandbarrel.VineAndBarrel.id(storage.unpackId()), "recipe for 1 " + storage.block());
			ItemStack unpacked = unpack.value().assemble(single);
			helper.assertTrue(unpacked.is(storage.produce()) && unpacked.getCount() == 9, "1 " + storage.block() + " should give back exactly 9 " + storage.produce() + ", gave " + unpacked);
		}
		helper.succeed();
	}

	/** C7: landing on the bale hurts exactly as much as landing on a hay bale (80% less than on stone). */
	private static void baleFallDamage(GameTestHelper helper) {
		List<Block> grounds = List.of(VBBlocks.BARLEY_BALE.get(), Blocks.HAY_BLOCK, Blocks.STONE);
		List<Float> lost = new ArrayList<>();
		for (int i = 0; i < grounds.size(); i++) {
			BlockPos ground = new BlockPos(i * 2, 1, 2);
			helper.setBlock(ground, grounds.get(i));
			Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, ground.above());
			float before = pig.getHealth();
			BlockState state = state(helper, ground);
			state.getBlock().fallOn(helper.getLevel(), state, helper.absolutePos(ground), pig, 9.0);
			lost.add(before - pig.getHealth());
		}
		helper.assertValueEqual(lost.get(0), lost.get(1), "fall damage on the bale vs a hay bale");
		helper.assertTrue(lost.get(0) > 0 && lost.get(0) < lost.get(2), "The bale should cut fall damage: bale " + lost.get(0) + ", stone " + lost.get(2));
		helper.succeed();
	}

	/** C7: a campfire on the bale sends signal smoke, like on a hay bale; on a crate it does not. */
	private static void baleSignalSmoke(GameTestHelper helper) {
		List<Block> grounds = List.of(VBBlocks.BARLEY_BALE.get(), Blocks.HAY_BLOCK, VBBlocks.GRAPE_CRATE.get());
		for (int i = 0; i < grounds.size(); i++) {
			BlockPos ground = new BlockPos(i * 2, 1, 2);
			helper.setBlock(ground.above(), Blocks.CAMPFIRE);
			helper.setBlock(ground, grounds.get(i)); // the campfire updates from its new neighbour below
		}
		helper.assertTrue(state(helper, new BlockPos(0, 2, 2)).getValue(CampfireBlock.SIGNAL_FIRE), "A campfire on the bale should send signal smoke");
		helper.assertTrue(state(helper, new BlockPos(2, 2, 2)).getValue(CampfireBlock.SIGNAL_FIRE), "Control: a campfire on hay sends signal smoke");
		helper.assertFalse(state(helper, new BlockPos(4, 2, 2)).getValue(CampfireBlock.SIGNAL_FIRE), "A campfire on a crate sends no signal smoke");
		helper.succeed();
	}
}
