package jexxme.vineandbarrel.block;

import jexxme.vineandbarrel.platform.hooks.PlayerDestroyHook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A grape vine or hop bine on a fence, wall, upright chain or bars ({@link VineSupports}): the same growth, picking, bone
 * meal and bee rules as every other plant of ours ({@link ClimbingCropBlock}), with one fewer fruit per pick than on a
 * trellis (its harvest and drop tables).
 * <p>
 * The block it grew into is kept exactly as it was in a {@link VineSupportBlockEntity}: drawn under the leaves (the
 * loaders' model wrappers), bumped into like before (its collision), connected to by its neighbours, and put back
 * whenever the plant goes, however it goes.
 * <p>
 * It is either a vine on its own plant below ({@code base=false}), or the plant's root itself, planted into a support
 * that stands on soil ({@code base=true}: the berry bush's soils, and nothing keeps it alive once the soil is gone).
 */
public class VineOnSupportBlock extends ClimbingCropBlock implements EntityBlock, PlayerDestroyHook
{
	public static final BooleanProperty BASE = BooleanProperty.create("base");
	public static final EnumProperty<SupportLook> LOOK = EnumProperty.create("look", SupportLook.class);
	private static final VoxelShape LEAVES_CROSS = Block.column(12.0, 0.0, 16.0);
	private static final VoxelShape LEAVES_THIN_X = Block.box(0.0, 0.0, 4.5, 16.0, 16.0, 11.5);
	private static final VoxelShape LEAVES_THIN_Z = Block.box(4.5, 0.0, 0.0, 11.5, 16.0, 16.0);
	private static final VoxelShape LEAVES_WIDE_X = Block.box(0.0, 0.0, 3.0, 16.0, 16.0, 13.0);
	private static final VoxelShape LEAVES_WIDE_Z = Block.box(3.0, 0.0, 0.0, 13.0, 16.0, 16.0);
	/** The root's young shoot (age 0), as small as the bush's sapling. */
	private static final VoxelShape SHOOT = Block.column(10.0, 0.0, 8.0);

	private final Supplier<? extends ClimbingBushBlock> bush;

	public VineOnSupportBlock(Properties properties, Supplier<? extends ClimbingBushBlock> bush, Supplier<? extends Item> produce, ResourceKey<LootTable> harvestLoot) {
		super(properties, produce, harvestLoot);
		this.bush = bush;
		registerDefaultState(defaultBlockState().setValue(BASE, false).setValue(LOOK, SupportLook.CROSS));
	}

	@Override
	public ClimbingBushBlock bushBlock() {
		return bush.get();
	}

	/**
	 * Puts {@code vine} (one of these blocks, any age) at {@code pos} holding {@code support}, the block that stood there
	 * (it grew into it, or was planted into it). The neighbours see no change: they saw the support before and still do.
	 */
	public static void place(Level level, BlockPos pos, BlockState vine, BlockState support, boolean base) {
		place(level, pos, vine, support, base, true);
	}

	/**
	 * Puts {@code vine} at {@code pos} holding {@code support}. When the support is new there ({@code supportWasThere}
	 * false: put into a bush), the neighbours are told once the support is known, so a fence beside it connects.
	 */
	public static void place(Level level, BlockPos pos, BlockState vine, BlockState support, boolean base, boolean supportWasThere) {
		BlockState placed = vine.setValue(LOOK, VineSupports.lookOf(support)).setValue(BASE, base);
		level.setBlock(pos, placed, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
		if (level.getBlockEntity(pos) instanceof VineSupportBlockEntity entity) {
			entity.setSupport(support);
		}
		if (!supportWasThere) {
			placed.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
		}
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new VineSupportBlockEntity(pos, state);
	}

	private static VoxelShape leaves(SupportLook look) {
		return switch (look) {
			case CROSS -> LEAVES_CROSS;
			case THIN_X -> LEAVES_THIN_X;
			case THIN_Z -> LEAVES_THIN_Z;
			case WIDE_X -> LEAVES_WIDE_X;
			case WIDE_Z -> LEAVES_WIDE_Z;
		};
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		BlockState support = VineSupports.supportAt(level, pos);
		VoxelShape leaves = state.getValue(BASE) && state.getValue(AGE) == 0 ? SHOOT : leaves(state.getValue(LOOK));
		return support == null ? leaves : Shapes.or(support.getShape(level, pos, context), leaves);
	}

	/** The support is still there: you bump into the fence or wall as before, the leaves let you through. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		BlockState support = VineSupports.supportAt(level, pos);
		return support == null ? Shapes.empty() : support.getCollisionShape(level, pos, context);
	}

	/** Mobs plan their paths around it, as around the fence or wall it holds. */
	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (state.getValue(BASE)) {
			BlockPos below = pos.below();
			return mayPlaceOn(level.getBlockState(below), level, below); // the root: on the berry bush's soils
		}
		return VineClimbing.vineCanSurvive(level, pos, this);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
									 BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		BlockState updated = state;
		if (directionToNeighbour == Direction.DOWN) {
			updated = updated.setValue(BASE, !isPlant(neighbourState));
		} else if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VineSupportBlockEntity entity && entity.support() != null) {
			// The support keeps following its neighbours (a fence connects to a new fence beside it), as if it stood here alone.
			// The server decides: the client's guess of a placement could be refused, and only block states roll back
			BlockState support = entity.support();
			BlockState followed = support.updateShape(level, ticks, pos, directionToNeighbour, neighbourPos,
					VineSupports.appearance(level, neighbourPos, neighbourState), random);
			if (followed != support && followed.is(support.getBlock())) {
				entity.setSupport(followed);
				updated = updated.setValue(LOOK, VineSupports.lookOf(followed));
			}
		}
		if (!updated.canSurvive(level, pos)) {
			ticks.scheduleTick(pos, this, 1); // like the vines on a rope or a trellis: the plant goes on the next tick, the support stays
		}
		return updated;
	}

	/** Newly here (grown, planted, pasted from a structure): the support catches up with its neighbours on the next tick. */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!oldState.is(this) && !level.isClientSide()) {
			level.scheduleTick(pos, this, 1);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.canSurvive(level, pos)) {
			// the support as it would stand here (a structure turned it, or it came without its neighbours' updates)
			if (level.getBlockEntity(pos) instanceof VineSupportBlockEntity entity && entity.support() != null) {
				BlockState support = entity.support();
				BlockState followed = Block.updateFromNeighbourShapes(support, level, pos);
				if (followed != support && followed.is(support.getBlock())) {
					entity.setSupport(followed);
					level.setBlock(pos, state.setValue(LOOK, VineSupports.lookOf(followed)), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
			return;
		}
		BlockState support = VineSupports.supportAt(level, pos);
		Block.dropResources(state, level, pos, level.getBlockEntity(pos));
		level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
		level.setBlock(pos, standing(support, level, pos), Block.UPDATE_ALL);
	}

	@Override
	public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack, boolean willHarvest, FluidState fluid) {
		// The game already called playerWillDestroy (sound, particles, the destroy game event) just before this
		BlockState support = VineSupports.supportAt(level, pos);
		return level.setBlock(pos, support != null ? standing(support, level, pos) : fluid.createLegacyBlock(),
				level.isClientSide() ? Block.UPDATE_ALL_IMMEDIATE : Block.UPDATE_ALL);
	}

	/** Puts the support back when the plant went by anything but a player (burnt, blown up, a command). */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		BlockState support = VineSupports.takeStash(level, pos);
		BlockState now = level.getBlockState(pos);
		if (support != null && !movedByPiston && (now.isAir() || now.is(BlockTags.FIRE))) { // burnt away: the support puts the fire out
			level.setBlock(pos, standing(support, level, pos), Block.UPDATE_ALL);
		}
	}

	/** The support as it stands here now, connected to its neighbours. */
	private static BlockState standing(@Nullable BlockState support, LevelAccessor level, BlockPos pos) {
		return support == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : Block.updateFromNeighbourShapes(support, level, pos);
	}

	/** A quarter turn turns the way the leaves spread (structure templates). */
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
			return state.setValue(LOOK, state.getValue(LOOK).turned());
		}
		return state;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(BASE, LOOK);
	}
}
