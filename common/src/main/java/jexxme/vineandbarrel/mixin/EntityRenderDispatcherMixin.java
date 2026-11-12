package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The round shadow under a player's feet goes as they fall on the face after a blackout (the body lies ahead of the
 * feet, nothing stands there), and comes back as they get up ({@link TipsyClient#blackoutShadow}). On both loaders,
 * where the game reads the renderer's shadow radius before it draws the shadow (1.20.1 has no method for it: the
 * dispatcher reads the field, once to decide whether there is a shadow and once for its size). Not required: without it
 * the shadow just stays.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin
{
	@ModifyExpressionValue(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shadowRadius:F", opcode = org.objectweb.asm.Opcodes.GETFIELD),
		require = 0)
	private float vineandbarrel$blackoutShadow(float radius, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true, ordinal = 1) float partial) {
		// (the partial tick the dispatcher hands the renderer, which draws the body with it)
		return TipsyClient.blackoutShadow(entity, radius, partial);
	}
}
