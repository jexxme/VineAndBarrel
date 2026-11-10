package jexxme.vineandbarrel.kettle.client;

import jexxme.vineandbarrel.kettle.BrewKettleItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** The kettle item's tooltip (K6): "Holds 4 servings of:" and the brew with its icon, exactly like FD's cooking pot. */
public class KettleTooltip implements ClientTooltipComponent
{
	private static final int ITEM_SIZE = 16;
	private static final int MARGIN = 4;

	private final ItemStack brew;

	public KettleTooltip(BrewKettleItem.KettleTooltipData data) {
		this.brew = data.brew();
	}

	private Component headline() {
		return brew.isEmpty() ? Component.translatable("tooltip.vineandbarrel.brew_kettle.empty") : BrewKettleScreen.servingsText(brew.getCount());
	}

	@Override
	public int getHeight() {
		Font font = Minecraft.getInstance().font;
		return brew.isEmpty() ? lineHeight(font) : lineHeight(font) + ITEM_SIZE;
	}

	@Override
	public int getWidth(Font font) {
		return brew.isEmpty() ? font.width(headline()) : Math.max(font.width(headline()), font.width(brew.getHoverName()) + ITEM_SIZE + MARGIN);
	}

	@Override
	public void renderImage(Font font, int x, int y, GuiGraphics gui) {
		if (!brew.isEmpty()) {
			gui.renderItem(brew, x, y + lineHeight(font), 0);
		}
	}

	@Override
	public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffers) {
		// chat colours are plain RGB
		Integer color = ChatFormatting.GRAY.getColor();
		int gray = color == null ? -1 : 0xFF000000 | color;
		font.drawInBatch(headline(), x, y, gray, brew.isEmpty(), matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
		if (!brew.isEmpty()) {
			font.drawInBatch(brew.getHoverName(), x + ITEM_SIZE + MARGIN, y + lineHeight(font) + MARGIN, -1, true, matrix, buffers, Font.DisplayMode.NORMAL, 0,
				LightTexture.FULL_BRIGHT);
		}
	}

	private static int lineHeight(Font font) {
		return font.lineHeight + 1;
	}
}
