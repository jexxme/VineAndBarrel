package jexxme.vineandbarrel.drink;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import jexxme.vineandbarrel.platform.registry.DeferredItem;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.util.List;

/**
 * The vessels and the drinks (DESIGN §5). One rule for every drink: it stacks to 16, drinks like an FD drink, gives
 * back its vessel, gives one vanilla buff and one level of Tipsy. It sounds like its vessel: vanilla's drink sound,
 * deeper from a mug, lighter from a wine glass. Playtest 1: a young drink's buff is level I for
 * {@value #YOUNG_TICKS} ticks (45 s), an aged one's level II for {@value #AGED_TICKS} ticks (90 s), with the
 * enchantment glint (no FD Comfort: FD itself switched Comfort off for its foods). Saturation is instant, so wheat beer
 * has its own short timings instead.
 */
public final class DrinkItems
{
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VineAndBarrel.MODID);

	public static final int STACK_SIZE = 16;
	/** A young drink's buff: level I for 45 seconds, like vanilla's Regeneration potion (Jexxme, pre-1.0 audit). */
	public static final int YOUNG_TICKS = 45 * 20;
	/** An aged drink's buff: level II for 90 seconds. */
	public static final int AGED_TICKS = 90 * 20;
	public static final int YOUNG_AMPLIFIER = 0;
	public static final int AGED_AMPLIFIER = 1;
	/**
	 * Saturation feeds once per tick: level I gives 1 food and 2 saturation, level II twice that. Young wheat beer:
	 * level I for 5 ticks (5 food, 10 saturation, about a baked potato); aged: level II for 4 ticks (8 food, 16
	 * saturation, about a steak). Filling, but no food replacement for a drink that stacks to 16.
	 */
	public static final int WHEAT_BEER_YOUNG_TICKS = 5;
	public static final int WHEAT_BEER_AGED_TICKS = 4;

	// ------------------------------------------------------------------ vessels (empty, stack like glass bottles and bowls)
	public static final DeferredItem<VesselItem> MUG = ITEMS.registerItem("mug", VesselItem::new);
	public static final DeferredItem<VesselItem> WINE_GLASS = ITEMS.registerItem("wine_glass", VesselItem::new);

	// ------------------------------------------------------------------ the 8 base drinks (§5.1), hard cider and melon wine (§5.2)
	// Hard cider comes from our apple juice, or from FD's apple cider; melon wine only from FD's melon juice.
	// Colours are the liquid's middle tone in the item art (colour = which drink); aged ones are the art's deeper shade.
	public static final DeferredItem<DrinkItem> RED_WINE = drink("red_wine", WINE_GLASS, 0x8E1A2E, MobEffects.REGENERATION, false);
	public static final DeferredItem<DrinkItem> AGED_RED_WINE = drink("aged_red_wine", WINE_GLASS, 0x7D0F22, MobEffects.REGENERATION, true);
	public static final DeferredItem<DrinkItem> SWEET_BERRY_WINE = drink("sweet_berry_wine", WINE_GLASS, 0xF27A92, MobEffects.MOVEMENT_SPEED, false);
	public static final DeferredItem<DrinkItem> AGED_SWEET_BERRY_WINE = drink("aged_sweet_berry_wine", WINE_GLASS, 0xF63A60, MobEffects.MOVEMENT_SPEED, true);
	public static final DeferredItem<DrinkItem> BARLEY_ALE = drink("barley_ale", MUG, 0xCC6C1C, MobEffects.DIG_SPEED, false);
	public static final DeferredItem<DrinkItem> AGED_BARLEY_ALE = drink("aged_barley_ale", MUG, 0xB4590E, MobEffects.DIG_SPEED, true);
	public static final DeferredItem<DrinkItem> WHEAT_BEER = drink("wheat_beer", MUG, 0xF2D06A, MobEffects.SATURATION, WHEAT_BEER_YOUNG_TICKS, false);
	public static final DeferredItem<DrinkItem> AGED_WHEAT_BEER = drink("aged_wheat_beer", MUG, 0xF8C52B, MobEffects.SATURATION, WHEAT_BEER_AGED_TICKS, true);
	public static final DeferredItem<DrinkItem> HARD_CIDER = drink("hard_cider", MUG, 0xD4684A, MobEffects.ABSORPTION, false);
	public static final DeferredItem<DrinkItem> AGED_HARD_CIDER = drink("aged_hard_cider", MUG, 0xCB4925, MobEffects.ABSORPTION, true);
	public static final DeferredItem<DrinkItem> MELON_WINE = drink("melon_wine", WINE_GLASS, 0xA0CC5C, MobEffects.FIRE_RESISTANCE, false);
	public static final DeferredItem<DrinkItem> AGED_MELON_WINE = drink("aged_melon_wine", WINE_GLASS, 0x8BC533, MobEffects.FIRE_RESISTANCE, true);
	// ------------------------------------------------------------------ new drinks: white wine (white grapes) and mead (honey)
	// Night Vision has no levels, so aged white wine lasts longer but is no stronger, like melon wine's Fire Resistance (Jexxme kept it)
	public static final DeferredItem<DrinkItem> WHITE_WINE = drink("white_wine", WINE_GLASS, 0xE6D888, MobEffects.NIGHT_VISION, false);
	public static final DeferredItem<DrinkItem> AGED_WHITE_WINE = drink("aged_white_wine", WINE_GLASS, 0xE3CD4F, MobEffects.NIGHT_VISION, true);
	public static final DeferredItem<DrinkItem> MEAD = drink("mead", MUG, 0xEEAA12, MobEffects.DAMAGE_RESISTANCE, false);
	public static final DeferredItem<DrinkItem> AGED_MEAD = drink("aged_mead", MUG, 0xD39303, MobEffects.DAMAGE_RESISTANCE, true);

	private DrinkItems() {
	}

	private static DeferredItem<DrinkItem> drink(String name, DeferredItem<VesselItem> vessel, int color, Holder<MobEffect> effect, boolean aged) {
		return drink(name, vessel, color, effect, aged ? AGED_TICKS : YOUNG_TICKS, aged);
	}

	/** @param ticks the buff's duration; the level is I for a young drink, II for an aged one */
	private static DeferredItem<DrinkItem> drink(String name, DeferredItem<VesselItem> vessel, int color, Holder<MobEffect> effect, int ticks, boolean aged) {
		MobEffectInstance buff = new MobEffectInstance(effect, ticks, aged ? AGED_AMPLIFIER : YOUNG_AMPLIFIER);
		return ITEMS.registerItem(name, p -> new DrinkItem(p, color, buff, aged,
			vessel == MUG ? BarrelRegistry.MUG_DRINK : BarrelRegistry.WINE_GLASS_DRINK), () -> {
			// FD's apple cider: always drinkable, no hunger of its own; the buff is the point. 1.21.1 has no consumable
			// component: DrinkItem gives the buff when the drink is finished, the vessel is what the food converts to; the drink
			// animation and the vessel's sound are DrinkItem's
			Item.Properties properties = new Item.Properties()
				.food(new FoodProperties.Builder().alwaysEdible().usingConvertsTo(vessel.get()).build())
				.craftRemainder(vessel.get())
				.stacksTo(STACK_SIZE);
			return aged ? properties.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true) : properties;
		});
	}

	/**
	 * How a drink sounds when poured (filling a barrel, tapping it): wine thin and light, beer deep and foamy, cider and
	 * mead in between. A pitch factor for vanilla's bottle and bucket sounds; 1 for anything else.
	 */
	public static float pourPitch(ItemStack drink) {
		if (drink.is(HARD_CIDER.get()) || drink.is(AGED_HARD_CIDER.get()) || drink.is(MEAD.get()) || drink.is(AGED_MEAD.get())) {
			return 1.0F;
		}
		PlacedDrink vessel = PlacedDrink.of(drink);
		return vessel == null ? 1.0F : vessel.isMug() ? 0.88F : 1.12F;
	}

	public static List<DrinkItem> allDrinks() {
		// Wines, then the mug drinks: the order of the creative tab and JEI
		return List.of(RED_WINE.get(), AGED_RED_WINE.get(), WHITE_WINE.get(), AGED_WHITE_WINE.get(), SWEET_BERRY_WINE.get(),
			AGED_SWEET_BERRY_WINE.get(), MELON_WINE.get(), AGED_MELON_WINE.get(), BARLEY_ALE.get(), AGED_BARLEY_ALE.get(), WHEAT_BEER.get(),
			AGED_WHEAT_BEER.get(), MEAD.get(), AGED_MEAD.get(), HARD_CIDER.get(), AGED_HARD_CIDER.get());
	}
}
