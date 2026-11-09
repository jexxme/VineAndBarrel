package jexxme.vineandbarrel.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import jexxme.vineandbarrel.watch.client.SceneRenderer;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * On this Minecraft version the particle renderer always draws into the main framebuffer (or the Fabulous particle
 * target), never into a picture-in-picture's texture. While a "Watch it" scene draws, its particles go into the scene's
 * texture instead, like everything else in it. Optional: if another mod replaces this pass, scenes only lose particles.
 */
@Mixin(QuadParticleFeatureRenderer.class)
public abstract class SceneParticleTargetMixin
{
	@ModifyArg(method = "executeGroup", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass"),
		index = 1, require = 0)
	private GpuTextureView vineandbarrel$sceneColour(GpuTextureView colour) {
		return SceneRenderer.isDrawing() && RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : colour;
	}

	@ModifyArg(method = "executeGroup", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass"),
		index = 3, require = 0)
	private @Nullable GpuTextureView vineandbarrel$sceneDepth(@Nullable GpuTextureView depth) {
		return SceneRenderer.isDrawing() && RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : depth;
	}
}
