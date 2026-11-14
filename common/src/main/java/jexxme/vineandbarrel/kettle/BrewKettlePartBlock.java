package jexxme.vineandbarrel.kettle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One block of a big Brew Kettle (see {@link KettleMultiblock}). It drops, picks and reads as a plain kettle of its
 * stage; the controller part (local 0, 0, 0) holds the block entity, every other part asks it. This class is the waxed
 * part; {@link WeatheringBrewKettlePartBlock} weathers, for the whole kettle at once.
 */
public class BrewKettlePartBlock extends Block implements EntityBlock
{
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final IntegerProperty PART = IntegerProperty.create("part", 0, KettlePart.count() - 1);
	/** How full the sight glass shows the kettle, 0 to {@link #MAX_LEVEL} (only the glass block draws it). */
	public static final int MAX_LEVEL = 4;
	public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, MAX_LEVEL);
	/** Extension blocks stand on the chimney (only the chimney block draws it). */
	public static final BooleanProperty EXTENDED = BrewKettleBlock.EXTENDED;
	/** A bottom block standing over a campfire, fire or lava shows the tray, like a single kettle. */
	public static final BooleanProperty TRAY = BooleanProperty.create("tray");

	private static final VoxelShape[][] SHAPES = new VoxelShape[KettlePart.count()][4];
	/** The tray under a bottom block over a fire, as a single kettle's: solid, so nobody steps up onto the fire's rim. */
	private static final VoxelShape TRAY_PLATE = Block.box(0.0D, -1.0D, 0.0D, 16.0D, 0.0D, 16.0D);
	private static final VoxelShape[][] SHAPES_WITH_TRAY = new VoxelShape[KettlePart.count()][4];

	private final WeatherState weatherState;

	public BrewKettlePartBlock(WeatherState weatherState, BlockBehaviour.Properties properties) {
		super(properties);
		this.weatherState = weatherState;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0).setValue(TRAY, false).setValue(LEVEL, 0)
			.setValue(EXTENDED, false));
	}

	public static KettlePart part(BlockState state) {
		return KettlePart.byId(state.getValue(PART));
	}

	public WeatherState getWeatherState() {
		return weatherState;
	}

	public boolean isWaxed() {
		return true;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART, TRAY, LEVEL, EXTENDED);
	}

	/** Reads as the kettle it was placed as (Jade, the debug screen's block name). */
	@Override
	public MutableComponent getName() {
		return KettleRegistry.singleOf(this).getName();
	}

	// ------------------------------------------------------------------ use: everything a single kettle does, for the whole kettle

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		return BrewKettleBlock.useKettle(player.getItemInHand(hand), state, level, pos, player, hand);
	}

	// ------------------------------------------------------------------ breaking: falls apart into single kettles

	@Override
	public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server) {
			KettleMultiblock.Kettle kettle = KettleMultiblock.kettleOf(state, pos);
			BrewKettleBlockEntity controller = KettleMultiblock.controller(level, pos);
			if (kettle != null && controller != null && KettleMultiblock.isIntact(level, kettle)) {
				KettleMultiblock.split(server, kettle, controller, state.getBlock(), pos, !player.getAbilities().instabuild);
			}
		}
		super.playerWillDestroy(level, pos, state, player);
	}

	/**
	 * Anything else that removes a part (an explosion, a command): the rest falls apart, the brew stays with them. The
	 * controller's block entity sees to it itself ({@link BrewKettleBlockEntity#preRemoveSideEffects}, while it is still
	 * there). Only a change of copper keeps the block entity (same part, another stage: vanilla's removal of it is
	 * skipped); joining and splitting move the brew themselves.
	 */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (state.is(newState.getBlock())) {
			return;
		}
		boolean keepsBlockEntity = newState.getBlock() instanceof BrewKettlePartBlock;
		if (!keepsBlockEntity && level.getBlockEntity(pos) instanceof BrewKettleBlockEntity controller) {
			controller.preRemoveSideEffects(pos, state);
		}
		boolean sameplace = keepsBlockEntity && newState.getValue(PART).equals(state.getValue(PART));
		// (a change of copper on this block, e.g. vanilla's honeycomb or lightning: onPlace turns the whole kettle)
		if (level instanceof ServerLevel server && !KettleMultiblock.isConverting() && !sameplace && !part(state).isController()) {
			KettleMultiblock.Kettle kettle = KettleMultiblock.kettleOf(state, pos);
			if (kettle != null && KettleMultiblock.restIntact(level, kettle, pos)
				&& level.getBlockEntity(kettle.controller()) instanceof BrewKettleBlockEntity controller) {
				KettleMultiblock.split(server, kettle, controller, state.getBlock(), pos, false);
			}
		}
		if (part(state).isChimney()) {
			// a chimney standing on it through a roof falls with it
			KettleChimneyBlock.checkPiecesAbove(level, pos);
		}
		if (!keepsBlockEntity) {
			super.onRemove(state, level, pos, newState, movedByPiston);
		}
	}

	/** A broken part drops as a plain kettle of its stage, carrying its share of the brew. */
	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		ItemStack kettle = new ItemStack(KettleRegistry.singleOf(this).asItem());
		net.minecraft.world.phys.Vec3 origin = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
		if (origin != null) {
			KettleContents share = KettleMultiblock.takePendingDrop(params.getLevel(), BlockPos.containing(origin));
			if (share != null) {
				BrewKettleItem.setContents(kettle, share);
			}
		}
		return List.of(kettle);
	}

	@Override
	public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
		return new ItemStack(KettleRegistry.singleOf(this).asItem());
	}

	// ------------------------------------------------------------------ copper: the whole kettle changes together

	/** Lightning, a command or another mod changed one block's copper: the rest of the kettle follows. */
	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide() && !KettleMultiblock.isConverting() && oldState.getBlock() instanceof BrewKettlePartBlock && oldState.getBlock() != this
			&& oldState.getValue(PART).equals(state.getValue(PART))) {
			KettleMultiblock.Kettle kettle = KettleMultiblock.kettleOf(state, pos);
			if (kettle != null) {
				KettleMultiblock.restage(level, kettle, KettleRegistry.singleOf(this));
			}
		}
	}

	// ------------------------------------------------------------------ shape, trays, turning

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		if (direction == Direction.DOWN && part(state).isBottom()) {
			return state.setValue(TRAY, BrewKettleBlock.trayState(level, pos) == KettleSupport.TRAY);
		}
		return state;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int id = state.getValue(PART);
		int facing = state.getValue(FACING).get2DDataValue();
		VoxelShape shape = SHAPES[id][facing];
		if (shape == null) {
			shape = SHAPES[id][facing] = computeShape(KettlePart.byId(id), state.getValue(FACING));
		}
		return shape;
	}

	/**
	 * A bottom block over a campfire, fire or lava stands on its tray, solid as a single kettle's: the kettle's body is
	 * 2 px in from its edges, and over the fire's rim beside it a villager pushed into the kettle (or a player walking
	 * into it) stepped up onto the lit campfire and burned.
	 */
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (!state.getValue(TRAY)) {
			return getShape(state, level, pos, context);
		}
		int id = state.getValue(PART);
		int facing = state.getValue(FACING).get2DDataValue();
		VoxelShape shape = SHAPES_WITH_TRAY[id][facing];
		if (shape == null) {
			shape = SHAPES_WITH_TRAY[id][facing] = Shapes.or(getShape(state, level, pos, context), TRAY_PLATE);
		}
		return shape;
	}

	/** The body (2 px in from the kettle's edges, up to the dome's top) cut to this block. */
	private static VoxelShape computeShape(KettlePart part, Direction facing) {
		KettleShape shape = part.shape();
		BlockPos at = KettlePart.offset(facing, part.x(), part.y(), part.z());
		BlockPos far = KettlePart.offset(facing, shape.size() - 1, 0, shape.size() - 1);
		// the kettle's box relative to this block, in px
		double minX = 16.0D * (Math.min(0, far.getX()) - at.getX()) + 2.0D;
		double minZ = 16.0D * (Math.min(0, far.getZ()) - at.getZ()) + 2.0D;
		double maxX = minX + 16.0D * shape.size() - 4.0D;
		double maxZ = minZ + 16.0D * shape.size() - 4.0D;
		double minY = -16.0D * part.y();
		double maxY = minY + KettleGeometry.hoodTop(shape);
		double x0 = Math.max(0, minX), x1 = Math.min(16, maxX), z0 = Math.max(0, minZ), z1 = Math.min(16, maxZ);
		double y0 = Math.max(0, minY), y1 = Math.min(16, maxY);
		if (x1 <= x0 || z1 <= z0 || y1 <= y0) {
			return Shapes.empty();
		}
		return Block.box(x0, y0, z0, x1, y1, z1);
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
		if (mirror == Mirror.NONE) {
			return state;
		}
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(PART, part(state).mirrored().id());
	}

	// ------------------------------------------------------------------ comparator, block entity

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		BrewKettleBlockEntity kettle = KettleMultiblock.controller(level, pos);
		return kettle != null ? kettle.comparatorSignal() : 0;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return part(state).isController() ? new BrewKettleBlockEntity(pos, state) : null;
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type != KettleRegistry.BREW_KETTLE_BE.get() || !part(state).isController()) {
			return null;
		}
		return level.isClientSide()
			? (l, p, s, be) -> BrewKettleBlockEntity.clientTick(l, p, s, (BrewKettleBlockEntity) be)
			: (l, p, s, be) -> BrewKettleBlockEntity.serverTick((ServerLevel) l, p, s, (BrewKettleBlockEntity) be);
	}
}
