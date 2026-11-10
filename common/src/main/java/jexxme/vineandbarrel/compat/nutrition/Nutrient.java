package jexxme.vineandbarrel.compat.nutrition;

import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The food groups of our food and drink, in Nutritional Balance's terms (its {@code #c:nutrients/<group>} item tags).
 * Grapes, juices, wines and ciders are fruit; barley, ales and beers are carbs; mead is honey, so sugars; the meals are what
 * is in them. The raw
 * barley is tagged too: Nutritional Balance follows recipes back to tagged ingredients, so anything brewed or cooked from
 * it later inherits its group. {@link jexxme.vineandbarrel.compat.CompatData} writes the tags from this table.
 * <p>
 * A new food or drink needs a line in {@link #GROUPS}; the GameTest {@code compat_every_food_has_a_nutrient} says so.
 */
public enum Nutrient
{
	CARBS("carbs"),
	FRUITS("fruits"),
	PROTEINS("proteins"),
	SUGARS("sugars"),
	VEGETABLES("vegetables");

	public final String name;
	public final TagKey<Item> tag;

	Nutrient(String name) {
		this.name = name;
		this.tag = TagKey.create(Registries.ITEM, new ResourceLocation("c", "nutrients/" + name));
	}

	private static final Map<Supplier<? extends Item>, Set<Nutrient>> GROUPS = new LinkedHashMap<>();

	static {
		group(FRUITS, VBItems.RED_GRAPES, VBItems.WHITE_GRAPES, BaseItems.GRAPE_JUICE, BaseItems.WHITE_GRAPE_JUICE, BaseItems.SWEET_BERRY_JUICE,
			BaseItems.APPLE_JUICE, DrinkItems.RED_WINE, DrinkItems.AGED_RED_WINE, DrinkItems.WHITE_WINE, DrinkItems.AGED_WHITE_WINE,
			DrinkItems.SWEET_BERRY_WINE, DrinkItems.AGED_SWEET_BERRY_WINE, DrinkItems.MELON_WINE, DrinkItems.AGED_MELON_WINE, DrinkItems.HARD_CIDER,
			DrinkItems.AGED_HARD_CIDER);
		group(CARBS, VBItems.BARLEY, DrinkItems.BARLEY_ALE, DrinkItems.AGED_BARLEY_ALE, DrinkItems.WHEAT_BEER, DrinkItems.AGED_WHEAT_BEER);
		group(SUGARS, DrinkItems.MEAD, DrinkItems.AGED_MEAD);
		// Fish in an ale and wheat batter; beef braised in red wine with onion and carrot
		group(PROTEINS, FDCompat.BEER_BATTERED_FISH, FDCompat.WINE_BRAISED_BEEF);
		group(CARBS, FDCompat.BEER_BATTERED_FISH);
		group(VEGETABLES, FDCompat.WINE_BRAISED_BEEF);
		// Pork braised in cider with apple and onion; mutton with barley, carrot and onion; rice with white wine and onion
		// in bone broth; chicken in red wine with mushroom, onion and bacon; bread of wheat and beer
		group(PROTEINS, FDCompat.CIDER_BRAISED_PORK, FDCompat.BARLEY_MUTTON_BROTH, FDCompat.COQ_AU_VIN);
		group(FRUITS, FDCompat.CIDER_BRAISED_PORK);
		group(CARBS, FDCompat.BARLEY_MUTTON_BROTH, FDCompat.WHITE_WINE_RISOTTO, FDCompat.BEER_BREAD);
		group(VEGETABLES, FDCompat.BARLEY_MUTTON_BROTH, FDCompat.WHITE_WINE_RISOTTO, FDCompat.COQ_AU_VIN);
	}

	@SafeVarargs
	private static void group(Nutrient nutrient, Supplier<? extends Item>... items) {
		for (Supplier<? extends Item> item : items) {
			GROUPS.computeIfAbsent(item, i -> EnumSet.noneOf(Nutrient.class)).add(nutrient);
		}
	}

	/** Our items in this group, in a stable order. */
	public List<Item> items() {
		return GROUPS.entrySet().stream().filter(entry -> entry.getValue().contains(this)).map(entry -> (Item) entry.getKey().get()).toList();
	}

	/** The groups of one of our items (empty if it has none). */
	public static Set<Nutrient> of(Item item) {
		return GROUPS.entrySet().stream().filter(entry -> entry.getKey().get() == item).findFirst()
			.map(entry -> (Set<Nutrient>) EnumSet.copyOf(entry.getValue())).orElse(EnumSet.noneOf(Nutrient.class));
	}
}
