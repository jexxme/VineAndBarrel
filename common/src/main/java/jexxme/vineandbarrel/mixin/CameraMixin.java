package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Where Tipsy moves the camera itself, on both loaders (their camera events can only turn it):
 * <ul>
 *   <li>once vanilla has put the first-person camera at the eyes, a player lying on the ground after a blackout sees
 *   the world from down there, and one who tripped from where the face lies ({@link TipsyClient#cameraOffset}; the
 *   turn comes from {@link TipsyClient#cameraAngles});</li>
 *   <li>at the end of vanilla's field of view (after its own effects and the loader's event), the FOV breathing at
 *   Tipsy V ({@link TipsyClient#fovFactor}).</li>
 * </ul>
 */
@Mixin(Camera.class)
public abstract class CameraMixin
{
	@Shadow
	private Entity entity;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	public abstract Vec3 position();

	@Inject(at = @At("TAIL"), method = "alignWithEntity")
	private void vineandbarrel$lieDown(float partialTicks, CallbackInfo ci) {
		Vec3 offset = TipsyClient.cameraOffset((Camera) (Object) this, partialTicks);
		if (offset != Vec3.ZERO) {
			this.setPosition(this.position().add(offset));
		}
	}

	@Inject(at = @At("RETURN"), method = "modifyFovBasedOnDeathOrFluid", cancellable = true)
	private void vineandbarrel$breathe(float partialTicks, float fov, CallbackInfoReturnable<Float> cir) {
		float factor = TipsyClient.fovFactor(entity, partialTicks);
		if (factor != 1.0F) {
			cir.setReturnValue(cir.getReturnValueF() * factor);
		}
	}
}
