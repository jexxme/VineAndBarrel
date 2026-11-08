package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.level.ItemLike;

import java.util.concurrent.CompletableFuture;

/** Recipes (26.2: a classic data provider, see {@link Runner}). */
public class VBRecipes extends RecipeProvider
{
	public VBRecipes(HolderLookup.Provider registries, RecipeOutput output) {
		super(registries, output);
	}

	public static class Runner extends RecipeProvider.Runner
	{
		public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
			super(output, registries);
		}

		@Override
		protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
			return new VBRecipes(registries, output);
		}

		@Override
		public String getName() {
			return "Vine & Barrel recipes";
		}
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
