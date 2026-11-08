package jexxme.vineandbarrel.smoke.mixin;

import jexxme.vineandbarrel.smoke.SmokeClient;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * What the hovered effect's tooltip came out as: TipsyClient.onEffectTooltip only runs when the inventory's effect list
 * draws a hovered entry (Fabric: EffectsInInventoryMixin), so this sees the whole path, hover check included.
 */
@Mixin(TipsyClient.class)
abstract class EffectTooltipCaptureMixin
{
	@Inject(method = "onEffectTooltip", at = @At("TAIL"))
	private static void vineandbarrel_smoke$capture(MobEffectInstance effect, List<Component> tooltip, CallbackInfo ci) {
		SmokeClient.effectTooltip(tooltip);
	}
}
