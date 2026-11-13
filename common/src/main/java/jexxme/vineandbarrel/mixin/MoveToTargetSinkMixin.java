package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import jexxme.vineandbarrel.world.village.TavernKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A villager walking to a kettle (his job site, or one he is about to take) walks to where he stands at it, beside the
 * campfire it sits on (beside the whole fire under a Big Kettle), by a way that never steps in or on anything that hurts.
 * Vanilla walks him to the block itself, and a path's target moves up out of solid blocks: through the kettle and the
 * shelf over it in every tavern, to the air above, so he ended up on the bar counter, or on a desert tavern's roof, and
 * was never hired there or worked there. Should no spot have a safe way there, the walk is vanilla's; any other walk is
 * vanilla's too. Wrapped, so other mods' changes chain with it.
 */
@Mixin(MoveToTargetSink.class)
public abstract class MoveToTargetSinkMixin
{
	@WrapOperation(method = "tryComputePath", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;"))
	private Path vineandbarrel$toTheKettle(PathNavigation navigation, BlockPos target, int reachRange, Operation<Path> original, @Local(argsOnly = true) Mob body) {
		if (body instanceof Villager && TavernKeeper.isJobSite(body.level().getBlockState(target))) {
			Path way = TavernKeeper.wayToKettle(body, target);
			if (way != null) {
				return way;
			}
		}
		return original.call(navigation, target, reachRange);
	}
}
