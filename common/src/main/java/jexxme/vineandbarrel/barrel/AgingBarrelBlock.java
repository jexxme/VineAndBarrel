package jexxme.vineandbarrel.barrel;

import jexxme.vineandbarrel.advancement.VBAdvancements;
import jexxme.vineandbarrel.drink.DrinkColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Aging Barrel: a cask lying on a wooden cradle, iron hoops, a brass tap on the end (DESIGN §9). No screen; every
 * interaction happens in the world (§13.1):
 * <ul>
 * <li>B1 placed with the tap toward you; B2 barrels side by side with the same facing join their cradles into one rack;
 * B3 a barrel on a barrel rests on chocks instead of a cradle;</li>
 * <li>B4 the bung shows the stage; B5 the airlock bubbles while fermenting; B6 the tap drips; B7 the aged pop;</li>
 * <li>B8 pour in / take back out; B9 tap with the right vessel (or get a hint); B10/B15 the status line;</li>
 * <li>B11 comparator; B12 keeps its batch as an item; B13 dispensers; B14 hoppers; B16 wood.</li>
 * </ul>
 */
public class AgingBarrelBlock extends Block implements EntityBlock
{
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<BungState> BUNG = EnumProperty.create("bung", BungState.class);
	/** A barrel with the same facing on the left (seen from the tap): the cradle beams run through (B2). */
	public static final BooleanProperty LEFT = BooleanProperty.create("left");
	public static final BooleanProperty RIGHT = BooleanProperty.create("right");
	/** Resting on a barrel below: chocks instead of a cradle (B3). */
	public static final BooleanProperty STACKED = BooleanProperty.create("stacked");
	/** The tap handle is turned for a moment after tapping (B9). */
	public static final BooleanProperty TAPPING = BooleanProperty.create("tapping");
	/** Servings in the barrel, shown by the sight glass on the end (0-8). */
	public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, AgingBarrelBlockEntity.CAPACITY);

	/** The cask lying along z (tap on the north end), then the cradle, then the chocks; mirrored to x for east/west. */
	private static final VoxelShape CASK_Z = Shapes.or(Block.box(4, 2, 1, 12, 15, 15), Block.box(2, 3, 1, 14, 14, 15), Block.box(1, 5, 1, 15, 12, 15));
	private static final VoxelShape CASK_X = Shapes.or(Block.box(1, 2, 4, 15, 15, 12), Block.box(1, 3, 2, 15, 14, 14), Block.box(1, 5, 1, 15, 12, 15));
	private static final VoxelShape CRADLE_Z = Shapes.or(Block.box(1, 0, 2, 15, 3, 4), Block.box(1, 0, 12, 15, 3, 14));
	private static final VoxelShape CRADLE_X = Shapes.or(Block.box(2, 0, 1, 4, 3, 15), Block.box(12, 0, 1, 14, 3, 15));
	private static final VoxelShape CHOCKS = Shapes.or(Block.box(4, 0, 4, 5.5, 2.5, 5.5), Block.box(10.5, 0, 4, 12, 2.5, 5.5),
		Block.box(4, 0, 10.5, 5.5, 2.5, 12), Block.box(10.5, 0, 10.5, 12, 2.5, 12));
	private static final VoxelShape SHAPE_Z = Shapes.or(CASK_Z, CRADLE_Z);
	private static final VoxelShape SHAPE_X = Shapes.or(CASK_X, CRADLE_X);
	private static final VoxelShape STACKED_Z = Shapes.or(CASK_Z, CHOCKS);
	private static final VoxelShape STACKED_X = Shapes.or(CASK_X, CHOCKS);

	public AgingBarrelBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BUNG, BungState.CORK)
			.setValue(LEFT, false).setValue(RIGHT, false).setValue(STACKED, false).setValue(TAPPING, false).setValue(FILL, 0));
	}

	// ------------------------------------------------------------------ B1-B3: facing, racks, stacking

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withConnections(defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()), context.getLevel(), context.getClickedPos());
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		return direction == Direction.UP ? state : withConnections(state, level, pos);
	}

	/** The rack and stack shape a barrel takes from its neighbours, like fences take theirs. */
	public static BlockState withConnections(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		boolean stacked = level.getBlockState(pos.below()).getBlock() instanceof AgingBarrelBlock;
		return state.setValue(STACKED, stacked)
			.setValue(LEFT, joins(level.getBlockState(pos.relative(facing.getCounterClockWise())), facing, stacked))
			.setValue(RIGHT, joins(level.getBlockState(pos.relative(facing.getClockWise())), facing, stacked));
	}

	/** Cradles join between barrels on the ground with the same facing (stacked ones have chocks, nothing to join). */
	private static boolean joins(BlockState neighbour, Direction facing, boolean stacked) {
		return !stacked && neighbour.getBlock() instanceof AgingBarrelBlock && neighbour.getValue(FACING) == facing && !neighbour.getValue(STACKED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		boolean alongZ = state.getValue(FACING).getAxis() == Direction.Axis.Z;
		if (state.getValue(STACKED)) {
			return alongZ ? STACKED_Z : STACKED_X;
		}
		return alongZ ? SHAPE_Z : SHAPE_X;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
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
		BlockState rotated = state.rotate(mirror.getRotation(state.getValue(FACING)));
		return mirror == Mirror.NONE ? rotated : rotated.setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, BUNG, LEFT, RIGHT, STACKED, TAPPING, FILL);
	}

	// ------------------------------------------------------------------ B8-B10, B15: in-world interaction

	/** Rules shared by hand, client prediction and tests: see {@link #useFor}. */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (held.isEmpty()) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		// Not the barrel's business: straight on to the item's own use, without the status line of an empty hand
		if (!(level.getBlockEntity(pos) instanceof AgingBarrelBlockEntity barrel)) {
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
		}
		Use use = useFor(barrel, held);
		if (use == Use.NONE) {
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
		}
		if (level.isClientSide()) {
			// The client knows the batch and the aging recipes (synced), so it claims exactly the clicks the server takes
			return ItemInteractionResult.SUCCESS;
		}
		switch (use) {
			case TAP -> {
				// B9: tap with an empty vessel; the wrong one gets a hint and nothing is used up.
				// The barrel works on a copy; createFilledResult takes the vessel from the hand (not in creative)
				boolean aged = barrel.stage() == BarrelStage.AGED;
				boolean homeBrewed = barrel.isHomeBrewed();
				ItemStack drink = barrel.tap(held.copy(), player);
				if (!drink.isEmpty()) {
					if (player instanceof ServerPlayer serverPlayer) {
						VBAdvancements.TAPPED_BARREL.get().trigger(serverPlayer, pos, aged, homeBrewed);
					}
					player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, drink));
					level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
				} else {
					hint(player, refusalToTap(barrel, held));
				}
			}
			case POUR -> {
				// B8: pour a base in (fill an empty barrel, or top up the same base while it ferments)
				ItemStack container = barrel.pour(held.copy(), player);
				if (container != null) {
					if (container.isEmpty()) {
						held.consume(1, player);
					} else {
						player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, container));
					}
					level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
				}
			}
			case REFUSE_POUR -> hint(player, BarrelStatus.refusal(barrel, barrel.pourRefusal(held)));
			case UNDO -> {
				// B8: until it is a drink, an empty bucket or bottle takes the base back out
				ItemStack base = barrel.takeBack(held.copy(), player);
				if (!base.isEmpty()) {
					player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, base));
					level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
				} else {
					hint(player, BarrelStatus.bucketNeedsFour());
				}
			}
			case WRONG_CONTAINER -> hint(player, refusalToTap(barrel, held));
			default -> {
			}
		}
		// The client swings the arm itself (it returned SUCCESS above)
		return ItemInteractionResult.CONSUME;
	}

	/** What a held item does at this barrel. The same answer on client and server, so neither claims a click alone. */
	enum Use
	{
		/** Not the barrel's business: the item's own use runs (drinking, placing a block, a water bucket ...). */
		NONE,
		TAP,
		POUR,
		/** A base the barrel won't take now (another base, full, a drink already): a hint, nothing used. */
		REFUSE_POUR,
		UNDO,
		/** An empty bucket or bottle at a barrel of drink: a hint that it is served in a mug or glass. */
		WRONG_CONTAINER
	}

	static Use useFor(AgingBarrelBlockEntity barrel, ItemStack held) {
		if (held.is(BarrelRegistry.DRINK_VESSELS)) {
			return Use.TAP;
		}
		AgingBarrelBlockEntity.Refusal refusal = barrel.pourRefusal(held);
		if (refusal == AgingBarrelBlockEntity.Refusal.NONE) {
			return Use.POUR;
		}
		if (refusal != AgingBarrelBlockEntity.Refusal.NOT_A_BASE) {
			return Use.REFUSE_POUR;
		}
		if (barrel.undoServings(held) > 0) {
			return Use.UNDO;
		}
		return isEmptyContainer(held) && barrel.stage().isDrink() ? Use.WRONG_CONTAINER : Use.NONE;
	}

	private static boolean isEmptyContainer(ItemStack stack) {
		return stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE);
	}

	private static Component refusalToTap(AgingBarrelBlockEntity barrel, ItemStack held) {
		return switch (barrel.stage()) {
			case EMPTY -> Component.translatable("message.vineandbarrel.aging_barrel.nothing_to_tap");
			case FERMENTING -> BarrelStatus.stillFermenting(barrel);
			default -> {
				Item needed = barrel.vesselNeeded();
				yield BarrelStatus.wrongVessel(barrel.content(), needed != null ? new ItemStack(needed) : held);
			}
		};
	}

	private static void hint(Player player, Component message) {
		player.displayClientMessage(message, true);
	}

	/** B10: the status line on an empty-hand right-click. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AgingBarrelBlockEntity barrel) {
			hint(player, BarrelStatus.of(barrel));
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	/** B9: the tap handle turns back after tapping. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(TAPPING)) {
			level.setBlock(pos, state.setValue(TAPPING, false), Block.UPDATE_ALL);
		}
	}

	// ------------------------------------------------------------------ B11 comparator, B12 item, block entity

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof AgingBarrelBlockEntity barrel ? barrel.comparatorSignal() : 0;
	}

	// Pick block with Ctrl (creative): 1.21.1's client itself adds the block entity's components (the batch, the name)
	// to the plain barrel item, so there is no getCloneItemStack here (26.x asks the block for the item with its data)

	/** The automation slots drop when the barrel goes (the batch stays in the barrel item, B12), and comparators look again. */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			if (level.getBlockEntity(pos) instanceof AgingBarrelBlockEntity barrel) {
				barrel.dropInventory();
			}
			level.updateNeighbourForOutputSignal(pos, this);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AgingBarrelBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type != BarrelRegistry.AGING_BARREL_BE.get()) {
			return null;
		}
		return level.isClientSide()
			? (l, p, s, be) -> clientTick(l, p, s, (AgingBarrelBlockEntity) be)
			: (l, p, s, be) -> AgingBarrelBlockEntity.serverTick((ServerLevel) l, p, s, (AgingBarrelBlockEntity) be);
	}

	// ------------------------------------------------------------------ B5, B6: bubbles, sound and drips (client)

	private static void clientTick(Level level, BlockPos pos, BlockState state, AgingBarrelBlockEntity barrel) {
		RandomSource random = level.getRandom();
		BarrelStage stage = barrel.stage();
		if (state.getValue(BUNG) == BungState.AIRLOCK && stage == BarrelStage.FERMENTING) {
			// B5: lively at first, rarer as it finishes (like a real airlock)
			float progress = Math.min(1.0F, (float) barrel.age() / Math.max(1, barrel.contents().fermentingTime()));
			float activity = 1.0F - 0.8F * progress;
			if (random.nextFloat() < 0.12F * activity) {
				level.addParticle(BarrelRegistry.FERMENT_BUBBLE.get(), pos.getX() + 0.5 + (random.nextFloat() - 0.5) * 0.04,
					pos.getY() + 18.2 / 16.0, pos.getZ() + 0.5 + (random.nextFloat() - 0.5) * 0.04, 0.0, 0.02, 0.0);
			}
			if (random.nextFloat() < 0.012F * activity) {
				level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, BarrelRegistry.BARREL_FERMENT.get(), SoundSource.BLOCKS,
					0.35F, 0.9F + random.nextFloat() * 0.25F, false);
			}
		}
		// B6: a drop from the tap now and then, in the drink's colour
		if (stage.isDrink() && random.nextInt(140) == 0) {
			Direction facing = state.getValue(FACING);
			double out = 0.5 + 0.5 / 16.0;
			level.addParticle(ColorParticleOption.create(BarrelRegistry.TAP_DRIP.get(), 0xFF000000 | DrinkColors.of(barrel.content())),
				pos.getX() + 0.5 + facing.getStepX() * out, pos.getY() + 3.8 / 16.0, pos.getZ() + 0.5 + facing.getStepZ() * out, 0.0, 0.0, 0.0);
		}
	}
}
