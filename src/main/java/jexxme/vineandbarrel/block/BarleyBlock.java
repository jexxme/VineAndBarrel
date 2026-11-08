package jexxme.vineandbarrel.block;

import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

/** Barley: a wheat-like field crop on farmland, 8 ages (0-7), replanted from Barley Seeds. */
public class BarleyBlock extends CropBlock
{
	public BarleyBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return VBItems.BARLEY_SEEDS.get();
	}
}
