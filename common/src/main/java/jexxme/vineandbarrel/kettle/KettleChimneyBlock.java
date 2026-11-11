package jexxme.vineandbarrel.kettle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One block of a kettle's extended chimney (WP4: right-click a kettle with a copper ingot and its chimney grows a block,
 * up to {@link #MAX}). A technical block like a piston head: no item, not in the creative tab; it drops the copper ingot
 * it was made from and takes the kettle's copper stage. It stands on the kettle's chimney or on another chimney block;
 * without that it breaks (and everything above it, like sugar cane).
 * <p>
 * A roof in the way stays whole: the next piece goes on top of it and the pipe is drawn through the roof blocks, up to
 * {@link #MAX_THROUGH} of them ({@link #THROUGH}, {@link #RISING}). Such a piece stands on the piece (or kettle) under
 * the roof, whatever the roof blocks between are.
 * <p>
 * {@link #SIZE} is the kettle's size (the pipe is 4, 5 or 6 px wide); a Big Kettle's chimney stands on a block corner,
 * so its pieces are drawn off-centre, turned by {@link #FACING} like the kettle.
 */
public class KettleChimneyBlock extends Block
{
	public static final int MAX = 8;
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final IntegerProperty SIZE = IntegerProperty.create("size", 1, 3);
	/** The top piece: half a pipe and the cap. */
	public static final BooleanProperty TOP = BooleanProperty.create("top");
	/**
	 * The piece stands in a hole in a flat roof: a copper flashing plate lies on the roof around it and seals the hole.
	 * (Chimneys from before the roof stayed whole broke the roof block and got it; new pieces only get it in a hole.)
	 */
	public static final BooleanProperty FLASHING = BooleanProperty.create("flashing");
	/** The most roof blocks the pipe goes through between two pieces. */
	public static final int MAX_THROUGH = 2;
	/** How many roof blocks right under this piece its pipe goes through (it draws the pipe in the one just below). */
	public static final IntegerProperty THROUGH = IntegerProperty.create("through", 0, MAX_THROUGH);
	/** The piece above goes through two roof blocks: this one draws its pipe on up into the lower of them. */
	public static final BooleanProperty RISING = BooleanProperty.create("rising");

	private final WeatherState weatherState;
	private final boolean waxed;

	public KettleChimneyBlock(WeatherState weatherState, boolean waxed, BlockBehaviour.Properties properties) {
		super(properties);
		this.weatherState = weatherState;
		this.waxed = waxed;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SIZE, 1).setValue(TOP, true).setValue(FLASHING, false)
			.setValue(THROUGH, 0).setValue(RISING, false));
	}

	public WeatherState getWeatherState() {
		return weatherState;
	}

	public boolean isWaxed() {
		return waxed;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SIZE, TOP, FLASHING, THROUGH, RISING);
	}

	@Override
	public MutableComponent getName() {
		return KettleRegistry.singleOf(this).getName();
	}

	/** A hole in a roof: at least two of the four blocks around it are solid on the side facing it. */
	public static boolean inRoof(BlockGetter level, BlockPos pos) {
		int solid = 0;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos next = pos.relative(side);
			if (level.getBlockState(next).isFaceSturdy(level, next, side.getOpposite())) {
				solid++;
			}
		}
		return solid >= 2;
	}

	/** A copper ingot on the chimney grows it, like on the kettle (you see where it goes). */
	@Override
	protected net.minecraft.world.InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
		net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
		if (!held.is(Items.COPPER_INGOT)) {
			return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		KettleMultiblock.Kettle kettle = KettleMultiblock.kettleAt(level, kettleUnder(level, pos));
		if (kettle == null || !KettleMultiblock.extendChimney(level, kettle, player)) {
			return net.minecraft.world.InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			held.consume(1, player);
		}
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------------ standing on the kettle

	/** What this piece stands on: the piece or kettle block right under it, or under the roof blocks it goes through. */
	public static BlockPos foot(BlockState state, BlockPos pos) {
		return pos.below(state.getValue(THROUGH) + 1);
	}

	/** Whether a piece can stand on {@code foot}: a piece of its size and facing, or the extended chimney of its kettle size. */
	private static boolean standsOn(BlockState state, BlockState foot) {
		if (foot.getBlock() instanceof KettleChimneyBlock) {
			return foot.getValue(SIZE).equals(state.getValue(SIZE)) && foot.getValue(FACING) == state.getValue(FACING);
		}
		return KettleMultiblock.chimneyOf(foot) == state.getValue(SIZE) && KettleMultiblock.isExtended(foot);
	}

	/** The piece standing on the piece or kettle block at {@code foot} (right on it or through a roof), or null. */
	public static @Nullable BlockPos pieceOn(BlockGetter level, BlockPos foot) {
		BlockState footState = level.getBlockState(foot);
		for (int through = 0; through <= MAX_THROUGH; through++) {
			BlockPos at = foot.above(through + 1);
			BlockState state = level.getBlockState(at);
			if (state.getBlock() instanceof KettleChimneyBlock && state.getValue(THROUGH) == through && standsOn(state, footState)) {
				return at;
			}
		}
		return null;
	}

	/** The kettle block under a chimney piece, walking down the chimney (for anything else: {@code pos} itself). */
	public static BlockPos kettleUnder(BlockGetter level, BlockPos pos) {
		BlockPos at = pos;
		BlockState state = level.getBlockState(at);
		for (int i = 0; i <= MAX && state.getBlock() instanceof KettleChimneyBlock; i++) {
			at = foot(state, at);
			state = level.getBlockState(at);
		}
		return at;
	}

	/** A piece's state with its cap and its pipe up into the roof as the piece standing on it needs them. */
	public static BlockState linked(BlockGetter level, BlockPos pos, BlockState state) {
		BlockPos above = pieceOn(level, pos);
		boolean rising = above != null && level.getBlockState(above).getValue(THROUGH) == MAX_THROUGH;
		return state.setValue(TOP, above == null).setValue(RISING, rising);
	}

	/**
	 * The piece or kettle block at {@code pos} went or changed: pieces standing on it through a roof check their footing
	 * next tick (the game only tells the block right above).
	 */
	public static void checkPiecesAbove(Level level, BlockPos pos) {
		for (int through = 1; through <= MAX_THROUGH; through++) {
			BlockPos at = pos.above(through + 1);
			BlockState state = level.getBlockState(at);
			if (state.getBlock() instanceof KettleChimneyBlock && state.getValue(THROUGH) == through) {
				level.scheduleTick(at, state.getBlock(), 1);
			}
		}
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return standsOn(state, level.getBlockState(foot(state, pos)));
	}

	/** Lost its footing (the kettle or the piece below went): breaks on the next tick, dropping its ingot. */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
			ticks.scheduleTick(pos, this, 1);
		}
		if (direction == Direction.UP) {
			// the top piece carries the cap: the piece under a removed top becomes the new top
			return linked(level, pos, state);
		}
		return state;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			level.destroyBlock(pos, true);
		}
	}

	/**
	 * A piece taken off: the piece under it (under the roof, too) gets the cap back, or the kettle's chimney its own if it
	 * was the last; pieces standing on it through a roof fall like the rest.
	 */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (!KettleMultiblock.isConverting()) {
			BlockPos foot = foot(state, pos);
			BlockState below = level.getBlockState(foot);
			if (below.getBlock() instanceof KettleChimneyBlock) {
				BlockState linked = linked(level, foot, below);
				if (linked != below) {
					level.setBlock(foot, linked, Block.UPDATE_ALL);
				}
			} else if (KettleMultiblock.isExtended(below) && pieceOn(level, foot) == null) {
				level.setBlock(foot, KettleMultiblock.withExtended(below, false), Block.UPDATE_ALL);
			}
		}
		checkPiecesAbove(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return List.of(new ItemStack(Items.COPPER_INGOT));
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(Items.COPPER_INGOT);
	}

	// ------------------------------------------------------------------ shape, turning

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int size = state.getValue(SIZE);
		double w = KettleGeometry.chimneyHalfWidth(size) + 0.5D;
		double top = 16.0D; // (the top piece's cap stands in the block above, like the kettle's own)
		double cx = 8.0D;
		double cz = 8.0D;
		if (size == 2) {
			// the Big Kettle's chimney stands on the corner towards the kettle's centre
			BlockPos toward = KettlePart.offset(state.getValue(FACING), 1, 0, 1);
			cx = toward.getX() > 0 ? 16.0D : 0.0D;
			cz = toward.getZ() > 0 ? 16.0D : 0.0D;
		}
		double x0 = Math.max(0, cx - w), x1 = Math.min(16, cx + w), z0 = Math.max(0, cz - w), z1 = Math.min(16, cz + w);
		return x1 > x0 && z1 > z0 ? Block.box(x0, 0, z0, x1, top, z1) : Shapes.empty();
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
	}
}
