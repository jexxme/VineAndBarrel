package jexxme.vineandbarrel.fabric.client;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import jexxme.vineandbarrel.fabric.mixin.client.RecipeMapAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Collection;

/**
 * Builds a {@link RecipeMap} from the recipes Fabric syncs to the client, so the common code gets the same thing as
 * from NeoForge's {@code RecipesReceivedEvent}.
 */
final class FabricRecipes
{
	private FabricRecipes() {
	}

	static RecipeMap map(Collection<RecipeHolder<?>> recipes) {
		ImmutableMultimap.Builder<RecipeType<?>, RecipeHolder<?>> byType = ImmutableMultimap.builder();
		ImmutableMap.Builder<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey = ImmutableMap.builder();
		for (RecipeHolder<?> recipe : recipes) {
			byType.put(recipe.value().getType(), recipe);
			byKey.put(recipe.id(), recipe);
		}
		return RecipeMapAccessor.vineandbarrel$create(byType.build(), byKey.buildKeepingLast());
	}
}
