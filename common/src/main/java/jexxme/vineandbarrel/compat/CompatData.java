package jexxme.vineandbarrel.compat;

import com.google.gson.JsonObject;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.compat.thirst.Quench;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.function.Predicate;

/**
 * The data files for the data-driven integrations ({@code ./gradlew runData}): thirst values for Tough As Nails,
 * Homeostatic and Thirst Was Taken 2 (from {@link Quench}), food groups for Nutritional Balance (from
 * {@link jexxme.vineandbarrel.compat.nutrition.Nutrient}),
 * and the convention tags for drinks and food that more mods read ({@code #c:drinks}, {@code #c:foods/...}).
 * Nothing here needs the other mods: tags in their namespaces are plain tags, and their JSON folders are only read by
 * them. Generated, so new drinks join on the next data run (the providers are NeoForge's data generators,
 * {@code neoforge/.../data/CompatDataProviders}; these are the shared names and contents, which the GameTests check).
 */
public final class CompatData
{
	/** Tough As Nails' tag folders (its {@code ModTags.Items}): thirst points 1-20, hydration 10-100 % in steps of 10. */
	public static final String TAN = "toughasnails";
	public static final String HOMEOSTATIC_FOLDER = "environment/drinkable";
	public static final String TWT_FOLDER = "thirstwastaken2/drinks";
	/** Our one Thirst Was Taken 2 file: data/vineandbarrel/thirstwastaken2/drinks/vineandbarrel.json. */
	public static final ResourceLocation TWT_FILE = VineAndBarrel.id(TWT_FOLDER + "/" + VineAndBarrel.MODID + ".json");

	private CompatData() {
	}

	public static TagKey<Item> tanThirstTag(int thirst) {
		return TagKey.create(Registries.ITEM, new ResourceLocation(TAN, "thirst/" + thirst + "_thirst_drinks"));
	}

	public static TagKey<Item> tanHydrationTag(int hydration) {
		return TagKey.create(Registries.ITEM, new ResourceLocation(TAN, "hydration/" + hydration + "_hydration_drinks"));
	}

	/** Where Homeostatic reads an item's values: data/vineandbarrel/environment/drinkable/<item>.json. */
	public static ResourceLocation homeostaticFile(Item item) {
		return VineAndBarrel.id(HOMEOSTATIC_FOLDER + "/" + BuiltInRegistries.ITEM.getKey(item).getPath() + ".json");
	}

	/** Homeostatic's file for an item, exactly in its format (as its own files for Farmer's Delight's drinks). */
	public static JsonObject homeostaticJson(Item item, Quench quench) {
		JsonObject json = new JsonObject();
		json.addProperty("type", BuiltInRegistries.ITEM.getKey(item).toString());
		json.addProperty("amount", quench.homeostaticAmount);
		json.addProperty("saturation", quench.homeostaticSaturation);
		// Homeostatic's "dirty water" chance: never from a drink or meal
		json.addProperty("effect_chance", 0.0F);
		json.addProperty("effect_duration", 0);
		json.addProperty("effect_potency", 0);
		return json;
	}

	/** Our one Thirst Was Taken 2 file: thirst and quenched for every item whose row counts there. */
	public static JsonObject thirstWasTakenJson() {
		JsonObject values = new JsonObject();
		for (Item item : quenching(Quench::twtDrink)) {
			Quench quench = Quench.of(item).orElseThrow();
			JsonObject entry = new JsonObject();
			entry.addProperty("thirst", quench.twtThirst);
			entry.addProperty("quenched", quench.twtQuenched);
			values.add(BuiltInRegistries.ITEM.getKey(item).toString(), entry);
		}
		JsonObject json = new JsonObject();
		json.add("values", values);
		return json;
	}

	/** Our items whose thirst row matches. */
	public static List<Item> quenching(Predicate<Quench> which) {
		return Quench.items().stream().filter(item -> Quench.of(item).filter(which).isPresent()).toList();
	}
}
