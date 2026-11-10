package jexxme.vineandbarrel.neoforge.mixin;

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
 * one drink, a vine leaves its rope). NeoForge 21.1's own {@code IBlockExtension#onDestroyedByPlayer} has no tool stack,
 * so the common blocks can't override it as they do on 26.x: the hook is called where NeoForge calls its method (the
 * {@code removeBlock} it patches into this class), with the same "may harvest" answer and fluid.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin
{
	@WrapOperation(method = "removeBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;onDestroyedByPlayer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/level/material/FluidState;)Z"))
	private boolean vineandbarrel$destroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid,
		Operation<Boolean> original) {
		if (state.getBlock() instanceof PlayerDestroyHook hook) {
			return hook.onDestroyedByPlayer(state, level, pos, player, player.getMainHandItem().copy(), willHarvest, fluid);
		}
		return original.call(state, level, pos, player, willHarvest, fluid);
	}
}
