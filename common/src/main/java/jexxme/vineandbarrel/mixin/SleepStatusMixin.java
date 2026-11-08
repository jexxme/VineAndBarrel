package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.Blackout;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * A blacked-out player lying in a bed isn't one of the sleepers ("1/2 players sleeping"): they are out cold, and only
 * for a few seconds. (They never reach deep sleep either, so they can't help skip the night.) Wrapped, not redirected,
 * so other mods that change who counts as sleeping chain with it instead of clashing.
 */
@Mixin(SleepStatus.class)
public abstract class SleepStatusMixin
{
	@WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isSleeping()Z"))
	private boolean vineandbarrel$notOutCold(ServerPlayer player, Operation<Boolean> isSleeping) {
		return isSleeping.call(player) && !Blackout.isOut(player);
	}
}
