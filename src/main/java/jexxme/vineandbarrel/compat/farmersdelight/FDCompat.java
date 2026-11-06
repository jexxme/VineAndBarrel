package jexxme.vineandbarrel.compat.farmersdelight;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;

/**
 * Farmer's Delight integration (DESIGN §7). Almost all of it is data: tags with optional FD entries, and recipes behind
 * {@code neoforge:mod_loaded}. Nothing here touches an FD class; FD things are looked up by id at runtime, so this
 * works the same with and without FD on the classpath.
 * <p>
 * The two cooking pot meals (§7) always exist, so worlds and loot stay stable without FD; only their recipes need FD.
 * They are FD-style bowl meals: FD's food values for a comparable meal, stack to 16, the bowl comes back, and FD's
 * Nourishment when FD is installed, exactly as FD's own meals give it.
 */
public final class FDCompat
{
	public static final String MODID = "farmersdelight";
	public static final Identifier NOURISHMENT = id("nourishment");
	public static final Identifier COMFORT = id("comfort");
	/** FD's {@code MEDIUM_DURATION} (3 minutes) and {@code LONG_DURATION} (5 minutes) for meal effects. */
	public static final int MEDIUM_DURATION = 3600;
	public static final int LONG_DURATION = 6000;

	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VineAndBarrel.MODID);

	/** Fish + barley ale + wheat: three ingredients like FD's fish stew, so its values (12, 0.8, Nourishment 3 min). */
	public static final DeferredItem<MealItem> BEER_BATTERED_FISH = meal("beer_battered_fish", 12, 0.8F, MEDIUM_DURATION);
	/** Beef + red wine + onion + carrot: four ingredients like FD's baked cod stew, so its values (14, 0.75, Nourishment 5 min). */
	public static final DeferredItem<MealItem> WINE_BRAISED_BEEF = meal("wine_braised_beef", 14, 0.75F, LONG_DURATION);

	private FDCompat() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MODID, path);
	}

	public static boolean isLoaded() {
		return ModList.get().isLoaded(MODID);
	}

	/** FD's Nourishment, if Farmer's Delight is installed. */
	public static Optional<Holder.Reference<MobEffect>> nourishment() {
		return BuiltInRegistries.MOB_EFFECT.get(NOURISHMENT);
	}

	public static List<MealItem> meals() {
		return List.of(BEER_BATTERED_FISH.get(), WINE_BRAISED_BEEF.get());
	}

	/** Like FD's {@code ModItems.bowlFoodItem}: a bowl meal stacks to 16 and gives its bowl back. */
	private static DeferredItem<MealItem> meal(String name, int nutrition, float saturation, int nourishmentTicks) {
		FoodProperties food = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
		return ITEMS.registerItem(name, p -> new MealItem(p, nourishmentTicks), () -> new Item.Properties()
			.food(food)
			.usingConvertsTo(Items.BOWL)
			.craftRemainder(Items.BOWL)
			.stacksTo(16));
	}

	public static void register(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
	}
}
