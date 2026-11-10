package jexxme.vineandbarrel.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla's own fire table (private on this Minecraft): {@code VBFlammability} enters our blocks into it and reads it
 * back for the loaders.
 */
@Mixin(FireBlock.class)
public interface FireBlockAccessor
{
	@Invoker("setFlammable")
	void vineandbarrel$setFlammable(Block block, int igniteOdds, int burnOdds);

	@Invoker("getBurnOdds")
	int vineandbarrel$getBurnOdds(BlockState state);

	@Invoker("getIgniteOdds")
	int vineandbarrel$getIgniteOdds(BlockState state);
}
