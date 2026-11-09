package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.block.BushSupport;
import jexxme.vineandbarrel.block.BushTrellis;
import jexxme.vineandbarrel.block.ClimbingBushBlock;
import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.block.VBFlammability;
import jexxme.vineandbarrel.block.TrellisBlock;
import jexxme.vineandbarrel.block.VineClimbing;
import jexxme.vineandbarrel.block.VineOnTrellisBlock;
import jexxme.vineandbarrel.entity.BeeGrowth;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import jexxme.vineandbarrel.tag.VBTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

import static jexxme.vineandbarrel.gametest.VBGameTests.LARGE;
import static jexxme.vineandbarrel.gametest.VBGameTests.test;

/**
 * The Trellis (no Farmer's Delight needed): placing and stacking it, a ripe bush climbing it to full height, a vine on it
 * growing, fruiting and being picked, breaking a vine giving the trellis back, and half-ripe picking on every plant
 * (bush, vine on a rope, vine on a trellis), with vanilla's sweet berry yields.
 */
final class VBTrellisTests
{
	private VBTrellisTests() {
	}

	static void registerAll() {
		test("crops_trellis_placement_tags_and_recipe", VBTrellisTests::placementTagsAndRecipe);
		test("crops_trellis_grape_climbs_to_full_height", LARGE, 200, h -> climbing(h, VBBlocks.GRAPE_BUSH.get(), VBBlocks.GRAPE_VINE_ON_TRELLIS.get()));
		test("crops_trellis_white_grape_climbs_to_full_height", LARGE, 200,
				h -> climbing(h, VBBlocks.WHITE_GRAPE_BUSH.get(), VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS.get()));
		test("crops_trellis_hop_climbs_to_full_height", LARGE, 200, h -> climbing(h, VBBlocks.HOP_BUSH.get(), VBBlocks.HOP_VINE_ON_TRELLIS.get()));
		test("crops_trellis_vine_grows_fruits_and_is_picked", LARGE, 200, VBTrellisTests::fruitingAndPicking);
		test("crops_trellis_breaking_a_vine_returns_the_trellis", LARGE, 200, VBTrellisTests::breakingReturnsTrellis);
		test("crops_half_ripe_picking_yields", LARGE, 200, VBTrellisTests::halfRipePicking);
		test("crops_trellis_bush_follows_its_support", VBTrellisTests::bushFollowsSupport);
		test("crops_trellis_pistons_and_the_height_limit", LARGE, 200, VBTrellisTests::pistonsAndHeightLimit);
		test("crops_trellis_explosion_leaves_the_trellis", LARGE, 200, VBTrellisTests::explosionLeavesTrellis);
		test("crops_trellis_holds_water_burns_and_turns", VBTrellisTests::waterFuelAndTurns);
		test("crops_trellis_goes_into_the_bush_either_way", VBTrellisTests::intoTheBushEitherWay);
		test("crops_trellis_bush_in_a_trellis_climbs_and_leaves_it", LARGE, 200, VBTrellisTests::bushInTrellisClimbsAndLeavesIt);
		test("crops_trellis_in_a_bush_survives_fire_water_and_pistons", LARGE, 200, VBTrellisTests::trellisInBushSurvives);
	}

	// ------------------------------------------------------------------ helpers

	private static BlockState trellis(Direction.Axis axis) {
		return VBBlocks.TRELLIS.get().defaultBlockState().setValue(TrellisBlock.AXIS, axis);
	}

	private static BlockState state(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockState(pos);
	}

