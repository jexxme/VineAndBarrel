package jexxme.vineandbarrel.fabric.mixin.client;

import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The first-person hands (NeoForge: {@code RenderHandEvent}): none in view while out cold or down on the face. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsMixin
{
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$hideHands(CallbackInfo callback) {
		if (TipsyClient.hidesHands()) {
			callback.cancel();
		}
	}
}
