package jexxme.vineandbarrel.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.platform.hooks.PlayerDestroyHook;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A player breaking one of our {@link PlayerDestroyHook} blocks: the block decides what is left (a table of drinks loses
 * one drink, a vine leaves its rope). The same spot where NeoForge calls {@code onDestroyedByPlayer} instead of
 * removing the block; what it returns gates {@code destroy} and the drops, as vanilla's own removal result does.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin
{
	@Shadow
	@Final
	protected ServerPlayer player;

	@WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
	private boolean vineandbarrel$destroyedByPlayer(ServerLevel level, BlockPos pos, boolean movedByPiston, Operation<Boolean> removeBlock) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof PlayerDestroyHook hook) {
			return hook.onDestroyedByPlayer(state, level, pos, player, player.getMainHandItem().copy(), player.hasCorrectToolForDrops(state), level.getFluidState(pos));
		}
		return removeBlock.call(level, pos, movedByPiston);
	}
}
