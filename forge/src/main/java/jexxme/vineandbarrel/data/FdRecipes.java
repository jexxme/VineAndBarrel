package jexxme.vineandbarrel.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The recipes that need Farmer's Delight, and the advancement that unlocks each one in the recipe book, written as
 * plain JSON (FD is never compiled against, so its recipe types have no builder here). Every file carries both loaders'
 * conditions, so it loads only with FD: Farmer's Delight on Forge, FD Refabricated on Fabric. Each recipe gets its
 * unlock from the same row, as FD does for its own: unlocked by holding one of its ingredients.
 * <p>
 * The shapes are those of the 1.20.1 builds' own files (Farmer's Delight 1.20.1-1.3.4, FD Refabricated 1.20.1-2.5.7):
 * ingredients are {@code {"item": ...}} / {@code {"tag": ...}} objects (several items: a list of them), and item stacks
 * are {@code {"item": ...}} with a {@code count} only above one. The two builds name their ingredient tags differently
 * ({@code forge:crops/onion} / {@code c:crops/onion}, {@code forge:raw_beef} / {@code c:foods/raw_meats/raw_beef}), so
 * every tag here is one of ours that holds both ({@code vineandbarrel:cooking/...}, see {@link VBTagProviders}).
 */
public class FdRecipes implements DataProvider
{
	private static final String RED_WINE = "vineandbarrel:red_wine", AGED_RED_WINE = "vineandbarrel:aged_red_wine";
	private static final String WHITE_WINE = "vineandbarrel:white_wine", AGED_WHITE_WINE = "vineandbarrel:aged_white_wine";
	private static final String HARD_CIDER = "vineandbarrel:hard_cider", AGED_HARD_CIDER = "vineandbarrel:aged_hard_cider";
	private static final String BARLEY_ALE = "vineandbarrel:barley_ale", AGED_BARLEY_ALE = "vineandbarrel:aged_barley_ale";
	private static final String WHEAT_BEER = "vineandbarrel:wheat_beer", AGED_WHEAT_BEER = "vineandbarrel:aged_wheat_beer";

	/** One recipe: its id under {@code recipes/}, its JSON body, and the items that unlock it (none: no unlock). */
	private record Row(String path, JsonObject recipe, List<String> unlockedBy) {
	}

	private final PackOutput output;

	public FdRecipes(PackOutput output) {
		this.output = output;
	}

