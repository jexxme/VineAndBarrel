package jexxme.vineandbarrel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * The grape bush and the hop bush: planted from the produce like sweet berries, on any soil that supports vegetation
 * (grass, dirt, farmland, FD rich soil). Outline shapes are the sweet berry bush's.
 */
public class ClimbingBushBlock extends ClimbingCropBlock
{
	private static final VoxelShape SHAPE_SAPLING = Block.column(10.0, 0.0, 8.0);
	private static final VoxelShape SHAPE_GROWING = Block.column(14.0, 0.0, 16.0);

	public ClimbingBushBlock(Properties properties, Supplier<? extends Block> vine, Supplier<? extends Item> produce, ResourceKey<LootTable> harvestLoot) {
		super(properties, null, vine, produce, harvestLoot);
	}

	@Override
	public Block bush() {
		return this;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(AGE)) {
			case 0 -> SHAPE_SAPLING;
			case MAX_AGE -> Shapes.block();
			default -> SHAPE_GROWING;
		};
	}
}
