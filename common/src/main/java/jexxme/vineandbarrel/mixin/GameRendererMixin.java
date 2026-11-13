package jexxme.vineandbarrel.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import jexxme.vineandbarrel.tipsy.client.DrunkEyes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drunk eyes ({@link DrunkEyes}): their post effect drawn over the world, right after the game's own (a spectated
 * creeper's), before the HUD. This version has only the one slot for the game's own effect, so ours goes in beside it.
 * Both loaders.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin
{
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	@Final
	private RenderTarget mainRenderTarget;
	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	@org.spongepowered.asm.mixin.Unique
	private static boolean vineandbarrel$broken;

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
	private void vineandbarrel$drunkEyes(CallbackInfo callback) {
		if (DrunkEyes.active() && !vineandbarrel$broken) {
			try {
				PostChain chain = this.minecraft.getShaderManager().getPostChain(DrunkEyes.EFFECT, LevelTargetBundle.MAIN_TARGETS);
				if (chain != null) {
					chain.process(this.mainRenderTarget, this.resourcePool);
				}
			} catch (RuntimeException e) {
				// a shader this machine can't run (a shader pack, an old driver): the eyes stay clear, the game goes on
				vineandbarrel$broken = true;
				jexxme.vineandbarrel.VineAndBarrel.LOGGER.warn("Tipsy: the drunk eyes' post effect can't run here, the eyes stay clear", e);
			}
		}
	}
}
