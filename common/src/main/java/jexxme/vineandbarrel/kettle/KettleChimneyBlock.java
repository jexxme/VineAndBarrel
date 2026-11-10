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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
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

import java.util.List;

/**
 * One block of a kettle's extended chimney (WP4: right-click a kettle with a copper ingot and its chimney grows a block,
 * up to {@link #MAX}). A technical block like a piston head: no item, not in the creative tab; it drops the copper ingot
 * it was made from and takes the kettle's copper stage. It stands on the kettle's chimney or on another chimney block;
 * without that it breaks (and everything above it, like sugar cane).
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
	/** The piece goes through a roof: a copper flashing plate lies on the roof around it and seals the hole. */
	public static final BooleanProperty FLASHING = BooleanProperty.create("flashing");

	private final WeatherState weatherState;
	private final boolean waxed;

	public KettleChimneyBlock(WeatherState weatherState, boolean waxed, BlockBehaviour.Properties properties) {
		super(properties);
		this.weatherState = weatherState;
		this.waxed = waxed;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SIZE, 1).setValue(TOP, true).setValue(FLASHING, false));
	}

	public WeatherState getWeatherState() {
		return weatherState;
	}

	public boolean isWaxed() {
		return waxed;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SIZE, TOP, FLASHING);
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
	public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
		net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(Items.COPPER_INGOT)) {
			return net.minecraft.world.InteractionResult.PASS;
		}
		BlockPos below = pos.below();
		for (int i = 0; i < MAX && level.getBlockState(below).getBlock() instanceof KettleChimneyBlock; i++) {
			below = below.below();
		}
		KettleMultiblock.Kettle kettle = KettleMultiblock.kettleAt(level, below);
		if (kettle == null || !KettleMultiblock.extendChimney(level, kettle, player)) {
			return net.minecraft.world.InteractionResult.PASS;
		}
		if (!level.isClientSide() && !player.getAbilities().instabuild) {
			held.shrink(1);
		}
		return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
	}

	// ------------------------------------------------------------------ standing on the kettle

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		if (below.getBlock() instanceof KettleChimneyBlock) {
			return below.getValue(SIZE).equals(state.getValue(SIZE)) && below.getValue(FACING) == state.getValue(FACING);
		}
		return KettleMultiblock.chimneyOf(below) == state.getValue(SIZE) && KettleMultiblock.isExtended(below);
	}

	/** Lost its footing (the kettle or the piece below went): breaks on the next tick, dropping its ingot. */
	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
			level.scheduleTick(pos, this, 1);
		}
		if (direction == Direction.UP) {
			// the top piece carries the cap: the piece under a removed top becomes the new top
			boolean top = !(neighbourState.getBlock() instanceof KettleChimneyBlock);
			if (state.getValue(TOP) != top) {
				return state.setValue(TOP, top);
			}
		}
		return state;
	}

	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			level.destroyBlock(pos, true);
		}
	}

	/** The last piece taken off: the kettle's chimney gets its own cap back. */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!KettleMultiblock.isConverting()) {
			BlockPos below = pos.below();
			BlockState kettle = level.getBlockState(below);
			if (KettleMultiblock.isExtended(kettle) && !(newState.getBlock() instanceof KettleChimneyBlock)) {
				level.setBlock(below, KettleMultiblock.withExtended(kettle, false), Block.UPDATE_ALL);
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return List.of(new ItemStack(Items.COPPER_INGOT));
	}

	@Override
	public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
		return new ItemStack(Items.COPPER_INGOT);
	}

	// ------------------------------------------------------------------ shape, turning

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
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
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
	}
}
