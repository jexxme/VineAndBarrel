package jexxme.vineandbarrel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The Trellis: a thin lattice panel for grape vines and hop bines to climb (see {@link VineClimbing}). Panels side by side
 * and stacked make one continuous lattice.
 * It stands anywhere and stacks upward; the panel faces the player who places it, and a trellis placed on another one
 * (bare or grown over) keeps its line, so rows stay straight. It holds water like a ladder; a vine doesn't climb a
 * trellis under water.
 * <p>
 * A grape or hop bush goes into it: planted into a trellis that stands on soil, or a trellis put into a bush (see
 * {@link ClimbingBushBlock#TRELLIS}); the plant then climbs the trellises above.
 */
public class TrellisBlock extends Block implements SimpleWaterloggedBlock
{
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final VoxelShape PANEL_X = Block.box(0.0, 0.0, 7.0, 16.0, 16.0, 9.0);
	private static final VoxelShape PANEL_Z = Block.box(7.0, 0.0, 0.0, 9.0, 16.0, 16.0);

	public TrellisBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(WATERLOGGED, false));
	}

	/** The panel of a trellis along {@code axis}: also what you bump into on a grown-over one. */
	public static VoxelShape panel(Direction.Axis axis) {
		return axis == Direction.Axis.X ? PANEL_X : PANEL_Z;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return panel(state.getValue(AXIS));
	}

	/** Mobs don't plan paths through the panel (like vanilla's chains and bars). */
	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState below = context.getLevel().getBlockState(context.getClickedPos().below());
		BlockState placed = defaultBlockState().setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER));
		if ((below.getBlock() instanceof TrellisBlock || below.getBlock() instanceof VineOnTrellisBlock) && below.hasProperty(AXIS)) {
			return placed.setValue(AXIS, below.getValue(AXIS));
		}
		if (below.getBlock() instanceof ClimbingBushBlock && below.getValue(ClimbingBushBlock.TRELLIS).axis() != null) {
			return placed.setValue(AXIS, below.getValue(ClimbingBushBlock.TRELLIS).axis());
		}
		// facing the player: the panel runs across the way they look
		return placed.setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
	}

	/**
	 * Grapes, white grapes or hops in the hand plant their bush into a trellis that stands on soil the bush grows on: the
	 * trellis stays in the bush (see {@link ClimbingBushBlock#TRELLIS}). Not under water, as no bush grows there.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
										  BlockHitResult hitResult) {
		if (!state.getValue(WATERLOGGED) && stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ClimbingBushBlock bush) {
			BlockState planted = bush.plantedIn(state, level, pos);
			if (planted.canSurvive(level, pos)) {
				if (level instanceof ServerLevel) {
					level.setBlock(pos, planted, Block.UPDATE_ALL);
					SoundType sound = planted.getSoundType();
					level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
					level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, planted));
					player.awardStat(Stats.ITEM_USED.get(item));
					stack.consume(1, player);
				}
				return InteractionResult.SUCCESS;
			}
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
									 BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return rotateAxis(state, rotation);
	}

	/** A quarter turn swaps the panel's axis (structure templates). */
	static BlockState rotateAxis(BlockState state, Rotation rotation) {
		if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
			return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		}
		return state;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS, WATERLOGGED);
	}
}
