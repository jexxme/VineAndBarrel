package jexxme.vineandbarrel.compat.thirst;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.compat.farmersdelight.MealItem;
import jexxme.vineandbarrel.drink.DrinkItem;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * How much our drinks and meals quench thirst in the thirst mods, one row per kind of item, one set of columns per mod.
 * Juices quench a lot, alcoholic drinks a little, aged ones more than young ones; a stew-like meal and fresh grapes count
 * where the mod counts food. Each mod's own values are the yardstick: a juice sits next to that mod's fruit juices, a young
 * drink near the bottom. {@link jexxme.vineandbarrel.compat.CompatData} writes the data files from this table, and the
 * GameTests check that each mod reads back exactly these numbers.
 * <ul>
 *   <li><b>Tough As Nails</b> (thirst 0-20, hydration %): its water bottle 3 / 40%, purified water 5 / 80%, its fruit
 *   juices 6-8 / 20-70%. Only drinks count there (its {@code #toughasnails:drinks} tag).</li>
 *   <li><b>Homeostatic</b> (water 0-20, saturation 0-5): purified water and Farmer's Delight's juices 3 / 0.7, a honey
 *   bottle 4 / 1.0, FD's stews 3 / 0.7, most solid food 2 / 0.6, sweet berries 2 / 0.3.</li>
 *   <li><b>Thirst Was Taken 2</b> (thirst 0-20, quenched 0-20): Farmer's Delight's juices 6 / 4, Brewin' and Chewin's
 *   wines 3 / 1, fruit 2 / 0. Without our values it would guess from the name, and give a young "wine" 6 / 4.</li>
 * </ul>
 */
public enum Quench
{
	/** A juice: our grape and sweet berry juice (the worts and buckets can't be drunk, so they quench nothing). */
	JUICE(7, 50, 4, 1.0F, 6, 4),
	/** An aged wine, beer or cider: smoother, and a little more quenching than a young one. */
	AGED(3, 20, 2, 0.4F, 4, 2),
	/** A young wine, beer or cider. */
	YOUNG(2, 10, 1, 0.2F, 3, 1),
	/** A bowl meal cooked in liquid (Wine-Braised Beef): like Farmer's Delight's stews. Tough As Nails counts drinks only. */
	STEW(0, 0, 3, 0.7F, 4, 2),
	/** A solid meal (Beer-Battered Fish): like most food in Homeostatic, nothing in the others. */
	MEAL(0, 0, 2, 0.6F, 0, 0),
	/** Fresh grapes: like sweet berries in Homeostatic, like any fruit in Thirst Was Taken 2. */
	FRUIT(0, 0, 2, 0.3F, 2, 0);

	/** Tough As Nails: thirst points and hydration percent (its tags come in steps of 10). */
	public final int tanThirst;
	public final int tanHydration;
	/** Homeostatic: water points and water saturation. */
	public final int homeostaticAmount;
	public final float homeostaticSaturation;
	/** Thirst Was Taken 2: thirst and quenched points. */
	public final int twtThirst;
	public final int twtQuenched;

	Quench(int tanThirst, int tanHydration, int homeostaticAmount, float homeostaticSaturation, int twtThirst, int twtQuenched) {
		this.tanThirst = tanThirst;
		this.tanHydration = tanHydration;
		this.homeostaticAmount = homeostaticAmount;
		this.homeostaticSaturation = homeostaticSaturation;
		this.twtThirst = twtThirst;
		this.twtQuenched = twtQuenched;
	}

	/** Whether Tough As Nails sees this as a drink at all. */
	public boolean tanDrink() {
		return tanThirst > 0;
	}

	public boolean twtDrink() {
		return twtThirst > 0 || twtQuenched > 0;
	}

	/** The drinkable kettle bases (bottles; the worts and the buckets can't be drunk). */
	private static final List<Supplier<? extends Item>> JUICES = List.of(BaseItems.GRAPE_JUICE, BaseItems.SWEET_BERRY_JUICE);

	/**
	 * The row for one of our items, or empty if it quenches nothing. Every {@link DrinkItem} is young or aged, so new
	 * wines and beers join by themselves; a new juice or meal needs a line here, and the GameTest
	 * {@code compat_every_drink_quenches} fails until everything eaten or drunk has a row.
	 */
	public static Optional<Quench> of(Item item) {
		if (item instanceof DrinkItem drink) {
			return Optional.of(drink.isAged() ? AGED : YOUNG);
		}
		if (JUICES.stream().anyMatch(juice -> juice.get() == item)) {
			return Optional.of(JUICE);
		}
		if (item == VBItems.RED_GRAPES.get()) {
			return Optional.of(FRUIT);
		}
		if (item instanceof MealItem) {
			return Optional.of(item == FDCompat.WINE_BRAISED_BEEF.get() ? STEW : MEAL);
		}
		return Optional.empty();
	}

	/** Every item of ours that quenches thirst somewhere, in registration order (stable data files). */
	public static List<Item> items() {
		return BuiltInRegistries.ITEM.stream()
			.filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(VineAndBarrel.MODID))
			.filter(item -> of(item).isPresent())
			.toList();
	}
}
