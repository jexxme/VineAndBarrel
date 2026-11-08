package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tag.VBTags;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla only lets a hay bale under a campfire make signal smoke ({@code CampfireBlock#isSmokeSource}). The Barley Bale
 * follows the same rule through our tag; with FD installed it is also in {@code farmersdelight:campfire_signal_smoke}.
 * Same approach as FD's {@code CampfireBaleMixin} (Farmer's Delight by vectorwing, MIT).
 */
@Mixin(CampfireBlock.class)
public abstract class CampfireSignalSmokeMixin
{
	@Inject(at = @At("HEAD"), method = "isSmokeSource", cancellable = true)
	private void vineandbarrel$isSmokeSource(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (state.is(VBTags.CAMPFIRE_SIGNAL_SMOKE)) {
			cir.setReturnValue(true);
		}
	}
}
