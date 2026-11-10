package jexxme.vineandbarrel.kettle.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.BrewKettleMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The Brew Kettle screen: FD's {@code CookingPotScreen} (MIT) without the meal and container slots. The ingredient grid,
 * heat indicator, progress arrow, the recipe book (K9) and a sight-glass gauge for the brew: its colour, filled to its
 * share of the kettle's servings. The brew is served by hand like a cauldron, so the screen has nothing to take.
 * The recipe book's clicks, keys, focus and layout are handled here as vanilla's crafting table screen handles them
 * (this Minecraft has no AbstractRecipeBookScreen).
 */
public class BrewKettleScreen extends AbstractContainerScreen<BrewKettleMenu> implements RecipeUpdateListener
{
	private static final ResourceLocation BACKGROUND = VineAndBarrel.id("textures/gui/brew_kettle.png");
	private static final int HEAT_X = 47, HEAT_Y = 55, HEAT_W = 17, HEAT_H = 15;
	private static final int ARROW_X = 89, ARROW_Y = 25, ARROW_W = 24, ARROW_H = 17;
	/** The gauge's frame (for the tooltip) and its liquid area; the glass overlay sits in the texture at GLASS_U/V. */
	private static final int GAUGE_X = 120, GAUGE_Y = 16, GAUGE_W = 20, GAUGE_H = 55;
	private static final int LIQUID_X = 123, LIQUID_Y = 19, LIQUID_W = 14, LIQUID_H = 49;
	private static final int GLASS_U = 176, GLASS_V = 64;

	private final BrewKettleRecipeBookComponent recipeBookComponent = new BrewKettleRecipeBookComponent();
	/** No room for the book beside the kettle: an open book then covers it. */
	private boolean widthTooNarrow;

