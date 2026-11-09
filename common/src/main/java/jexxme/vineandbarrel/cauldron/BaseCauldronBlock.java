package jexxme.vineandbarrel.cauldron;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * A cauldron holding one of our bases, like vanilla's water cauldron: filled and emptied with the base's bucket and
 * bottles ({@link VBCauldrons}), puts out burning mobs, read by a comparator, and a plain cauldron again when broken or
 * emptied. Unlike water it has four levels, because our bucket holds four servings everywhere (kettle, barrel), so
 * nothing is lost or gained on the way through. No rain, no dripstone: nothing fills it but its own base.
 */
public class BaseCauldronBlock extends AbstractCauldronBlock
{
	public static final int MIN_LEVEL = 1;
	/** A bucket's four servings. */
	public static final int MAX_LEVEL = 4;
	public static final IntegerProperty LEVEL = IntegerProperty.create("level", MIN_LEVEL, MAX_LEVEL);
	/** The vanilla cauldron's floor and its 3 px per level, one level lower: 6, 9, 12, 15 px, so full sits where full water does. */
	private static final VoxelShape[] FILLED_SHAPES = Util.make(() -> Block.boxes(MAX_LEVEL - 1,
		level -> Shapes.or(AbstractCauldronBlock.SHAPE, Block.column(12.0, 4.0, contentPixels(level + 1)))));

	private final Supplier<? extends Item> bottle;
	private final Supplier<? extends Item> bucket;

	public BaseCauldronBlock(BlockBehaviour.Properties properties, Supplier<? extends Item> bottle, Supplier<? extends Item> bucket) {
		super(properties, new CauldronInteraction.Dispatcher());
		this.bottle = bottle;
		this.bucket = bucket;
		registerDefaultState(stateDefinition.any().setValue(LEVEL, MIN_LEVEL));
	}

	/** What a click with an item does to this cauldron ({@link VBCauldrons} fills it). */
	public CauldronInteraction.Dispatcher interactions() {
		return interactions;
	}

	public Item bottle() {
		return bottle.get();
	}

	public Item bucket() {
		return bucket.get();
	}

	private static double contentPixels(int level) {
		return 3.0 + 3.0 * level;
	}

	@Override
	public boolean isFull(BlockState state) {
		return state.getValue(LEVEL) == MAX_LEVEL;
	}

	@Override
	protected double getContentHeight(BlockState state) {
		return contentPixels(state.getValue(LEVEL)) / 16.0;
	}

	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
		return FILLED_SHAPES[state.getValue(LEVEL) - 1];
	}

	/** A burning mob in it is put out and uses up a level, as in water. */
	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (level instanceof ServerLevel serverLevel) {
			BlockPos at = pos.immutable();
			effectApplier.runBefore(InsideBlockEffectType.EXTINGUISH, burning -> {
				if (burning.isOnFire() && burning.mayInteract(serverLevel, at)) {
					lowerFillLevel(state, level, at);
				}
			});
		}
		effectApplier.apply(InsideBlockEffectType.EXTINGUISH);
	}

	/** One level less; the last one leaves an empty cauldron. */
	public static void lowerFillLevel(BlockState state, Level level, BlockPos pos) {
		int lower = state.getValue(LEVEL) - 1;
		BlockState lowered = lower == 0 ? Blocks.CAULDRON.defaultBlockState() : state.setValue(LEVEL, lower);
		level.setBlockAndUpdate(pos, lowered);
		level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(lowered));
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return state.getValue(LEVEL);
	}

	/** Like the water cauldron, it is picked as a cauldron. */
	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(Items.CAULDRON);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LEVEL);
	}
}
