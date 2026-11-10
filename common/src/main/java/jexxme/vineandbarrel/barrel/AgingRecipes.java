package jexxme.vineandbarrel.barrel;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finding the aging recipe of a base, the same way on server and client. The aging recipes are sent to clients (1.20.1:
 * vanilla sends every recipe, and the client reads them from its own recipe manager), so a client knows exactly which
 * clicks a barrel takes. If two recipes share a base, the one with the
 * lowest id wins, always, and a warning names both. A recipe whose drink names no vessel is reported and left out,
 * right after the recipes load.
 */
public final class AgingRecipes
{
	/**
	 * The sorted recipes of one side, and what they were made from: the recipe manager's aging recipes. 1.20.1's recipe
	 * manager hands out a new list on every call, but the recipes in it are new objects only after a reload (server) or
	 * a recipe packet (client), so the same recipes in the same order are the same load.
	 */
	private record Sorted(List<AgingRecipe> madeFrom, List<AgingRecipe> recipes)
	{
		static final Sorted NONE = new Sorted(List.of(), List.of());

		boolean isMadeFrom(List<AgingRecipe> loaded) {
			if (loaded.size() != madeFrom.size()) {
				return false;
			}
			for (int i = 0; i < loaded.size(); i++) {
				if (loaded.get(i) != madeFrom.get(i)) {
					return false;
				}
			}
			return true;
		}
	}

	private static volatile Sorted sortedClient = Sorted.NONE;
	private static volatile Sorted sortedServer = Sorted.NONE;
	private static final Set<Item> WARNED = ConcurrentHashMap.newKeySet();

	private AgingRecipes() {
	}

	/** When recipes are sent to the players (after every reload): check the new recipes now, so a broken data pack is reported right away. */
	public static void onDatapackSync(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		if (overworld != null) {
			all(overworld);
		}
	}

	public static void onServerStarted(MinecraftServer server) {
		all(server.overworld());
	}

	/** When the player leaves a world or server: the client forgets that world's recipes. */
	public static void clearClientRecipes() {
		sortedClient = Sorted.NONE;
	}

	/** Every aging recipe, sorted by id. */
	public static List<AgingRecipe> all(Level level) {
		RecipeManager manager = level.getRecipeManager();
		List<AgingRecipe> loaded = manager.getAllRecipesFor(BarrelRegistry.AGING.get());
		if (level instanceof ServerLevel) {
			Sorted sorted = sortedServer;
			if (!sorted.isMadeFrom(loaded)) {
				sorted = new Sorted(loaded, usable(loaded, VineAndBarrel.LOGGER::error));
				sortedServer = sorted;
				WARNED.clear();
			}
			return sorted.recipes;
		}
		Sorted sorted = sortedClient;
		if (!sorted.isMadeFrom(loaded)) {
			sorted = new Sorted(loaded, usable(loaded, message -> {
			}));
			sortedClient = sorted;
		}
		return sorted.recipes;
	}

	/** The recipes that can work, sorted by id; each one that can't is reported (once per load) and left out. */
	public static List<AgingRecipe> usable(Collection<AgingRecipe> recipes, Consumer<String> report) {
		List<AgingRecipe> list = new ArrayList<>();
		for (AgingRecipe recipe : recipes) {
			Optional<String> problem = recipe.problem();
			if (problem.isPresent()) {
				report.accept("Ignoring aging recipe " + recipe.getId() + ": " + problem.get());
			} else {
				list.add(recipe);
			}
		}
		list.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
		return List.copyOf(list);
	}

	/** The recipe this stack is the base (or bucket of base) of: the lowest id if several claim it. */
	public static @Nullable AgingRecipe forBase(Level level, ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		AgingRecipe chosen = null;
		for (AgingRecipe recipe : all(level)) {
			if (recipe.servingsOf(stack) > 0) {
				if (chosen == null) {
					chosen = recipe;
				} else {
					if (!level.isClientSide() && WARNED.add(stack.getItem())) {
						VineAndBarrel.LOGGER.warn("Aging recipes {} and {} both start from {}; using {} (the lowest id)",
							chosen.getId(), recipe.getId(), stack.getItem(), chosen.getId());
					}
					break;
				}
			}
		}
		return chosen;
	}
}
