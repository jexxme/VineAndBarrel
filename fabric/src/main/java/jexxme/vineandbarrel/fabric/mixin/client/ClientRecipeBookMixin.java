package jexxme.vineandbarrel.fabric.mixin.client;

import jexxme.vineandbarrel.fabric.client.FabricClientRecipeBook;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The tab of a recipe of our own recipe book (the Brew Kettle's), where the client sorts every recipe into its tab
 * (NeoForge: the recipe category finder of {@code RegisterRecipeBookCategoriesEvent}). Vanilla would log "Unknown recipe
 * category" for the type and file the recipe under UNKNOWN.
 */
@Mixin(ClientRecipeBook.class)
abstract class ClientRecipeBookMixin
{
	@Inject(method = "getCategory", at = @At("HEAD"), cancellable = true)
	private static void vineandbarrel$brewingTab(Recipe<?> recipe, CallbackInfoReturnable<RecipeBookCategories> callback) {
		RecipeBookCategories tab = FabricClientRecipeBook.tabOf(recipe);
		if (tab != null) {
			callback.setReturnValue(tab);
		}
	}
}
