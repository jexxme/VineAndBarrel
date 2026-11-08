package jexxme.vineandbarrel.kettle.client;

import jexxme.vineandbarrel.kettle.BrewKettleItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

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
	public int getHeight(Font font) {
		return brew.isEmpty() ? lineHeight(font) : lineHeight(font) + ITEM_SIZE;
	}

	@Override
	public int getWidth(Font font) {
		return brew.isEmpty() ? font.width(headline()) : Math.max(font.width(headline()), font.width(brew.getHoverName()) + ITEM_SIZE + MARGIN);
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor gui) {
		if (!brew.isEmpty()) {
			gui.item(brew, x, y + lineHeight(font), 0);
		}
	}

	@Override
	public void extractText(GuiGraphicsExtractor gui, Font font, int x, int y) {
		// gui.text skips text with zero alpha, and chat colours are plain RGB
		Integer color = TextColor.GRAY.getValue();
		int gray = color == null ? -1 : ARGB.opaque(color);
		gui.text(font, headline(), x, y, gray, brew.isEmpty());
		if (!brew.isEmpty()) {
			gui.text(font, brew.getHoverName(), x + ITEM_SIZE + MARGIN, y + lineHeight(font) + MARGIN, -1);
		}
	}

	private static int lineHeight(Font font) {
		return font.lineHeight + 1;
	}
}
