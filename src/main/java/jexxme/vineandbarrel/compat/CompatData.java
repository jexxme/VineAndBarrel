package jexxme.vineandbarrel.compat;

import com.google.gson.JsonObject;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.compat.nutrition.Nutrient;
import jexxme.vineandbarrel.compat.thirst.Quench;
import jexxme.vineandbarrel.drink.DrinkItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ItemTagsProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * The data files for the data-driven integrations ({@code ./gradlew runData}): thirst values for Tough As Nails,
 * Homeostatic and Thirst Was Taken 2 (from {@link Quench}), food groups for Nutritional Balance (from {@link Nutrient}),
 * and the NeoForge convention tags for drinks and food that more mods read ({@code #c:drinks}, {@code #c:foods/...}).
 * Nothing here needs the other mods: tags in their namespaces are plain tags, and their JSON folders are only read by
 * them. Generated, so new drinks join on the next data run.
 */
@EventBusSubscriber(modid = VineAndBarrel.MODID, value = Dist.CLIENT)
public final class CompatData
{
	/** Tough As Nails' tag folders (its {@code ModTags.Items}): thirst points 1-20, hydration 10-100 % in steps of 10. */
	public static final String TAN = "toughasnails";
	public static final String HOMEOSTATIC_FOLDER = "environment/drinkable";
	public static final String TWT_FOLDER = "thirstwastaken2/drinks";
	/** Our one Thirst Was Taken 2 file: data/vineandbarrel/thirstwastaken2/drinks/vineandbarrel.json. */
	public static final Identifier TWT_FILE = VineAndBarrel.id(TWT_FOLDER + "/" + VineAndBarrel.MODID + ".json");

	private CompatData() {
	}

	public static TagKey<Item> tanThirstTag(int thirst) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(TAN, "thirst/" + thirst + "_thirst_drinks"));
	}

	public static TagKey<Item> tanHydrationTag(int hydration) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(TAN, "hydration/" + hydration + "_hydration_drinks"));
	}

	/** Where Homeostatic reads an item's values: data/vineandbarrel/environment/drinkable/<item>.json. */
	public static Identifier homeostaticFile(Item item) {
		return VineAndBarrel.id(HOMEOSTATIC_FOLDER + "/" + BuiltInRegistries.ITEM.getKey(item).getPath() + ".json");
	}

	@SubscribeEvent
	public static void gatherData(GatherDataEvent.Client event) {
		event.createProvider(CompatItemTags::new);
		event.createProvider(HomeostaticDrinks::new);
		event.createProvider(ThirstWasTakenDrinks::new);
	}

	private static List<Item> quenching(Predicate<Quench> which) {
		return Quench.items().stream().filter(item -> Quench.of(item).filter(which).isPresent()).toList();
	}

	private static ResourceKey<Item> key(Item item) {
		return BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow();
	}

	// ------------------------------------------------------------------ tags
	public static class CompatItemTags extends ItemTagsProvider
	{
		public CompatItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
			super(output, lookupProvider, VineAndBarrel.MODID);
		}

		@Override
		public String getName() {
			return "Compat item tags (thirst, nutrients, conventions) for " + VineAndBarrel.MODID;
		}

		@Override
		protected void addTags(HolderLookup.Provider provider) {
			// Tough As Nails: one thirst tag and one hydration tag per drink; its #toughasnails:drinks holds all thirst tags
			for (Item item : quenching(Quench::tanDrink)) {
				Quench quench = Quench.of(item).orElseThrow();
				tag(tanThirstTag(quench.tanThirst)).add(key(item));
				tag(tanHydrationTag(quench.tanHydration)).add(key(item));
			}

			// Nutritional Balance
			for (Nutrient nutrient : Nutrient.values()) {
				nutrient.items().forEach(item -> tag(nutrient.tag).add(key(item)));
			}

			// NeoForge's conventions: juices are #c:drinks/juice (and so #c:drinks) and drink-containing bottles; wines,
			// beers and ciders are plain #c:drinks; grapes are a fruit, the braised beef a stew, the battered fish food
			for (Item juice : quenching(quench -> quench == Quench.JUICE)) {
				tag(Tags.Items.DRINKS_JUICE).add(key(juice));
				tag(Tags.Items.DRINK_CONTAINING_BOTTLE).add(key(juice));
			}
			for (Item drink : Quench.items()) {
				if (drink instanceof DrinkItem) {
					tag(Tags.Items.DRINKS).add(key(drink));
				}
			}
			quenching(quench -> quench == Quench.FRUIT).forEach(fruit -> tag(Tags.Items.FOODS_FRUIT).add(key(fruit)));
			quenching(quench -> quench == Quench.STEW).forEach(stew -> tag(Tags.Items.FOODS_SOUP).add(key(stew)));
			quenching(quench -> quench == Quench.MEAL).forEach(meal -> tag(Tags.Items.FOODS).add(key(meal)));
		}
	}

	// ------------------------------------------------------------------ Homeostatic: one file per item
	public static class HomeostaticDrinks implements DataProvider
	{
		private final PackOutput output;

		public HomeostaticDrinks(PackOutput output) {
			this.output = output;
		}

		/** The file's content, exactly in Homeostatic's format (as its own files for Farmer's Delight's drinks). */
		public static JsonObject json(Item item, Quench quench) {
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

		@Override
		public CompletableFuture<?> run(CachedOutput cache) {
			PackOutput.PathProvider path = output.createPathProvider(PackOutput.Target.DATA_PACK, HOMEOSTATIC_FOLDER);
			List<CompletableFuture<?>> writes = new ArrayList<>();
			for (Item item : Quench.items()) {
				Quench quench = Quench.of(item).orElseThrow();
				writes.add(DataProvider.saveStable(cache, json(item, quench), path.json(BuiltInRegistries.ITEM.getKey(item))));
			}
			return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
		}

		@Override
		public String getName() {
			return "Homeostatic drinkable items for " + VineAndBarrel.MODID;
		}
	}

	// ------------------------------------------------------------------ Thirst Was Taken 2: one file for all
	public static class ThirstWasTakenDrinks implements DataProvider
	{
		private final PackOutput output;

		public ThirstWasTakenDrinks(PackOutput output) {
			this.output = output;
		}

		public static JsonObject json() {
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

		@Override
		public CompletableFuture<?> run(CachedOutput cache) {
			PackOutput.PathProvider path = output.createPathProvider(PackOutput.Target.DATA_PACK, TWT_FOLDER);
			return DataProvider.saveStable(cache, json(), path.json(VineAndBarrel.id(VineAndBarrel.MODID)));
		}

		@Override
		public String getName() {
			return "Thirst Was Taken 2 drinks for " + VineAndBarrel.MODID;
		}
	}
}
