package jexxme.vineandbarrel.fabric.mixin;

import jexxme.vineandbarrel.tipsy.TipsyEvents;
import net.minecraft.world.entity.player.Player;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Waking up, with how (what NeoForge's {@code PlayerWakeUpEvent} is): sleeping through the night clears Tipsy. And a
 * blackout's bed by day (what NeoForge's {@code CanContinueSleepingEvent} is): the bed's rule is overruled while out cold.
 */
@Mixin(Player.class)
abstract class PlayerMixin
{
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"))
	private boolean vineandbarrel$keepSleeping(boolean canSleep) {
		return canSleep || TipsyEvents.keepsSleeping((Player) (Object) this);
	}

	@Inject(method = "stopSleepInBed", at = @At("HEAD"))
	private void vineandbarrel$wakeUp(boolean forcefulWakeUp, boolean updateLevelList, CallbackInfo callback) {
		TipsyEvents.onWakeUp((Player) (Object) this, forcefulWakeUp, updateLevelList);
	}
}
