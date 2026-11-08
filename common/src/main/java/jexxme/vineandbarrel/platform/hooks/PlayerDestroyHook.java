package jexxme.vineandbarrel.platform.hooks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * A block that decides itself what is left when a player breaks it. The same method as NeoForge's
 * {@code IBlockExtension#onDestroyedByPlayer}, so on NeoForge it simply overrides that. Fabric has no such hook, so our
 * Fabric mixins call it at the same two spots (the server's {@code ServerPlayerGameMode} and the client's guess in
 * {@code MultiPlayerGameMode}). Returning false keeps the block: no {@code destroy}, no drops.
 */
public interface PlayerDestroyHook
{
	/** Returns whether the block was removed (vanilla removes it and returns {@code true}). */
	boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack, boolean willHarvest, FluidState fluid);
}
