package jexxme.vineandbarrel.fabric.mixin.client;

import jexxme.vineandbarrel.tipsy.TipsyWobble;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tipsy's camera turn (the sway, the jolts, a trip's look down, the blackout's tipped view), where NeoForge posts
 * {@code ViewportEvent.ComputeCameraAngles}: once the camera has its final rotation, whichever way it got it (first or third person, the mirrored front view, in a
 * bed, in a minecart), the angles are added on top. Vanilla's camera has no roll, so it is added to the rotation
 * {@code setRotation} builds.
 */
@Mixin(Camera.class)
abstract class CameraMixin
{
	@Shadow
	private Entity entity;

	@Shadow
	private float yRot;

	@Shadow
	private float xRot;

	@Unique
	private float vineandbarrel$roll;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void vineandbarrel$wobble(float partialTicks, CallbackInfo ci) {
		TipsyWobble.Angles angles = TipsyClient.cameraAngles(entity, partialTicks);
		if (angles.isNone()) {
			return;
		}
		vineandbarrel$roll = angles.roll();
		try {
			setRotation(yRot + angles.yaw(), xRot + angles.pitch());
		} finally {
			vineandbarrel$roll = 0.0F;
		}
	}

	@ModifyArg(method = "setRotation", at = @At(value = "INVOKE", target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;"), index = 2)
	private float vineandbarrel$roll(float roll) {
		return roll - vineandbarrel$roll * (float) (Math.PI / 180.0);
	}
}
