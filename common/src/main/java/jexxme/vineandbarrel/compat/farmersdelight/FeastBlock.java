package jexxme.vineandbarrel.compat.farmersdelight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;

import java.util.function.Supplier;

/**
 * A feast, the way Farmer's Delight's feasts work, in our own code (FD is never compiled against): a placed dish with
 * four servings, one taken with each bowl, then the leftovers, which a last click clears (dropping its loot). Broken
 * whole it drops itself; broken once served from, only the leftovers' loot. Like FD's, it needs solid ground.
 */
public class FeastBlock extends Block
{
	public static final int MAX_SERVINGS = 4;
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final IntegerProperty SERVINGS = IntegerProperty.create("servings", 0, MAX_SERVINGS);
	private static final VoxelShape POT = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 6.0D, 14.0D);
	/** The pot with its two handles, which stick out on the sides left and right of its facing. */
	private static final VoxelShape SHAPE_X = Shapes.or(POT, Block.box(0.5D, 4.0D, 6.0D, 15.5D, 5.0D, 10.0D));
	private static final VoxelShape SHAPE_Z = Shapes.or(POT, Block.box(6.0D, 4.0D, 0.5D, 10.0D, 5.0D, 15.5D));

	private final Supplier<? extends Item> serving;

	public FeastBlock(Properties properties, Supplier<? extends Item> serving) {
		super(properties);
		this.serving = serving;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SERVINGS, MAX_SERVINGS));
	}

	/** One serving: the meal in a bowl. */
	public ItemStack serving() {
		return new ItemStack(serving.get());
	}

	/** What a serving is eaten from: the serving's bowl (its crafting remainder). */
	public Item container() {
		Item remainder = serving.get().getCraftingRemainingItem();
		return remainder == null ? net.minecraft.world.item.Items.BOWL : remainder;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (state.getValue(SERVINGS) == 0) {
			clearLeftovers(level, pos, player);
			return ItemInteractionResult.sidedSuccess(level.isClientSide());
		}
		if (!stack.is(container())) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!level.isClientSide()) {
			ItemStack meal = serving();
			Item bowl = stack.getItem();
			stack.consume(1, player);
			player.awardStat(Stats.ITEM_USED.get(bowl));
			if (!player.getInventory().add(meal)) {
				player.drop(meal, false);
			}
			level.setBlock(pos, state.setValue(SERVINGS, state.getValue(SERVINGS) - 1), Block.UPDATE_ALL);
			level.playSound(null, pos, FDCompat.FEAST_SERVE.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
			if (level instanceof ServerLevel server) {
				server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, serving()), pos.getX() + 0.5D, pos.getY() + 0.4D,
					pos.getZ() + 0.5D, 6, 0.2D, 0.1D, 0.2D, 0.05D);
			}
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(SERVINGS) == 0) {
			return clearLeftovers(level, pos, player);
		}
		// PASS, as FD's feasts: a bowl in the off hand still serves, and a block in the main hand still places
		if (!player.getOffhandItem().is(container())) {
			player.displayClientMessage(Component.translatable("block.vineandbarrel.feast.use_container", new ItemStack(container()).getHoverName()), true);
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult clearLeftovers(Level level, BlockPos pos, Player player) {
		if (!level.isClientSide()) {
			level.playSound(null, pos, SoundEvents.LANTERN_BREAK, SoundSource.BLOCKS, 0.8F, 0.8F);
			level.destroyBlock(pos, true, player);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isSolid();
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		return direction == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
			: super.updateShape(state, direction, neighbourState, level, pos, neighbourPos);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_X : SHAPE_Z;
	}

	/** A comparator reads the servings left, like FD's feasts. */
	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return state.getValue(SERVINGS);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SERVINGS);
	}
}
