package jexxme.vineandbarrel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * A grape vine or hop bine grown onto an FD rope, adapted from FD's {@code HangingTomatoBlock}
 * (Farmer's Delight by vectorwing, MIT, see NOTICE_farmersdelight.txt).
 * <p>
 * The rope is part of this block (its model draws the rope, like FD's {@code template_crop_with_rope}). It lives only on
 * top of its own bush or vine and needs the same light as a tomato vine. Whenever the vine goes (broken, its support
 * removed, an explosion), the rope is put back, like FD's rope permanence.
 */
public class VineOnRopeBlock extends ClimbingCropBlock
{
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

	public VineOnRopeBlock(Properties properties, Supplier<? extends Block> bush, Supplier<? extends Item> produce, ResourceKey<LootTable> harvestLoot) {
		super(properties, bush, null, produce, harvestLoot);
	}

	@Override
	public Block vine() {
		return this;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return (below.is(bush()) || below.is(this)) && (level.getRawBrightness(pos, 0) >= 8 || level.canSeeSky(pos));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
									 BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			ticks.scheduleTick(pos, this, 1); // like FD: break on the next tick, which then puts the rope back
		}
		return state;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			level.destroyBlock(pos, true);
		}
	}

	@Override
	public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack, boolean willHarvest, FluidState fluid) {
		playerWillDestroy(level, pos, state, player);
		return level.setBlock(pos, RopeClimbing.ropeState(level, pos), level.isClientSide() ? Block.UPDATE_ALL_IMMEDIATE : Block.UPDATE_ALL);
	}

	/** Puts the rope back when the vine was removed by anything other than a player (who is handled above). */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		if (!movedByPiston && level.getBlockState(pos).isAir()) {
			BlockState rope = RopeClimbing.ropeState(level, pos);
			if (!rope.isAir()) {
				level.setBlock(pos, rope, Block.UPDATE_ALL);
			}
		}
	}
}
