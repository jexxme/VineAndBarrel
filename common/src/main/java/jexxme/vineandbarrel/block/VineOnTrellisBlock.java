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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * A grape vine or hop bine grown onto a {@link TrellisBlock}: the trellis twin of {@link VineOnRopeBlock}, with the same
 * growth, picking, bone meal, bee and drop rules ({@link ClimbingCropBlock}).
 * <p>
 * The trellis is part of this block (its model draws the panel, facing the way the trellis did). It lives only on top of
 * its own bush or vine, within the height limit, and needs the same light as a vine on a rope. Whenever the vine goes (broken, its support removed,
 * an explosion), the trellis is put back where it stood, like the rope under a vine on a rope.
 */
public class VineOnTrellisBlock extends ClimbingCropBlock implements PlayerDestroyHook
{
	public static final EnumProperty<Direction.Axis> AXIS = TrellisBlock.AXIS;
	private static final VoxelShape LEAVES_X = Block.box(0.0, 0.0, 5.0, 16.0, 16.0, 11.0);
	private static final VoxelShape LEAVES_Z = Block.box(5.0, 0.0, 0.0, 11.0, 16.0, 16.0);

	private final Supplier<? extends ClimbingBushBlock> bush;
	private final Supplier<? extends Block> trellis;

	public VineOnTrellisBlock(Properties properties, Supplier<? extends ClimbingBushBlock> bush, Supplier<? extends Block> trellis,
							  Supplier<? extends Item> produce, ResourceLocation harvestLoot) {
		super(properties, produce, harvestLoot);
		this.bush = bush;
		this.trellis = trellis;
		registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	public ClimbingBushBlock bushBlock() {
		return bush.get();
	}

	/** The bare trellis this vine grew on, facing the same way: what is left when the vine goes. */
	public BlockState trellisState(BlockState vine) {
		return trellisState(vine.getValue(AXIS));
	}

	/** A bare trellis whose panel runs along {@code axis}. */
	public BlockState trellisState(Direction.Axis axis) {
		BlockState bare = trellis.get().defaultBlockState();
		return bare.hasProperty(AXIS) ? bare.setValue(AXIS, axis) : bare;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Direction.Axis.X ? LEAVES_X : LEAVES_Z;
	}

	/** The wood is still there: you bump into the panel like on a bare trellis, the leaves let you through. */
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return TrellisBlock.panel(state.getValue(AXIS));
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return VineClimbing.vineCanSurvive(level, pos, this);
	}

	/** Mobs don't plan paths through the panel (like vanilla's chains and bars). */
	@Override
	public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
		return false;
	}

	@Override
	public BlockState updateShape(BlockState state, Direction directionToNeighbour, BlockState neighbourState, LevelAccessor level, BlockPos pos,
									 BlockPos neighbourPos) {
		if (!state.canSurvive(level, pos)) {
			level.scheduleTick(pos, this, 1); // like the vine on a rope: break on the next tick, which then puts the trellis back
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
		return level.setBlock(pos, trellisState(state), level.isClientSide() ? Block.UPDATE_ALL_IMMEDIATE : Block.UPDATE_ALL);
	}

	/**
	 * Puts the trellis back when the vine was removed by anything other than a player (who is handled above), burnt away
	 * too (the trellis puts the fire out, as under a bush).
	 */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		super.onRemove(state, level, pos, newState, movedByPiston);
		if (state.is(newState.getBlock()) || level.isClientSide()) {
			return; // onRemove also runs when only the state changes (growing, picking): the vine is still there
		}
		BlockState now = level.getBlockState(pos);
		if (!movedByPiston && (now.isAir() || now.is(BlockTags.FIRE))) {
			level.setBlock(pos, trellisState(state), Block.UPDATE_ALL);
		}
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return TrellisBlock.rotateAxis(state, rotation);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(AXIS);
	}
}
