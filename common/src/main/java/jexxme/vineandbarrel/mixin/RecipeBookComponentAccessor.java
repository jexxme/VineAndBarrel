package jexxme.vineandbarrel.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The recipe book's search field, so the "Watch it" key doesn't take a G typed into it. */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor
{
	@Accessor("searchBox")
	@Nullable EditBox vineandbarrel$searchBox();
}