	private static List<Row> rows() {
		List<Row> rows = new ArrayList<>();
		// Three ingredients: FD's fish stew values. Four: FD's baked cod stew values (FDCompat).
		rows.add(cooking("beer_battered_fish", FDCompat.BEER_BATTERED_FISH, 1.0F, 0, List.of(BARLEY_ALE, AGED_BARLEY_ALE),
			tag(VBTagProviders.COOKING_RAW_FISH), eitherAge("barley_ale"), tag(VBTagProviders.COOKING_WHEAT)));
		rows.add(cooking("wine_braised_beef", FDCompat.WINE_BRAISED_BEEF, 1.0F, 0, List.of(RED_WINE, AGED_RED_WINE),
			tag(VBTagProviders.COOKING_RAW_BEEF), eitherAge("red_wine"), tag(VBTagProviders.COOKING_ONION), tag(VBTagProviders.COOKING_CARROT)));
		rows.add(cooking("cider_braised_pork", FDCompat.CIDER_BRAISED_PORK, 1.0F, 0, List.of(HARD_CIDER, AGED_HARD_CIDER),
			tag(VBTagProviders.COOKING_RAW_PORK), eitherAge("hard_cider"), ingredient("minecraft:apple"), tag(VBTagProviders.COOKING_ONION)));
		rows.add(cooking("barley_mutton_broth", FDCompat.BARLEY_MUTTON_BROTH, 1.0F, 0, List.of("vineandbarrel:barley"),
			tag(VBTagProviders.COOKING_RAW_MUTTON), tag(VBTagProviders.COOKING_BARLEY), tag(VBTagProviders.COOKING_CARROT), tag(VBTagProviders.COOKING_ONION)));
		// The risotto's stock is FD's bone broth; its bowl comes back out of the pot like a drink's glass
		rows.add(cooking("white_wine_risotto", FDCompat.WHITE_WINE_RISOTTO, 1.0F, 0, List.of(WHITE_WINE, AGED_WHITE_WINE),
			tag(VBTagProviders.COOKING_RICE), eitherAge("white_wine"), tag(VBTagProviders.COOKING_ONION), ingredient("farmersdelight:bone_broth")));
		// A feast, like FD's stuffed pumpkin: six ingredients, twice the cooking time, and no bowl (it is served from the pot)
		rows.add(cooking("coq_au_vin_block", FDCompat.COQ_AU_VIN_BLOCK_ITEM, 2.0F, 400, List.of(RED_WINE, AGED_RED_WINE),
			tag(VBTagProviders.COOKING_RAW_CHICKEN), tag(VBTagProviders.COOKING_RAW_CHICKEN), eitherAge("red_wine"), ingredient("minecraft:brown_mushroom"),
			tag(VBTagProviders.COOKING_ONION), tag(VBTagProviders.COOKING_RAW_BACON)));
		// Beer bread: three wheat and a beer for the dough, two loaves (the mug stays in the grid)
		JsonObject bread = new JsonObject();
		bread.addProperty("type", "minecraft:crafting_shapeless");
		bread.addProperty("category", "misc"); // the crafting book has no food tab (vanilla files bread there too)
		bread.add("ingredients", array(tag(VBTagProviders.COOKING_WHEAT), tag(VBTagProviders.COOKING_WHEAT), tag(VBTagProviders.COOKING_WHEAT),
			any(BARLEY_ALE, AGED_BARLEY_ALE, WHEAT_BEER, AGED_WHEAT_BEER)));
		bread.add("result", result(FDCompat.BEER_BREAD, 2));
		rows.add(new Row("beer_bread", bread, List.of(BARLEY_ALE, AGED_BARLEY_ALE, WHEAT_BEER, AGED_WHEAT_BEER)));
		// Wild crops on FD's cutting board, like every one of FD's own: wild barley exactly like FD's wild rice (the grain,
		// and a straw half the time); grapes and hops like FD's wild cabbages, carrots and potatoes (the crop, and two dyes of
		// the plant's colour at 50% each). The cutting board has no recipe book, so (as FD's own) no unlock
		rows.add(wildCutting("wild_barley", () -> jexxme.vineandbarrel.registry.VBItems.BARLEY.get(), "farmersdelight:straw", 1));
		rows.add(wildCutting("wild_grapes", () -> jexxme.vineandbarrel.registry.VBItems.RED_GRAPES.get(), "minecraft:purple_dye", 2));
		rows.add(wildCutting("wild_white_grapes", () -> jexxme.vineandbarrel.registry.VBItems.WHITE_GRAPES.get(), "minecraft:yellow_dye", 2));
		rows.add(wildCutting("wild_hops", () -> jexxme.vineandbarrel.registry.VBItems.HOPS.get(), "minecraft:lime_dye", 2));
		return rows;
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cache) {
		PackOutput.PathProvider recipes = output.createPathProvider(PackOutput.Target.DATA_PACK, "recipes");
		PackOutput.PathProvider advancements = output.createPathProvider(PackOutput.Target.DATA_PACK, "advancements");
		List<CompletableFuture<?>> writes = new ArrayList<>();
		for (Row row : rows()) {
			writes.add(DataProvider.saveStable(cache, withConditions(row.recipe()), recipes.json(VineAndBarrel.id(row.path()))));
			if (!row.unlockedBy().isEmpty()) {
				writes.add(DataProvider.saveStable(cache, withConditions(unlock(row)), advancements.json(VineAndBarrel.id("recipes/food/" + leaf(row.path())))));
			}
		}
		return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
	}

	@Override
	public String getName() {
		return "Farmer's Delight recipes and their unlocks for " + VineAndBarrel.MODID;
	}

	// ------------------------------------------------------------------ JSON

	/** A wild crop on the cutting board with a knife: its crop, and {@code count} of {@code extra} at 50% each. */
	private static Row wildCutting(String wild, Supplier<? extends Item> crop, String extra, int count) {
		JsonObject cutting = new JsonObject();
		cutting.addProperty("type", "farmersdelight:cutting");
		cutting.add("ingredients", array(ingredient("vineandbarrel:" + wild)));
		JsonObject bonus = new JsonObject();
		bonus.addProperty("chance", 0.5F);
		if (count > 1) {
			bonus.addProperty("count", count);
		}
		bonus.addProperty("item", extra);
		cutting.add("result", array(result(crop, 1), bonus));
		// FD's own cutting recipes on Forge also take anything with its knife tool action; that ingredient type only exists
		// there (FD Refabricated's own files take the knives tag alone), so ours take the knives tag only, which both read
		cutting.add("tool", tag(VBTagProviders.COOKING_KNIVES));
		return new Row("cutting/" + wild, cutting, List.of());
	}

