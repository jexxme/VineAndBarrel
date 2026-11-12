package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The round shadow under a player's feet goes as they fall on the face after a blackout (the body lies ahead of the
 * feet, nothing stands there), and comes back as they get up ({@link TipsyClient#blackoutShadow}). On both loaders, once
 * the game has worked the shadow out. Not required: without it the shadow just stays.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin
{
	@Inject(method = "finalizeRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)V",
		at = @At("TAIL"), require = 0)
	private void vineandbarrel$blackoutShadow(Entity entity, EntityRenderState state, CallbackInfo callback) {
		TipsyClient.blackoutShadow(entity, state);
	}
}
