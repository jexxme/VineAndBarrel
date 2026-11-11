package jexxme.vineandbarrel.kettle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.WeatheringCopper;
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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import jexxme.vineandbarrel.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The Brew Kettle: a sibling of Farmer's Delight's cooking pot (MIT, adapted from {@code CookingPotBlock}) with a copper
 * body. Support states (K1), lid open while its screen is open or a finished brew waits inside (K2), container click (K5), comparator (K7), and copper
 * waxing / scraping (K8). This class is the waxed kettle; {@link WeatheringBrewKettleBlock} adds weathering over time.
 */
public class BrewKettleBlock extends Block implements SimpleWaterloggedBlock, EntityBlock
{
	public static final int MAX_FILL = 4;
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<KettleSupport> SUPPORT = EnumProperty.create("support", KettleSupport.class);
	/** The lid is up while someone has the screen open (K2). */
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	/**
	 * How full the waiting brew shows, 0 (no brew, or brewing) to 4 (a full kettle). Above 0 the lid stays up and the
	 * brew is visible inside, in its colour. Set by the block entity from the servings.
	 */
	public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, MAX_FILL);
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	/** Extension blocks stand on the chimney (copper ingots, WP4): the kettle's own chimney ends at its top, capless. */
	public static final BooleanProperty EXTENDED = BooleanProperty.create("extended");

	/** Body plus the stepped lid and knob (the side handles stay outside the outline, like FD's pot handles). */
	protected static final VoxelShape SHAPE = Shapes.or(Block.box(2.0D, 0.0D, 2.0D, 14.0D, 10.0D, 14.0D),
		Block.box(3.0D, 10.0D, 3.0D, 13.0D, 12.0D, 13.0D), Block.box(5.0D, 12.0D, 5.0D, 11.0D, 14.0D, 11.0D));
	protected static final VoxelShape SHAPE_WITH_TRAY = Shapes.or(SHAPE, Block.box(0.0D, -1.0D, 0.0D, 16.0D, 0.0D, 16.0D));

	private final WeatherState weatherState;

	public BrewKettleBlock(WeatherState weatherState, BlockBehaviour.Properties properties) {
		super(properties);
		this.weatherState = weatherState;
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SUPPORT, KettleSupport.NONE)
			.setValue(OPEN, false).setValue(FILL, 0).setValue(WATERLOGGED, false).setValue(EXTENDED, false));
	}

	public WeatherState getWeatherState() {
		return weatherState;
	}

	public boolean isWaxed() {
		return true;
	}

	// ------------------------------------------------------------------ interaction

	@Override
	protected InteractionResult useItemOn(ItemStack heldStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		// K1: sneak + empty hand hangs the kettle from its handle (or puts it back on its feet), exactly like FD's pot
		if (heldStack.isEmpty() && player.isShiftKeyDown() && !state.getValue(EXTENDED)) {
			level.setBlockAndUpdate(pos, state.setValue(SUPPORT, state.getValue(SUPPORT) == KettleSupport.HANDLE ? trayState(level, pos) : KettleSupport.HANDLE));
			level.playSound(null, pos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
			return InteractionResult.SUCCESS;
		}
		return useKettle(heldStack, state, level, pos, player, hand);
	}

	/**
	 * What a held item does to a kettle, single or big (any of its blocks): K8 honeycomb waxes and an axe scrapes the whole
	 * kettle like vanilla copper (contents stay); K5 an empty bottle or bucket fills straight from the brew, like a
	 * cauldron (the client predicts from its synced copy). Anything else falls through to the screen.
	 */
	static InteractionResult useKettle(ItemStack heldStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
		KettleMultiblock.Kettle kettle = KettleMultiblock.kettleOf(state, pos);
		if (kettle == null) {
			return InteractionResult.PASS;
		}
		Block look = KettleRegistry.singleOf(state.getBlock());
		// a copper ingot grows the chimney a block (to the roof, at most 8)
		if (heldStack.is(net.minecraft.world.item.Items.COPPER_INGOT)) {
			if (!KettleMultiblock.extendChimney(level, kettle, player)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				heldStack.consume(1, player);
			}
			return InteractionResult.SUCCESS;
		}
		if (heldStack.getItem() instanceof HoneycombItem) {
			Block waxed = KettleRegistry.waxedOf(look);
			if (waxed != null) {
				if (!level.isClientSide()) {
					heldStack.consume(1, player);
					changeCopper(level, kettle, waxed, player, LevelEvent.PARTICLES_AND_SOUND_WAX_ON);
				}
				return InteractionResult.SUCCESS;
			}
		}
		if (isAxe(heldStack)) {
			Block scraped = KettleRegistry.scrapedOf(look);
			if (scraped != null) {
				if (!level.isClientSide()) {
					boolean waxOff = look instanceof BrewKettleBlock kettleBlock && kettleBlock.isWaxed();
					heldStack.hurtAndBreak(1, player, hand.asEquipmentSlot());
					changeCopper(level, kettle, scraped, player, waxOff ? LevelEvent.PARTICLES_WAX_OFF : LevelEvent.PARTICLES_SCRAPE);
					level.playSound(null, pos, waxOff ? SoundEvents.AXE_WAX_OFF : SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1.0F, 1.0F);
				}
				return InteractionResult.SUCCESS;
			}
		}
		if (level.getBlockEntity(kettle.controller()) instanceof BrewKettleBlockEntity be && be.canServe(heldStack)) {
			if (!level.isClientSide()) {
				ItemStack filled = be.drawServing(heldStack);
				player.setItemInHand(hand, ItemUtils.createFilledResult(heldStack, player, filled.copy()));
				be.awardServing(player, filled, filled.getCount());
				level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
			}
			return InteractionResult.SUCCESS;
		}
		// No brew or not a container for it: the empty-hand use opens the screen
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return openScreen(level, pos, player);
	}

	/** Opens the kettle's screen from any of its blocks (the controller's block entity holds the brew). */
	static InteractionResult openScreen(Level level, BlockPos pos, Player player) {
		BrewKettleBlockEntity kettle = KettleMultiblock.controller(level, pos);
		if (player instanceof ServerPlayer serverPlayer && kettle != null) {
			Services.PLATFORM.openBlockMenu(serverPlayer, kettle, kettle.getBlockPos());
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean isAxe(ItemStack stack) {
		return Services.PLATFORM.canScrape(stack);
	}

	/** One step back with an axe: wax off first, otherwise one oxidation stage, or null if there is nothing to scrape. */
	@Nullable
	public BlockState scrapedState(BlockState state) {
		Block scraped = KettleRegistry.scrapedOf(this);
		return scraped == null ? null : scraped.withPropertiesOf(state);
	}

	/** Waxing and scraping: the whole kettle changes copper, with the particles on every block. */
	private static void changeCopper(Level level, KettleMultiblock.Kettle kettle, Block single, @Nullable Player player, int levelEvent) {
		KettleMultiblock.restage(level, kettle, single);
		for (BlockPos at : kettle.positions()) {
			// 26.2's wax event plays its sound too: once, the other blocks get the particles alone
			if (levelEvent == LevelEvent.PARTICLES_AND_SOUND_WAX_ON && !at.equals(kettle.controller())) {
				if (level instanceof ServerLevel server) {
					server.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 4, 0.4, 0.4, 0.4, 0.0);
				}
			} else {
				level.levelEvent(player, levelEvent, at, 0);
			}
			level.gameEvent(GameEvent.BLOCK_CHANGE, at, GameEvent.Context.of(player, level.getBlockState(at)));
		}
	}

	// ------------------------------------------------------------------ growing (WP4): a placed kettle joins the kettles around it

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.@Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		// placing kettles against kettles needs sneaking (else their screen opens), so a cube always joins. A tick later:
		// the placing is final then (a protection mod may still undo it in this one)
		if (level instanceof ServerLevel server && placer instanceof Player) {
			server.scheduleTick(pos, this, 1);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		KettleMultiblock.tryJoin(level, pos);
	}

	/** Weathering (or lightning, or another mod) changed this kettle's copper: its chimney pieces follow. */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide() && !KettleMultiblock.isConverting() && oldState.getBlock() instanceof BrewKettleBlock && oldState.getBlock() != this
			&& state.getValue(EXTENDED)) {
			KettleMultiblock.Kettle kettle = KettleMultiblock.kettleOf(state, pos);
			if (kettle != null) {
				KettleMultiblock.restage(level, kettle, this);
			}
		}
	}

	/** Weathering, waxing and scraping swap the block for another kettle: keep the block entity (contents) like vanilla's copper chest. */
	@Override
	protected boolean shouldChangedStateKeepBlockEntity(BlockState oldState) {
		return oldState.getBlock() instanceof BrewKettleBlock;
	}

	// ------------------------------------------------------------------ support states (K1), exactly FD's rules

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		BlockState state = this.defaultBlockState()
			.setValue(FACING, context.getHorizontalDirection().getOpposite())
			.setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER)
			.setValue(FILL, BrewKettleBlockEntity.fillLevel(context.getItemInHand()
				.getOrDefault(KettleRegistry.KETTLE_CONTENTS.get(), KettleContents.EMPTY).servings()));
		if (context.getClickedFace() == Direction.DOWN) {
			return state.setValue(SUPPORT, KettleSupport.HANDLE);
		}
		return state.setValue(SUPPORT, trayState(level, pos));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		if (direction.getAxis() == Direction.Axis.Y && state.getValue(SUPPORT) != KettleSupport.HANDLE) {
			return state.setValue(SUPPORT, trayState(level, pos));
		}
		return state;
	}

	public static KettleSupport trayState(LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).is(KettleTags.TRAY_HEAT_SOURCES) ? KettleSupport.TRAY : KettleSupport.NONE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(SUPPORT) == KettleSupport.TRAY ? SHAPE_WITH_TRAY : SHAPE;
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
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING, SUPPORT, OPEN, FILL, WATERLOGGED, EXTENDED);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	// ------------------------------------------------------------------ item, comparator (K7), block entity

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		if (includeData && level.getBlockEntity(pos) instanceof BrewKettleBlockEntity kettle) {
			return kettle.getAsItem();
		}
		return super.getCloneItemStack(level, pos, state, includeData);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		// Contents are dropped by BrewKettleBlockEntity#preRemoveSideEffects (26.x: the block entity is already gone here)
		level.updateNeighbourForOutputSignal(pos, this);
		// a chimney standing on it through a roof falls with it
		KettleChimneyBlock.checkPiecesAbove(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof BrewKettleBlockEntity kettle ? kettle.comparatorSignal() : 0;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BrewKettleBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type != KettleRegistry.BREW_KETTLE_BE.get()) {
			return null;
		}
		return level.isClientSide()
			? (l, p, s, be) -> BrewKettleBlockEntity.clientTick(l, p, s, (BrewKettleBlockEntity) be)
			: (l, p, s, be) -> BrewKettleBlockEntity.serverTick((ServerLevel) l, p, s, (BrewKettleBlockEntity) be);
	}
}
