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
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The recipes that need Farmer's Delight, and the advancement that unlocks each one in the recipe book, written as
 * plain JSON (FD is never compiled against, so its recipe types have no builder here). Every file carries both loaders'
 * conditions, so it loads only with FD: FD Neoforged on NeoForge, FD Refabricated on Fabric. Each recipe gets its
 * unlock from the same row, as FD does for its own: unlocked by holding one of its ingredients.
 */
public class FdRecipes implements DataProvider
{
	private static final String RED_WINE = "vineandbarrel:red_wine", AGED_RED_WINE = "vineandbarrel:aged_red_wine";
	private static final String WHITE_WINE = "vineandbarrel:white_wine", AGED_WHITE_WINE = "vineandbarrel:aged_white_wine";
	private static final String HARD_CIDER = "vineandbarrel:hard_cider", AGED_HARD_CIDER = "vineandbarrel:aged_hard_cider";
	private static final String BARLEY_ALE = "vineandbarrel:barley_ale", AGED_BARLEY_ALE = "vineandbarrel:aged_barley_ale";
	private static final String WHEAT_BEER = "vineandbarrel:wheat_beer", AGED_WHEAT_BEER = "vineandbarrel:aged_wheat_beer";

	/** One recipe: its id under {@code recipe/}, its JSON body, and the items that unlock it (none: no unlock). */
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
			tag("c:foods/safe_raw_fish"), any(BARLEY_ALE, AGED_BARLEY_ALE), tag("c:crops/wheat")));
		rows.add(cooking("wine_braised_beef", FDCompat.WINE_BRAISED_BEEF, 1.0F, 0, List.of(RED_WINE, AGED_RED_WINE),
			tag("c:foods/raw_beef"), any(RED_WINE, AGED_RED_WINE), tag("c:crops/onion"), tag("c:crops/carrot")));
		rows.add(cooking("cider_braised_pork", FDCompat.CIDER_BRAISED_PORK, 1.0F, 0, List.of(HARD_CIDER, AGED_HARD_CIDER),
			tag("c:foods/raw_pork"), any(HARD_CIDER, AGED_HARD_CIDER), item("minecraft:apple"), tag("c:crops/onion")));
		rows.add(cooking("barley_mutton_broth", FDCompat.BARLEY_MUTTON_BROTH, 1.0F, 0, List.of("vineandbarrel:barley"),
			tag("c:foods/raw_mutton"), tag("c:crops/barley"), tag("c:crops/carrot"), tag("c:crops/onion")));
		// The risotto's stock is FD's bone broth; its bowl comes back out of the pot like a drink's glass
		rows.add(cooking("white_wine_risotto", FDCompat.WHITE_WINE_RISOTTO, 1.0F, 0, List.of(WHITE_WINE, AGED_WHITE_WINE),
			tag("c:crops/rice"), any(WHITE_WINE, AGED_WHITE_WINE), tag("c:crops/onion"), item("farmersdelight:bone_broth")));
		// A feast, like FD's stuffed pumpkin: six ingredients, twice the cooking time, and no bowl (it is served from the pot)
		rows.add(cooking("coq_au_vin_block", FDCompat.COQ_AU_VIN_BLOCK_ITEM, 2.0F, 400, List.of(RED_WINE, AGED_RED_WINE),
			tag("c:foods/raw_chicken"), tag("c:foods/raw_chicken"), any(RED_WINE, AGED_RED_WINE), item("minecraft:brown_mushroom"),
			tag("c:crops/onion"), tag("c:foods/raw_bacon")));
		// Beer bread: three wheat and a beer for the dough, two loaves (the mug stays in the grid)
		JsonObject bread = new JsonObject();
		bread.addProperty("type", "minecraft:crafting_shapeless");
		bread.addProperty("category", "misc"); // the crafting book has no food tab (vanilla files bread there too)
		bread.add("ingredients", array(tag("c:crops/wheat"), tag("c:crops/wheat"), tag("c:crops/wheat"),
			any(BARLEY_ALE, AGED_BARLEY_ALE, WHEAT_BEER, AGED_WHEAT_BEER)));
		bread.add("result", result(FDCompat.BEER_BREAD, 2));
		rows.add(new Row("beer_bread", bread, List.of(BARLEY_ALE, AGED_BARLEY_ALE, WHEAT_BEER, AGED_WHEAT_BEER)));
		// Wild barley on FD's cutting board, exactly like FD's wild rice: the grain, and a straw half the time. The
		// cutting board has no recipe book, so (as FD's own) no unlock
		JsonObject cutting = new JsonObject();
		cutting.addProperty("type", "farmersdelight:cutting");
		cutting.add("ingredients", array(item("vineandbarrel:wild_barley")));
		JsonObject straw = new JsonObject();
		straw.addProperty("chance", 0.5F);
		JsonObject strawItem = new JsonObject();
		strawItem.addProperty("id", "farmersdelight:straw");
		straw.add("item", strawItem);
		JsonObject grain = new JsonObject();
		grain.add("item", result(() -> jexxme.vineandbarrel.registry.VBItems.BARLEY.get(), 1));
		cutting.add("result", array(grain, straw));
		cutting.addProperty("tool", "#c:tools/knife");
		rows.add(new Row("cutting/wild_barley", cutting, List.of()));
		return rows;
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cache) {
		PackOutput.PathProvider recipes = output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe");
		PackOutput.PathProvider advancements = output.createPathProvider(PackOutput.Target.DATA_PACK, "advancement");
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
		root.add("requirements", array(array(item("has_the_recipe"), item("has_ingredient"))));
		JsonObject rewards = new JsonObject();
		rewards.add("recipes", array(item(recipe)));
		root.add("rewards", rewards);
		return root;
	}

	/** Both loaders' "only with Farmer's Delight", first in the file as in the hand-written ones. */
	private static JsonObject withConditions(JsonObject body) {
		JsonObject root = new JsonObject();
		JsonObject neoforge = new JsonObject();
		neoforge.addProperty("type", "neoforge:mod_loaded");
		neoforge.addProperty("modid", FDCompat.MODID);
		root.add("neoforge:conditions", array(neoforge));
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
		result.addProperty("id", BuiltInRegistries.ITEM.getKey(item.get()).toString());
		return result;
	}

	private static String leaf(String path) {
		return path.substring(path.lastIndexOf('/') + 1);
	}

	private static JsonElement item(String id) {
		return new com.google.gson.JsonPrimitive(id);
	}

	private static JsonElement tag(String id) {
		return item("#" + id);
	}

	private static JsonElement any(String... ids) {
		JsonArray array = new JsonArray();
		for (String id : ids) {
			array.add(id);
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
