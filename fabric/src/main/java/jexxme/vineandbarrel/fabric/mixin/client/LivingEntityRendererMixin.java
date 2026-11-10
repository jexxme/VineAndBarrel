package jexxme.vineandbarrel.fabric.mixin.client;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.vertex.PoseStack;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * T1: others see you sway, hiccup, stumble, trip and black out (NeoForge: {@code RenderLivingEvent.Pre} and {@code Post}).
 * Whether a lean was pushed onto the pose stack is kept between the two for this one call, so the matching pop happens
 * exactly once.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin
{
	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
	private void vineandbarrel$lean(LivingEntity entity, float entityYaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int packedLight, CallbackInfo callback,
		@Share("pushed") LocalBooleanRef pushed) {
		pushed.set(TipsyClient.beforeRenderLiving(entity, partialTicks, pose));
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
	private void vineandbarrel$unlean(LivingEntity entity, float entityYaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int packedLight, CallbackInfo callback,
		@Share("pushed") LocalBooleanRef pushed) {
		TipsyClient.afterRenderLiving(pushed.get(), pose);
	}
}
