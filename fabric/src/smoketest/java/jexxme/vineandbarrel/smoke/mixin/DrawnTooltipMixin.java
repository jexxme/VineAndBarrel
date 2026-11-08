package jexxme.vineandbarrel.smoke.mixin;

import jexxme.vineandbarrel.smoke.SmokeClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** How many lines the tooltip that is really drawn has (another tooltip set first for the same frame would win). */
@Mixin(GuiGraphicsExtractor.class)
abstract class DrawnTooltipMixin
{
	@Inject(method = "tooltip", at = @At("HEAD"))
	private void vineandbarrel_smoke$drawn(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner,
			@Nullable Identifier background, boolean focused, CallbackInfo ci) {
		SmokeClient.drawnTooltip(lines.size());
	}
}
