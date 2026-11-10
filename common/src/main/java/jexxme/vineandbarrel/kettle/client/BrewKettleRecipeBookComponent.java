package jexxme.vineandbarrel.kettle.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.mixin.RecipeBookComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * The kettle's recipe book (K9), FD's {@code CookingPotRecipeBookComponent} (MIT) with our tabs and filter button. The
 * tabs are the book type's ({@link BrewingRecipeBookCategories}, registered by {@link KettleClient}); which slots are
 * the grid is the menu's say ({@code BrewKettleMenu#getSize}).
 */
public class BrewKettleRecipeBookComponent extends RecipeBookComponent
{
	private static final WidgetSprites FILTER_BUTTON = new WidgetSprites(
		VineAndBarrel.id("recipe_book/brew_kettle_enabled"),
		VineAndBarrel.id("recipe_book/brew_kettle_disabled"),
		VineAndBarrel.id("recipe_book/brew_kettle_enabled_highlighted"),
		VineAndBarrel.id("recipe_book/brew_kettle_disabled_highlighted"));

	/** The book's own place on the screen (vanilla keeps these private), for {@link #showSearchTab}. */
	private int screenWidth;
	private int screenHeight;
	private boolean narrow;

	@Override
	public void init(int width, int height, Minecraft minecraft, boolean widthTooNarrow, RecipeBookMenu<?, ?> menu) {
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

	@Override
	protected void initFilterButtonTextures() {
		this.filterButton.initTextureValues(FILTER_BUTTON);
	}

	@Override
	protected Component getRecipeFilterName() {
		return Component.translatable("container.vineandbarrel.recipe_book.brewable");
	}

	@Override
	public void setupGhostRecipe(RecipeHolder<?> recipe, List<Slot> slots) {
		// Only the ingredients: the brew has no slot (the gauge shows it once it is brewed)
		this.ghostRecipe.setRecipe(recipe);
		this.placeRecipe(this.menu.getGridWidth(), this.menu.getGridHeight(), this.menu.getResultSlotIndex(), recipe, recipe.value().getIngredients().iterator(), 0);
	}
}
