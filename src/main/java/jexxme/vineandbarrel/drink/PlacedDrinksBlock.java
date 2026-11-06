package jexxme.vineandbarrel.drink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * D1: up to four mugs or wine glasses, full or empty, standing on top of a block like candles or sea pickles (tavern
 * tables). Each spot keeps its own item in the block entity; the block state only says what stands where, and the
 * liquid of each spot is tinted in its drink's colour. Sneak-use puts one down; right-click with an empty hand picks the
 * one you point at back up.
 */
public class PlacedDrinksBlock extends Block implements EntityBlock
{
	public static final int SPOTS = 4;
	public static final List<EnumProperty<PlacedDrink>> SPOT = List.of(
		EnumProperty.create("spot_0", PlacedDrink.class), EnumProperty.create("spot_1", PlacedDrink.class),
		EnumProperty.create("spot_2", PlacedDrink.class), EnumProperty.create("spot_3", PlacedDrink.class));

	/** The north-west corner of each spot's 4x4 footprint: 0 = NW, 1 = NE, 2 = SW, 3 = SE. */
	public static final int[][] SPOT_ORIGIN = {{2, 2}, {10, 2}, {2, 10}, {10, 10}};
	private static final int MUG_HEIGHT = 5;
	private static final int GLASS_HEIGHT = 6;

	public PlacedDrinksBlock(Properties properties) {
		super(properties);
		BlockState state = this.stateDefinition.any();
		for (EnumProperty<PlacedDrink> spot : SPOT) {
			state = state.setValue(spot, PlacedDrink.NONE);
		}
		this.registerDefaultState(state);
	}

	/** The spot under a point of the block's top (x, z in block-local 0..1). */
	public static int spotAt(double localX, double localZ) {
		return (localX >= 0.5 ? 1 : 0) + (localZ >= 0.5 ? 2 : 0);
	}

	public static int spotAt(BlockPos pos, Vec3 hit) {
		return spotAt(hit.x - pos.getX(), hit.z - pos.getZ());
	}

	// ------------------------------------------------------------------ interaction

	/**
	 * Holding something, a right-click is that item's (drinking a drink in hand, placing a block); only an empty hand
	 * picks up. Putting a drink down is always a sneak-use (see {@link DrinkPlacing}), so the two never mix.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
	}

	/** Empty hand: pick up the drink you point at (or the nearest one). */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			ItemStack taken = takeFrom(level, pos, spotAt(pos, hit.getLocation()));
			if (taken.isEmpty()) {
				return InteractionResult.PASS;
			}
			if (!player.getInventory().add(taken)) {
				player.drop(taken, false, Prediction.SERVER_ONLY);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Takes the item off a spot (the preferred one if it is taken, else the nearest taken one) and removes the block
	 * once the last one is gone. Returns the item, or empty if nothing stands here.
	 */
	public static ItemStack takeFrom(Level level, BlockPos pos, int preferred) {
		if (!(level.getBlockEntity(pos) instanceof PlacedDrinksBlockEntity drinks)) {
			return ItemStack.EMPTY;
		}
		int spot = -1;
		for (int candidate : nearestFirst(preferred)) {
			if (!drinks.getItem(candidate).isEmpty()) {
				spot = candidate;
				break;
			}
		}
		if (spot < 0) {
			return ItemStack.EMPTY;
		}
		ItemStack taken = drinks.getItem(spot);
		drinks.setItem(spot, ItemStack.EMPTY);
		level.playSound(null, pos, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 0.6F, 1.3F);
		if (drinks.count() == 0) {
			level.removeBlock(pos, false);
		} else {
			level.setBlock(pos, level.getBlockState(pos).setValue(SPOT.get(spot), PlacedDrink.NONE), Block.UPDATE_ALL);
		}
		level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
		return taken;
	}

	/**
	 * Breaking a table of drinks knocks off only the drink you aim at (it drops, not in creative); the block goes with
	 * the last one, like taking them one by one with an empty hand.
	 */
	@Override
	public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack, boolean willHarvest, FluidState fluid) {
		if (!(level.getBlockEntity(pos) instanceof PlacedDrinksBlockEntity drinks) || drinks.count() <= 1) {
			return super.onDestroyedByPlayer(state, level, pos, player, toolStack, willHarvest, fluid);
		}
		if (!level.isClientSide()) {
			Vec3 aim = player.pick(player.blockInteractionRange(), 1.0F, false).getLocation();
			ItemStack knocked = takeFrom(level, pos, spotAt(pos, aim));
			if (!knocked.isEmpty() && !player.hasInfiniteMaterials()) {
				Block.popResource(level, pos, knocked);
			}
		}
		// The block stays (with the other drinks on it)
		return false;
	}

	/** The spots in order of distance from {@code spot}: itself, its two neighbours, then the opposite corner. */
	static int[] nearestFirst(int spot) {
		return new int[]{spot, spot ^ 1, spot ^ 2, spot ^ 3};
	}

	// ------------------------------------------------------------------ support, shape

	/** Needs a sturdy top below, like candles. */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return Block.canSupportCenter(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return direction == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
			: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape shape = Shapes.empty();
		for (int i = 0; i < SPOTS; i++) {
			PlacedDrink drink = state.getValue(SPOT.get(i));
			if (drink != PlacedDrink.NONE) {
				int x = SPOT_ORIGIN[i][0];
				int z = SPOT_ORIGIN[i][1];
				int height = drink.isMug() ? MUG_HEIGHT : GLASS_HEIGHT;
				shape = Shapes.or(shape, Block.box(x, 0, z, x + 4, height, z + 4));
			}
		}
		return shape.isEmpty() ? Block.box(2, 0, 2, 14, 5, 14) : shape;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		if (level.getBlockEntity(pos) instanceof PlacedDrinksBlockEntity drinks) {
			for (int i = 0; i < SPOTS; i++) {
				if (!drinks.getItem(i).isEmpty()) {
					return drinks.getItem(i);
				}
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		SPOT.forEach(builder::add);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PlacedDrinksBlockEntity(pos, state);
	}
}
