package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.compat.CompatData;
import jexxme.vineandbarrel.compat.Conventions;
import jexxme.vineandbarrel.compat.nutrition.Nutrient;
import jexxme.vineandbarrel.compat.thirst.Quench;
import jexxme.vineandbarrel.drink.DrinkItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the data files for the data-driven integrations ({@code ./gradlew runData}): what {@link CompatData} names,
 * from {@link Quench} and {@link Nutrient}. Shared by both loaders (common/src/generated).
 */
@EventBusSubscriber(modid = VineAndBarrel.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class CompatDataProviders
{
	private CompatDataProviders() {
	}

	@SubscribeEvent
	public static void gatherData(GatherDataEvent event) {
		if (!event.includeServer()) {
			return;
		}
		event.createProvider((output, lookup) -> new CompatItemTags(output, lookup, event.getExistingFileHelper()));
		event.createProvider(HomeostaticDrinks::new);
		event.createProvider(ThirstWasTakenDrinks::new);
	}

	private static ResourceKey<Item> key(Item item) {
		return BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow();
	}

	// ------------------------------------------------------------------ tags
	public static class CompatItemTags extends ItemTagsProvider
	{
		public CompatItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, ExistingFileHelper existingFileHelper) {
			// no block tags to copy from
			super(output, lookupProvider, CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()), VineAndBarrel.MODID, existingFileHelper);
		}

		@Override
		public String getName() {
			return "Compat item tags (thirst, nutrients, conventions) for " + VineAndBarrel.MODID;
		}

		@Override
		protected void addTags(HolderLookup.Provider provider) {
			// Tough As Nails: one thirst tag and one hydration tag per drink; its #toughasnails:drinks holds all thirst tags
			for (Item item : CompatData.quenching(Quench::tanDrink)) {
				Quench quench = Quench.of(item).orElseThrow();
				tag(CompatData.tanThirstTag(quench.tanThirst)).add(item);
				tag(CompatData.tanHydrationTag(quench.tanHydration)).add(item);
			}

			// Nutritional Balance
			for (Nutrient nutrient : Nutrient.values()) {
				nutrient.items().forEach(item -> tag(nutrient.tag).add(item));
			}

			// NeoForge's conventions: juices are #c:drinks/juice (and so #c:drinks) and drink-containing bottles; wines,
			// beers and ciders are plain #c:drinks; grapes are a fruit, the braised beef a stew, the battered fish food
			for (Item juice : CompatData.quenching(quench -> quench == Quench.JUICE)) {
				tag(Conventions.DRINKS_JUICE).add(juice);
				tag(Conventions.DRINK_CONTAINING_BOTTLE).add(juice);
			}
			for (Item drink : Quench.items()) {
				if (drink instanceof DrinkItem) {
					tag(Conventions.DRINKS).add(drink);
				}
			}
			CompatData.quenching(quench -> quench == Quench.FRUIT).forEach(fruit -> tag(Conventions.FOODS_FRUIT).add(fruit));
			CompatData.quenching(quench -> quench == Quench.STEW).forEach(stew -> tag(Conventions.FOODS_SOUP).add(stew));
			CompatData.quenching(quench -> quench == Quench.MEAL).forEach(meal -> tag(Conventions.FOODS).add(meal));
			tag(Conventions.FOODS_BREAD).add(jexxme.vineandbarrel.compat.farmersdelight.FDCompat.BEER_BREAD.get());
		}
	}

	// ------------------------------------------------------------------ Homeostatic: one file per item
	public static class HomeostaticDrinks implements DataProvider
	{
		private final PackOutput output;

		public HomeostaticDrinks(PackOutput output) {
			this.output = output;
		}

		@Override
		public CompletableFuture<?> run(CachedOutput cache) {
			PackOutput.PathProvider path = output.createPathProvider(PackOutput.Target.DATA_PACK, CompatData.HOMEOSTATIC_FOLDER);
			List<CompletableFuture<?>> writes = new ArrayList<>();
			for (Item item : Quench.items()) {
				Quench quench = Quench.of(item).orElseThrow();
				writes.add(DataProvider.saveStable(cache, CompatData.homeostaticJson(item, quench), path.json(BuiltInRegistries.ITEM.getKey(item))));
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

		@Override
		public CompletableFuture<?> run(CachedOutput cache) {
			PackOutput.PathProvider path = output.createPathProvider(PackOutput.Target.DATA_PACK, CompatData.TWT_FOLDER);
			return DataProvider.saveStable(cache, CompatData.thirstWasTakenJson(), path.json(VineAndBarrel.id(VineAndBarrel.MODID)));
		}

		@Override
		public String getName() {
			return "Thirst Was Taken 2 drinks for " + VineAndBarrel.MODID;
		}
	}
}
