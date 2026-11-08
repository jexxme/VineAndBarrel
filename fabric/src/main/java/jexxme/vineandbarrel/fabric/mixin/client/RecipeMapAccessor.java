package jexxme.vineandbarrel.fabric.mixin.client;

import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/** {@link RecipeMap}'s private constructor, for {@code FabricRecipes} (an accessor mixin holds nothing else). */
@Mixin(RecipeMap.class)
public interface RecipeMapAccessor
{
	@Invoker("<init>")
	static RecipeMap vineandbarrel$create(Multimap<RecipeType<?>, RecipeHolder<?>> byType, Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey) {
		throw new AssertionError("mixin");
	}
}
