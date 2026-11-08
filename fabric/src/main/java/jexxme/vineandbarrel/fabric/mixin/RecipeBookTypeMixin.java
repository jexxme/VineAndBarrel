package jexxme.vineandbarrel.fabric.mixin;

import net.minecraft.world.inventory.RecipeBookType;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The Brew Kettle's recipe book type, a new constant of vanilla's enum (declared by the class tweaker's
 * {@code extend-enum}); NeoForge adds the same constant from META-INF/enumextensions.json.
 */
@Mixin(RecipeBookType.class)
enum RecipeBookTypeMixin
{
	VINEANDBARREL_BREWING
}
