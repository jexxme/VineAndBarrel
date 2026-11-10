package jexxme.vineandbarrel.neoforge.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.platform.hooks.PlayerDestroyHook;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The client's own guess when the player breaks a block: our {@link PlayerDestroyHook} blocks decide it here too, so a
 * table that only loses one drink doesn't vanish for a moment (see {@code ServerPlayerGameModeMixin}).
 */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin
{
	@WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;onDestroyedByPlayer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/level/material/FluidState;)Z"))
	private boolean vineandbarrel$destroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid,
		Operation<Boolean> original) {
		if (state.getBlock() instanceof PlayerDestroyHook hook) {
			return hook.onDestroyedByPlayer(state, level, pos, player, player.getMainHandItem().copy(), willHarvest, fluid);
		}
		return original.call(state, level, pos, player, willHarvest, fluid);
	}
}
