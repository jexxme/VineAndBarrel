package jexxme.vineandbarrel.compat.jei;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.barrel.AgingRecipes;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.kettle.BrewingRecipe;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;

import java.util.Comparator;
import java.util.List;

/**
 * The recipes the server sent, kept for the JEI plugin (as FD's JEI integration keeps them). Uses no JEI class, so it
 * loads fine without JEI.
 */
public final class JeiRecipeCache
{
	private static RecipeMap recipes = RecipeMap.EMPTY;

	private JeiRecipeCache() {
	}

	/** When the server's recipes arrive on the client (each loader calls this from its own event). */
	public static void onRecipesReceived(RecipeMap received) {
		recipes = received;
	}

	/** Every brewing recipe, sorted by id. */
	public static List<RecipeHolder<BrewingRecipe>> brewing() {
		return recipes.byType(KettleRegistry.BREWING.get()).stream()
			.sorted(Comparator.comparing(holder -> holder.id().identifier().toString()))
			.toList();
	}

	/** Every aging recipe that can work, sorted by id (the same ones the barrel takes). */
	public static List<RecipeHolder<AgingRecipe>> aging() {
		return AgingRecipes.usable(recipes.byType(BarrelRegistry.AGING.get()), message -> {
		});
	}
}
