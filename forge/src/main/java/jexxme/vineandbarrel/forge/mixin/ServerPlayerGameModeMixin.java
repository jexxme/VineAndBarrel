package jexxme.vineandbarrel.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.platform.hooks.PlayerDestroyHook;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A player breaking one of our {@link PlayerDestroyHook} blocks: the block decides what is left (a table of drinks loses
 * one drink, a vine leaves its rope). Forge 47's own {@code IForgeBlock#onDestroyedByPlayer} has no tool stack, so the
 * common blocks can't override it as they do on 26.x: the hook is called where Forge calls its method (the
 * {@code removeBlock} it patches into this class), with the same "may harvest" answer and fluid. Both names are Forge's
 * own (they have no SRG name), so nothing here is remapped.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin
{
	@WrapOperation(method = "removeBlock", remap = false, at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraft/world/level/block/state/BlockState;onDestroyedByPlayer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/level/material/FluidState;)Z"))
	private boolean vineandbarrel$destroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid,
		Operation<Boolean> original) {
		if (state.getBlock() instanceof PlayerDestroyHook hook) {
			// Forge 47 calls playerWillDestroy (sound, particles, the destroy game event) inside the method this replaces,
			// not before it as vanilla and later versions do: so it is called here
			state.getBlock().playerWillDestroy(level, pos, state, player);
			return hook.onDestroyedByPlayer(state, level, pos, player, player.getMainHandItem().copy(), willHarvest, fluid);
		}
		return original.call(state, level, pos, player, willHarvest, fluid);
	}
}
