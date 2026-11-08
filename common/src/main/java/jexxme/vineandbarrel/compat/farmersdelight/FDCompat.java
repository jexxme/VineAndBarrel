package jexxme.vineandbarrel.compat.farmersdelight;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import jexxme.vineandbarrel.platform.registry.DeferredBlock;
import jexxme.vineandbarrel.platform.registry.DeferredItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.util.List;
import java.util.Optional;

/**
 * Farmer's Delight integration (DESIGN §7). Almost all of it is data: tags with optional FD entries, and recipes behind
 * {@code neoforge:mod_loaded}. Nothing here touches an FD class; FD things are looked up by id at runtime, so this
 * works the same with and without FD on the classpath.
 * <p>
 * The cooking pot meals (§7), the Coq au Vin feast and the beer bread always exist, so worlds and loot stay stable without FD; only their recipes need FD.
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

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VineAndBarrel.MODID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VineAndBarrel.MODID);
	private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, VineAndBarrel.MODID);
	/** Serving from the feast: vanilla's equip sound with a subtitle of its own, like FD's {@code block.food.take_portion}. */
	public static final Holder<SoundEvent> FEAST_SERVE = SOUNDS.register("block.feast.serve",
		() -> SoundEvent.createVariableRangeEvent(VineAndBarrel.id("block.feast.serve"))).asHolder();

	/** Fish + pale ale + wheat: three ingredients like FD's fish stew, so its values (12, 0.8, Nourishment 3 min). */
	public static final DeferredItem<MealItem> BEER_BATTERED_FISH = meal("beer_battered_fish", 12, 0.8F, MEDIUM_DURATION);
	/** Beef + red wine + onion + carrot: four ingredients like FD's baked cod stew, so its values (14, 0.75, Nourishment 5 min). */
	public static final DeferredItem<MealItem> WINE_BRAISED_BEEF = meal("wine_braised_beef", 14, 0.75F, LONG_DURATION);
	/** Pork + hard cider + apple + onion: four ingredients, FD's 4-ingredient values. */
	public static final DeferredItem<MealItem> CIDER_BRAISED_PORK = meal("cider_braised_pork", 14, 0.75F, LONG_DURATION);
	/** Mutton + barley + carrot + onion (a Scotch broth): four ingredients, FD's 4-ingredient values. */
	public static final DeferredItem<MealItem> BARLEY_MUTTON_BROTH = meal("barley_mutton_broth", 14, 0.75F, LONG_DURATION);
	/** Rice + white wine + onion + FD's bone broth as the stock: four ingredients, FD's 4-ingredient values. */
	public static final DeferredItem<MealItem> WHITE_WINE_RISOTTO = meal("white_wine_risotto", 14, 0.75F, LONG_DURATION);
	/** One serving of the Coq au Vin feast, like FD's feast bowls: FD's 4-ingredient values. Only served from the feast. */
	public static final DeferredItem<MealItem> COQ_AU_VIN = meal("coq_au_vin", 14, 0.75F, LONG_DURATION);
	/** Wheat and ale or wheat beer, crafted: a plain loaf, a little more than vanilla bread (5, 0.6), no effect. */
	public static final DeferredItem<Item> BEER_BREAD = ITEMS.registerSimpleItem("beer_bread", () -> new Item.Properties()
		.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).build()));

	/** Coq au Vin, braised in its iron pot and placed as a feast: four bowls, then the bones. Cooked in FD's pot. */
	public static final DeferredBlock<FeastBlock> COQ_AU_VIN_BLOCK = BLOCKS.registerBlock("coq_au_vin_block",
		p -> new FeastBlock(p, COQ_AU_VIN), () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
			.strength(0.8F).sound(SoundType.LANTERN).noOcclusion().pushReaction(PushReaction.DESTROY));
	public static final DeferredItem<BlockItem> COQ_AU_VIN_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(COQ_AU_VIN_BLOCK,
		() -> new Item.Properties().stacksTo(1));

	private FDCompat() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MODID, path);
	}

	public static boolean isLoaded() {
		return jexxme.vineandbarrel.platform.Services.PLATFORM.isModLoaded(MODID);
	}

	/** FD's Nourishment, if Farmer's Delight is installed. */
	public static Optional<Holder.Reference<MobEffect>> nourishment() {
		return BuiltInRegistries.MOB_EFFECT.get(NOURISHMENT);
	}

	public static List<MealItem> meals() {
		return List.of(BEER_BATTERED_FISH.get(), WINE_BRAISED_BEEF.get(), CIDER_BRAISED_PORK.get(), BARLEY_MUTTON_BROTH.get(),
			WHITE_WINE_RISOTTO.get(), COQ_AU_VIN.get());
	}

	/** Everything of ours that is made with FD, in the creative tab's order: the bread, the feast, the bowl meals. */
	public static List<Item> items() {
		List<Item> items = new java.util.ArrayList<>(List.of(BEER_BREAD.get(), COQ_AU_VIN_BLOCK_ITEM.get()));
		items.addAll(meals());
		return items;
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

	public static void register() {
		BLOCKS.register();
		ITEMS.register();
		SOUNDS.register();
	}
}