	private static boolean boneMeal(GameTestHelper helper, BlockPos pos) {
		return BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), helper.getLevel(), helper.absolutePos(pos));
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

	/** A ripe bush on grass at {@code pos}. */
	private static void ripeBush(GameTestHelper helper, BlockPos pos, Block bush) {
		helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(pos, bush.defaultBlockState().setValue(ClimbingCropBlock.AGE, ClimbingCropBlock.MAX_AGE));
	}

	// ------------------------------------------------------------------ the block

	/**
	 * Audit (2026-11-08): a trellis placed in water holds it like a ladder, and a ripe bush under it doesn't climb it; it
	 * burns in a furnace as long as a ladder; a bush under a trellis turns with it in a structure.
	 */
	private static void waterFuelAndTurns(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos ground = new BlockPos(1, 1, 1);
		helper.setBlock(ground, Blocks.STONE);
		helper.setBlock(ground.above(), Blocks.WATER);
		ItemStack stack = new ItemStack(VBItems.TRELLIS.get(), 4);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		helper.placeAt(player, stack, ground, Direction.UP);
		BlockState wet = state(helper, ground.above());
		helper.assertTrue(wet.is(VBBlocks.TRELLIS.get()) && wet.getValue(TrellisBlock.WATERLOGGED), "placed in water, the trellis holds it: " + wet);
		helper.assertTrue(wet.getFluidState().isSource(), "the water source stays");

		BlockPos bush = new BlockPos(3, 2, 1);
		ripeBush(helper, bush, VBBlocks.GRAPE_BUSH.get());
		helper.setBlock(bush.above(), trellis(Direction.Axis.X).setValue(TrellisBlock.WATERLOGGED, true));
		helper.assertFalse(VineClimbing.canClimb(helper.getLevel(), helper.absolutePos(bush), (ClimbingCropBlock) VBBlocks.GRAPE_BUSH.get()),
				"no climbing a trellis under water");
		helper.setBlock(bush.above(), trellis(Direction.Axis.X));
		helper.assertTrue(VineClimbing.canClimb(helper.getLevel(), helper.absolutePos(bush), (ClimbingCropBlock) VBBlocks.GRAPE_BUSH.get()),
				"a dry trellis is climbed");

		// 26.2: burn times from the loader (NeoForge: our furnace_fuels data map, Fabric: its fuel registry; 26.3: the cooking_fuel component)
		int fuel = jexxme.vineandbarrel.platform.Services.PLATFORM.burnTime(new ItemStack(VBItems.TRELLIS.get()), helper.getLevel());
		helper.assertTrue(fuel > 0, "the trellis is fuel");
		helper.assertValueEqual(fuel, jexxme.vineandbarrel.platform.Services.PLATFORM.burnTime(new ItemStack(Items.LADDER), helper.getLevel()), "burns like a ladder");

		BlockState underX = VBBlocks.GRAPE_BUSH.get().defaultBlockState().setValue(ClimbingBushBlock.SUPPORT, BushSupport.TRELLIS_X);
		helper.assertValueEqual(underX.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90).getValue(ClimbingBushBlock.SUPPORT), BushSupport.TRELLIS_Z,
				"a quarter turn turns the bush with its trellis");
		helper.assertValueEqual(underX.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_180).getValue(ClimbingBushBlock.SUPPORT), BushSupport.TRELLIS_X,
				"a half turn keeps its line");
		helper.succeed();
	}

	/**
	 * A trellis faces the player who places it and keeps the line of the one below it; it is a vine support (and so is
	 * every rope in {@code #farmersdelight:ropes}), mined with an axe, not a ladder; it drops itself; five sticks in an X
	 * make four.
	 */
	private static void placementTagsAndRecipe(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos ground = new BlockPos(1, 1, 1);
		helper.setBlock(ground, Blocks.STONE);
		ItemStack stack = new ItemStack(VBItems.TRELLIS.get(), 8);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.setYRot(0.0F); // looking south: the panel runs east-west
		helper.placeAt(player, stack, ground, Direction.UP);
		helper.assertBlockState(ground.above(), trellis(Direction.Axis.X));
		player.setYRot(90.0F); // looking west, but stacked on a trellis it keeps the line
		helper.placeAt(player, stack, ground.above(), Direction.UP);
		helper.assertBlockState(ground.above(2), trellis(Direction.Axis.X));
		BlockPos other = new BlockPos(3, 1, 1);
		helper.setBlock(other, Blocks.STONE);
		helper.placeAt(player, stack, other, Direction.UP);
		helper.assertBlockState(other.above(), trellis(Direction.Axis.Z));
		helper.assertValueEqual(stack.getCount(), 5, "trellises left after placing three");

		BlockState bare = trellis(Direction.Axis.X);
		helper.assertTrue(bare.is(VBTags.VINE_SUPPORTS), "The trellis is a vine support");
		helper.assertTrue(VineClimbing.isSupport(bare) && !VineClimbing.isRope(bare), "The trellis is a support, not a rope");
		helper.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK).get(VBTags.FD_ROPES).ifPresent(ropes -> ropes.stream().map(Holder::value)
				.forEach(rope -> helper.assertTrue(rope.defaultBlockState().is(VBTags.VINE_SUPPORTS), rope + " (a rope) is a vine support")));
		for (Block block : List.of(VBBlocks.TRELLIS.get(), VBBlocks.GRAPE_VINE_ON_TRELLIS.get(), VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS.get(),
				VBBlocks.HOP_VINE_ON_TRELLIS.get())) {
			helper.assertTrue(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE), block + " is mined with an axe");
			helper.assertFalse(block.defaultBlockState().is(BlockTags.CLIMBABLE), block + " is not a ladder");
		}
		List<ItemStack> drops = Block.getDrops(bare, helper.getLevel(), helper.absolutePos(ground.above()), null, player, ItemStack.EMPTY);
		helper.assertTrue(drops.size() == 1 && drops.getFirst().is(VBItems.TRELLIS.get()) && drops.getFirst().getCount() == 1,
				"A trellis drops itself, dropped " + drops);

		List<ItemStack> grid = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			grid.add(i % 2 == 0 ? new ItemStack(Items.STICK) : ItemStack.EMPTY);
		}
		CraftingInput lattice = CraftingInput.of(3, 3, grid);
		RecipeHolder<CraftingRecipe> recipe = helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, lattice, helper.getLevel())
				.orElseThrow(() -> new IllegalStateException("No recipe for five sticks in an X"));
		ItemStack made = recipe.value().assemble(lattice);
		helper.assertTrue(made.is(VBItems.TRELLIS.get()) && made.getCount() == 4, "Five sticks in an X should make 4 trellises, made " + made);

		// mobs don't path through the panel; it burns like a fence; a rope, a Trellis or a fence grows a vine, a glass pane doesn't
		for (Block block : List.of(VBBlocks.TRELLIS.get(), VBBlocks.GRAPE_VINE_ON_TRELLIS.get())) {
			helper.assertFalse(block.defaultBlockState().isPathfindable(PathComputationType.LAND), block + " is no path for mobs");
		}
		helper.assertValueEqual(VBFlammability.igniteOdds(bare), VBFlammability.igniteOdds(Blocks.OAK_FENCE.defaultBlockState()), "trellis fire spread (a fence's)");
		helper.assertValueEqual(VBFlammability.burnOdds(bare), VBFlammability.burnOdds(Blocks.OAK_FENCE.defaultBlockState()), "trellis flammability (a fence's)");
		ClimbingCropBlock grapes = VBBlocks.GRAPE_BUSH.get();
		helper.assertTrue(grapes.vineOn(Blocks.GLASS_PANE.defaultBlockState()) == null, "a glass pane grows no vine");
		helper.assertTrue(grapes.vineOn(Blocks.OAK_FENCE.defaultBlockState()) != null
				&& grapes.vineOn(Blocks.OAK_FENCE.defaultBlockState()).is(VBBlocks.GRAPE_VINE_ON_SUPPORT.get()), "a fence grows the vine on a support");
		helper.assertTrue(grapes.vineOn(bare) != null && grapes.vineOn(bare).is(VBBlocks.GRAPE_VINE_ON_TRELLIS.get()), "a trellis grows the vine on a trellis");

		// a trellis placed on a vine keeps the vine's line
		BlockPos bush = new BlockPos(1, 1, 3);
		ripeBush(helper, bush, VBBlocks.GRAPE_BUSH.get());
		helper.setBlock(bush.above(), VBBlocks.GRAPE_VINE_ON_TRELLIS.get().defaultBlockState().setValue(VineOnTrellisBlock.AXIS, Direction.Axis.Z));
		player.setYRot(0.0F); // looking south, which on the ground would give an east-west panel
		helper.placeAt(player, stack, bush.above(), Direction.UP);
		helper.assertBlockState(bush.above(2), trellis(Direction.Axis.Z));
		helper.succeed();
	}

	// ------------------------------------------------------------------ the bush's look

	/**
	 * The bush follows the block above it: under a trellis it is drawn as the base of the trellis vine (facing the same
	 * way), under a rope as the base of the rope vine, on its own as the round bush; planted under a trellis it knows
	 * right away. Growing, picking and climbing keep it.
	 */
	private static void bushFollowsSupport(GameTestHelper helper) {
		BlockPos bush = new BlockPos(2, 1, 2);
		ripeBush(helper, bush, VBBlocks.GRAPE_BUSH.get());
		helper.assertValueEqual(state(helper, bush).getValue(ClimbingBushBlock.SUPPORT), BushSupport.NONE, "a bush on its own");
		helper.setBlock(bush.above(), trellis(Direction.Axis.Z));
		helper.assertValueEqual(state(helper, bush).getValue(ClimbingBushBlock.SUPPORT), BushSupport.TRELLIS_Z, "a bush under a north-south trellis");
		helper.setBlock(bush.above(), VBBlocks.GRAPE_VINE_ON_TRELLIS.get().defaultBlockState().setValue(VineOnTrellisBlock.AXIS, Direction.Axis.X));
		helper.assertValueEqual(state(helper, bush).getValue(ClimbingBushBlock.SUPPORT), BushSupport.TRELLIS_X, "a bush under its vine on an east-west trellis");
		helper.useBlock(bush, helper.makeMockPlayer(GameType.SURVIVAL));
		helper.assertValueEqual(state(helper, bush).getValue(ClimbingBushBlock.SUPPORT), BushSupport.TRELLIS_X, "picking keeps the look");
		helper.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK).get(VBTags.FD_ROPES).flatMap(ropes -> ropes.stream().findFirst())
				.ifPresent(rope -> {
					helper.setBlock(bush.above(), rope.value());
					helper.assertValueEqual(state(helper, bush).getValue(ClimbingBushBlock.SUPPORT), BushSupport.ROPE, "a bush under a rope");
				});
		helper.setBlock(bush.above(), Blocks.AIR);
		helper.assertValueEqual(state(helper, bush).getValue(ClimbingBushBlock.SUPPORT), BushSupport.NONE, "the trellis gone, the round bush again");
		// planted under a standing trellis
		BlockPos soil = new BlockPos(4, 1, 2);
		helper.setBlock(soil, Blocks.GRASS_BLOCK);
		helper.setBlock(soil.above(2), trellis(Direction.Axis.X));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack grapes = new ItemStack(VBItems.RED_GRAPES.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, grapes);
		helper.placeAt(player, grapes, soil, Direction.UP);
		helper.assertTrue(state(helper, soil.above()).is(VBBlocks.GRAPE_BUSH.get()), "grapes plant a bush under a trellis");
		helper.assertValueEqual(state(helper, soil.above()).getValue(ClimbingBushBlock.SUPPORT), BushSupport.TRELLIS_X, "a bush planted under a trellis");
		helper.succeed();
	}

	// ------------------------------------------------------------------ pistons and explosions

	/**
	 * Pistons move a vine on a trellis whole, like a vine on a rope: pushed off its plant it turns back into its trellis.
	 * Pushed on top of another full-grown plant it would make it {@value VineClimbing#MAX_HEIGHT} + 1 tall: it doesn't
	 * survive there either, drops its fruit and leaves the trellis.
	 */
	private static void pistonsAndHeightLimit(GameTestHelper helper) {
		helper.assertValueEqual(VBBlocks.GRAPE_VINE_ON_TRELLIS.get().defaultBlockState().getPistonPushReaction(), PushReaction.NORMAL, "push reaction");
		Block vine = VBBlocks.GRAPE_VINE_ON_TRELLIS.get();
		BlockState ripeVine = vine.defaultBlockState().setValue(ClimbingCropBlock.AGE, 3);
		// off its plant: a column at x=2, pushed east from x=1
		BlockPos lone = new BlockPos(2, 1, 1);
		ripeBush(helper, lone, VBBlocks.GRAPE_BUSH.get());
		helper.setBlock(lone.above(), ripeVine);
		helper.setBlock(lone.above().west(), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		// onto a full plant: the ripe vine of a raised 2-tall plant at x=2 is pushed onto the top of a 3-tall plant at x=3
		BlockPos shortBush = new BlockPos(2, 3, 5);
		helper.setBlock(shortBush.below(2), Blocks.STONE);
		ripeBush(helper, shortBush, VBBlocks.GRAPE_BUSH.get());
		helper.setBlock(shortBush.above(), ripeVine);
		BlockPos tall = new BlockPos(3, 1, 5);
		ripeBush(helper, tall, VBBlocks.GRAPE_BUSH.get());
		helper.setBlock(tall.above(), ripeVine);
		helper.setBlock(tall.above(2), ripeVine);
		helper.setBlock(shortBush.above().west(), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		helper.setBlock(lone.above().west().above(), Blocks.REDSTONE_BLOCK);
		helper.setBlock(shortBush.above().west().above(), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> {
			helper.assertBlockState(lone.above().east(), trellis(Direction.Axis.X));
			helper.assertBlockState(tall.above(3), trellis(Direction.Axis.X));
			helper.assertTrue(state(helper, tall.above(2)).is(vine), "the full-grown plant keeps its own vines");
			helper.assertValueEqual(VineClimbing.heightAt(helper.getLevel(), helper.absolutePos(tall.above(2)), VBBlocks.GRAPE_BUSH.get()),
					VineClimbing.MAX_HEIGHT, "the plant's height");
			helper.assertItemEntityPresent(VBItems.RED_GRAPES.get(), tall.above(3), 3.0);
		});
	}

	/** An explosion takes the vine (and its fruit) but leaves the trellis, like a rope under a vine on a rope. */
	private static void explosionLeavesTrellis(GameTestHelper helper) {
		BlockPos bush = new BlockPos(4, 1, 4);
		ripeBush(helper, bush, VBBlocks.HOP_BUSH.get());
		helper.setBlock(bush.above(), VBBlocks.HOP_VINE_ON_TRELLIS.get().defaultBlockState().setValue(ClimbingCropBlock.AGE, 3)
				.setValue(VineOnTrellisBlock.AXIS, Direction.Axis.Z));
		net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(bush.above()));
		helper.getLevel().explode(null, at.x, at.y, at.z, 1.0F, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
		helper.succeedWhen(() -> helper.assertBlockState(bush.above(), trellis(Direction.Axis.Z)));
	}

	// ------------------------------------------------------------------ a trellis in the bush's own block

	private static BlockState bushIn(Block bush, int age, BushTrellis trellis) {
		return bush.defaultBlockState().setValue(ClimbingCropBlock.AGE, age).setValue(ClimbingBushBlock.TRELLIS, trellis);
	}

	/**
	 * The trellis goes into the plant, in either order. Grapes used on a trellis standing on soil plant a young bush into
	 * it (the trellis stays, facing its way); not on stone, not under water. A trellis used on a bush goes into it, facing
	 * the player, or lined up with the trellis above it. A trellis stacked on such a bush keeps its line; a quarter turn
	 * turns it; you bump into its panel and mobs don't path through it.
	 */
	private static void intoTheBushEitherWay(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		// trellis first
		BlockPos soil = new BlockPos(1, 1, 1);
		helper.setBlock(soil.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(soil, trellis(Direction.Axis.Z));
		ItemStack grapes = new ItemStack(VBItems.RED_GRAPES.get(), 4);
		player.setItemInHand(InteractionHand.MAIN_HAND, grapes);
		helper.useBlock(soil, player);
		helper.assertBlockState(soil, bushIn(VBBlocks.GRAPE_BUSH.get(), 0, BushTrellis.Z));
		helper.assertValueEqual(grapes.getCount(), 3, "grapes left after planting one into a trellis");
		BlockPos stone = new BlockPos(3, 1, 1);
		helper.setBlock(stone.below(), Blocks.STONE);
		helper.setBlock(stone, trellis(Direction.Axis.X));
		helper.useBlock(stone, player);
		helper.assertBlockState(stone, trellis(Direction.Axis.X));
		BlockPos wet = new BlockPos(5, 1, 1);
		helper.setBlock(wet.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(wet, trellis(Direction.Axis.X).setValue(TrellisBlock.WATERLOGGED, true));
		helper.useBlock(wet, player);
		helper.assertTrue(state(helper, wet).is(VBBlocks.TRELLIS.get()), "nothing is planted into a trellis under water");
		helper.assertValueEqual(grapes.getCount(), 3, "no grapes used on stone or under water");

		// plant first
		BlockPos bush = new BlockPos(1, 1, 3);
		helper.setBlock(bush.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(bush, VBBlocks.HOP_BUSH.get().defaultBlockState().setValue(ClimbingCropBlock.AGE, 1));
		ItemStack trellises = new ItemStack(VBItems.TRELLIS.get(), 4);
		player.setItemInHand(InteractionHand.MAIN_HAND, trellises);
		player.setYRot(0.0F); // looking south: the panel runs east-west
		helper.useBlock(bush, player);
		helper.assertBlockState(bush, bushIn(VBBlocks.HOP_BUSH.get(), 1, BushTrellis.X));
		helper.assertValueEqual(trellises.getCount(), 3, "trellises left after putting one into a bush");
		// a trellis stacked on it keeps its line, whichever way the player looks
		player.setYRot(90.0F);
		helper.placeAt(player, trellises, bush, Direction.UP);
		helper.assertBlockState(bush.above(), trellis(Direction.Axis.X));
		helper.assertValueEqual(state(helper, bush).getValue(ClimbingBushBlock.SUPPORT), BushSupport.TRELLIS_X, "and the bush knows the trellis above");
		// lined up with a trellis already above it
		BlockPos under = new BlockPos(3, 1, 3);
		helper.setBlock(under.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(under, VBBlocks.GRAPE_BUSH.get().defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
		helper.setBlock(under.above(), trellis(Direction.Axis.Z));
		player.setYRot(0.0F);
		helper.useBlock(under, player);
		helper.assertValueEqual(state(helper, under).getValue(ClimbingBushBlock.TRELLIS), BushTrellis.Z, "a trellis put into a bush lines up with the one above");
		helper.assertValueEqual(state(helper, under).getValue(ClimbingCropBlock.AGE), 3, "putting a trellis in picks nothing");

		BlockState in = bushIn(VBBlocks.GRAPE_BUSH.get(), 2, BushTrellis.X);
		helper.assertValueEqual(in.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90).getValue(ClimbingBushBlock.TRELLIS), BushTrellis.Z,
				"a quarter turn turns the trellis in the bush");
		helper.assertValueEqual(in.getCollisionShape(helper.getLevel(), helper.absolutePos(soil)), TrellisBlock.panel(Direction.Axis.X), "you bump into the panel");
		helper.assertTrue(VBBlocks.GRAPE_BUSH.get().defaultBlockState().getCollisionShape(helper.getLevel(), helper.absolutePos(soil)).isEmpty(),
				"a bush without a trellis lets you through, as before");
		helper.assertFalse(in.isPathfindable(PathComputationType.LAND), "no path for mobs through a trellis in a bush");
		helper.succeed();
	}

	/**
	 * A ripe bush with a trellis in it climbs the trellises above to full height like any bush. A player breaking it gets
	 * its fruit and leaves its trellis standing, facing the same way, and the vines above turn back into their trellises.
	 * An explosion leaves the trellis too.
	 */
	private static void bushInTrellisClimbsAndLeavesIt(GameTestHelper helper) {
		BlockPos bush = new BlockPos(2, 1, 2);
		helper.setBlock(bush.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(bush, bushIn(VBBlocks.GRAPE_BUSH.get(), ClimbingCropBlock.MAX_AGE, BushTrellis.Z));
		helper.setBlock(bush.above(), trellis(Direction.Axis.Z));
		helper.setBlock(bush.above(2), trellis(Direction.Axis.Z));
		helper.assertTrue(boneMeal(helper, bush), "bone meal makes it climb");
		helper.assertTrue(state(helper, bush.above()).is(VBBlocks.GRAPE_VINE_ON_TRELLIS.get()), "it climbs the trellis above");
		helper.assertTrue(boneMeal(helper, bush.above()) && boneMeal(helper, bush.above()) && boneMeal(helper, bush.above()) && boneMeal(helper, bush.above()),
				"bone meal ripens the vine and makes it climb on");
		helper.assertTrue(state(helper, bush.above(2)).is(VBBlocks.GRAPE_VINE_ON_TRELLIS.get()), "full height, the trellis in the bush counted with it");
		helper.assertValueEqual(state(helper, bush).getValue(ClimbingBushBlock.TRELLIS), BushTrellis.Z, "climbing keeps the trellis in the bush");
		// the vines above picked back to unripe, so whatever fruit drops below comes from the bush itself
		for (BlockPos vine : List.of(bush.above(), bush.above(2))) {
			helper.setBlock(vine, state(helper, vine).setValue(ClimbingCropBlock.AGE, ClimbingCropBlock.PICKED_AGE));
		}

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.gameMode.destroyBlock(helper.absolutePos(bush));
		helper.assertBlockState(bush, trellis(Direction.Axis.Z));

		BlockPos blown = new BlockPos(5, 1, 5);
		helper.setBlock(blown.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(blown, bushIn(VBBlocks.HOP_BUSH.get(), 1, BushTrellis.X));
		net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(blown));
		helper.getLevel().explode(null, at.x, at.y + 0.5, at.z, 1.0F, net.minecraft.world.level.Level.ExplosionInteraction.TNT);

		helper.runAfterDelay(5, () -> {
			helper.assertBlockState(bush.above(), trellis(Direction.Axis.Z));
			helper.assertBlockState(bush.above(2), trellis(Direction.Axis.Z));
			int grapes = collect(helper, bush, VBItems.RED_GRAPES.get());
			helper.assertTrue(grapes >= 2, "breaking the ripe plant gives its fruit (and no trellis item), dropped " + grapes);
			helper.assertBlockState(blown, trellis(Direction.Axis.X));
			helper.succeed();
		});
	}

	/**
	 * However the plant goes, its trellis isn't lost: fire burning the plant leaves it, a water source flowing in leaves
	 * it holding the water, flowing water washing the plant away drops it as an item. A piston moves the plant whole, like
	 * its vines: onto soil it lives on with its trellis, off its soil it breaks and the trellis stays.
	 */
	private static void trellisInBushSurvives(GameTestHelper helper) {
		BlockPos burnt = new BlockPos(1, 1, 1);
		BlockPos soaked = new BlockPos(3, 1, 1);
		BlockPos washed = new BlockPos(5, 1, 1);
		for (BlockPos pos : List.of(burnt, soaked, washed)) {
			helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
			helper.setBlock(pos, bushIn(VBBlocks.GRAPE_BUSH.get(), 2, BushTrellis.X));
		}
		helper.assertValueEqual(VBBlocks.GRAPE_BUSH.get().defaultBlockState().getPistonPushReaction(), PushReaction.NORMAL, "bush push reaction");
		helper.setBlock(burnt, Blocks.FIRE);
		helper.assertBlockState(burnt, trellis(Direction.Axis.X));
		helper.setBlock(soaked, Blocks.WATER);
		helper.assertBlockState(soaked, trellis(Direction.Axis.X).setValue(TrellisBlock.WATERLOGGED, true));
		helper.setBlock(washed, Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 2));
		helper.assertItemEntityPresent(VBItems.TRELLIS.get(), washed, 1.5);
		// its soil dug away: the bush breaks a tick later, drops its fruit and leaves the trellis
		BlockPos dug = new BlockPos(16, 1, 16); // well away from the water above
		helper.setBlock(dug.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(dug, bushIn(VBBlocks.GRAPE_BUSH.get(), ClimbingCropBlock.MAX_AGE, BushTrellis.X));
		helper.setBlock(dug.below(), Blocks.AIR);
		// on farmland the bush keeps it farmland, trellis or not (a trellis in it is solid)
		BlockPos farm = new BlockPos(16, 1, 20);
		helper.setBlock(farm.below(), Blocks.FARMLAND);
		helper.setBlock(farm, bushIn(VBBlocks.HOP_BUSH.get(), 1, BushTrellis.Z));
		helper.assertTrue(state(helper, farm).is(BlockTags.MAINTAINS_FARMLAND), "a bush keeps the farmland under it");
		helper.assertBlockPresent(Blocks.FARMLAND, farm.below());

		// pushed east onto grass: lives on; pushed east off its soil: breaks, the trellis stays
		BlockPos moved = new BlockPos(2, 1, 4);
		BlockPos dropped = new BlockPos(2, 1, 7);
		helper.setBlock(moved.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(moved.east().below(), Blocks.GRASS_BLOCK);
		helper.setBlock(dropped.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(dropped.east().below(), Blocks.STONE);
		for (BlockPos pos : List.of(moved, dropped)) {
			helper.setBlock(pos, bushIn(VBBlocks.HOP_BUSH.get(), 1, BushTrellis.Z));
			helper.setBlock(pos.west(), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
			helper.setBlock(pos.west(2), Blocks.REDSTONE_BLOCK);
		}
		helper.succeedWhen(() -> {
			helper.assertBlockState(dug, trellis(Direction.Axis.X));
			helper.assertItemEntityPresent(VBItems.RED_GRAPES.get(), dug, 1.5);
			helper.assertBlockState(moved.east(), bushIn(VBBlocks.HOP_BUSH.get(), 1, BushTrellis.Z));
			helper.assertBlockState(dropped.east(), trellis(Direction.Axis.Z));
		});
	}

	// ------------------------------------------------------------------ climbing

	/**
	 * A ripe bush under a column of trellises: a random tick grows the vine onto the first one (facing the trellis's way),
	 * bone meal takes it to {@value VineClimbing#MAX_HEIGHT} blocks, as on an FD rope, and no further (the top trellis stays
	 * bare). When the bush goes, every vine turns back into its trellis, and nothing drops.
	 */
	private static void climbing(GameTestHelper helper, Block bush, Block vine) {
		BlockPos bushPos = new BlockPos(4, 1, 4);
		ripeBush(helper, bushPos, bush);
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(bushPos.above(y), trellis(Direction.Axis.Z));
		}
		for (int i = 0; i < 2000 && !state(helper, bushPos.above()).is(vine); i++) {
			helper.randomTick(bushPos);
		}
		helper.assertTrue(state(helper, bushPos.above()).is(vine), "The ripe bush should climb the trellis above it");
		helper.assertValueEqual(state(helper, bushPos.above()).getValue(ClimbingCropBlock.AGE), 0, "age of a fresh vine");
		helper.assertValueEqual(state(helper, bushPos.above()).getValue(VineOnTrellisBlock.AXIS), Direction.Axis.Z, "the vine faces the trellis's way");
		helper.assertBlockState(bushPos.above(2), trellis(Direction.Axis.Z));
		for (int i = 0; i < 40; i++) {
			boneMeal(helper, bushPos);
		}
		helper.assertTrue(state(helper, bushPos.above(2)).is(vine), "Bone meal should grow the vine two trellises high");
		helper.assertBlockState(bushPos.above(3), trellis(Direction.Axis.Z));
		helper.assertValueEqual(VineClimbing.heightAt(helper.getLevel(), helper.absolutePos(bushPos.above(2)), (ClimbingCropBlock) bush),
				VineClimbing.MAX_HEIGHT, "plant height");
		helper.assertFalse(boneMeal(helper, bushPos), "A fully grown " + VineClimbing.MAX_HEIGHT + "-tall plant is no bone meal target");
		for (int i = 0; i < 300; i++) {
			helper.randomTick(bushPos.above(2));
		}
		helper.assertBlockState(bushPos.above(3), trellis(Direction.Axis.Z));
		helper.getLevel().destroyBlock(helper.absolutePos(bushPos), false);
		helper.succeedWhen(() -> {
			for (int y = 1; y <= 3; y++) {
				helper.assertBlockState(bushPos.above(y), trellis(Direction.Axis.Z));
			}
			helper.assertItemEntityNotPresent(VBItems.TRELLIS.get(), bushPos.above(), 4.0);
		});
	}

	// ------------------------------------------------------------------ fruit

	/**
	 * A vine on a trellis grows through every age on random ticks and bone meal, bees help it like the bush, and it is
	 * picked like the bush: 2-3 when ripe, 1-2 half-ripe, back to its second age. Breaking it drops only the fruit.
	 */
	private static void fruitingAndPicking(GameTestHelper helper) {
		BlockPos bush = new BlockPos(2, 1, 2);
		BlockPos vine = bush.above();
		ripeBush(helper, bush, VBBlocks.GRAPE_BUSH.get());
		Block grapeVine = VBBlocks.GRAPE_VINE_ON_TRELLIS.get();
		helper.setBlock(vine, grapeVine.defaultBlockState());
		List<Integer> seen = new ArrayList<>(List.of(0));
		for (int i = 0; i < 20000 && state(helper, vine).getValue(ClimbingCropBlock.AGE) < ClimbingCropBlock.MAX_AGE; i++) {
			helper.randomTick(vine);
			int age = state(helper, vine).getValue(ClimbingCropBlock.AGE);
			if (age != seen.getLast()) {
				helper.assertValueEqual(age, seen.getLast() + 1, "next age of the vine");
				seen.add(age);
			}
		}
		helper.assertValueEqual(seen, List.of(0, 1, 2, 3), "ages the vine went through");
		helper.setBlock(vine, grapeVine.defaultBlockState());
		helper.assertTrue(boneMeal(helper, vine), "Bone meal grows a young vine");
		helper.assertValueEqual(state(helper, vine).getValue(ClimbingCropBlock.AGE), 1, "vine age after bone meal");
		Bee bee = helper.spawn(EntityType.BEE, vine.above());
		helper.assertTrue(BeeGrowth.grow(bee) != null, "A bee grows the vine on a trellis");
		helper.assertValueEqual(state(helper, vine).getValue(ClimbingCropBlock.AGE), 2, "vine age after a bee pass");
		bee.discard();

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Item grapes = VBItems.RED_GRAPES.get();
		boolean[] sawRipe = new boolean[4];
		boolean[] sawHalf = new boolean[4];
		for (int i = 0; i < 32; i++) {
			helper.setBlock(vine, grapeVine.defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
			helper.useBlock(vine, player);
			int ripe = collect(helper, vine, grapes);
			helper.assertTrue(ripe == 2 || ripe == 3, "Picking a ripe vine should give 2-3, gave " + ripe);
			sawRipe[ripe] = true;
			helper.assertValueEqual(state(helper, vine).getValue(ClimbingCropBlock.AGE), ClimbingCropBlock.PICKED_AGE, "vine age after picking");
			helper.setBlock(vine, grapeVine.defaultBlockState().setValue(ClimbingCropBlock.AGE, 2));
			helper.useBlock(vine, player);
			int half = collect(helper, vine, grapes);
			helper.assertTrue(half == 1 || half == 2, "Picking a half-ripe vine should give 1-2, gave " + half);
			sawHalf[half] = true;
			helper.assertValueEqual(state(helper, vine).getValue(ClimbingCropBlock.AGE), ClimbingCropBlock.PICKED_AGE, "vine age after picking it half-ripe");
		}
		helper.assertTrue(sawRipe[2] && sawRipe[3] && sawHalf[1] && sawHalf[2], "Over 32 pickings every yield should appear");
		helper.setBlock(vine, grapeVine.defaultBlockState().setValue(ClimbingCropBlock.AGE, 1));
		helper.useBlock(vine, player);
		helper.assertValueEqual(collect(helper, vine, grapes), 0, "fruit from a vine that has none");
		helper.assertValueEqual(state(helper, vine).getValue(ClimbingCropBlock.AGE), 1, "an unripe vine keeps its age");
		for (int age = 0; age <= 3; age++) {
			List<ItemStack> drops = Block.getDrops(grapeVine.defaultBlockState().setValue(ClimbingCropBlock.AGE, age), helper.getLevel(),
					helper.absolutePos(vine), null, player, ItemStack.EMPTY);
			int fruit = count(drops, grapes);
			helper.assertValueEqual(drops.size() - (fruit > 0 ? 1 : 0), 0, "other drops of a vine on a trellis " + drops);
			int[] range = age == 3 ? new int[] {2, 3} : age == 2 ? new int[] {1, 2} : new int[] {0, 0};
			helper.assertTrue(fruit >= range[0] && fruit <= range[1], "A vine at age " + age + " should drop " + range[0] + "-" + range[1] + ", dropped " + fruit);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ breaking

	/**
	 * A player breaking a ripe vine on a trellis gets its fruit and leaves the trellis standing, facing the same way. The
	 * vine above it loses its plant and turns back into its trellis too. No trellis item drops: the trellises stay up.
	 */
	private static void breakingReturnsTrellis(GameTestHelper helper) {
		BlockPos bush = new BlockPos(2, 1, 2);
		ripeBush(helper, bush, VBBlocks.HOP_BUSH.get());
		Block hopVine = VBBlocks.HOP_VINE_ON_TRELLIS.get();
		helper.setBlock(bush.above(), hopVine.defaultBlockState().setValue(ClimbingCropBlock.AGE, 3).setValue(VineOnTrellisBlock.AXIS, Direction.Axis.Z));
		helper.setBlock(bush.above(2), hopVine.defaultBlockState().setValue(ClimbingCropBlock.AGE, 1).setValue(VineOnTrellisBlock.AXIS, Direction.Axis.Z));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.gameMode.destroyBlock(helper.absolutePos(bush.above()));
		helper.assertBlockState(bush.above(), trellis(Direction.Axis.Z));
		helper.runAfterDelay(5, () -> {
			helper.assertBlockState(bush.above(2), trellis(Direction.Axis.Z));
			int hops = collect(helper, bush.above(), VBItems.HOPS.get());
			helper.assertTrue(hops == 2 || hops == 3, "Breaking a ripe hop bine should drop 2-3 hops (and no trellis), dropped " + hops);
			helper.assertBlockPresent(VBBlocks.HOP_BUSH.get(), bush);
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ half-ripe picking

	/**
	 * Half-ripe picking on every plant, like the sweet berry bush at age 2: the bush, its vine on a rope (FD's rope, or
	 * the test data's stand-in) and its vine on a trellis give 1-2 and drop back to age 1; ripe ones give 2-3.
	 */
	private static void halfRipePicking(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		int x = 1;
		for (Block bushBlock : List.of(VBBlocks.GRAPE_BUSH.get(), VBBlocks.WHITE_GRAPE_BUSH.get(), VBBlocks.HOP_BUSH.get())) {
			ClimbingCropBlock bush = (ClimbingCropBlock) bushBlock;
			Item fruit = bush.produce();
			BlockPos base = new BlockPos(x, 1, 2);
			x += 3;
			ripeBush(helper, base, bush);
			for (Block plant : List.of(bush, bush.ropeVine(), bush.trellisVine())) {
				BlockPos pos = plant == bush ? base : base.above();
				int[] seen = new int[4];
				for (int i = 0; i < 24; i++) {
					helper.setBlock(pos, plant.defaultBlockState().setValue(ClimbingCropBlock.AGE, ClimbingCropBlock.HALF_RIPE_AGE));
					helper.useBlock(pos, player);
					int picked = collect(helper, pos, fruit);
					helper.assertTrue(picked == 1 || picked == 2, "Picking a half-ripe " + plant + " should give 1-2, gave " + picked);
					seen[picked]++;
					helper.assertValueEqual(state(helper, pos).getValue(ClimbingCropBlock.AGE), ClimbingCropBlock.PICKED_AGE, "age of " + plant + " after picking");
				}
				helper.assertTrue(seen[1] > 0 && seen[2] > 0, "Over 24 half-ripe pickings of " + plant + " both 1 and 2 should appear");
				if (plant != bush) {
					helper.setBlock(pos, Blocks.AIR);
				}
				helper.setBlock(base, bush.defaultBlockState().setValue(ClimbingCropBlock.AGE, ClimbingCropBlock.MAX_AGE));
			}
		}
		helper.succeed();
	}
}
