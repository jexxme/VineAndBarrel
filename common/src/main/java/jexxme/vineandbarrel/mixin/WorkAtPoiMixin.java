package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import jexxme.vineandbarrel.world.village.TavernKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A tavern keeper works at his kettle from where he stands at it: beside the campfire it sits on, or beside any block of
 * a Big Kettle's bottom layer ({@link TavernKeeper#reaches}). Vanilla's villager works at a job site whose middle is
 * within 1.73 blocks of his feet; a kettle on a campfire is a block higher than any job block, and from the next block
 * it is 1.8 to 2.2 away, so he would never work (never restock his trades), nor step any closer: closer is the fire.
 * Every other villager works as before.
 */
@Mixin(WorkAtPoi.class)
public abstract class WorkAtPoiMixin
{
	@WrapOperation(method = {"checkExtraStartConditions", "canStillUse"}, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
	private boolean vineandbarrel$atHisKettle(BlockPos site, Position at, double reach, Operation<Boolean> original, @Local(argsOnly = true) Villager body) {
		if (TavernKeeper.isKeeper(body) && TavernKeeper.isJobSite(body.level().getBlockState(site))) {
			return TavernKeeper.reaches(body.level(), site, at);
		}
		return original.call(site, at, reach);
	}
}
