package jexxme.vineandbarrel.fabric.mixin;

import jexxme.vineandbarrel.tipsy.TipsyEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After every entity's tick on the server (what NeoForge's {@code EntityTickEvent.Post} is): Tipsy's side effects and sync. */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin
{
	@Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER))
	private void vineandbarrel$afterTick(Entity entity, CallbackInfo callback) {
		TipsyEvents.onEntityTick(entity);
	}

	@Inject(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V", shift = At.Shift.AFTER))
	private void vineandbarrel$afterRideTick(Entity vehicle, Entity entity, CallbackInfo callback) {
		TipsyEvents.onEntityTick(entity);
	}
}
