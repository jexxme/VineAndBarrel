package jexxme.vineandbarrel.compat.jei;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.barrel.AgingRecipes;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.kettle.BrewingRecipe;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

import java.util.Comparator;
import java.util.List;

/**
 * The recipes the server sent, kept for the JEI plugin (as FD's JEI integration keeps them). Uses no JEI class, so it
 * loads fine without JEI.
 */
@EventBusSubscriber(modid = VineAndBarrel.MODID, value = Dist.CLIENT)
public final class JeiRecipeCache
{
	private static RecipeMap recipes = RecipeMap.EMPTY;

	private JeiRecipeCache() {
	}

	@SubscribeEvent
	public static void onRecipesReceived(RecipesReceivedEvent event) {
		recipes = event.getRecipeMap();
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
