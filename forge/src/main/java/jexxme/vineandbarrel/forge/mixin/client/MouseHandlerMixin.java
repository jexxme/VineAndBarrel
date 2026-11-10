package jexxme.vineandbarrel.forge.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The mouse sensitivity the view turns with (NeoForge 21.1: {@code CalculatePlayerTurnEvent}, which Forge 47 does not
 * have): out cold or down after a trip, it doesn't turn. The same mixin as on Fabric.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin
{
	@ModifyExpressionValue(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
	private Object vineandbarrel$sensitivity(Object sensitivity) {
		return TipsyClient.mouseSensitivity((Double) sensitivity);
	}
}
