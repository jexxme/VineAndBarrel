package jexxme.vineandbarrel.block;

import jexxme.vineandbarrel.platform.hooks.PlayerDestroyHook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
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
 * top of its own bush or vine (on a rope or a trellis), within the height limit, and needs the same light as a tomato
 * vine. Whenever the vine goes
 * (broken, its support removed, an explosion), the rope is put back, like FD's rope permanence.
 */
public class VineOnRopeBlock extends ClimbingCropBlock implements PlayerDestroyHook
{
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

	private final Supplier<? extends ClimbingBushBlock> bush;

	public VineOnRopeBlock(Properties properties, Supplier<? extends ClimbingBushBlock> bush, Supplier<? extends Item> produce, ResourceLocation harvestLoot) {
		super(properties, produce, harvestLoot);
		this.bush = bush;
	}

	@Override
	public ClimbingBushBlock bushBlock() {
		return bush.get();
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return VineClimbing.vineCanSurvive(level, pos, this);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction directionToNeighbour, BlockState neighbourState, LevelAccessor level, BlockPos pos,
									 BlockPos neighbourPos) {
		if (!state.canSurvive(level, pos)) {
			level.scheduleTick(pos, this, 1); // like FD: break on the next tick, which then puts the rope back
		}
		return state;
	}

	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			level.destroyBlock(pos, true);
		}
	}

	@Override
	public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack, boolean willHarvest, FluidState fluid) {
		// The game already called playerWillDestroy (sound, particles, the destroy game event) just before this
		return level.setBlock(pos, VineClimbing.ropeState(level, pos), level.isClientSide() ? Block.UPDATE_ALL_IMMEDIATE : Block.UPDATE_ALL);
	}

	/**
	 * Puts the rope back when the vine was removed by anything other than a player (who is handled above), burnt away too
	 * (as a trellis is).
	 */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		super.onRemove(state, level, pos, newState, movedByPiston);
		if (state.is(newState.getBlock()) || level.isClientSide()) {
			return; // onRemove also runs when only the state changes (growing, picking): the vine is still there
		}
		BlockState now = level.getBlockState(pos);
		if (!movedByPiston && (now.isAir() || now.is(BlockTags.FIRE))) {
			BlockState rope = VineClimbing.ropeState(level, pos);
			if (!rope.isAir()) {
				level.setBlock(pos, rope, Block.UPDATE_ALL);
			}
		}
	}
}
