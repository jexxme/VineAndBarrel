package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.block.VineSupports;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A fence connects to a fence that a grape vine grew over (and treats it as the fence it is), placed or updated.
 * The vine keeps the block it grew into; neighbours ask {@link VineSupports#appearance} and see that block. Wrapped, so
 * other mods' changes to the same methods chain with it.
 */
@Mixin(FenceBlock.class)
public abstract class FenceBlockMixin
{
	// the full descriptor: WallBlock has a private updateShape overload too
	@WrapMethod(method = "updateShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/ScheduledTickAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/level/block/state/BlockState;")
	private BlockState vineandbarrel$seeSupport(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
												 BlockPos neighbourPos, BlockState neighbourState, RandomSource random, Operation<BlockState> original) {
		return original.call(state, level, ticks, pos, directionToNeighbour, neighbourPos, VineSupports.appearance(level, neighbourPos, neighbourState), random);
	}

	@WrapOperation(method = "getStateForPlacement", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState vineandbarrel$seeSupportWhenPlaced(BlockGetter level, BlockPos pos, Operation<BlockState> getBlockState) {
		return VineSupports.appearance(level, pos, getBlockState.call(level, pos));
	}
}
