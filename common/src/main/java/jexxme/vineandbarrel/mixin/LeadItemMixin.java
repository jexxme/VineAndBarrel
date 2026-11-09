package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.block.VineSupports;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A lead ties to a fence that a grape vine grew over, like to any fence. */
@Mixin(LeadItem.class)
public abstract class LeadItemMixin
{
	@WrapOperation(method = "useOn", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState vineandbarrel$seeSupport(Level level, BlockPos pos, Operation<BlockState> getBlockState) {
		return VineSupports.appearance(level, pos, getBlockState.call(level, pos));
	}
}
