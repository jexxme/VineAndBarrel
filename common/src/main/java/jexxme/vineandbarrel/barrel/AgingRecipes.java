package jexxme.vineandbarrel.barrel;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
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
 * Finding the aging recipe of a base, the same way on server and client. The aging recipes are sent to clients (1.21.1:
 * vanilla sends every recipe, and the client reads them from its own recipe manager), so a client knows exactly which
 * clicks a barrel takes. If two recipes share a base, the one with the
 * lowest id wins, always, and a warning names both. A recipe whose drink names no vessel is reported and left out,
 * right after the recipes load.
 */
public final class AgingRecipes
{
	/**
	 * The sorted recipes of one side, and what they were made from: the recipe manager's collection of all recipes, which
	 * is a new object after every reload (server) and every recipe packet (client).
	 */
	private record Sorted(@Nullable Object madeFrom, List<RecipeHolder<AgingRecipe>> recipes)
	{
		static final Sorted NONE = new Sorted(null, List.of());
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
	public static List<RecipeHolder<AgingRecipe>> all(Level level) {
		RecipeManager manager = level.getRecipeManager();
		Object loaded = manager.getRecipes();
		if (level instanceof ServerLevel) {
			Sorted sorted = sortedServer;
			if (sorted.madeFrom != loaded) {
				sorted = new Sorted(loaded, usable(manager.getAllRecipesFor(BarrelRegistry.AGING.get()), VineAndBarrel.LOGGER::error));
				sortedServer = sorted;
				WARNED.clear();
			}
			return sorted.recipes;
		}
		Sorted sorted = sortedClient;
		if (sorted.madeFrom != loaded) {
			sorted = new Sorted(loaded, usable(manager.getAllRecipesFor(BarrelRegistry.AGING.get()), message -> {
			}));
			sortedClient = sorted;
		}
		return sorted.recipes;
	}

	/** The recipes that can work, sorted by id; each one that can't is reported (once per load) and left out. */
	public static List<RecipeHolder<AgingRecipe>> usable(Collection<RecipeHolder<AgingRecipe>> recipes, Consumer<String> report) {
		List<RecipeHolder<AgingRecipe>> list = new ArrayList<>();
		for (RecipeHolder<AgingRecipe> holder : recipes) {
			Optional<String> problem = holder.value().problem();
			if (problem.isPresent()) {
				report.accept("Ignoring aging recipe " + holder.id() + ": " + problem.get());
			} else {
				list.add(holder);
			}
		}
		list.sort(Comparator.comparing(holder -> holder.id().toString()));
		return List.copyOf(list);
	}

	/** The recipe this stack is the base (or bucket of base) of: the lowest id if several claim it. */
	public static @Nullable AgingRecipe forBase(Level level, ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		RecipeHolder<AgingRecipe> chosen = null;
		for (RecipeHolder<AgingRecipe> holder : all(level)) {
			if (holder.value().servingsOf(stack) > 0) {
				if (chosen == null) {
					chosen = holder;
				} else {
					if (!level.isClientSide() && WARNED.add(stack.getItem())) {
						VineAndBarrel.LOGGER.warn("Aging recipes {} and {} both start from {}; using {} (the lowest id)",
							chosen.id(), holder.id(), stack.getItem(), chosen.id());
					}
					break;
				}
			}
		}
		return chosen == null ? null : chosen.value();
	}
}
