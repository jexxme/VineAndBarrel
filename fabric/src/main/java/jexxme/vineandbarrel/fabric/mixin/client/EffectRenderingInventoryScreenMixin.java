package jexxme.vineandbarrel.fabric.mixin.client;

import com.google.common.collect.Ordering;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The inventory's effect list (NeoForge: {@code GatherEffectScreenTooltipsEvent} and the mob effect client extension):
 * hovering Tipsy explains the level, and Tipsy's icon-less side effects don't show up as entries.
 */
@Mixin(EffectRenderingInventoryScreen.class)
abstract class EffectRenderingInventoryScreenMixin<T extends AbstractContainerMenu> extends AbstractContainerScreen<T>
{
	private EffectRenderingInventoryScreenMixin(T menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Unique
	private static boolean vineandbarrel$hidden(MobEffectInstance effect) {
		return TipsyClient.SIDE_EFFECTS.contains(effect.getEffect()) && !TipsyClient.isVisibleInInventory(effect);
	}

	@ModifyExpressionValue(method = "renderEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"))
	private Collection<MobEffectInstance> vineandbarrel$hideSideEffects(Collection<MobEffectInstance> effects) {
		if (effects.stream().noneMatch(EffectRenderingInventoryScreenMixin::vineandbarrel$hidden)) {
			return effects;
		}
		return effects.stream().filter(effect -> !vineandbarrel$hidden(effect)).toList();
	}

	/**
	 * Whether this frame drew the list with names. Not read off the free width: another mod (JEI) draws the icons alone
	 * even where the names would fit.
	 */
	@Unique
	private boolean vineandbarrel$wideList;

	@Inject(method = "renderEffects", at = @At("HEAD"))
	private void vineandbarrel$newFrame(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo callback) {
		vineandbarrel$wideList = false;
	}

	@Inject(method = "renderEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/EffectRenderingInventoryScreen;renderLabels(Lnet/minecraft/client/gui/GuiGraphics;IILjava/lang/Iterable;)V"))
	private void vineandbarrel$namesDrawn(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo callback) {
		vineandbarrel$wideList = true;
	}

	/**
	 * The effect whose entry the mouse is over, found the way vanilla's own list is laid out (the entries' left edge,
	 * their step and their order), or null. {@code wide}: the list with names (120 wide), otherwise the icons alone (32).
	 */
	@Unique
	private @Nullable MobEffectInstance vineandbarrel$hovered(int mouseX, int mouseY, boolean wide) {
		int left = this.leftPos + this.imageWidth + 2;
		int space = this.width - left;
		if (this.minecraft == null || this.minecraft.player == null || space < 32) {
			return null;
		}
		Collection<MobEffectInstance> effects = vineandbarrel$hideSideEffects(this.minecraft.player.getActiveEffects());
		if (effects.isEmpty() || mouseX < left || mouseX > left + (wide ? 120 : 33)) {
			return null;
		}
		int step = effects.size() > 5 ? 132 / (effects.size() - 1) : 33;
		int top = this.topPos;
		MobEffectInstance hovered = null;
		for (MobEffectInstance effect : Ordering.natural().sortedCopy(effects)) {
			if (mouseY >= top && mouseY <= top + step) {
				hovered = effect;
			}
			top += step;
		}
		return hovered;
	}

	/** The icons alone: vanilla's tooltip (name and time) with Tipsy's lines after it. */
	@WrapOperation(method = "renderEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V"))
	private void vineandbarrel$tipsyTooltip(GuiGraphics graphics, Font font, List<Component> lines, Optional<TooltipComponent> image, int mouseX, int mouseY,
		Operation<Void> original) {
		MobEffectInstance effect = vineandbarrel$hovered(mouseX, mouseY, false);
		if (effect != null) {
			List<Component> tooltip = new ArrayList<>(lines);
			TipsyClient.onEffectTooltip(effect, tooltip);
			lines = tooltip;
		}
		original.call(graphics, font, lines, image, mouseX, mouseY);
	}

	/** The list with names has no tooltip of its own: Tipsy's lines alone, like NeoForge's event there. */
	@Inject(method = "renderEffects", at = @At("TAIL"))
	private void vineandbarrel$tipsyTooltipWide(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo callback) {
		MobEffectInstance effect = vineandbarrel$wideList ? vineandbarrel$hovered(mouseX, mouseY, true) : null;
		if (effect != null) {
			List<Component> tooltip = new ArrayList<>();
			TipsyClient.onEffectTooltip(effect, tooltip);
			if (!tooltip.isEmpty()) {
				graphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
			}
		}
	}
}
