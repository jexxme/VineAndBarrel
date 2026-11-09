package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.block.BushSupport;
import jexxme.vineandbarrel.block.ClimbingBushBlock;
import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.block.SupportLook;
import jexxme.vineandbarrel.block.TrellisFrame;
import jexxme.vineandbarrel.block.VineClimbing;
import jexxme.vineandbarrel.block.VineOnSupportBlock;
import jexxme.vineandbarrel.block.VineSupports;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static jexxme.vineandbarrel.gametest.VBGameTests.LARGE;
import static jexxme.vineandbarrel.gametest.VBGameTests.test;

/**
 * Trellis 2.0: grapes and hops also climb fences, walls, upright chains and bars ({@code #vineandbarrel:makeshift_vine_supports}).
 * The vine keeps that exact block (a spruce fence, a waxed copper chain), its neighbours still see it, it comes back
 * however the plant goes, and picking gives one fewer than on a trellis.
 */
final class VBSupportTests
{
	private VBSupportTests() {
	}

	static void registerAll() {
		test("crops_support_grapes_climb_a_fence_row", LARGE, 200, VBSupportTests::climbsFenceRow);
		test("crops_support_walls_chains_and_bars", LARGE, 200, VBSupportTests::wallsChainsAndBars);
		test("crops_support_what_holds_a_vine", VBSupportTests::whatHoldsAVine);
		test("crops_support_gives_the_block_back", LARGE, 200, VBSupportTests::givesTheBlockBack);
		test("crops_support_yields_one_fewer", LARGE, 200, VBSupportTests::yieldsOneFewer);
		test("crops_support_neighbours_and_leads", LARGE, 200, VBSupportTests::neighboursAndLeads);
		test("crops_support_root_lives_on_soil", LARGE, 200, VBSupportTests::rootLivesOnSoil);
		test("crops_support_planting_both_ways", LARGE, 200, VBSupportTests::plantingBothWays);
		test("crops_trellis_frame_follows_the_row", LARGE, 100, VBSupportTests::frameFollowsTheRow);
		test("crops_support_holds_plain_blocks_and_catches_up", LARGE, 100, VBSupportTests::holdsPlainBlocksAndCatchesUp);
		test("crops_burnt_vines_leave_their_support", LARGE, 100, VBSupportTests::burntVinesLeaveTheirSupport);
	}

	// ------------------------------------------------------------------ helpers

	private static BlockState state(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockState(pos);
	}

