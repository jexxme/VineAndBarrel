package jexxme.vineandbarrel.neoforge.client;

import jexxme.vineandbarrel.kettle.client.BrewingRecipeBookCategories;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

import java.util.List;
import java.util.function.Supplier;

/**
 * The constructor arguments of the Brew Kettle's recipe book tabs, which NeoForge adds to {@link RecipeBookCategories}
 * (META-INF/enumextensions.json names these fields). Loaded while that enum loads, so nothing else may be in here; the
 * icons touch our items and are only asked for when a tab is drawn.
 */
public final class BrewingBookEnumParams
{
	public static final EnumProxy<RecipeBookCategories> SEARCH = new EnumProxy<>(RecipeBookCategories.class,
		(Supplier<List<ItemStack>>) BrewingRecipeBookCategories::searchIcons);
	public static final EnumProxy<RecipeBookCategories> JUICES = new EnumProxy<>(RecipeBookCategories.class,
		(Supplier<List<ItemStack>>) BrewingRecipeBookCategories::juicesIcons);
	public static final EnumProxy<RecipeBookCategories> WORTS = new EnumProxy<>(RecipeBookCategories.class,
		(Supplier<List<ItemStack>>) BrewingRecipeBookCategories::wortsIcons);
	public static final EnumProxy<RecipeBookCategories> MISC = new EnumProxy<>(RecipeBookCategories.class,
		(Supplier<List<ItemStack>>) BrewingRecipeBookCategories::miscIcons);

	private BrewingBookEnumParams() {
	}
}
