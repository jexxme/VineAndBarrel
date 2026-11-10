package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.entity.BeeGrowth;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * C2: vanilla's bee growth goal has a case per plant class and none for ours, so our grape and hop bushes get the
 * sweet berry bush's case here. The tick only runs while the goal is active (the bee carries nectar), and vanilla's own
 * pass never matches our blocks, so nothing can grow twice.
 */
@Mixin(targets = "net.minecraft.world.entity.animal.Bee$BeeGrowCropGoal")
public abstract class BeeGrowCropGoalMixin
{
	// The outer bee: a compiler-made field that Mojang's names leave out, so in a released game it keeps the loader's own
	// name: its intermediary name on Fabric, its SRG name on Forge (both read off the 1.20.1 mappings). No refmap entry
	// is made for it (remap = false): the name is this$0 in development and one of the aliases otherwise
	@Shadow(aliases = {"field_20373", "f_28021_"}, remap = false)
	@Final
	Bee this$0;

	@Inject(method = "tick", at = @At("HEAD"))
	private void vineandbarrel$growClimbingCrops(CallbackInfo ci) {
		if (this$0.getRandom().nextInt(BeeGrowth.ONE_IN) == 0) {
			BeeGrowth.grow(this$0);
		}
	}
}
