package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.Blackout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A villager whose bed has a blacked-out player in it keeps the bed. Vanilla's night-time check (the REST schedule's
 * {@code ValidateNearbyPoi} for HOME) takes an occupied bed near an awake villager as lost: it forgets its home and
 * frees the claim, so the village's beds get shuffled. A blackout's bed is only borrowed for a few seconds.
 */
@Mixin(ValidateNearbyPoi.class)
public abstract class ValidateNearbyPoiMixin
{
	@Inject(at = @At("HEAD"), method = "bedIsOccupied", cancellable = true)
	private static void vineandbarrel$borrowedBed(ServerLevel level, BlockPos pos, LivingEntity body, CallbackInfoReturnable<Boolean> cir) {
		if (!level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(1.0),
			player -> Blackout.isOut(player) && pos.equals(player.getSleepingPos().orElse(null))).isEmpty()) {
			cir.setReturnValue(false);
		}
	}
}
