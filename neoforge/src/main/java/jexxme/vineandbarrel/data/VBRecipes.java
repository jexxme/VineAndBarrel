package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.concurrent.CompletableFuture;

/** Recipes (1.21.1: a classic data provider; it runs under vanilla's provider name "Recipes"). */
public class VBRecipes extends RecipeProvider
{
	public VBRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	protected void buildRecipes(RecipeOutput output) {
		// C7 storage blocks, crafted both ways: 9 produce <-> 1 block (FD crates, vanilla hay bale)
		storage(output, RecipeCategory.FOOD, VBItems.RED_GRAPES.get(), VBItems.GRAPE_CRATE.get(), "red_grapes_from_crate");
		storage(output, RecipeCategory.FOOD, VBItems.WHITE_GRAPES.get(), VBItems.WHITE_GRAPE_CRATE.get(), "white_grapes_from_crate");
		storage(output, RecipeCategory.MISC, VBItems.HOPS.get(), VBItems.HOPS_CRATE.get(), "hops_from_crate");
		storage(output, RecipeCategory.MISC, VBItems.BARLEY.get(), VBItems.BARLEY_BALE.get(), "barley_from_bale");

		// The Trellis: five sticks crossed into a lattice make four (a ladder takes seven for three)
		ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, VBItems.TRELLIS.get(), 4)
				.define('S', Items.STICK)
				.pattern("S S")
				.pattern(" S ")
				.pattern("S S")
				.unlockedBy("has_stick", has(Items.STICK))
				.save(output, VineAndBarrel.id("trellis"));
	}

	private static void storage(RecipeOutput output, RecipeCategory unpackedCategory, ItemLike unpacked, ItemLike packed, String unpackingId) {
		String packedId = VineAndBarrel.id(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(packed.asItem()).getPath()).toString();
		nineBlockStorageRecipes(output, unpackedCategory, unpacked, RecipeCategory.BUILDING_BLOCKS, packed,
				packedId, null, VineAndBarrel.id(unpackingId).toString(), null);
	}
}
