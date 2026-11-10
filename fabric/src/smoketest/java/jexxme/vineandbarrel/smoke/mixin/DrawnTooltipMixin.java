package jexxme.vineandbarrel.smoke.mixin;

import jexxme.vineandbarrel.smoke.SmokeClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** How many lines the tooltip that is really drawn has (every tooltip of a frame is drawn at once on this Minecraft). */
@Mixin(GuiGraphics.class)
abstract class DrawnTooltipMixin
{
	@Inject(method = "renderTooltipInternal", at = @At("HEAD"))
	private void vineandbarrel_smoke$drawn(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner, CallbackInfo ci) {
		SmokeClient.drawnTooltip(lines.size());
	}
}
