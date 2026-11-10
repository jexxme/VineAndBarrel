package jexxme.vineandbarrel.fabric.mixin.client;

import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after the player's input was read (NeoForge: {@code MovementInputUpdateEvent}): the drift at V; out cold or down after a trip, the keys do nothing. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin
{
	@Shadow
	public Input input;

	@Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;tick(ZF)V", shift = At.Shift.AFTER))
	private void vineandbarrel$movementInput(CallbackInfo callback) {
		TipsyClient.onMovementInput((LocalPlayer) (Object) this, input);
	}
}
