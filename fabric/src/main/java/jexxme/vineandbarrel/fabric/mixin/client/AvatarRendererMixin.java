package jexxme.vineandbarrel.fabric.mixin.client;

import jexxme.vineandbarrel.fabric.client.TipsySway;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** T1: a tipsy (or blacked-out) player's render state carries how the body is posed (NeoForge: a render state modifier). */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin
{
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void vineandbarrel$swayLevel(Avatar avatar, AvatarRenderState state, float partialTicks, CallbackInfo callback) {
		state.setData(TipsySway.POSE, TipsyClient.bodyPose(avatar));
	}
}
