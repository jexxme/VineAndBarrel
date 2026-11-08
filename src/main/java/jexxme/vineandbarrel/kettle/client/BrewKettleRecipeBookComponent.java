package jexxme.vineandbarrel.kettle.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.BrewKettleMenu;
import jexxme.vineandbarrel.kettle.BrewingRecipeDisplay;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

import java.util.List;
import java.util.Optional;

/** The kettle's recipe book (K9), FD's {@code CookingPotRecipeBookComponent} (MIT) with our tabs and filter button. */
public class BrewKettleRecipeBookComponent extends RecipeBookComponent<BrewKettleMenu>
{
	private static final WidgetSprites FILTER_BUTTON = new WidgetSprites(
		VineAndBarrel.id("recipe_book/brew_kettle_enabled"),
		VineAndBarrel.id("recipe_book/brew_kettle_disabled"),
		VineAndBarrel.id("recipe_book/brew_kettle_enabled_highlighted"),
		VineAndBarrel.id("recipe_book/brew_kettle_disabled_highlighted"));

	public BrewKettleRecipeBookComponent(BrewKettleMenu menu) {
		super(menu, tabs());
	}

	/** Built when the screen opens, never statically: the holders aren't bound before that. */
	private static List<TabInfo> tabs() {
		return List.of(
			new TabInfo(Items.COMPASS.getDefaultInstance(), Optional.empty(), KettleRegistry.BREWING_SEARCH.get()),
			new TabInfo(BaseItems.SWEET_BERRY_JUICE.get().getDefaultInstance(), Optional.empty(), KettleRegistry.BREWING_JUICES.get()),
			new TabInfo(BaseItems.BARLEY_WORT_BOTTLE.get().getDefaultInstance(), Optional.empty(), KettleRegistry.BREWING_WORTS.get()),
			new TabInfo(KettleRegistry.kettleItem().getDefaultInstance(), Optional.empty(), KettleRegistry.BREWING_MISC.get()));
	}

	@Override
	protected WidgetSprites getFilterButtonTextures() {
		return FILTER_BUTTON;
	}

	@Override
	protected boolean isCraftingSlot(Slot slot) {
		return slot.index < BrewKettleMenu.INDEX_INVENTORY;
	}

	@Override
	protected void selectMatchingRecipes(RecipeCollection recipes, StackedItemContents contents) {
		recipes.selectRecipes(contents, display -> display instanceof BrewingRecipeDisplay);
	}

	@Override
	protected Component getRecipeFilterName() {
		return Component.translatable("container.vineandbarrel.recipe_book.brewable");
	}

	@Override
	protected void fillGhostRecipe(GhostSlots ghostSlots, RecipeDisplay display, ContextMap context) {
		// Only the ingredients: the brew has no slot (the gauge shows it once it is brewed)
		if (display instanceof BrewingRecipeDisplay brewing) {
			for (int i = 0; i < brewing.ingredients().size(); ++i) {
				ghostSlots.setInput(this.menu.getSlot(i), context, brewing.ingredients().get(i));
			}
		}
	}
}
