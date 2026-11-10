package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.watch.client.SceneRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A "Watch it" scene's figure drinking: there is no drinking pose in third person, so its main arm takes the horn's,
 * which raises the hand to the mouth. On this Minecraft version the player renderer picks the arm pose itself, from the
 * item in use, so the figure's is set here.
 */
@Mixin(PlayerRenderer.class)
public abstract class SceneFigureDrinkMixin
{
	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private static void vineandbarrel$sceneFigureDrinks(AbstractClientPlayer player, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
		if (hand == InteractionHand.MAIN_HAND && SceneRenderer.isDrinking(player)) {
			cir.setReturnValue(HumanoidModel.ArmPose.TOOT_HORN);
		}
	}
}
