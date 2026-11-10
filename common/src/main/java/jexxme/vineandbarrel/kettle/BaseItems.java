package jexxme.vineandbarrel.kettle;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import jexxme.vineandbarrel.platform.registry.DeferredItem;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.util.List;

/**
 * The bases the Brew Kettle makes (DESIGN §4.1, §5): every base exists as a bottle (1 serving) and a bucket
 * (4 servings). Juice bottles are drinkable like a honey bottle (D3). Buckets are plain items, so they can never be
 * placed as a fluid (D4, like the milk bucket).
 */
public final class BaseItems
{
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VineAndBarrel.MODID);

	/**
	 * Light food, always drinkable, like a honey bottle but with the vanilla drink sound (D3): drunk in 2 seconds, and
	 * the bottle is left ({@link JuiceItem} makes it a drink, sets the time and gives the bottle back: this Minecraft's
	 * food has neither an eating time nor a "converts to").
	 */
	public static final FoodProperties JUICE_FOOD = new FoodProperties.Builder().nutrition(2).saturationMod(0.3F).alwaysEat().build();
	/** 2 seconds. */
	private static final int JUICE_DRINK_TICKS = 40;

	public static final DeferredItem<Item> GRAPE_JUICE = juice("grape_juice");
	public static final DeferredItem<Item> GRAPE_JUICE_BUCKET = bucket("grape_juice_bucket");
	public static final DeferredItem<Item> SWEET_BERRY_JUICE = juice("sweet_berry_juice");
	public static final DeferredItem<Item> SWEET_BERRY_JUICE_BUCKET = bucket("sweet_berry_juice_bucket");
	public static final DeferredItem<Item> BARLEY_WORT_BOTTLE = wort("barley_wort_bottle");
	public static final DeferredItem<Item> BARLEY_WORT_BUCKET = bucket("barley_wort_bucket");
	public static final DeferredItem<Item> WHEAT_WORT_BOTTLE = wort("wheat_wort_bottle");
	public static final DeferredItem<Item> WHEAT_WORT_BUCKET = bucket("wheat_wort_bucket");
	public static final DeferredItem<Item> WHITE_GRAPE_JUICE = juice("white_grape_juice");
	public static final DeferredItem<Item> WHITE_GRAPE_JUICE_BUCKET = bucket("white_grape_juice_bucket");
	public static final DeferredItem<Item> APPLE_JUICE = juice("apple_juice");
	public static final DeferredItem<Item> APPLE_JUICE_BUCKET = bucket("apple_juice_bucket");
	/** Honey and water, the base of mead. Like a wort it is not drunk as it is. */
	public static final DeferredItem<Item> HONEY_MUST_BOTTLE = wort("honey_must_bottle");
	public static final DeferredItem<Item> HONEY_MUST_BUCKET = bucket("honey_must_bucket");

	/**
	 * One liquid colour per base (DESIGN §9: colour = which drink), taken from the item art. The open kettle shows its
	 * brew in this colour (K2).
	 */
	private static final int GRAPE_COLOR = 0x74307A;
	private static final int SWEET_BERRY_COLOR = 0xC83A5C;
	private static final int BARLEY_COLOR = 0x94561E;
	private static final int WHEAT_COLOR = 0xCFA44C;
	private static final int WHITE_GRAPE_COLOR = 0xC4C860;
	private static final int APPLE_COLOR = 0xD08660;
	private static final int HONEY_COLOR = 0xEFBB34;
	public static final int WATER_COLOR = 0x3F76E4;

	private BaseItems() {
	}

	private static DeferredItem<Item> juice(String name) {
		return ITEMS.registerItem(name, JuiceItem::new, p -> p.craftRemainder(Items.GLASS_BOTTLE).food(JUICE_FOOD).stacksTo(16));
	}

	/** A juice bottle: food that is drunk (vanilla's drink animation and sound, no crumbs), and the bottle comes back. */
	private static class JuiceItem extends Item
	{
		JuiceItem(Properties properties) {
			super(properties);
		}

		@Override
		public UseAnim getUseAnimation(ItemStack stack) {
			return UseAnim.DRINK;
		}

		@Override
		public int getUseDuration(ItemStack stack) {
			return JUICE_DRINK_TICKS;
		}

		/**
		 * The empty bottle, as newer Minecraft's food hands back what it "converts to": to players only and not in
		 * creative; in place of the last juice of a stack, otherwise into the inventory.
		 */
		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			// The empty bottle comes back like a drink's vessel: into the inventory, or at the drinker's feet if it is full
			jexxme.vineandbarrel.drink.DrinkItem.Remainder bottle = jexxme.vineandbarrel.drink.DrinkItem.Remainder.before(stack);
			return bottle.after(super.finishUsingItem(stack, level, entity), level, entity);
		}

		/** Played once when the bottle is finished (LivingEntity#eat): a gulp, not a crunch. */
		@Override
		public SoundEvent getEatingSound() {
			return SoundEvents.GENERIC_DRINK;
		}
	}

	private static DeferredItem<Item> wort(String name) {
		return ITEMS.registerSimpleItem(name, p -> p.craftRemainder(Items.GLASS_BOTTLE).stacksTo(16));
	}

	private static DeferredItem<Item> bucket(String name) {
		return ITEMS.registerSimpleItem(name, p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
	}

	public static List<Item> all() {
		// Juices, then worts and must: the order of the creative tab and JEI
		return List.of(GRAPE_JUICE.get(), GRAPE_JUICE_BUCKET.get(), WHITE_GRAPE_JUICE.get(), WHITE_GRAPE_JUICE_BUCKET.get(),
			SWEET_BERRY_JUICE.get(), SWEET_BERRY_JUICE_BUCKET.get(), APPLE_JUICE.get(), APPLE_JUICE_BUCKET.get(), BARLEY_WORT_BOTTLE.get(),
			BARLEY_WORT_BUCKET.get(), WHEAT_WORT_BOTTLE.get(), WHEAT_WORT_BUCKET.get(), HONEY_MUST_BOTTLE.get(), HONEY_MUST_BUCKET.get());
	}

	/** The liquid colour of a brew, or -1 if it isn't one of our bases. */
	public static int liquidColor(ItemStack brew) {
		if (brew.is(GRAPE_JUICE.get()) || brew.is(GRAPE_JUICE_BUCKET.get())) return GRAPE_COLOR;
		if (brew.is(SWEET_BERRY_JUICE.get()) || brew.is(SWEET_BERRY_JUICE_BUCKET.get())) return SWEET_BERRY_COLOR;
		if (brew.is(BARLEY_WORT_BOTTLE.get()) || brew.is(BARLEY_WORT_BUCKET.get())) return BARLEY_COLOR;
		if (brew.is(WHEAT_WORT_BOTTLE.get()) || brew.is(WHEAT_WORT_BUCKET.get())) return WHEAT_COLOR;
		if (brew.is(WHITE_GRAPE_JUICE.get()) || brew.is(WHITE_GRAPE_JUICE_BUCKET.get())) return WHITE_GRAPE_COLOR;
		if (brew.is(APPLE_JUICE.get()) || brew.is(APPLE_JUICE_BUCKET.get())) return APPLE_COLOR;
		if (brew.is(HONEY_MUST_BOTTLE.get()) || brew.is(HONEY_MUST_BUCKET.get())) return HONEY_COLOR;
		return -1;
	}
}
