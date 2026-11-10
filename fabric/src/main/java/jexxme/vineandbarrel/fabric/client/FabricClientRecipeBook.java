package jexxme.vineandbarrel.fabric.client;

import net.minecraft.client.RecipeBookCategories;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * What the client's recipe book mixins ({@code RecipeBookCategoriesMixin}, {@code ClientRecipeBookMixin}) answer for a
 * recipe book of ours: its tabs, and the tab of each of its recipes. Set once at client start by
 * {@link FabricClientRegistrar#recipeBook} (the Brew Kettle's is the only one); NeoForge keeps the same in its
 * {@code RegisterRecipeBookCategoriesEvent}.
 */
public final class FabricClientRecipeBook
{
	private record Book(RecipeBookType type, List<RecipeBookCategories> tabs, RecipeType<?> recipeType, Function<RecipeHolder<?>, RecipeBookCategories> tabOf)
	{
	}

	private static volatile @Nullable Book book;

	private FabricClientRecipeBook() {
	}

	static void set(RecipeBookType type, List<RecipeBookCategories> tabs, RecipeType<?> recipeType, Function<RecipeHolder<?>, RecipeBookCategories> tabOf) {
		book = new Book(type, List.copyOf(tabs), recipeType, tabOf);
	}

	/** The tabs of the book a menu with this type shows, or null if it is not ours (vanilla's own answer stands). */
	public static @Nullable List<RecipeBookCategories> tabs(RecipeBookType type) {
		Book ours = book;
		return ours != null && ours.type() == type ? ours.tabs() : null;
	}

	/** The tab this recipe is on, or null if it is not a recipe of our book (vanilla's own answer stands). */
	public static @Nullable RecipeBookCategories tabOf(RecipeHolder<?> recipe) {
		Book ours = book;
		return ours != null && recipe.value().getType() == ours.recipeType() ? ours.tabOf().apply(recipe) : null;
	}
}
