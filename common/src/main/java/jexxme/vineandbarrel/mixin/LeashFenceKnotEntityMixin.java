package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.block.VineSupports;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A lead tied to a fence stays tied when a grape vine grows over the fence: the knot sees the fence inside the vine. */
@Mixin(LeashFenceKnotEntity.class)
public abstract class LeashFenceKnotEntityMixin
{
	@WrapOperation(method = "survives", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState vineandbarrel$seeSupport(Level level, BlockPos pos, Operation<BlockState> getBlockState) {
		return VineSupports.appearance(level, pos, getBlockState.call(level, pos));
	}
}