	public BrewKettleScreen(BrewKettleMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		this.widthTooNarrow = this.width < 379;
		this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow, this.menu);
		this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
		ScreenPosition button = getRecipeBookButtonPosition();
		this.addRenderableWidget(new ImageButton(button.x(), button.y(), 20, 18, RecipeBookComponent.RECIPE_BUTTON_SPRITES, pressed -> {
			this.recipeBookComponent.toggleVisibility();
			this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
			ScreenPosition moved = getRecipeBookButtonPosition();
			pressed.setPosition(moved.x(), moved.y());
		}));
		this.addWidget(this.recipeBookComponent);
		this.titleLabelX = 28;
	}

	protected ScreenPosition getRecipeBookButtonPosition() {
		return new ScreenPosition(this.leftPos + 5, this.height / 2 - 49);
	}

	@Override
	public void containerTick() {
		super.containerTick();
		this.recipeBookComponent.tick();
	}

	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float a) {
		if (this.recipeBookComponent.isVisible() && this.widthTooNarrow) {
			this.renderBackground(gui, mouseX, mouseY, a);
			this.recipeBookComponent.render(gui, mouseX, mouseY, a);
		} else {
			super.render(gui, mouseX, mouseY, a);
			this.recipeBookComponent.render(gui, mouseX, mouseY, a);
			// (no slot is the result: every ghost item is drawn slot-sized)
			this.recipeBookComponent.renderGhostRecipe(gui, this.leftPos, this.topPos, false, a);
		}
		this.renderTooltip(gui, mouseX, mouseY);
		this.recipeBookComponent.renderTooltip(gui, this.leftPos, this.topPos, mouseX, mouseY);
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		return this.recipeBookComponent.keyPressed(key, scanCode, modifiers) || super.keyPressed(key, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char character, int modifiers) {
		return this.recipeBookComponent.charTyped(character, modifiers) || super.charTyped(character, modifiers);
	}

	@Override
	protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
		return (!this.widthTooNarrow || !this.recipeBookComponent.isVisible()) && super.isHovering(x, y, width, height, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.recipeBookComponent.mouseClicked(mouseX, mouseY, button)) {
			this.setFocused(this.recipeBookComponent);
			return true;
		}
		return this.widthTooNarrow && this.recipeBookComponent.isVisible() || super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
		boolean outside = mouseX < left || mouseY < top || mouseX >= left + this.imageWidth || mouseY >= top + this.imageHeight;
		return this.recipeBookComponent.hasClickedOutside(mouseX, mouseY, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, button) && outside;
	}

	@Override
	protected void slotClicked(Slot slot, int slotId, int button, ClickType type) {
		super.slotClicked(slot, slotId, button, type);
		this.recipeBookComponent.slotClicked(slot);
	}

	@Override
	public void recipesUpdated() {
		this.recipeBookComponent.recipesUpdated();
	}

	@Override
	public RecipeBookComponent getRecipeBookComponent() {
		return this.recipeBookComponent;
	}

	/**
	 * The gauge says what brew it is and how many servings are left; the heat icon whether it is heated and what heats it;
	 * a greyed arrow that the ingredients make nothing.
	 */
	@Override
	protected void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
		if (this.menu.getCarried().isEmpty() && this.isHovering(GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
			ItemStack brew = this.menu.getBrew();
			int servings = this.menu.getServings();
			List<Component> lines = new java.util.ArrayList<>(brew.isEmpty() || servings <= 0
				? List.of(Component.translatable("tooltip.vineandbarrel.brew_kettle.empty").withStyle(ChatFormatting.GRAY))
				: List.of(brew.getHoverName().copy().withStyle(brew.getRarity().color()),
					(servings == 1 ? Component.translatable("container.vineandbarrel.brew_kettle.one_serving")
						: Component.translatable("container.vineandbarrel.brew_kettle.servings", servings)).withStyle(ChatFormatting.GRAY)));
			if (this.menu.getBlocks() > 1) {
				// a big kettle: what one batch makes
				lines.add(Component.translatable("container.vineandbarrel.brew_kettle.batch", 4 * this.menu.getBlocks()).withStyle(ChatFormatting.GRAY));
			}
			gui.renderComponentTooltip(this.font, lines, mouseX, mouseY);
			return;
		}
		if (this.isHovering(HEAT_X, HEAT_Y, HEAT_W, HEAT_H, mouseX, mouseY)) {
			gui.renderComponentTooltip(this.font, List.of(
				Component.translatable("container.vineandbarrel.brew_kettle." + (this.menu.isHeated() ? "heated" : "not_heated")),
				Component.translatable("container.vineandbarrel.brew_kettle.heat_sources").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
			return;
		}
		if (this.menu.ingredientsMakeNothing() && this.isHovering(ARROW_X, ARROW_Y, ARROW_W, ARROW_H, mouseX, mouseY)) {
			gui.renderTooltip(this.font, Component.translatable("container.vineandbarrel.brew_kettle.makes_nothing"), mouseX, mouseY);
			return;
		}
		super.renderTooltip(gui, mouseX, mouseY);
	}

	/** How many of the gauge's pixels the liquid fills: its share of the kettle's servings, at least one while any is left. */
	static int gaugeHeight(int servings, int capacity) {
		if (servings <= 0) {
			return 0;
		}
		return Mth.clamp(Mth.ceil(servings * (float) LIQUID_H / capacity), 1, LIQUID_H);
	}

	static Component servingsText(int servings) {
		return servings == 1
			? Component.translatable("tooltip.vineandbarrel.brew_kettle.single_serving")
			: Component.translatable("tooltip.vineandbarrel.brew_kettle.many_servings", servings);
	}

	@Override
	protected void renderBg(GuiGraphics gui, float a, int mouseX, int mouseY) {
		gui.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
		if (this.menu.isHeated()) {
			gui.blit(BACKGROUND, this.leftPos + HEAT_X, this.topPos + HEAT_Y, 176, 0, HEAT_W, HEAT_H);
		}
		int progress = this.menu.getBrewProgressScaled();
		gui.blit(BACKGROUND, this.leftPos + ARROW_X, this.topPos + ARROW_Y, 176, 15, progress + 1, ARROW_H);
		if (this.menu.ingredientsMakeNothing()) {
			// Greyed out: the panel's own grey over the empty arrow
			gui.fill(this.leftPos + ARROW_X, this.topPos + ARROW_Y, this.leftPos + ARROW_X + ARROW_W + 1, this.topPos + ARROW_Y + ARROW_H, 0xA0C6C6C6);
		}

		// The brew in the sight glass: its colour, a lighter surface line, the glass highlight over it
		int height = gaugeHeight(this.menu.getServings(), this.menu.getCapacity());
		if (height > 0) {
			int color = BaseItems.liquidColor(this.menu.getBrew());
			int rgb = color == -1 ? BaseItems.WATER_COLOR : color;
			int x0 = this.leftPos + LIQUID_X, x1 = x0 + LIQUID_W;
			int y1 = this.topPos + LIQUID_Y + LIQUID_H, y0 = y1 - height;
			gui.fill(x0, y0, x1, y1, 0xFF000000 | rgb);
			gui.fill(x0, y0, x1, y0 + 1, 0xFF000000 | lighter(rgb));
		}
		gui.blit(BACKGROUND, this.leftPos + LIQUID_X, this.topPos + LIQUID_Y, GLASS_U, GLASS_V, LIQUID_W, LIQUID_H);
	}

	/** The liquid's surface: halfway to white. */
	private static int lighter(int rgb) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return ((r + 255) / 2) << 16 | ((g + 255) / 2) << 8 | (b + 255) / 2;
	}
}
