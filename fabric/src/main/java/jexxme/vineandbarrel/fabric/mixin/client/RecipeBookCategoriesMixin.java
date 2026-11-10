package jexxme.vineandbarrel.fabric.mixin.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import jexxme.vineandbarrel.fabric.client.FabricClientRecipeBook;
import jexxme.vineandbarrel.kettle.client.BrewingRecipeBookCategories;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The kettle recipe book's tabs, four new constants of vanilla's client enum (see {@link BrewingRecipeBookCategories}
 * for their names, order and icons): made with the enum's own constructor and appended to its values as the enum class
 * loads, like {@code RecipeBookTypeMixin} does it for the book's type. NeoForge adds the same constants from
 * META-INF/enumextensions.json and says how they belong together in {@code RegisterRecipeBookCategoriesEvent}; here
 * the search tab's aggregate, the book's tab list and the icons are answered in this class.
 */
@Mixin(RecipeBookCategories.class)
abstract class RecipeBookCategoriesMixin
{
	// The compiler's own field: the mappings know it only by its intermediary name (the alias, which is its name in a
	// released game), so it is found by name, not through the refmap
	@SuppressWarnings("target")
	@Shadow(aliases = "field_1805", remap = false)
	@Final
	@Mutable
	private static RecipeBookCategories[] $VALUES;

	@Shadow
	@Final
	@Mutable
	public static Map<RecipeBookCategories, List<RecipeBookCategories>> AGGREGATE_CATEGORIES;

	/** A kettle tab's icons, made the first time the tab is drawn (our items don't exist yet while the enum loads). */
	@Unique
	private @Nullable List<ItemStack> vineandbarrel$icons;

	@Invoker("<init>")
	private static RecipeBookCategories vineandbarrel$create(String name, int ordinal, ItemStack... icons) {
		throw new AssertionError("mixin");
	}

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void vineandbarrel$addBrewingTabs(CallbackInfo ci) {
		// Without icons: getIconItems answers for them (below)
		String[] names = {BrewingRecipeBookCategories.SEARCH, BrewingRecipeBookCategories.JUICES, BrewingRecipeBookCategories.WORTS, BrewingRecipeBookCategories.MISC};
		RecipeBookCategories[] values = Arrays.copyOf($VALUES, $VALUES.length + names.length);
		for (int i = 0; i < names.length; i++) {
			int ordinal = $VALUES.length + i;
			values[ordinal] = vineandbarrel$create(names[i], ordinal);
		}
		RecipeBookCategories search = values[$VALUES.length];
		List<RecipeBookCategories> searched = ImmutableList.copyOf(Arrays.copyOfRange(values, $VALUES.length + 1, values.length));
		$VALUES = values;
		// The search tab shows the recipes of the other three (read by ClientRecipeBook#setupCollections)
		AGGREGATE_CATEGORIES = ImmutableMap.<RecipeBookCategories, List<RecipeBookCategories>>builder().putAll(AGGREGATE_CATEGORIES).put(search, searched).build();
	}

	/** Vanilla's switch only knows its own four book types. */
	@Inject(method = "getCategories", at = @At("HEAD"), cancellable = true)
	private static void vineandbarrel$brewingTabs(RecipeBookType type, CallbackInfoReturnable<List<RecipeBookCategories>> callback) {
		List<RecipeBookCategories> tabs = FabricClientRecipeBook.tabs(type);
		if (tabs != null) {
			callback.setReturnValue(tabs);
		}
	}

	@Inject(method = "getIconItems", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$brewingIcons(CallbackInfoReturnable<List<ItemStack>> callback) {
		String name = ((Enum<?>) (Object) this).name();
		if (name.startsWith("VINEANDBARREL_BREWING_")) {
			List<ItemStack> icons = vineandbarrel$icons;
			if (icons == null) {
				icons = BrewingRecipeBookCategories.icons(name);
				vineandbarrel$icons = icons;
			}
			callback.setReturnValue(icons);
		}
	}
}
