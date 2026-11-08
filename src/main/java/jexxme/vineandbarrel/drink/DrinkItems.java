package jexxme.vineandbarrel.drink;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The vessels and the drinks (DESIGN §5). One rule for every drink: it stacks to 16, drinks like an FD drink, gives
 * back its vessel, gives one vanilla buff and one level of Tipsy. Playtest 1: a young drink's buff is level I for
 * {@value #YOUNG_TICKS} ticks (3 min), an aged one's level II for {@value #AGED_TICKS} ticks (5 min), with the
 * enchantment glint (no FD Comfort: FD itself switched Comfort off for its foods). Saturation is instant, so wheat beer
 * has its own short timings instead.
 */
public final class DrinkItems
{
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VineAndBarrel.MODID);

	/** FD's apple cider: always drinkable, no hunger of its own; the buff is the point. */
	public static final FoodProperties DRINK_FOOD = new FoodProperties.Builder().alwaysEdible().build();
	public static final int STACK_SIZE = 16;
	/** A young drink's buff: level I for 3 minutes. */
	public static final int YOUNG_TICKS = 3 * 60 * 20;
	/** An aged drink's buff: level II for 5 minutes. */
	public static final int AGED_TICKS = 5 * 60 * 20;
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

	// ------------------------------------------------------------------ the 8 base drinks (§5.1) and the 4 FD bonus drinks (§5.2)
	// Colours are the liquid's middle tone in the item art (colour = which drink); aged ones are the art's deeper shade.
	public static final DeferredItem<DrinkItem> RED_WINE = drink("red_wine", WINE_GLASS, 0x8E1A2E, MobEffects.REGENERATION, false);
	public static final DeferredItem<DrinkItem> AGED_RED_WINE = drink("aged_red_wine", WINE_GLASS, 0x7D0F22, MobEffects.REGENERATION, true);
	public static final DeferredItem<DrinkItem> SWEET_BERRY_WINE = drink("sweet_berry_wine", WINE_GLASS, 0xF27A92, MobEffects.SPEED, false);
	public static final DeferredItem<DrinkItem> AGED_SWEET_BERRY_WINE = drink("aged_sweet_berry_wine", WINE_GLASS, 0xF63A60, MobEffects.SPEED, true);
	public static final DeferredItem<DrinkItem> BARLEY_ALE = drink("barley_ale", MUG, 0xCC6C1C, MobEffects.HASTE, false);
	public static final DeferredItem<DrinkItem> AGED_BARLEY_ALE = drink("aged_barley_ale", MUG, 0xB4590E, MobEffects.HASTE, true);
	public static final DeferredItem<DrinkItem> WHEAT_BEER = drink("wheat_beer", MUG, 0xF2D06A, MobEffects.SATURATION, WHEAT_BEER_YOUNG_TICKS, false);
	public static final DeferredItem<DrinkItem> AGED_WHEAT_BEER = drink("aged_wheat_beer", MUG, 0xF8C52B, MobEffects.SATURATION, WHEAT_BEER_AGED_TICKS, true);
	public static final DeferredItem<DrinkItem> HARD_CIDER = drink("hard_cider", MUG, 0xD4684A, MobEffects.ABSORPTION, false);
	public static final DeferredItem<DrinkItem> AGED_HARD_CIDER = drink("aged_hard_cider", MUG, 0xCB4925, MobEffects.ABSORPTION, true);
	public static final DeferredItem<DrinkItem> MELON_WINE = drink("melon_wine", WINE_GLASS, 0xA0CC5C, MobEffects.FIRE_RESISTANCE, false);
	public static final DeferredItem<DrinkItem> AGED_MELON_WINE = drink("aged_melon_wine", WINE_GLASS, 0x8BC533, MobEffects.FIRE_RESISTANCE, true);

	private DrinkItems() {
	}

	private static DeferredItem<DrinkItem> drink(String name, DeferredItem<VesselItem> vessel, int color, Holder<MobEffect> effect, boolean aged) {
		return drink(name, vessel, color, effect, aged ? AGED_TICKS : YOUNG_TICKS, aged);
	}

	/** @param ticks the buff's duration; the level is I for a young drink, II for an aged one */
	private static DeferredItem<DrinkItem> drink(String name, DeferredItem<VesselItem> vessel, int color, Holder<MobEffect> effect, int ticks, boolean aged) {
		MobEffectInstance buff = new MobEffectInstance(effect, ticks, aged ? AGED_AMPLIFIER : YOUNG_AMPLIFIER);
		return ITEMS.registerItem(name, p -> new DrinkItem(p, color, buff, aged), () -> {
			Item.Properties properties = new Item.Properties()
				.food(DRINK_FOOD, Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(buff)).build())
				.usingConvertsTo(vessel.get())
				.craftRemainder(vessel.get())
				.stacksTo(STACK_SIZE);
			return aged ? properties.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true) : properties;
		});
	}

	public static List<DrinkItem> allDrinks() {
		return List.of(RED_WINE.get(), AGED_RED_WINE.get(), SWEET_BERRY_WINE.get(), AGED_SWEET_BERRY_WINE.get(), BARLEY_ALE.get(),
			AGED_BARLEY_ALE.get(), WHEAT_BEER.get(), AGED_WHEAT_BEER.get(), HARD_CIDER.get(), AGED_HARD_CIDER.get(), MELON_WINE.get(),
			AGED_MELON_WINE.get());
	}
}
