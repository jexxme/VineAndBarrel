package jexxme.vineandbarrel.kettle.client;

import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.BrewingRecipe;
import jexxme.vineandbarrel.kettle.BrewingRecipeBookTab;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;

/**
 * The kettle recipe book's tabs (K9). On this Minecraft a tab is a constant of vanilla's client enum
 * {@link RecipeBookCategories}, which only the loaders can add (Forge: the enum's {@code create}; Fabric: a mixin), under
 * the names here. Everything else about them is here, for both loaders: their order, their icons, which tab a recipe
 * is on. Nothing here is looked up before it is asked for (the loaders name the icon methods while the enum loads).
 */
public final class BrewingRecipeBookCategories
{
	/** The names of the enum constants the loaders add. */
	public static final String SEARCH = "VINEANDBARREL_BREWING_SEARCH";
	public static final String JUICES = "VINEANDBARREL_BREWING_JUICES";
	public static final String WORTS = "VINEANDBARREL_BREWING_WORTS";
	public static final String MISC = "VINEANDBARREL_BREWING_MISC";

	private BrewingRecipeBookCategories() {
	}

	public static RecipeBookCategories search() {
		return RecipeBookCategories.valueOf(SEARCH);
	}

	public static RecipeBookCategories juices() {
		return RecipeBookCategories.valueOf(JUICES);
	}

	public static RecipeBookCategories worts() {
		return RecipeBookCategories.valueOf(WORTS);
	}

	public static RecipeBookCategories misc() {
		return RecipeBookCategories.valueOf(MISC);
	}

	/** The book's tabs, top to bottom: search (every recipe), juices, worts, the rest. */
	public static List<RecipeBookCategories> tabs() {
		return List.of(search(), juices(), worts(), misc());
	}

	/** The tabs whose recipes the search tab shows: all the others. */
	public static List<RecipeBookCategories> searched() {
		return List.of(juices(), worts(), misc());
	}

	// ------------------------------------------------------------------ icons (new stacks each time: the caller keeps them)

	public static List<ItemStack> searchIcons() {
		return List.of(new ItemStack(Items.COMPASS));
	}

	public static List<ItemStack> juicesIcons() {
		return List.of(new ItemStack(BaseItems.SWEET_BERRY_JUICE.get()));
	}

	public static List<ItemStack> wortsIcons() {
		return List.of(new ItemStack(BaseItems.BARLEY_WORT_BOTTLE.get()));
	}

	public static List<ItemStack> miscIcons() {
		return List.of(new ItemStack(KettleRegistry.kettleItem()));
	}

	/** The icons of the tab with this enum constant name (one of the four above). */
	public static List<ItemStack> icons(String name) {
		return switch (name) {
			case SEARCH -> searchIcons();
			case JUICES -> juicesIcons();
			case WORTS -> wortsIcons();
			case MISC -> miscIcons();
			default -> throw new IllegalArgumentException("Not a kettle recipe book tab: " + name);
		};
	}

	// ------------------------------------------------------------------ which tab a recipe is on

	public static RecipeBookCategories of(BrewingRecipeBookTab tab) {
		return switch (tab) {
			case JUICES -> juices();
			case WORTS -> worts();
			case MISC -> misc();
		};
	}

	/** The tab of a {@code vineandbarrel:brewing} recipe (what the client's recipe book asks for every recipe of that type). */
	public static RecipeBookCategories of(Recipe<?> recipe) {
		return recipe instanceof BrewingRecipe brewing ? of(brewing.tab()) : misc();
	}
}
