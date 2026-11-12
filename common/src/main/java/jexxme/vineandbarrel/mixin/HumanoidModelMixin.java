package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player out on the ground after a blackout moves head and limbs, not only the whole body: at the end of the humanoid
 * model's posing, over what the game (and other mods) did ({@link TipsyClient#poseBlackout}). The player model copies
 * the limbs to the sleeves, trousers and jacket right after, the armour copies them from the player model, and the held
 * items and the hat follow the arms and the head. Not required: if another mod takes this method over, the body still
 * falls and gets up as a whole.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin
{
	/** Whether the blackout posed this model the last time it was posed (one model draws every player). */
	@Unique
	private boolean vineandbarrel$posed;

	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"), require = 0)
	private void vineandbarrel$blackout(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
		CallbackInfo callback) {
		this.vineandbarrel$posed = TipsyClient.poseBlackout((HumanoidModel<?>) (Object) this, entity, this.vineandbarrel$posed);
	}
}
