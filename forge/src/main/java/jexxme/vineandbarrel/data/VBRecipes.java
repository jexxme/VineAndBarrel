package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.function.Consumer;

/** Recipes (1.20.1: a classic data provider; it runs under vanilla's provider name "Recipes"). */
public class VBRecipes extends RecipeProvider
{
	public VBRecipes(PackOutput output) {
		super(output);
	}

	@Override
	protected void buildRecipes(Consumer<FinishedRecipe> output) {
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

	private static void storage(Consumer<FinishedRecipe> output, RecipeCategory unpackedCategory, ItemLike unpacked, ItemLike packed, String unpackingId) {
		ResourceLocation packedId = VineAndBarrel.id(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(packed.asItem()).getPath());
		// vanilla's nineBlockStorageRecipes with both recipe names given (private on 1.20.1), so written out here
		ShapelessRecipeBuilder.shapeless(unpackedCategory, unpacked, 9).requires(packed).unlockedBy(getHasName(packed), has(packed))
				.save(output, VineAndBarrel.id(unpackingId));
		ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, packed).define('#', unpacked).pattern("###").pattern("###").pattern("###")
				.unlockedBy(getHasName(unpacked), has(unpacked)).save(output, packedId);
	}
}
