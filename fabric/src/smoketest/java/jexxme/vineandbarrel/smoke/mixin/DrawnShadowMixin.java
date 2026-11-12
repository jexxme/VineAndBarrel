package jexxme.vineandbarrel.smoke.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import jexxme.vineandbarrel.smoke.SmokeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The round shadow the game really draws under the smoke client's own player, render by render (none at all when the
 * dispatcher skips it): what the blackout's shadow hook ({@code jexxme.vineandbarrel.mixin.EntityRenderDispatcherMixin})
 * changes on this Minecraft, which has no method to ask for the radius.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class DrawnShadowMixin
{
	@Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
		at = @At("HEAD"))
	private void vineandbarrel_smoke$starts(Entity entity, double x, double y, double z, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
		int light, CallbackInfo ci) {
		if (entity == Minecraft.getInstance().player) {
			SmokeClient.playerRenderStarts();
		}
	}

	@Inject(method = "renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V",
		at = @At("HEAD"))
	private static void vineandbarrel_smoke$shadow(PoseStack pose, MultiBufferSource buffers, Entity entity, float strength, float partial, LevelReader level,
		float radius, CallbackInfo ci) {
		if (entity == Minecraft.getInstance().player) {
			SmokeClient.shadowDrawn(radius);
		}
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
		at = @At("RETURN"))
	private void vineandbarrel_smoke$ends(Entity entity, double x, double y, double z, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
		int light, CallbackInfo ci) {
		if (entity == Minecraft.getInstance().player) {
			SmokeClient.playerRenderEnds();
		}
	}
}
