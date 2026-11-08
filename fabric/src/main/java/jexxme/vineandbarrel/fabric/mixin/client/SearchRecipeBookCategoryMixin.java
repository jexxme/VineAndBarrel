package jexxme.vineandbarrel.fabric.mixin.client;

import jexxme.vineandbarrel.fabric.FabricRecipeBook;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * The kettle book's search tab, a new constant of vanilla's search tabs (declared by the class tweaker's
 * {@code extend-enum}; NeoForge registers it with an event). Its categories are only known once ours are registered, so
 * they come from {@link FabricRecipeBook#searchIncluded()}, not the constructor.
 */
@Mixin(SearchRecipeBookCategory.class)
enum SearchRecipeBookCategoryMixin
{
	VINEANDBARREL_BREWING;

	SearchRecipeBookCategoryMixin(RecipeBookCategory... included) {
	}

	@Inject(method = "includedCategories", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$brewingCategories(CallbackInfoReturnable<List<RecipeBookCategory>> callback) {
		if (((Enum<?>) (Object) this).name().equals(FabricRecipeBook.BREWING)) {
			callback.setReturnValue(FabricRecipeBook.searchIncluded());
		}
	}
}
