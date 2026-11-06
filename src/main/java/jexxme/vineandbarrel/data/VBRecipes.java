package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.MultiRegistryBootstrap;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;

import java.util.Set;

/** Recipes, generated as datapack registry entries (26.3). */
public class VBRecipes extends RecipeProvider
{
	public VBRecipes(BootstrapContext<Recipe<?>> recipeOutput, BootstrapContext<Advancement> advancementOutput) {
		super(recipeOutput, advancementOutput);
	}

	public static MultiRegistryBootstrap bootstrap() {
		return new MultiRegistryBootstrap()
		{
			@Override
			public Set<ResourceKey<? extends Registry<?>>> requestedRegistries() {
				return Set.of(Registries.RECIPE, Registries.ADVANCEMENT);
			}

			@Override
			public void run(MultiRegistryBootstrap.BootstrapGetter registries) {
				new VBRecipes(registries.get(Registries.RECIPE), registries.get(Registries.ADVANCEMENT)).buildRecipes();
			}
		};
	}

	@Override
	protected void buildRecipes() {
		// C7 storage blocks, crafted both ways: 9 produce <-> 1 block (FD crates, vanilla hay bale)
		storage(RecipeCategory.FOOD, VBItems.RED_GRAPES.get(), VBItems.GRAPE_CRATE.get(), "red_grapes_from_crate");
		storage(RecipeCategory.MISC, VBItems.HOPS.get(), VBItems.HOPS_CRATE.get(), "hops_from_crate");
		storage(RecipeCategory.MISC, VBItems.BARLEY.get(), VBItems.BARLEY_BALE.get(), "barley_from_bale");
	}

	private void storage(RecipeCategory unpackedCategory, ItemLike unpacked, ItemLike packed, String unpackingId) {
		String packedId = VineAndBarrel.id(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(packed.asItem()).getPath()).toString();
		nineBlockStorageRecipes(unpackedCategory, unpacked, RecipeCategory.BUILDING_BLOCKS, packed,
				packedId, null, VineAndBarrel.id(unpackingId).toString(), null);
	}
}
