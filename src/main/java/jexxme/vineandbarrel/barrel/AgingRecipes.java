package jexxme.vineandbarrel.barrel;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
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
 * Finding the aging recipe of a base, the same way on server and client. The aging recipes are sent to clients (NeoForge
 * recipe sync), so a client knows exactly which clicks a barrel takes. If two recipes share a base, the one with the
 * lowest id wins, always, and a warning names both. A recipe whose drink names no vessel is reported and left out,
 * right after the recipes load.
 */
@EventBusSubscriber(modid = VineAndBarrel.MODID)
public final class AgingRecipes
{
	private static volatile List<RecipeHolder<AgingRecipe>> clientRecipes = List.of();
	private static volatile @Nullable RecipeMap sortedFor;
	private static volatile List<RecipeHolder<AgingRecipe>> sortedServer = List.of();
	private static final Set<Item> WARNED = ConcurrentHashMap.newKeySet();

	private AgingRecipes() {
	}

	@SubscribeEvent
	public static void onDatapackSync(OnDatapackSyncEvent event) {
		event.sendRecipes(BarrelRegistry.AGING.get());
		// After every reload: check the new recipes now, so a broken data pack is reported right away
		ServerLevel overworld = event.getPlayerList().getServer().overworld();
		if (overworld != null) {
			all(overworld);
		}
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		all(event.getServer().overworld());
	}

	/** Called by the client when the server's recipes arrive (and with an empty list when leaving a world). */
	public static void setClientRecipes(Collection<RecipeHolder<AgingRecipe>> recipes) {
		clientRecipes = usable(recipes, message -> {
		});
	}

	/** Every aging recipe, sorted by id. */
	public static List<RecipeHolder<AgingRecipe>> all(Level level) {
		if (level instanceof ServerLevel serverLevel) {
			RecipeMap map = serverLevel.recipeAccess().recipeMap();
			if (sortedFor != map) {
				sortedServer = usable(map.byType(BarrelRegistry.AGING.get()), VineAndBarrel.LOGGER::error);
				sortedFor = map;
				WARNED.clear();
			}
			return sortedServer;
		}
		return clientRecipes;
	}

	/** The recipes that can work, sorted by id; each one that can't is reported (once per load) and left out. */
	public static List<RecipeHolder<AgingRecipe>> usable(Collection<RecipeHolder<AgingRecipe>> recipes, Consumer<String> report) {
		List<RecipeHolder<AgingRecipe>> list = new ArrayList<>();
		for (RecipeHolder<AgingRecipe> holder : recipes) {
			Optional<String> problem = holder.value().problem();
			if (problem.isPresent()) {
				report.accept("Ignoring aging recipe " + holder.id().identifier() + ": " + problem.get());
			} else {
				list.add(holder);
			}
		}
		list.sort(Comparator.comparing(holder -> holder.id().identifier().toString()));
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
							chosen.id().identifier(), holder.id().identifier(), stack.getItem(), chosen.id().identifier());
					}
					break;
				}
			}
		}
		return chosen == null ? null : chosen.value();
	}
}
