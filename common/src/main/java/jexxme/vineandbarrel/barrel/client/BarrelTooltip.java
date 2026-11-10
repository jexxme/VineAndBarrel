package jexxme.vineandbarrel.barrel.client;

import jexxme.vineandbarrel.barrel.AgingBarrelItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** The barrel item's tooltip (B12): "Holds 5 servings of:" and the drink with its icon, like the Brew Kettle and FD's pot. */
public class BarrelTooltip implements ClientTooltipComponent
{
	private static final int ITEM_SIZE = 16;
	private static final int MARGIN = 4;

	private final ItemStack drink;
	private final int servings;

	public BarrelTooltip(AgingBarrelItem.BarrelTooltipData data) {
		this.drink = data.drink();
		this.servings = data.servings();
	}

	private Component headline() {
		if (drink.isEmpty()) {
			return Component.translatable("tooltip.vineandbarrel.aging_barrel.empty");
		}
		return servings == 1 ? Component.translatable("tooltip.vineandbarrel.aging_barrel.single_serving")
			: Component.translatable("tooltip.vineandbarrel.aging_barrel.many_servings", servings);
	}

	@Override
	public int getHeight() {
		// 1.21.1 asks without a font: every font's line is as high as the default one's
		int line = Minecraft.getInstance().font.lineHeight + 1;
		return drink.isEmpty() ? line : line + ITEM_SIZE;
	}

	@Override
	public int getWidth(Font font) {
		return drink.isEmpty() ? font.width(headline()) : Math.max(font.width(headline()), font.width(drink.getHoverName()) + ITEM_SIZE + MARGIN);
	}

	@Override
	public void renderImage(Font font, int x, int y, GuiGraphics gui) {
		if (!drink.isEmpty()) {
			gui.renderItem(drink, x, y + lineHeight(font), 0);
		}
	}

	@Override
	public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffers) {
		// chat colours are plain RGB
		Integer color = ChatFormatting.GRAY.getColor();
		int gray = color == null ? -1 : FastColor.ARGB32.opaque(color);
		font.drawInBatch(headline(), x, y, gray, drink.isEmpty(), matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
		if (!drink.isEmpty()) {
			font.drawInBatch(drink.getHoverName(), x + ITEM_SIZE + MARGIN, y + lineHeight(font) + MARGIN, -1, true, matrix, buffers,
				Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
		}
	}

	private static int lineHeight(Font font) {
		return font.lineHeight + 1;
	}
}
