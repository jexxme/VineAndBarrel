package jexxme.vineandbarrel.smoke.mixin;

import jexxme.vineandbarrel.smoke.SmokeCommands;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.BlackoutEnding;
import jexxme.vineandbarrel.tipsy.BlackoutSpots;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dev-only (the smoke test): the blackout's draw gives the ending the smoke test asked for ({@code /vbsmoke blackout
 * home}), so the run is the same every time. Everything else about the blackout (the timeline, the wake-up, the bed)
 * is the mod's own code.
 */
@Mixin(Blackout.class)
abstract class ForcedBlackoutEndingMixin
{
	@Inject(method = "choose(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/util/RandomSource;)Ljexxme/vineandbarrel/tipsy/BlackoutSpots$Wake;",
		at = @At("HEAD"), cancellable = true)
	private static void vineandbarrel_smoke$forcedEnding(ServerPlayer player, RandomSource random, CallbackInfoReturnable<BlackoutSpots.Wake> callback) {
		BlackoutEnding forced = SmokeCommands.takeForcedEnding();
		if (forced != null) {
			BlackoutSpots.Wake wake = BlackoutSpots.find(forced, player, random);
			if (wake != null) {
				callback.setReturnValue(wake);
			}
		}
	}
}
