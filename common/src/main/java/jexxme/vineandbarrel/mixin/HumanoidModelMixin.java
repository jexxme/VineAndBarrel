package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player out on the ground after a blackout moves head and limbs, not only the whole body: at the end of the humanoid
 * model's posing, over what the game (and other mods) did ({@link TipsyClient#poseBlackout}). The player model, its
 * armour and its cape are all humanoid models posed from the same render state, so they move together. Not required: if
 * another mod takes this method over, the body still falls and gets up as a whole.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin
{
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"), require = 0)
	private void vineandbarrel$blackout(HumanoidRenderState state, CallbackInfo callback) {
		TipsyClient.poseBlackout((HumanoidModel<?>) (Object) this, state);
	}
}
