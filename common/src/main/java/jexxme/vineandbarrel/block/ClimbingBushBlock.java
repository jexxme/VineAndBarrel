package jexxme.vineandbarrel.block;

import jexxme.vineandbarrel.platform.hooks.PlayerDestroyHook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The grape bush and the hop bush: planted from the produce like sweet berries, on any soil that supports vegetation
 * (grass, dirt, farmland, FD rich soil). Outline shapes are the sweet berry bush's. It knows both of its vines: the one
 * on an FD rope and the one on a Trellis, and it follows the block above ({@link #SUPPORT}) so that under a trellis or
 * a rope it is drawn as the base of that vine.
 * <p>
 * A trellis can stand in the bush's own block ({@link #TRELLIS}), in either order: a trellis put into the bush with a
 * right-click, or the bush planted into a trellis standing on soil ({@link TrellisBlock}). The bush is then drawn as the
 * foot of the vine on that trellis, you bump into the panel as on any trellis, and when the plant goes (broken, blown up,
 * its soil gone) the trellis stays standing. It climbs the trellises above it as any bush does.
 */
public class ClimbingBushBlock extends ClimbingCropBlock implements PlayerDestroyHook
{
	public static final EnumProperty<BushSupport> SUPPORT = EnumProperty.create("support", BushSupport.class);
	public static final EnumProperty<BushTrellis> TRELLIS = EnumProperty.create("trellis", BushTrellis.class);
	private static final VoxelShape SHAPE_SAPLING = Block.box(3.0, 0.0, 3.0, 13.0, 8.0, 13.0);
	private static final VoxelShape SHAPE_GROWING = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
	// drawn as the foot of a vine: the young shoot, then the vine's leafy planes along a trellis, fence, wall or bars, or
	// round a rope or chain (the outline follows what is drawn, as vanilla's do)
	private static final VoxelShape FOOT_SHOOT_X = Block.box(3.0, 0.0, 5.0, 13.0, 8.0, 11.0);
	private static final VoxelShape FOOT_SHOOT_Z = Block.box(5.0, 0.0, 3.0, 11.0, 8.0, 13.0);
	private static final VoxelShape FOOT_PLANES_X = Block.box(0.0, 0.0, 5.0, 16.0, 16.0, 11.0);
	private static final VoxelShape FOOT_PLANES_Z = Block.box(5.0, 0.0, 0.0, 11.0, 16.0, 16.0);
	private static final VoxelShape FOOT_WOUND = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
	/** The foot of a vine crossed through a chain: two crossed planes. */
	private static final VoxelShape FOOT_CROSS = Shapes.or(Block.box(0.0, 0.0, 7.0, 16.0, 16.0, 9.0), Block.box(7.0, 0.0, 0.0, 9.0, 16.0, 16.0));

	private final Supplier<? extends Block> ropeVine;
	private final Supplier<? extends Block> trellisVine;
	private final Supplier<? extends Block> supportVine;

	public ClimbingBushBlock(Properties properties, Supplier<? extends Block> ropeVine, Supplier<? extends Block> trellisVine,
							 Supplier<? extends Block> supportVine,
							 Supplier<? extends Item> produce, ResourceLocation harvestLoot) {
		super(properties, produce, harvestLoot);
		this.ropeVine = ropeVine;
		this.trellisVine = trellisVine;
		this.supportVine = supportVine;
		registerDefaultState(defaultBlockState().setValue(SUPPORT, BushSupport.NONE).setValue(TRELLIS, BushTrellis.NONE));
	}

	@Override
	public ClimbingBushBlock bushBlock() {
		return this;
	}

	Block ropeVineBlock() {
		return ropeVine.get();
	}

	Block trellisVineBlock() {
		return trellisVine.get();
	}

	Block supportVineBlock() {
		return supportVine.get();
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState above = context.getLevel().getBlockState(context.getClickedPos().above());
		return defaultBlockState().setValue(SUPPORT, BushSupport.of(this, above));
	}

	/** This bush, young, planted into {@code trellis} at {@code pos}: the trellis stays in it, facing the same way. */
	public BlockState plantedIn(BlockState trellis, LevelReader level, BlockPos pos) {
		return defaultBlockState().setValue(TRELLIS, BushTrellis.of(trellis.getValue(TrellisBlock.AXIS)))
				.setValue(SUPPORT, BushSupport.of(this, level.getBlockState(pos.above())));
	}

	/** The bare trellis a bush with one in it leaves behind, facing the same way. */
	public BlockState trellisState(BlockState bush) {
		Direction.Axis axis = bush.getValue(TRELLIS).axis();
		return ((VineOnTrellisBlock) trellisVineBlock()).trellisState(axis == null ? Direction.Axis.X : axis);
	}

	/**
	 * A trellis in the hand goes into the bush, facing the player, or lined up with the trellis or vine above it so the
	 * row stays straight. A fence, wall, chain or bars goes in too, connected to its neighbours: the plant then grows inside
	 * it (see {@link VineOnSupportBlock}), keeping its age. Sneaking places either beside, the vanilla way. Anything else is
	 * the bush's usual business: bone meal, picking.
	 */
	@Override
	protected @Nullable InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
										  BlockHitResult hitResult) {
		if (state.getValue(TRELLIS) == BushTrellis.NONE && stack.getItem() instanceof BlockItem item && item.getBlock() instanceof TrellisBlock) {
			BlockState above = level.getBlockState(pos.above());
			Direction.Axis axis = (above.getBlock() instanceof TrellisBlock || above.is(trellisVineBlock())) && above.hasProperty(TrellisBlock.AXIS)
					? above.getValue(TrellisBlock.AXIS) : player.getDirection().getClockWise().getAxis();
			BlockState withTrellis = state.setValue(TRELLIS, BushTrellis.of(axis));
			if (!level.isUnobstructed(withTrellis, pos, CollisionContext.of(player))) {
				return InteractionResult.FAIL; // the panel would stand in someone, as a placed block can't
			}
			if (level instanceof ServerLevel) {
				level.setBlock(pos, withTrellis, Block.UPDATE_ALL);
				SoundType sound = item.getBlock().defaultBlockState().getSoundType();
				level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
				level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, withTrellis));
				player.awardStat(Stats.ITEM_USED.get(item));
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
			}
			return InteractionResult.sidedSuccess(level.isClientSide());
		}
		if (state.getValue(TRELLIS) == BushTrellis.NONE && stack.getItem() instanceof BlockItem item
				&& VineSupports.isMakeshift(item.getBlock().defaultBlockState())) {
			BlockState support = Block.updateFromNeighbourShapes(item.getBlock().defaultBlockState(), level, pos);
			if (VineSupports.isMakeshift(support)) {
				if (!level.isUnobstructed(support, pos, CollisionContext.of(player))) {
					return InteractionResult.FAIL; // the fence or wall would stand in someone, as a placed block can't
				}
				if (level instanceof ServerLevel) {
					VineOnSupportBlock.place(level, pos, supportVineBlock().defaultBlockState().setValue(AGE, state.getValue(AGE)), support, true, false);
					SoundType sound = support.getSoundType();
					level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
					level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, level.getBlockState(pos)));
					player.awardStat(Stats.ITEM_USED.get(item));
					if (!player.getAbilities().instabuild) {
						stack.shrink(1);
					}
				}
				return InteractionResult.sidedSuccess(level.isClientSide());
			}
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
	}

	/** A player breaking a bush with a trellis in it takes the plant (its drops as usual) and leaves the trellis. */
	@Override
	public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack, boolean willHarvest, FluidState fluid) {
		BlockState left = state.getValue(TRELLIS) == BushTrellis.NONE ? fluid.createLegacyBlock() : trellisState(state);
		return level.setBlock(pos, left, level.isClientSide() ? Block.UPDATE_ALL_IMMEDIATE : Block.UPDATE_ALL);
	}

	/**
	 * When the plant was removed by anything other than a player (who is handled above), its trellis stays: put back where
	 * the plant leaves air or fire, holding the water a water source brings (a trellis is waterloggable), and dropped as an
	 * item where flowing water or lava washed the plant away.
	 */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		super.onRemove(state, level, pos, newState, movedByPiston);
		if (state.is(newState.getBlock()) || level.isClientSide() || state.getValue(TRELLIS) == BushTrellis.NONE || movedByPiston) {
			return; // onRemove also runs when only the state changes (growing, picking): the plant is still there
		}
		BlockState now = level.getBlockState(pos);
		FluidState fluid = now.getFluidState();
		if (now.isAir() || now.is(BlockTags.FIRE)) {
			level.setBlock(pos, trellisState(state), Block.UPDATE_ALL);
		} else if (now.getBlock() instanceof LiquidBlock && fluid.is(FluidTags.WATER) && fluid.isSource()) {
			level.setBlock(pos, trellisState(state).setValue(TrellisBlock.WATERLOGGED, true), Block.UPDATE_ALL);
		} else if (!fluid.isEmpty()) {
			Block.popResource(level, pos, new ItemStack(trellisState(state).getBlock()));
		}
	}

	/**
	 * A bush that can't live where it is (its soil gone, or a piston left it on stone) breaks on the next tick, dropping its
	 * fruit and leaving its trellis, instead of turning to air at once: a piston that spits a bush out places it through
	 * this and would otherwise leave nothing at all.
	 */
	@Override
	public BlockState updateShape(BlockState state, Direction directionToNeighbour, BlockState neighbourState, LevelAccessor level, BlockPos pos,
									 BlockPos neighbourPos) {
		if (!state.canSurvive(level, pos)) {
			level.scheduleTick(pos, this, 1);
			return state;
		}
		BlockState updated = super.updateShape(state, directionToNeighbour, neighbourState, level, pos, neighbourPos);
		if (directionToNeighbour == Direction.UP && updated.is(this)) {
			return updated.setValue(SUPPORT, BushSupport.of(this, neighbourState));
		}
		return updated;
	}

	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			level.destroyBlock(pos, true);
		}
	}

	/**
	 * A quarter turn swaps the trellis axis the bush is drawn under and the one of the trellis in it (structure templates,
	 * like the trellis itself); a mirror keeps them.
	 */
	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
			BlockState turned = switch (state.getValue(SUPPORT)) {
				case TRELLIS_X -> state.setValue(SUPPORT, BushSupport.TRELLIS_Z);
				case TRELLIS_Z -> state.setValue(SUPPORT, BushSupport.TRELLIS_X);
				case FACES_X -> state.setValue(SUPPORT, BushSupport.FACES_Z);
				case FACES_Z -> state.setValue(SUPPORT, BushSupport.FACES_X);
				default -> state;
			};
			return switch (turned.getValue(TRELLIS)) {
				case X -> turned.setValue(TRELLIS, BushTrellis.Z);
				case Z -> turned.setValue(TRELLIS, BushTrellis.X);
				case NONE -> turned;
			};
		}
		return state;
	}

	/**
	 * The outline follows what is drawn: the round bush on its own (the sweet berry bush's shapes), or, as the foot of a
	 * vine, its young shoot and then its leafy planes (with the panel of a trellis standing in it).
	 */
	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int age = state.getValue(AGE);
		Direction.Axis trellis = state.getValue(TRELLIS).axis();
		Direction.Axis planes = trellis != null ? trellis : switch (state.getValue(SUPPORT)) {
			case TRELLIS_X, FACES_X -> Direction.Axis.X;
			case TRELLIS_Z, FACES_Z -> Direction.Axis.Z;
			default -> null;
		};
		VoxelShape bush;
		if (planes != null) {
			bush = planes == Direction.Axis.X ? (age == 0 ? FOOT_SHOOT_X : FOOT_PLANES_X) : (age == 0 ? FOOT_SHOOT_Z : FOOT_PLANES_Z);
		} else if (state.getValue(SUPPORT) == BushSupport.ROPE) {
			bush = age == 0 ? SHAPE_SAPLING : FOOT_WOUND;
		} else if (state.getValue(SUPPORT) == BushSupport.CROSS) {
			bush = age == 0 ? SHAPE_SAPLING : FOOT_CROSS;
		} else {
			bush = switch (age) {
				case 0 -> SHAPE_SAPLING;
				case MAX_AGE -> Shapes.block();
				default -> SHAPE_GROWING;
			};
		}
		return trellis == null ? bush : Shapes.or(bush, TrellisBlock.panel(trellis));
	}

	/** With a trellis in it you bump into the panel, as on a bare trellis; the leaves let you through. */
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Direction.Axis trellis = state.getValue(TRELLIS).axis();
		return trellis == null ? super.getCollisionShape(state, level, pos, context) : TrellisBlock.panel(trellis);
	}

	/** Mobs don't plan paths through a trellis in a bush, as through a bare one. */
	@Override
	public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
		return state.getValue(TRELLIS) == BushTrellis.NONE && super.isPathfindable(state, level, pos, type);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(SUPPORT, TRELLIS);
	}
}
