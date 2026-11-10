package jexxme.vineandbarrel.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * The recipe book's search field, so the "Watch it" key doesn't take a G typed into it, and its tab buttons, so the
 * kettle's book can keep its search tab showing (see {@code BrewKettleRecipeBookComponent}).
 */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor
{
	@Accessor("searchBox")
	@Nullable EditBox vineandbarrel$searchBox();

	@Accessor("tabButtons")
	List<RecipeBookTabButton> vineandbarrel$tabButtons();
}
