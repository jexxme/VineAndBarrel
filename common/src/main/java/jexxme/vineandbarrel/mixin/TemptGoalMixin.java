package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import jexxme.vineandbarrel.entity.AnimalFoods;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * C3/C7: chickens, cows and sheep follow a player holding what they eat, and a llama one holding the Barley Bale, as on
 * the newer versions, where what tempts an animal is an item tag. On this Minecraft it is a fixed set per goal, so the
 * goal also asks the animal's tag (see {@link AnimalFoods}).
 */
@Mixin(TemptGoal.class)
public abstract class TemptGoalMixin
{
	@Shadow
	@Final
	protected PathfinderMob mob;

	@ModifyReturnValue(method = "shouldFollow", at = @At("RETURN"))
	private boolean vineandbarrel$temptedByTag(boolean original, LivingEntity holder) {
		return original || AnimalFoods.tempts(mob, holder.getMainHandItem()) || AnimalFoods.tempts(mob, holder.getOffhandItem());
	}
}
