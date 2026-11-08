package jexxme.vineandbarrel.barrel.client;

import jexxme.vineandbarrel.barrel.AgingBarrelItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

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
	public int getHeight(Font font) {
		return drink.isEmpty() ? lineHeight(font) : lineHeight(font) + ITEM_SIZE;
	}

	@Override
	public int getWidth(Font font) {
		return drink.isEmpty() ? font.width(headline()) : Math.max(font.width(headline()), font.width(drink.getHoverName()) + ITEM_SIZE + MARGIN);
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor gui) {
		if (!drink.isEmpty()) {
			gui.item(drink, x, y + lineHeight(font), 0);
		}
	}

	@Override
	public void extractText(GuiGraphicsExtractor gui, Font font, int x, int y) {
		// gui.text skips text with zero alpha, and chat colours are plain RGB
		Integer color = ChatFormatting.GRAY.getColor();
		int gray = color == null ? -1 : ARGB.opaque(color);
		gui.text(font, headline(), x, y, gray, drink.isEmpty());
		if (!drink.isEmpty()) {
			gui.text(font, drink.getHoverName(), x + ITEM_SIZE + MARGIN, y + lineHeight(font) + MARGIN, -1);
		}
	}

	private static int lineHeight(Font font) {
		return font.lineHeight + 1;
	}
}
