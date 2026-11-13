package jexxme.vineandbarrel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rowdy in your own eyes ({@link TipsyClient#windupHand}): as the arm draws back for a wild swing, the hand in view goes
 * back and down with it, then the game's own swing throws it forward. Both loaders.
 */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin
{
	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0,
		shift = At.Shift.AFTER), require = 0)
	private void vineandbarrel$windup(AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand, float swingProgress,
		ItemStack stack, float equippedProgress, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo callback) {
		if (hand == InteractionHand.MAIN_HAND) {
			TipsyClient.windupHand(poseStack, player.getMainArm() == HumanoidArm.RIGHT, partialTicks);
		}
	}
}