	/** Places {@code state} at {@code pos} connected to its neighbours, as a player would. */
	private static void build(GameTestHelper helper, BlockPos pos, BlockState state) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(pos);
		level.setBlockAndUpdate(at, Block.updateFromNeighbourShapes(state, level, at));
	}

	private static BlockState support(GameTestHelper helper, BlockPos pos) {
		return VineSupports.supportAt(helper.getLevel(), helper.absolutePos(pos));
	}

	private static void ripeBush(GameTestHelper helper, BlockPos pos, Block bush) {
		helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(pos, bush.defaultBlockState().setValue(ClimbingCropBlock.AGE, ClimbingCropBlock.MAX_AGE));
	}

	private static boolean boneMeal(GameTestHelper helper, BlockPos pos) {
		return BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), helper.getLevel(), helper.absolutePos(pos));
	}

	/** A vine of {@code vineBlock} at {@code age} put into the block at {@code pos} (as a climb or a planting does). */
	private static void overgrow(GameTestHelper helper, BlockPos pos, Block vineBlock, int age, boolean base) {
		BlockState support = state(helper, pos);
		VineOnSupportBlock.place(helper.getLevel(), helper.absolutePos(pos), vineBlock.defaultBlockState().setValue(ClimbingCropBlock.AGE, age), support, base);
	}

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

	// ------------------------------------------------------------------ climbing

	/**
	 * A ripe grape bush under an oak fence row climbs into the middle fence (spread across it, east-west), keeps the fence
	 * as it was, and its neighbours stay connected; bone meal takes it into the lone fence on top (wound round it), and the
	 * plant stops at {@value VineClimbing#MAX_HEIGHT}. When the bush goes, both fences come back as they stood.
	 */
	private static void climbsFenceRow(GameTestHelper helper) {
		BlockPos bush = new BlockPos(3, 2, 3);
		helper.setBlock(bush.below(2), Blocks.STONE);
		ripeBush(helper, bush, VBBlocks.GRAPE_BUSH.get());
		for (int x = 2; x <= 4; x++) {
			build(helper, new BlockPos(x, 3, 3), Blocks.OAK_FENCE.defaultBlockState());
		}
		build(helper, bush.above(2), Blocks.OAK_FENCE.defaultBlockState());
		build(helper, bush.above(3), Blocks.OAK_FENCE.defaultBlockState());
		BlockState rowFence = state(helper, bush.above());
		helper.assertValueEqual(state(helper, bush).getValue(ClimbingBushBlock.SUPPORT), BushSupport.FACES_X, "a bush under an east-west fence row");

		helper.assertTrue(boneMeal(helper, bush), "bone meal makes a ripe bush climb the fence above");
		BlockState vine = state(helper, bush.above());
		helper.assertTrue(vine.is(VBBlocks.GRAPE_VINE_ON_SUPPORT.get()), "the bush grew into the fence, found " + vine);
		helper.assertValueEqual(vine.getValue(VineOnSupportBlock.LOOK), SupportLook.THIN_X, "spread across the east-west fence");
		helper.assertValueEqual(vine.getValue(VineOnSupportBlock.BASE), false, "a vine on its bush, not a root");
		helper.assertValueEqual(vine.getValue(ClimbingCropBlock.AGE), 0, "a fresh vine");
		helper.assertValueEqual(support(helper, bush.above()), rowFence, "the fence kept inside");
		helper.assertTrue(state(helper, bush.above().west()).getValue(CrossCollisionBlock.EAST), "the west fence still connects to the overgrown one");
		helper.assertTrue(state(helper, bush.above().east()).getValue(CrossCollisionBlock.WEST), "the east fence still connects to the overgrown one");

		for (int i = 0; i < 40; i++) {
			boneMeal(helper, bush);
		}
		BlockState top = state(helper, bush.above(2));
		helper.assertTrue(top.is(VBBlocks.GRAPE_VINE_ON_SUPPORT.get()), "bone meal grows it into the lone fence on top, found " + top);
		helper.assertValueEqual(top.getValue(VineOnSupportBlock.LOOK), SupportLook.CROSS, "wound round a lone post");
		helper.assertBlockPresent(Blocks.OAK_FENCE, bush.above(3));
		helper.assertValueEqual(VineClimbing.heightAt(helper.getLevel(), helper.absolutePos(bush.above(2)), VBBlocks.GRAPE_BUSH.get()),
				VineClimbing.MAX_HEIGHT, "plant height");

		helper.getLevel().destroyBlock(helper.absolutePos(bush), false);
		helper.succeedWhen(() -> {
			helper.assertBlockState(bush.above(), rowFence);
			helper.assertBlockPresent(Blocks.OAK_FENCE, bush.above(2));
			helper.assertItemEntityNotPresent(Items.OAK_FENCE, bush.above(), 4.0);
		});
	}

	/**
	 * Hops climb a mossy wall running north-south (spread wide across it), white grapes an upright copper chain (wound round
	 * it), red grapes copper bars (spread thin), each on a random tick like on a trellis. (The iron chain is the test data's
	 * stand-in for FD's rope, so it grows a vine on a rope here; in play it holds a vine like any chain.)
	 */
	private static void wallsChainsAndBars(GameTestHelper helper) {
		record Column(Block bush, BlockState support, boolean row, SupportLook look) {
		}
		List<Column> columns = List.of(
				new Column(VBBlocks.HOP_BUSH.get(), Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(), true, SupportLook.WIDE_Z),
				new Column(VBBlocks.WHITE_GRAPE_BUSH.get(), Blocks.COPPER_CHAIN.weathering().unaffected().defaultBlockState(), false, SupportLook.CROSS),
				new Column(VBBlocks.GRAPE_BUSH.get(), Blocks.COPPER_BARS.weathering().unaffected().defaultBlockState(), true, SupportLook.THIN_Z));
		int x = 2;
		for (Column column : columns) {
			BlockPos bush = new BlockPos(x, 1, 4);
			ripeBush(helper, bush, column.bush());
			if (column.row()) {
				build(helper, bush.above().north(), column.support());
				build(helper, bush.above().south(), column.support());
			}
			build(helper, bush.above(), column.support());
			BlockState before = state(helper, bush.above());
			ClimbingCropBlock crop = (ClimbingCropBlock) column.bush();
			for (int i = 0; i < 3000 && !state(helper, bush.above()).is(crop.supportVine()); i++) {
				helper.randomTick(bush);
			}
			BlockState vine = state(helper, bush.above());
			helper.assertTrue(vine.is(crop.supportVine()), column.bush() + " should climb " + before + ", found " + vine);
			helper.assertValueEqual(vine.getValue(VineOnSupportBlock.LOOK), column.look(), "how the vine sits on " + before);
			helper.assertValueEqual(support(helper, bush.above()), before, "the block kept inside");
			x += 4;
		}
		helper.succeed();
	}

	/**
	 * What holds a vine: fences, walls, upright chains and bars; not a chain lying down, a ladder, a glass pane, a fence
	 * gate or a waterlogged fence. How it sits: wound round a lone post, a corner or a chain; spread along a straight row.
	 */
	private static void whatHoldsAVine(GameTestHelper helper) {
		ClimbingCropBlock grapes = VBBlocks.GRAPE_BUSH.get();
		for (BlockState holds : List.of(Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.NETHER_BRICK_FENCE.defaultBlockState(),
				Blocks.STONE_BRICK_WALL.defaultBlockState(), Blocks.COPPER_CHAIN.weathering().unaffected().defaultBlockState(),
				Blocks.COPPER_CHAIN.waxed().oxidized().defaultBlockState(),
				Blocks.IRON_BARS.defaultBlockState(), Blocks.COPPER_BARS.weathering().weathered().defaultBlockState())) {
			helper.assertTrue(VineSupports.isMakeshift(holds), holds + " holds a vine");
			helper.assertTrue(grapes.vineOn(holds) != null && grapes.vineOn(holds).is(VBBlocks.GRAPE_VINE_ON_SUPPORT.get()), holds + " grows the vine on a support");
		}
		for (BlockState holdsNot : List.of(Blocks.COPPER_CHAIN.weathering().unaffected().defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X),
				Blocks.LADDER.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState(), Blocks.OAK_FENCE_GATE.defaultBlockState(),
				Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), Blocks.OAK_LOG.defaultBlockState())) {
			helper.assertTrue(grapes.vineOn(holdsNot) == null, holdsNot + " grows no vine");
		}
		BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
		helper.assertValueEqual(VineSupports.lookOf(fence), SupportLook.CROSS, "a lone fence post");
		helper.assertValueEqual(VineSupports.lookOf(fence.setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.EAST, true)),
				SupportLook.CROSS, "a fence corner");
		helper.assertValueEqual(VineSupports.lookOf(fence.setValue(CrossCollisionBlock.WEST, true)), SupportLook.THIN_X, "a fence row's end");
		helper.assertValueEqual(VineSupports.lookOf(Blocks.STONE_BRICK_WALL.defaultBlockState().setValue(WallBlock.EAST, WallSide.LOW)
				.setValue(WallBlock.WEST, WallSide.TALL)), SupportLook.WIDE_X, "a wall running east-west");
		helper.assertValueEqual(VineSupports.lookOf(Blocks.IRON_BARS.defaultBlockState().setValue(CrossCollisionBlock.NORTH, true)
				.setValue(CrossCollisionBlock.SOUTH, true)), SupportLook.THIN_Z, "bars running north-south");
		helper.assertValueEqual(VineSupports.lookOf(Blocks.IRON_CHAIN.defaultBlockState()), SupportLook.CROSS, "a chain");
		helper.assertValueEqual(VBBlocks.GRAPE_VINE_ON_SUPPORT.get().defaultBlockState().getPistonPushReaction(), PushReaction.BLOCK,
				"pistons leave an overgrown support alone");
		helper.succeed();
	}

	// ------------------------------------------------------------------ giving the block back

	/**
	 * However the plant goes, the exact block comes back: a player breaks it (fruit drops, no fence item), an explosion, a
	 * fire burning it away (removed, or turned into fire), and the root's soil going.
	 */
	private static void givesTheBlockBack(GameTestHelper helper) {
		List<BlockState> supports = List.of(Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState(),
				Blocks.COPPER_CHAIN.waxed().exposed().defaultBlockState(), Blocks.COPPER_BARS.weathering().unaffected().defaultBlockState(), Blocks.CRIMSON_FENCE.defaultBlockState());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockState[] expected = new BlockState[supports.size()];
		BlockPos[] at = new BlockPos[supports.size()];
		for (int i = 0; i < supports.size(); i++) {
			BlockPos bush = new BlockPos(2 + i * 3, 1, 3);
			ripeBush(helper, bush, VBBlocks.GRAPE_BUSH.get());
			build(helper, bush.above().west(), supports.get(i));
			build(helper, bush.above(), supports.get(i));
			expected[i] = state(helper, bush.above());
			at[i] = bush.above();
			overgrow(helper, at[i], VBBlocks.GRAPE_VINE_ON_SUPPORT.get(), ClimbingCropBlock.MAX_AGE, false);
			helper.assertValueEqual(support(helper, at[i]), expected[i], "kept inside");
		}
		// a player: the fence stays, the ripe grapes drop, no fence item
		player.gameMode.destroyBlock(helper.absolutePos(at[0]));
		helper.assertBlockState(at[0], expected[0]);
		int picked = collect(helper, at[0], VBItems.RED_GRAPES.get());
		helper.assertTrue(picked == 1 || picked == 2, "breaking a ripe vine on a fence drops 1-2 grapes (and no fence), dropped " + picked);
		// an explosion
		Vec3 boom = Vec3.atCenterOf(helper.absolutePos(at[1]));
		helper.getLevel().explode(null, boom.x, boom.y, boom.z, 1.0F, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
		// fire: burnt away (removed), and turned into fire
		helper.getLevel().removeBlock(helper.absolutePos(at[2]), false);
		helper.getLevel().setBlockAndUpdate(helper.absolutePos(at[3]), Blocks.FIRE.defaultBlockState());
		// the soil goes under a root
		BlockPos root = at[4].below();
		helper.setBlock(root.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(root, Blocks.AIR);
		build(helper, root, supports.get(4));
		BlockState rootFence = state(helper, root);
		overgrow(helper, root, VBBlocks.GRAPE_VINE_ON_SUPPORT.get(), ClimbingCropBlock.MAX_AGE, true);
		helper.setBlock(root.below(), Blocks.STONE);
		helper.succeedWhen(() -> {
			helper.assertBlockState(at[1], expected[1]);
			helper.assertBlockState(at[2], expected[2]);
			helper.assertBlockState(at[3], expected[3]);
			helper.assertBlockState(root, rootFence);
			helper.assertBlockState(at[4], expected[4]); // the vine above lost its root and went too
		});
	}

	// ------------------------------------------------------------------ fruit

	/** Picking off a fence gives one fewer than off a trellis: 1-2 ripe, 1 half-ripe; breaking drops the same. */
	private static void yieldsOneFewer(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		int x = 1;
		for (Block bushBlock : List.of(VBBlocks.GRAPE_BUSH.get(), VBBlocks.WHITE_GRAPE_BUSH.get(), VBBlocks.HOP_BUSH.get())) {
			ClimbingCropBlock bush = (ClimbingCropBlock) bushBlock;
			Block vineBlock = bush.supportVine();
			Item fruit = bush.produce();
			BlockPos base = new BlockPos(x, 1, 2);
			x += 3;
			ripeBush(helper, base, bush);
			BlockPos vine = base.above();
			boolean[] sawRipe = new boolean[4];
			for (int i = 0; i < 32; i++) {
				build(helper, vine, Blocks.OAK_FENCE.defaultBlockState());
				overgrow(helper, vine, vineBlock, ClimbingCropBlock.MAX_AGE, false);
				helper.useBlock(vine, player);
				int ripe = collect(helper, vine, fruit);
				helper.assertTrue(ripe == 1 || ripe == 2, "picking a ripe " + vineBlock + " should give 1-2, gave " + ripe);
				sawRipe[ripe] = true;
				helper.assertValueEqual(state(helper, vine).getValue(ClimbingCropBlock.AGE), ClimbingCropBlock.PICKED_AGE, "age after picking");
				helper.setBlock(vine, state(helper, vine).setValue(ClimbingCropBlock.AGE, ClimbingCropBlock.HALF_RIPE_AGE));
				helper.useBlock(vine, player);
				helper.assertValueEqual(collect(helper, vine, fruit), 1, "picking a half-ripe " + vineBlock);
				helper.setBlock(vine, Blocks.AIR);
			}
			helper.assertTrue(sawRipe[1] && sawRipe[2], "over 32 pickings of a ripe " + vineBlock + " both 1 and 2 should appear");
			for (int age = 0; age <= ClimbingCropBlock.MAX_AGE; age++) {
				List<ItemStack> drops = Block.getDrops(vineBlock.defaultBlockState().setValue(ClimbingCropBlock.AGE, age), helper.getLevel(),
						helper.absolutePos(vine), null, player, ItemStack.EMPTY);
				int count = drops.stream().filter(stack -> stack.is(fruit)).mapToInt(ItemStack::getCount).sum();
				int[] range = age == 3 ? new int[] {1, 2} : age == 2 ? new int[] {1, 1} : new int[] {0, 0};
				helper.assertTrue(count >= range[0] && count <= range[1], vineBlock + " at age " + age + " should drop " + range[0] + "-" + range[1] + ", dropped " + drops);
			}
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ neighbours

	/**
	 * An overgrown block is still what it was for its neighbours: a fence placed beside an overgrown fence connects (and the
	 * overgrown one connects back, now spread along the row), a wall beside an overgrown wall too, a gate between two
	 * overgrown walls sits low, and a lead tied to a fence stays tied when the vine grows over it.
	 */
	private static void neighboursAndLeads(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		// a fence placed next to an overgrown lone fence
		BlockPos fence = new BlockPos(2, 2, 2);
		helper.setBlock(fence.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(fence.east().below(), Blocks.STONE);
		build(helper, fence, Blocks.OAK_FENCE.defaultBlockState());
		overgrow(helper, fence, VBBlocks.GRAPE_VINE_ON_SUPPORT.get(), 2, true);
		helper.assertValueEqual(state(helper, fence).getValue(VineOnSupportBlock.LOOK), SupportLook.CROSS, "a lone overgrown post");
		ItemStack fences = new ItemStack(Items.OAK_FENCE, 4);
		player.setItemInHand(InteractionHand.MAIN_HAND, fences);
		helper.placeAt(player, fences, fence.east().below(), Direction.UP);
		helper.assertTrue(state(helper, fence.east()).getValue(CrossCollisionBlock.WEST), "a new fence connects to the overgrown fence");
		helper.assertTrue(support(helper, fence).getValue(CrossCollisionBlock.EAST), "the overgrown fence connects back");
		helper.assertValueEqual(state(helper, fence).getValue(VineOnSupportBlock.LOOK), SupportLook.THIN_X, "now spread along the row");
		// a wall placed next to an overgrown wall
		BlockPos wall = new BlockPos(2, 2, 5);
		helper.setBlock(wall.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(wall.east().below(), Blocks.STONE);
		build(helper, wall, Blocks.COBBLESTONE_WALL.defaultBlockState());
		overgrow(helper, wall, VBBlocks.HOP_VINE_ON_SUPPORT.get(), 1, true);
		ItemStack walls = new ItemStack(Items.COBBLESTONE_WALL, 4);
		player.setItemInHand(InteractionHand.MAIN_HAND, walls);
		helper.placeAt(player, walls, wall.east().below(), Direction.UP);
		helper.assertTrue(state(helper, wall.east()).getValue(WallBlock.WEST) != WallSide.NONE, "a new wall connects to the overgrown wall");
		helper.assertTrue(support(helper, wall).getValue(WallBlock.EAST) != WallSide.NONE, "the overgrown wall connects back");
		// a gate between two overgrown walls
		BlockPos gate = new BlockPos(5, 2, 8);
		for (BlockPos side : List.of(gate.west(), gate.east())) {
			helper.setBlock(side.below(), Blocks.GRASS_BLOCK);
			build(helper, side, Blocks.COBBLESTONE_WALL.defaultBlockState());
			overgrow(helper, side, VBBlocks.GRAPE_VINE_ON_SUPPORT.get(), 0, true);
		}
		helper.setBlock(gate.below(), Blocks.STONE);
		ItemStack gates = new ItemStack(Items.OAK_FENCE_GATE);
		player.setItemInHand(InteractionHand.MAIN_HAND, gates);
		player.setYRot(0.0F); // facing south: the gate spans east-west, between the walls
		helper.placeAt(player, gates, gate.below(), Direction.UP);
		helper.assertTrue(state(helper, gate).getValue(FenceGateBlock.IN_WALL), "a gate between overgrown walls sits low, in the wall");
		// a lead tied to a fence stays tied
		BlockPos post = new BlockPos(8, 2, 2);
		helper.setBlock(post.below(), Blocks.GRASS_BLOCK);
		build(helper, post, Blocks.OAK_FENCE.defaultBlockState());
		LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(helper.getLevel(), helper.absolutePos(post));
		overgrow(helper, post, VBBlocks.GRAPE_VINE_ON_SUPPORT.get(), 3, true);
		helper.assertTrue(knot.survives(), "a lead's knot stays on a fence the vine grew over");
		knot.discard();
		helper.succeed();
	}

	// ------------------------------------------------------------------ the root

	/**
	 * Planted into a fence on soil, the plant is its own root: it lives on grass or farmland, grows through its ages on
	 * random ticks, climbs the fence above when ripe, and is pickable. On stone it can't live.
	 */
	private static void rootLivesOnSoil(GameTestHelper helper) {
		BlockPos root = new BlockPos(3, 2, 3);
		helper.setBlock(root.below(), Blocks.FARMLAND);
		build(helper, root, Blocks.BIRCH_FENCE.defaultBlockState());
		build(helper, root.above(), Blocks.BIRCH_FENCE.defaultBlockState());
		overgrow(helper, root, VBBlocks.HOP_VINE_ON_SUPPORT.get(), 0, true);
		helper.assertTrue(state(helper, root).canSurvive(helper.getLevel(), helper.absolutePos(root)), "a root lives on farmland");
		for (int i = 0; i < 20000 && !state(helper, root.above()).is(VBBlocks.HOP_VINE_ON_SUPPORT.get()); i++) {
			helper.randomTick(root);
		}
		helper.assertValueEqual(state(helper, root).getValue(ClimbingCropBlock.AGE), ClimbingCropBlock.MAX_AGE, "the root grew ripe");
		helper.assertTrue(state(helper, root.above()).is(VBBlocks.HOP_VINE_ON_SUPPORT.get()), "the ripe root climbed the fence above");
		helper.assertValueEqual(VineClimbing.heightAt(helper.getLevel(), helper.absolutePos(root.above()), VBBlocks.HOP_BUSH.get()), 2, "root and vine");
		BlockPos onStone = new BlockPos(6, 2, 3);
		helper.setBlock(onStone.below(), Blocks.STONE);
		build(helper, onStone, Blocks.BIRCH_FENCE.defaultBlockState());
		BlockState stoneFence = state(helper, onStone);
		overgrow(helper, onStone, VBBlocks.HOP_VINE_ON_SUPPORT.get(), 0, true);
		helper.assertFalse(state(helper, onStone).canSurvive(helper.getLevel(), helper.absolutePos(onStone)), "a root can't live on stone");
		helper.setBlock(onStone.below(), Blocks.COBBLESTONE); // any update makes it notice
		helper.succeedWhen(() -> helper.assertBlockState(onStone, stoneFence));
	}

	// ------------------------------------------------------------------ planting, both ways round

	/**
	 * A plant and its support share a block, whichever came first: grapes used on a fence standing on grass grow inside it
	 * (not on one standing on stone, not into a waterlogged one), and a fence used on a ripe hop bush goes into it, keeps
	 * its age and connects to the fence beside it.
	 */
	private static void plantingBothWays(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		// grapes into a fence on grass
		BlockPos onGrass = new BlockPos(2, 2, 2);
		helper.setBlock(onGrass.below(), Blocks.GRASS_BLOCK);
		build(helper, onGrass, Blocks.SPRUCE_FENCE.defaultBlockState());
		BlockState spruce = state(helper, onGrass);
		ItemStack grapes = new ItemStack(VBItems.RED_GRAPES.get(), 8);
		player.setItemInHand(InteractionHand.MAIN_HAND, grapes);
		helper.useBlock(onGrass, player);
		BlockState root = state(helper, onGrass);
		helper.assertTrue(root.is(VBBlocks.GRAPE_VINE_ON_SUPPORT.get()), "grapes planted into a fence on grass, found " + root);
		helper.assertValueEqual(root.getValue(VineOnSupportBlock.BASE), true, "the plant's root");
		helper.assertValueEqual(root.getValue(ClimbingCropBlock.AGE), 0, "a young plant");
		helper.assertValueEqual(support(helper, onGrass), spruce, "the fence kept inside");
		helper.assertValueEqual(grapes.getCount(), 7, "one grape used");
		// not on stone, not under water
		BlockPos onStone = new BlockPos(5, 2, 2);
		helper.setBlock(onStone.below(), Blocks.STONE);
		build(helper, onStone, Blocks.SPRUCE_FENCE.defaultBlockState());
		helper.useBlock(onStone, player);
		helper.assertBlockPresent(Blocks.SPRUCE_FENCE, onStone);
		BlockPos wet = new BlockPos(8, 2, 2);
		helper.setBlock(wet.below(), Blocks.GRASS_BLOCK);
		build(helper, wet, Blocks.SPRUCE_FENCE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
		helper.useBlock(wet, player);
		helper.assertBlockPresent(Blocks.SPRUCE_FENCE, wet);
		helper.assertValueEqual(grapes.getCount(), 7, "no grapes used on stone or under water");
		// a fence into a ripe hop bush, next to a fence
		BlockPos bush = new BlockPos(2, 2, 6);
		ripeBush(helper, bush, VBBlocks.HOP_BUSH.get());
		helper.setBlock(bush.east().below(), Blocks.STONE);
		build(helper, bush.east(), Blocks.OAK_FENCE.defaultBlockState());
		ItemStack fences = new ItemStack(Items.OAK_FENCE, 2);
		player.setItemInHand(InteractionHand.MAIN_HAND, fences);
		helper.useBlock(bush, player);
		BlockState hops = state(helper, bush);
		helper.assertTrue(hops.is(VBBlocks.HOP_VINE_ON_SUPPORT.get()), "the fence went into the hop bush, found " + hops);
		helper.assertValueEqual(hops.getValue(ClimbingCropBlock.AGE), ClimbingCropBlock.MAX_AGE, "the bush kept its age");
		helper.assertValueEqual(hops.getValue(VineOnSupportBlock.BASE), true, "still the plant's root");
		helper.assertTrue(support(helper, bush).is(Blocks.OAK_FENCE) && support(helper, bush).getValue(CrossCollisionBlock.EAST), "the fence connects east");
		helper.assertTrue(state(helper, bush.east()).getValue(CrossCollisionBlock.WEST), "the fence beside connects back");
		helper.assertValueEqual(fences.getCount(), 1, "one fence used");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the trellis frame

	/**
	 * A trellis row draws its own frame: end posts where the row stops (on the bushes under it too), a rail and caps where
	 * nothing lattice is above; a north-south row the same way round; a lone trellis framed on every side.
	 */
	private static void frameFollowsTheRow(GameTestHelper helper) {
		BlockState alongX = VBBlocks.TRELLIS.get().defaultBlockState().setValue(jexxme.vineandbarrel.block.TrellisBlock.AXIS, Direction.Axis.X);
		BlockState alongZ = alongX.setValue(jexxme.vineandbarrel.block.TrellisBlock.AXIS, Direction.Axis.Z);
		// an east-west row of three, two high, on bushes; the middle column's top is a vine
		for (int x = 1; x <= 3; x++) {
			helper.setBlock(new BlockPos(x, 0, 1), Blocks.GRASS_BLOCK);
			helper.setBlock(new BlockPos(x, 1, 1), VBBlocks.GRAPE_BUSH.get().defaultBlockState().setValue(ClimbingCropBlock.AGE, 3));
			helper.setBlock(new BlockPos(x, 2, 1), alongX);
			helper.setBlock(new BlockPos(x, 3, 1), alongX);
		}
		helper.setBlock(new BlockPos(2, 3, 1), VBBlocks.GRAPE_VINE_ON_TRELLIS.get().defaultBlockState().setValue(jexxme.vineandbarrel.block.TrellisBlock.AXIS, Direction.Axis.X));
		helper.assertValueEqual(frame(helper, new BlockPos(1, 3, 1)), List.of(TrellisFrame.Part.POST_LEFT, TrellisFrame.Part.RAIL, TrellisFrame.Part.CAP_LEFT),
				"west end, top");
		helper.assertValueEqual(frame(helper, new BlockPos(2, 3, 1)), List.of(TrellisFrame.Part.RAIL), "middle, top (a vine)");
		helper.assertValueEqual(frame(helper, new BlockPos(3, 2, 1)), List.of(TrellisFrame.Part.POST_RIGHT), "east end, below the top");
		helper.assertValueEqual(frame(helper, new BlockPos(3, 1, 1)), List.of(TrellisFrame.Part.POST_RIGHT), "the bush under the east end carries its post");
		helper.assertValueEqual(frame(helper, new BlockPos(2, 1, 1)), List.of(), "the bush in the middle");
		// a north-south row of two
		helper.setBlock(new BlockPos(6, 1, 1), alongZ);
		helper.setBlock(new BlockPos(6, 1, 2), alongZ);
		helper.assertValueEqual(frame(helper, new BlockPos(6, 1, 1)), List.of(TrellisFrame.Part.POST_LEFT, TrellisFrame.Part.RAIL, TrellisFrame.Part.CAP_LEFT),
				"north end of a north-south row");
		// a lone trellis, and one beside a row going the other way
		helper.setBlock(new BlockPos(6, 1, 5), alongX);
		helper.setBlock(new BlockPos(7, 1, 5), alongZ);
		helper.assertValueEqual(frame(helper, new BlockPos(6, 1, 5)), List.of(TrellisFrame.Part.POST_LEFT, TrellisFrame.Part.POST_RIGHT, TrellisFrame.Part.RAIL,
				TrellisFrame.Part.CAP_LEFT, TrellisFrame.Part.CAP_RIGHT), "a trellis with nothing on its line around it");
		// bushes with a trellis in their own block: part of the row, the one with nothing above gets the rail
		for (int x = 1; x <= 2; x++) {
			helper.setBlock(new BlockPos(x, 0, 8), Blocks.GRASS_BLOCK);
			helper.setBlock(new BlockPos(x, 1, 8), VBBlocks.GRAPE_BUSH.get().defaultBlockState().setValue(ClimbingBushBlock.TRELLIS, jexxme.vineandbarrel.block.BushTrellis.X));
		}
		helper.setBlock(new BlockPos(1, 2, 8), alongX);
		helper.assertValueEqual(frame(helper, new BlockPos(1, 1, 8)), List.of(TrellisFrame.Part.POST_LEFT), "a bush in a trellis, a trellis above it");
		helper.assertValueEqual(frame(helper, new BlockPos(2, 1, 8)), List.of(TrellisFrame.Part.POST_RIGHT, TrellisFrame.Part.RAIL, TrellisFrame.Part.CAP_RIGHT),
				"a bush in a trellis with nothing above");
		helper.succeed();
	}

	private static List<TrellisFrame.Part> frame(GameTestHelper helper, BlockPos pos) {
		return TrellisFrame.parts(helper.getLevel(), helper.absolutePos(pos), state(helper, pos));
	}

	// ------------------------------------------------------------------ robustness

	/**
	 * A vine holds only a plain block (never another vine on a support or a block with a block entity, as a command could
	 * try), and a support placed without its neighbours' updates (a structure, a paste) catches up on the next tick.
	 */
	private static void holdsPlainBlocksAndCatchesUp(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
		build(helper, pos, Blocks.OAK_FENCE.defaultBlockState());
		overgrow(helper, pos, VBBlocks.GRAPE_VINE_ON_SUPPORT.get(), 1, true);
		jexxme.vineandbarrel.block.VineSupportBlockEntity entity = (jexxme.vineandbarrel.block.VineSupportBlockEntity) helper.getBlockEntity(pos,
				jexxme.vineandbarrel.block.VineSupportBlockEntity.class);
		entity.setSupport(VBBlocks.HOP_VINE_ON_SUPPORT.get().defaultBlockState());
		helper.assertTrue(entity.support() == null, "a vine can't hold another vine on a support");
		entity.setSupport(Blocks.CHEST.defaultBlockState());
		helper.assertTrue(entity.support() == null, "a vine can't hold a block with a block entity");
		// placed holding a lone fence while a fence stands east of it: it connects on the next tick
		BlockPos stale = new BlockPos(5, 2, 2);
		helper.setBlock(stale.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(stale.east().below(), Blocks.STONE);
		build(helper, stale.east(), Blocks.OAK_FENCE.defaultBlockState());
		VineOnSupportBlock.place(helper.getLevel(), helper.absolutePos(stale), VBBlocks.GRAPE_VINE_ON_SUPPORT.get().defaultBlockState(),
				Blocks.OAK_FENCE.defaultBlockState(), true);
		helper.succeedWhen(() -> {
			helper.assertTrue(support(helper, stale).getValue(CrossCollisionBlock.EAST), "the held fence caught up with the fence beside it");
			helper.assertValueEqual(state(helper, stale).getValue(VineOnSupportBlock.LOOK), SupportLook.THIN_X, "and the leaves spread along the row");
		});
	}

	/**
	 * A vine that burns away leaves its support, whatever it is: a trellis (the trellis puts the fire out), a rope (FD's, or
	 * the test data's stand-in), a fence.
	 */
	private static void burntVinesLeaveTheirSupport(GameTestHelper helper) {
		BlockState trellis = VBBlocks.TRELLIS.get().defaultBlockState().setValue(jexxme.vineandbarrel.block.TrellisBlock.AXIS, Direction.Axis.Z);
		BlockPos onTrellis = new BlockPos(2, 2, 2);
		ripeBush(helper, onTrellis.below(), VBBlocks.GRAPE_BUSH.get());
		helper.setBlock(onTrellis, VBBlocks.GRAPE_VINE_ON_TRELLIS.get().defaultBlockState().setValue(jexxme.vineandbarrel.block.TrellisBlock.AXIS, Direction.Axis.Z));
		helper.getLevel().setBlockAndUpdate(helper.absolutePos(onTrellis), Blocks.FIRE.defaultBlockState());
		helper.assertBlockState(onTrellis, trellis);
		BlockPos onRope = new BlockPos(5, 2, 2);
		ripeBush(helper, onRope.below(), VBBlocks.HOP_BUSH.get());
		helper.setBlock(onRope, VBBlocks.HOP_VINE_ON_ROPE.get().defaultBlockState());
		BlockState rope = jexxme.vineandbarrel.block.VineClimbing.ropeState(helper.getLevel(), helper.absolutePos(onRope));
		helper.getLevel().setBlockAndUpdate(helper.absolutePos(onRope), Blocks.FIRE.defaultBlockState());
		helper.assertTrue(state(helper, onRope).is(rope.getBlock()), "a burnt vine on a rope leaves the rope, found " + state(helper, onRope));
		BlockPos onFence = new BlockPos(8, 2, 2);
		helper.setBlock(onFence.below(), Blocks.GRASS_BLOCK);
		build(helper, onFence, Blocks.SPRUCE_FENCE.defaultBlockState());
		overgrow(helper, onFence, VBBlocks.GRAPE_VINE_ON_SUPPORT.get(), 3, true);
		helper.getLevel().setBlockAndUpdate(helper.absolutePos(onFence), Blocks.FIRE.defaultBlockState());
		helper.assertBlockPresent(Blocks.SPRUCE_FENCE, onFence);
		helper.succeed();
	}
}
