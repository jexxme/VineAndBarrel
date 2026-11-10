package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import jexxme.vineandbarrel.entity.AnimalFoods;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.sensing.TemptingSensor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * C3: a goat follows a player holding barley, as on the newer versions, where what tempts a goat is the item tag
 * {@code minecraft:goat_food}. A goat is tempted through this sensor (it has a brain, no tempt goal), which on this
 * Minecraft knows a fixed set of items and not whose sensor it is: each mob has its own, so it remembers its mob from
 * its tick and also asks that animal's tag (see {@link AnimalFoods}).
 */
@Mixin(TemptingSensor.class)
public abstract class TemptingSensorMixin
{
	@Unique
	private PathfinderMob vineandbarrel$mob;

	// the full descriptor: the sensor also has the bridge doTick(ServerLevel, LivingEntity)
	@Inject(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/PathfinderMob;)V", at = @At("HEAD"))
	private void vineandbarrel$whoseSensor(ServerLevel level, PathfinderMob mob, CallbackInfo ci) {
		vineandbarrel$mob = mob;
	}

	@ModifyReturnValue(method = "isTemptation", at = @At("RETURN"))
	private boolean vineandbarrel$temptedByTag(boolean original, ItemStack stack) {
		return original || vineandbarrel$mob != null && AnimalFoods.tempts(vineandbarrel$mob, stack);
	}
}
