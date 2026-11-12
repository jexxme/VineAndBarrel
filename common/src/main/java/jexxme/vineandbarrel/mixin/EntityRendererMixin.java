package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The round shadow under a player's feet goes as they fall on the face after a blackout (the body lies ahead of the
 * feet, nothing stands there), and comes back as they get up ({@link TipsyClient#blackoutShadow}). On both loaders,
 * where the game asks the renderer for the shadow's radius before it draws it (the living renderers scale this one by
 * the entity's size). Not required: without it the shadow just stays.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin
{
	@Inject(method = "getShadowRadius(Lnet/minecraft/world/entity/Entity;)F", at = @At("RETURN"), cancellable = true, require = 0)
	private void vineandbarrel$blackoutShadow(Entity entity, CallbackInfoReturnable<Float> cir) {
		float radius = cir.getReturnValueF();
		float shown = TipsyClient.blackoutShadow(entity, radius);
		if (shown != radius) {
			cir.setReturnValue(shown);
		}
	}
}
