package jexxme.vineandbarrel.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import jexxme.vineandbarrel.fabric.client.TipsySway;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** T1: others see you sway, hiccup, stumble, trip and black out (NeoForge: {@code RenderLivingEvent.Pre} and {@code Post}). */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin
{
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
	private void vineandbarrel$lean(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo callback) {
		if (TipsyClient.beforeRenderLiving(state, state.getData(TipsySway.POSE), pose)) {
			state.setData(TipsySway.PUSHED, Boolean.TRUE);
		}
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("TAIL"))
	private void vineandbarrel$unlean(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo callback) {
		boolean pushed = Boolean.TRUE.equals(state.getData(TipsySway.PUSHED));
		state.setData(TipsySway.PUSHED, null);
		TipsyClient.afterRenderLiving(pushed, pose);
	}
}
