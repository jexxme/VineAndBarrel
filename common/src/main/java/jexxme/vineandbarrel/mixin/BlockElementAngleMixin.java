package jexxme.vineandbarrel.mixin;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block models may turn an element by any angle, as on 26.x. This Minecraft only reads -45, -22.5, 0, 22.5 and 45 and
 * drops the whole model otherwise; the open Brew Kettle's lid is tilted by 35 degrees. The baking itself
 * works with any angle, only the check when reading is in the way.
 */
@Mixin(targets = "net.minecraft.client.renderer.block.model.BlockElement$Deserializer")
public abstract class BlockElementAngleMixin
{
	@Inject(method = "getAngle", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$anyAngle(JsonObject json, CallbackInfoReturnable<Float> cir) {
		float angle = GsonHelper.getAsFloat(json, "angle");
		if (Math.abs(angle) <= 45.0F) {
			cir.setReturnValue(angle);
		}
	}
}