	private static Row cooking(String name, Supplier<? extends Item> result, float experience, int cookingTime, List<String> unlockedBy, JsonElement... ingredients) {
		JsonObject recipe = new JsonObject();
		recipe.addProperty("type", "farmersdelight:cooking");
		if (cookingTime > 0) {
			recipe.addProperty("cookingtime", cookingTime);
		}
		recipe.addProperty("experience", experience);
		recipe.add("ingredients", array(ingredients));
		recipe.addProperty("recipe_book_tab", "meals");
		recipe.add("result", result(result, 1));
		return new Row("cooking/" + name, recipe, unlockedBy);
	}

	/** Like FD's: unlocked once the player holds one of the items, or already has the recipe. */
	private static JsonObject unlock(Row row) {
		String recipe = VineAndBarrel.id(row.path()).toString();
		JsonObject root = new JsonObject();
		root.addProperty("parent", "minecraft:recipes/root");
		JsonObject criteria = new JsonObject();
		JsonObject hasItem = new JsonObject();
		hasItem.addProperty("trigger", "minecraft:inventory_changed");
		JsonObject predicate = new JsonObject();
		predicate.add("items", array(row.unlockedBy().stream().map(FdRecipes::item).toArray(JsonElement[]::new)));
		JsonObject hasItemConditions = new JsonObject();
		hasItemConditions.add("items", array(predicate));
		hasItem.add("conditions", hasItemConditions);
		criteria.add("has_ingredient", hasItem);
		JsonObject hasRecipe = new JsonObject();
		hasRecipe.addProperty("trigger", "minecraft:recipe_unlocked");
		JsonObject hasRecipeConditions = new JsonObject();
		hasRecipeConditions.addProperty("recipe", recipe);
		hasRecipe.add("conditions", hasRecipeConditions);
		criteria.add("has_the_recipe", hasRecipe);
		root.add("criteria", criteria);
		root.add("requirements", array(array(item("has_ingredient"), item("has_the_recipe"))));
		JsonObject rewards = new JsonObject();
		rewards.add("recipes", array(item(recipe)));
		root.add("rewards", rewards);
		// as vanilla's recipe unlocks on 1.20.1
		root.addProperty("sends_telemetry_event", false);
		return root;
	}

	/**
	 * Both loaders' "only with Farmer's Delight": Forge 47 reads a top-level {@code conditions} list on every recipe
	 * ({@code RecipeManager}) and advancement ({@code ConditionalAdvancement}), Fabric API {@code fabric:load_conditions};
	 * each ignores the other's key.
	 */
	private static JsonObject withConditions(JsonObject body) {
		JsonObject root = new JsonObject();
		JsonObject forge = new JsonObject();
		forge.addProperty("type", "forge:mod_loaded");
		forge.addProperty("modid", FDCompat.MODID);
		root.add("conditions", array(forge));
		JsonObject fabric = new JsonObject();
		fabric.addProperty("condition", "fabric:all_mods_loaded");
		fabric.add("values", array(item(FDCompat.MODID)));
		root.add("fabric:load_conditions", array(fabric));
		body.entrySet().forEach(entry -> root.add(entry.getKey(), entry.getValue()));
		return root;
	}

	private static JsonObject result(Supplier<? extends Item> item, int count) {
		JsonObject result = new JsonObject();
		if (count > 1) {
			result.addProperty("count", count);
		}
		result.addProperty("item", BuiltInRegistries.ITEM.getKey(item.get()).toString());
		return result;
	}

	private static String leaf(String path) {
		return path.substring(path.lastIndexOf('/') + 1);
	}

	/** A plain id (in a list of ids, a requirement, a reward). */
	private static JsonElement item(String id) {
		return new com.google.gson.JsonPrimitive(id);
	}

	/** An ingredient of one item. */
	private static JsonElement ingredient(String id) {
		JsonObject ingredient = new JsonObject();
		ingredient.addProperty("item", id);
		return ingredient;
	}

	/** An ingredient of a tag. */
	private static JsonElement tag(String id) {
		JsonObject ingredient = new JsonObject();
		ingredient.addProperty("tag", id);
		return ingredient;
	}

	private static JsonElement tag(TagKey<Item> tag) {
		return tag(tag.location().toString());
	}

	/**
	 * A drink, young or aged: the item tag {@code vineandbarrel:cooking/<drink>} (VBTagProviders), as on the later
	 * versions, where Farmer's Delight's cooking pot reads each ingredient as one JSON object.
	 */
	private static JsonElement eitherAge(String drink) {
		return tag("vineandbarrel:cooking/" + drink);
	}

	/** An ingredient of any of these items. */
	private static JsonElement any(String... ids) {
		JsonArray array = new JsonArray();
		for (String id : ids) {
			array.add(ingredient(id));
		}
		return array;
	}

	private static JsonArray array(JsonElement... elements) {
		JsonArray array = new JsonArray();
		for (JsonElement element : elements) {
			array.add(element);
		}
		return array;
	}
}
