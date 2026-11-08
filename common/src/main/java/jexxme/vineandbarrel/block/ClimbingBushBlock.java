package jexxme.vineandbarrel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootTable;
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
 */
public class ClimbingBushBlock extends ClimbingCropBlock
{
	public static final EnumProperty<BushSupport> SUPPORT = EnumProperty.create("support", BushSupport.class);
	private static final VoxelShape SHAPE_SAPLING = Block.column(10.0, 0.0, 8.0);
	private static final VoxelShape SHAPE_GROWING = Block.column(14.0, 0.0, 16.0);

	private final Supplier<? extends Block> ropeVine;
	private final Supplier<? extends Block> trellisVine;

	public ClimbingBushBlock(Properties properties, Supplier<? extends Block> ropeVine, Supplier<? extends Block> trellisVine,
							 Supplier<? extends Item> produce, ResourceKey<LootTable> harvestLoot) {
		super(properties, produce, harvestLoot);
		this.ropeVine = ropeVine;
		this.trellisVine = trellisVine;
		registerDefaultState(defaultBlockState().setValue(SUPPORT, BushSupport.NONE));
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

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState above = context.getLevel().getBlockState(context.getClickedPos().above());
		return defaultBlockState().setValue(SUPPORT, BushSupport.of(this, above));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
									 BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		BlockState updated = super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
		if (directionToNeighbour == Direction.UP && updated.is(this)) {
			return updated.setValue(SUPPORT, BushSupport.of(this, neighbourState));
		}
		return updated;
	}

	/** A quarter turn swaps the trellis axis the bush is drawn under (structure templates, like the trellis itself); a mirror keeps it. */
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
			return switch (state.getValue(SUPPORT)) {
				case TRELLIS_X -> state.setValue(SUPPORT, BushSupport.TRELLIS_Z);
				case TRELLIS_Z -> state.setValue(SUPPORT, BushSupport.TRELLIS_X);
				default -> state;
			};
		}
		return state;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(AGE)) {
			case 0 -> SHAPE_SAPLING;
			case MAX_AGE -> Shapes.block();
			default -> SHAPE_GROWING;
		};
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(SUPPORT);
	}
}
