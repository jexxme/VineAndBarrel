package jexxme.vineandbarrel.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.platform.hooks.PlayerDestroyHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The client's own guess when the player breaks a block: our {@link PlayerDestroyHook} blocks decide it here too, as on
 * NeoForge, so a table that only loses one drink doesn't vanish for a moment (see {@code ServerPlayerGameModeMixin}).
 */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin
{
	@Shadow
	@Final
	private Minecraft minecraft;

	@WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
	private boolean vineandbarrel$destroyedByPlayer(Level level, BlockPos pos, BlockState newState, int flags, Operation<Boolean> setBlock) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof PlayerDestroyHook hook && minecraft.player != null) {
			return hook.onDestroyedByPlayer(state, level, pos, minecraft.player, minecraft.player.getMainHandItem().copy(),
				minecraft.player.hasCorrectToolForDrops(state), level.getFluidState(pos));
		}
		return setBlock.call(level, pos, newState, flags);
	}
}
