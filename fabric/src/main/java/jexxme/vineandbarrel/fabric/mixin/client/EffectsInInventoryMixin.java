package jexxme.vineandbarrel.fabric.mixin.client;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The inventory's effect list (NeoForge: {@code GatherEffectScreenTooltipsEvent} and the mob effect client extension):
 * hovering Tipsy explains the level, and Tipsy's icon-less side effects don't show up as entries.
 */
@Mixin(EffectsInInventory.class)
abstract class EffectsInInventoryMixin
{
	@ModifyVariable(method = "extractRenderState", at = @At("STORE"), ordinal = 0)
	private Collection<MobEffectInstance> vineandbarrel$hideSideEffects(Collection<MobEffectInstance> effects) {
		if (effects.stream().noneMatch(effect -> TipsyClient.SIDE_EFFECTS.contains(effect.getEffect()) && !TipsyClient.isVisibleInInventory(effect))) {
			return effects;
		}
		return effects.stream().filter(effect -> !TipsyClient.SIDE_EFFECTS.contains(effect.getEffect()) || TipsyClient.isVisibleInInventory(effect)).toList();
	}

	@WrapOperation(method = "extractEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/EffectsInInventory;extractText(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/Font;IIIIII)V"))
	private void vineandbarrel$tipsyTooltip(EffectsInInventory self, GuiGraphicsExtractor graphics, Component effectText, Component duration, Font font,
		int x0, int y0, int textureWidth, int yStep, int mouseX, int mouseY, Operation<Void> original, @Local MobEffectInstance effect) {
		original.call(self, graphics, effectText, duration, font, x0, y0, textureWidth, yStep, mouseX, mouseY);
		if (mouseX < x0 || mouseX > x0 + textureWidth || mouseY < y0 || mouseY > y0 + yStep) {
			return;
		}
		// Like NeoForge: the compact list starts the tooltip with name and time, the wide one with nothing
		boolean compact = textureWidth - 32 - 7 <= 0 || font.width(effectText) > textureWidth - 32 - 7;
		List<Component> tooltip = new ArrayList<>(compact ? List.of(effectText, duration) : List.of());
		int before = tooltip.size();
		TipsyClient.onEffectTooltip(effect, tooltip);
		if (tooltip.size() != before) {
			// Replacing the one vanilla's compact list just set for the same entry (name and time only)
			graphics.setTooltipForNextFrame(font, Lists.transform(tooltip, Component::getVisualOrderText), DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, true);
		}
	}
}
