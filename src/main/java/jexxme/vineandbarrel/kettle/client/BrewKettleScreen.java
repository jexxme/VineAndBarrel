package jexxme.vineandbarrel.kettle.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.BrewKettleMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The Brew Kettle screen: FD's {@code CookingPotScreen} (MIT) without the meal and container slots. The ingredient grid,
 * heat indicator, progress arrow, the recipe book (K9) and a sight-glass gauge for the brew: its colour, filled to its
 * share of the kettle's servings. The brew is served by hand like a cauldron, so the screen has nothing to take.
 * {@link AbstractRecipeBookScreen} already handles the recipe book's clicks, focus and layout.
 */
public class BrewKettleScreen extends AbstractRecipeBookScreen<BrewKettleMenu>
{
	private static final Identifier BACKGROUND = VineAndBarrel.id("textures/gui/brew_kettle.png");
	private static final int HEAT_X = 47, HEAT_Y = 55, HEAT_W = 17, HEAT_H = 15;
	private static final int ARROW_X = 89, ARROW_Y = 25, ARROW_H = 17;
	/** The gauge's frame (for the tooltip) and its liquid area; the glass overlay sits in the texture at GLASS_U/V. */
	private static final int GAUGE_X = 120, GAUGE_Y = 16, GAUGE_W = 20, GAUGE_H = 55;
	private static final int LIQUID_X = 123, LIQUID_Y = 19, LIQUID_W = 14, LIQUID_H = 49;
	private static final int GLASS_U = 176, GLASS_V = 64;

	public BrewKettleScreen(BrewKettleMenu menu, Inventory inventory, Component title) {
		super(menu, new BrewKettleRecipeBookComponent(menu), inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = 28;
	}

	@Override
	protected ScreenPosition getRecipeBookButtonPosition() {
		return new ScreenPosition(this.leftPos + 5, this.height / 2 - 49);
	}

	/** The gauge says what brew it is and how many servings are left; the heat icon says whether it is heated. */
	@Override
	protected void extractTooltip(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
		if (this.menu.getCarried().isEmpty() && this.isHovering(GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
			ItemStack brew = this.menu.getBrew();
			int servings = this.menu.getServings();
			List<Component> lines = brew.isEmpty() || servings <= 0
				? List.of(Component.translatable("tooltip.vineandbarrel.brew_kettle.empty").withStyle(ChatFormatting.GRAY))
				: List.of(brew.getHoverName().copy().withStyle(brew.getRarity().getStyleModifier()),
					(servings == 1 ? Component.translatable("container.vineandbarrel.brew_kettle.one_serving")
						: Component.translatable("container.vineandbarrel.brew_kettle.servings", servings)).withStyle(ChatFormatting.GRAY));
			gui.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
			return;
		}
		if (this.isHovering(HEAT_X, HEAT_Y, HEAT_W, HEAT_H, mouseX, mouseY)) {
			gui.setTooltipForNextFrame(this.font, Component.translatable("container.vineandbarrel.brew_kettle." + (this.menu.isHeated() ? "heated" : "not_heated")), mouseX, mouseY);
			return;
		}
		super.extractTooltip(gui, mouseX, mouseY);
	}

	/** How many of the gauge's pixels the liquid fills: its share of the kettle's servings, at least one while any is left. */
	static int gaugeHeight(int servings) {
		if (servings <= 0) {
			return 0;
		}
		return Mth.clamp(Mth.ceil(servings * (float) LIQUID_H / BrewKettleBlockEntity.MAX_SERVINGS), 1, LIQUID_H);
	}

	static Component servingsText(int servings) {
		return servings == 1
			? Component.translatable("tooltip.vineandbarrel.brew_kettle.single_serving")
			: Component.translatable("tooltip.vineandbarrel.brew_kettle.many_servings", servings);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float a) {
		super.extractBackground(gui, mouseX, mouseY, a);
		gui.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
		if (this.menu.isHeated()) {
			gui.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos + HEAT_X, this.topPos + HEAT_Y, 176, 0, HEAT_W, HEAT_H, 256, 256);
		}
		int progress = this.menu.getBrewProgressScaled();
		gui.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos + ARROW_X, this.topPos + ARROW_Y, 176, 15, progress + 1, ARROW_H, 256, 256);

		// The brew in the sight glass: its colour, a lighter surface line, the glass highlight over it
		int height = gaugeHeight(this.menu.getServings());
		if (height > 0) {
			int color = BaseItems.liquidColor(this.menu.getBrew());
			int rgb = color == -1 ? BaseItems.WATER_COLOR : color;
			int x0 = this.leftPos + LIQUID_X, x1 = x0 + LIQUID_W;
			int y1 = this.topPos + LIQUID_Y + LIQUID_H, y0 = y1 - height;
			gui.fill(x0, y0, x1, y1, 0xFF000000 | rgb);
			gui.fill(x0, y0, x1, y0 + 1, 0xFF000000 | lighter(rgb));
		}
		gui.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos + LIQUID_X, this.topPos + LIQUID_Y, GLASS_U, GLASS_V, LIQUID_W, LIQUID_H, 256, 256);
	}

	/** The liquid's surface: halfway to white. */
	private static int lighter(int rgb) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return ((r + 255) / 2) << 16 | ((g + 255) / 2) << 8 | (b + 255) / 2;
	}
}
