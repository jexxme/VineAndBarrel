package jexxme.vineandbarrel.fabric.mixin.client;

import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The first-person hands (NeoForge: {@code RenderHandEvent}): none in view while out cold or down on the face. */
@Mixin(ItemInHandRenderer.class) // 26.1.x: the hands still live in ItemInHandRenderer#renderHandsWithItems
abstract class FirstPersonHandsMixin
{
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$hideHands(CallbackInfo callback) {
		if (TipsyClient.hidesHands()) {
			callback.cancel();
		}
	}
}
