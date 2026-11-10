package jexxme.vineandbarrel.fabric.mixin;

import jexxme.vineandbarrel.fabric.FabricRecipeBook;
import net.minecraft.world.inventory.RecipeBookType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * The Brew Kettle's recipe book type, a new constant of vanilla's enum: made with the enum's own constructor and
 * appended to its values as the enum class loads, so {@code values()}, {@code valueOf} and every {@code EnumMap} know
 * it from the start. NeoForge adds the same constant from META-INF/enumextensions.json. (The Fabric Loader this
 * Minecraft's jar must run on has no class tweaker {@code extend-enum}.)
 */
@Mixin(RecipeBookType.class)
abstract class RecipeBookTypeMixin
{
	// The compiler's own field: the mappings know it only by its intermediary name (the alias, which is its name in a
	// released game), so it is found by name, not through the refmap
	@SuppressWarnings("target")
	@Shadow(aliases = "field_25767", remap = false)
	@Final
	@Mutable
	private static RecipeBookType[] $VALUES;

	@Invoker("<init>")
	private static RecipeBookType vineandbarrel$create(String name, int ordinal) {
		throw new AssertionError("mixin");
	}

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void vineandbarrel$addBrewing(CallbackInfo ci) {
		RecipeBookType[] values = Arrays.copyOf($VALUES, $VALUES.length + 1);
		values[values.length - 1] = vineandbarrel$create(FabricRecipeBook.BREWING, values.length - 1);
		$VALUES = values;
	}
}
