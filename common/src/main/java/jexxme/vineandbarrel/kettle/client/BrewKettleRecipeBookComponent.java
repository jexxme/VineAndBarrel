package jexxme.vineandbarrel.kettle.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.mixin.RecipeBookComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.RecipeBookCategories;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;

/**
 * The kettle's recipe book (K9), FD's {@code CookingPotRecipeBookComponent} (MIT) with our tabs and filter button. The
 * tabs are the book type's ({@link BrewingRecipeBookCategories}, registered by {@link KettleClient}); which slots are
 * the grid is the menu's say ({@code BrewKettleMenu#getSize}).
 */
public class BrewKettleRecipeBookComponent extends RecipeBookComponent
{
	/** The filter button's four pictures, 26x16 each (this Minecraft has no GUI sprites: each is drawn as its own texture). */
	private static final ResourceLocation FILTER_ENABLED = VineAndBarrel.id("textures/gui/sprites/recipe_book/brew_kettle_enabled.png");
	private static final ResourceLocation FILTER_DISABLED = VineAndBarrel.id("textures/gui/sprites/recipe_book/brew_kettle_disabled.png");
	private static final ResourceLocation FILTER_ENABLED_HIGHLIGHTED = VineAndBarrel.id("textures/gui/sprites/recipe_book/brew_kettle_enabled_highlighted.png");
	private static final ResourceLocation FILTER_DISABLED_HIGHLIGHTED = VineAndBarrel.id("textures/gui/sprites/recipe_book/brew_kettle_disabled_highlighted.png");

	/** The book's own place on the screen (vanilla keeps these private), for {@link #showSearchTab}. */
	private int screenWidth;
	private int screenHeight;
	private boolean narrow;

	@Override
	public void init(int width, int height, Minecraft minecraft, boolean widthTooNarrow, RecipeBookMenu<?> menu) {
		this.screenWidth = width;
		this.screenHeight = height;
		this.narrow = widthTooNarrow;
		super.init(width, height, minecraft, widthTooNarrow, menu);
	}

	@Override
	public void initVisuals() {
		super.initVisuals();
		showSearchTab();
	}

	@Override
	public void recipesUpdated() {
		super.recipesUpdated();
		showSearchTab();
	}

	/**
	 * The search tab always shows, like the crafting table's and the furnace's. Vanilla only knows those two by name and
	 * hides every other tab that has no recipe to show, so ours is put back and the tabs are stacked again (vanilla's
	 * layout: 27 px each, from 3 px below the book's top, 30 px left of it).
	 */
	private void showSearchTab() {
		RecipeBookCategories search = BrewingRecipeBookCategories.search();
		int x = (this.screenWidth - IMAGE_WIDTH) / 2 - (this.narrow ? 0 : 86) - 30;
		int y = (this.screenHeight - IMAGE_HEIGHT) / 2 + 3;
		int row = 0;
		for (RecipeBookTabButton tab : ((RecipeBookComponentAccessor) this).vineandbarrel$tabButtons()) {
			if (tab.getCategory() == search) {
				tab.visible = true;
			}
			if (tab.visible) {
				tab.setPosition(x, y + 27 * row++);
			}
		}
	}

	/**
	 * Vanilla's filter button on this Minecraft cuts its four states out of one 256x256 sheet. Ours are four pictures, so
	 * the button vanilla just made is swapped for one that draws them (same place, state and tooltip; vanilla only ever
	 * reaches the button through this field).
	 */
	@Override
	protected void initFilterButtonTextures() {
		StateSwitchingButton vanilla = this.filterButton;
		StateSwitchingButton button = new FilterButton(vanilla.getX(), vanilla.getY(), vanilla.getWidth(), vanilla.getHeight(), vanilla.isStateTriggered());
		button.setTooltip(vanilla.getTooltip());
		this.filterButton = button;
	}

	private static final class FilterButton extends StateSwitchingButton
	{
		FilterButton(int x, int y, int width, int height, boolean triggered) {
			super(x, y, width, height, triggered);
		}

		@Override
		public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float a) {
			boolean highlighted = this.isHoveredOrFocused();
			ResourceLocation picture = this.isStateTriggered
				? highlighted ? FILTER_ENABLED_HIGHLIGHTED : FILTER_ENABLED
				: highlighted ? FILTER_DISABLED_HIGHLIGHTED : FILTER_DISABLED;
			RenderSystem.disableDepthTest();
			gui.blit(picture, this.getX(), this.getY(), 0.0F, 0.0F, this.width, this.height, this.width, this.height);
			RenderSystem.enableDepthTest();
		}
	}

	@Override
	protected Component getRecipeFilterName() {
		return Component.translatable("container.vineandbarrel.recipe_book.brewable");
	}

	@Override
	public void setupGhostRecipe(Recipe<?> recipe, List<Slot> slots) {
		// Only the ingredients: the brew has no slot (the gauge shows it once it is brewed)
		this.ghostRecipe.setRecipe(recipe);
		this.placeRecipe(this.menu.getGridWidth(), this.menu.getGridHeight(), this.menu.getResultSlotIndex(), recipe, recipe.getIngredients().iterator(), 0);
	}
}
