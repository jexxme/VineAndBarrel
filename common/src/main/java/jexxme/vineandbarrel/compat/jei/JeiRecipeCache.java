package jexxme.vineandbarrel.compat.jei;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.barrel.AgingRecipes;
import jexxme.vineandbarrel.kettle.BrewingRecipe;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Comparator;
import java.util.List;

/**
 * The recipes the server sent, for the JEI plugin (as FD's JEI integration reads them). 1.21.1: vanilla sends every
 * recipe to the client, so they are read from the client world's recipe manager when asked, and nothing is kept here.
 * Uses no JEI class, so it loads fine without JEI. Client only.
 */
public final class JeiRecipeCache
{
	private JeiRecipeCache() {
	}

	/** Every brewing recipe, sorted by id (none while in no world). */
	public static List<RecipeHolder<BrewingRecipe>> brewing() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return List.of();
		}
		return level.getRecipeManager().getAllRecipesFor(KettleRegistry.BREWING.get()).stream()
			.sorted(Comparator.comparing(holder -> holder.id().toString()))
			.toList();
	}

	/** Every aging recipe that can work, sorted by id (the same ones the barrel takes). */
	public static List<RecipeHolder<AgingRecipe>> aging() {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? List.of() : AgingRecipes.all(level);
	}
}
