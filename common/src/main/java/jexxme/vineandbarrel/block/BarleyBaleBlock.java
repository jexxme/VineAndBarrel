package jexxme.vineandbarrel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HayBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Barley Bale: a hay bale in every way. Copying the hay block's properties doesn't copy its fire data (vanilla
 * registers that separately in {@code FireBlock.bootStrap}), so it burns like hay here, the way FD's straw bale does.
 */
public class BarleyBaleBlock extends HayBlock
{
	public BarleyBaleBlock(Properties properties) {
		super(properties);
	}


}
