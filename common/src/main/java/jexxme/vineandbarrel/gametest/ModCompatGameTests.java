package jexxme.vineandbarrel.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.compat.CompatData;
import jexxme.vineandbarrel.compat.Conventions;
import jexxme.vineandbarrel.compat.appleskin.AppleSkinCompat;
import jexxme.vineandbarrel.compat.appleskin.DrinkFood;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.compat.nutrition.Nutrient;
import jexxme.vineandbarrel.compat.thirst.Quench;
import jexxme.vineandbarrel.drink.DrinkItem;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import jexxme.vineandbarrel.platform.Services;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The optional integrations with other mods (AppleSkin, the thirst mods, the nutrition mods). Every test runs in all
 * three GameTest runs (the compat run runs only these): without and with Farmer's Delight it checks our side (the data files load and say exactly what
 * {@link Quench} and {@link Nutrient} say, AppleSkin's hook, which is in every dev run); in the compat run
 * ({@code ./gradlew runGameTestServerCompat}), where Tough As Nails, Homeostatic, Thirst Was Taken 2, Nutritional
 * Balance and Spice of Life are installed, it also asks each mod itself what it made of our data. The nested probes are
 * the only code that touches those mods' classes ({@link CompatProbes}, per loader), and only run when the mod is loaded.
 * Fabric's compat run has neither Nutritional Balance nor Spice of Life, which have no Fabric build for this Minecraft.
 */
public final class ModCompatGameTests
{
	private static final ResourceLocation SMALL = VineAndBarrel.id("gametest/empty_5x5");
	private static final List<Entry> TESTS = new ArrayList<>();

	private record Entry(String name, Consumer<GameTestHelper> function) {
	}

	public static final String TOUGH_AS_NAILS = "toughasnails";
	public static final String HOMEOSTATIC = "homeostatic";
	public static final String THIRST_WAS_TAKEN = "thirstwastaken2";
	public static final String NUTRITIONAL_BALANCE = "nutritionalbalance";
	public static final String SPICE_OF_LIFE = "solonion";
	/** The mods the loader's metadata lists as optional for these integrations (checked per loader: compat_optional_dependencies). */
	public static final List<String> OPTIONAL = List.of(AppleSkinCompat.MODID, TOUGH_AS_NAILS, HOMEOSTATIC, THIRST_WAS_TAKEN, NUTRITIONAL_BALANCE);
	/** Everything any loader's compat run installs (with the libraries Tough As Nails and Spice of Life need). */
	private static final List<String> COMPAT_RUN = List.of(TOUGH_AS_NAILS, "glitchcore", HOMEOSTATIC, THIRST_WAS_TAKEN, NUTRITIONAL_BALANCE, SPICE_OF_LIFE, "creativecore");
	/** The loader's probes (see {@link CompatProbes}), found the first time a test asks. */
	private static final java.util.function.Supplier<CompatProbes> PROBES = com.google.common.base.Suppliers.memoize(() -> Services.load(CompatProbes.class));

	static {
		test("compat_run_has_the_right_mods", ModCompatGameTests::rightMods);
		loaderTest("compat_optional_dependencies");
		test("compat_every_drink_quenches", ModCompatGameTests::everyDrinkQuenches);
		test("compat_tough_as_nails_tags", ModCompatGameTests::toughAsNailsTags);
		test("compat_homeostatic_data", ModCompatGameTests::homeostaticData);
		test("compat_thirst_was_taken_data", ModCompatGameTests::thirstWasTakenData);
		test("compat_every_food_has_a_nutrient", ModCompatGameTests::nutrientTags);
		test("compat_convention_tags", ModCompatGameTests::conventionTags);
		test("compat_appleskin_shows_wheat_beer_food", ModCompatGameTests::appleSkin);
		test("compat_wheat_beer_feeds_what_appleskin_shows", ModCompatGameTests::wheatBeerFeeds);
		test("compat_tough_as_nails_quenches", ModCompatGameTests::toughAsNailsQuenches);
		test("compat_homeostatic_reads_ours", ModCompatGameTests::homeostaticReadsOurs);
		test("compat_thirst_was_taken_reads_ours", ModCompatGameTests::thirstWasTakenReadsOurs);
		test("compat_nutritional_balance_reads_ours", ModCompatGameTests::nutritionalBalanceReadsOurs);
		test("compat_spice_of_life_counts_meals", ModCompatGameTests::spiceOfLifeCountsMeals);
	}

	private ModCompatGameTests() {
	}

	/** A test that reads the loader's own mod metadata (NeoForgeGameTests, FabricGameTests), under the same name. */
	private static void loaderTest(String name) {
		Consumer<GameTestHelper> function = Services.PLATFORM.loaderGameTest(name);
		if (function != null) {
			test(name, function);
		}
	}

	private static void test(String name, Consumer<GameTestHelper> function) {
		TESTS.add(new Entry(name, function));
	}

	public static void register() {
		jexxme.vineandbarrel.platform.Services.PLATFORM.addGameTests(ModCompatGameTests::registerTests);
	}

	private static void registerTests(GameTestRegistrar event) {
		for (Entry test : TESTS) {
			VBGameTests.add(event, "mod_compat", test.name(), SMALL, 40, test.function());
		}
	}

	// ------------------------------------------------------------------ helpers

	private static boolean loaded(String modId) {
		return jexxme.vineandbarrel.platform.Services.PLATFORM.isModLoaded(modId);
	}

	/** Every item of ours, in registration order. */
	private static List<Item> ourItems() {
		return BuiltInRegistries.ITEM.stream().filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(VineAndBarrel.MODID)).toList();
	}

	/** Eaten or drunk: on this Minecraft that is having food (a drink is a food whose item drinks). */
	private static boolean consumable(Item item) {
		return item.components().has(DataComponents.FOOD);
	}

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	private static JsonElement readJson(GameTestHelper helper, ResourceLocation file) {
		ResourceManager resources = helper.getLevel().getServer().getResourceManager();
		Optional<Resource> resource = resources.getResource(file);
		helper.assertTrue(resource.isPresent(), "data file " + file + " is loaded");
		try (Reader reader = resource.get().openAsReader()) {
			return JsonParser.parseReader(reader);
		} catch (IOException e) {
			throw new IllegalStateException("can't read " + file, e);
		}
	}

	private static void assertClose(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 1.0E-4F, what + ": expected " + expected + ", got " + actual);
	}

	private static final class TestPlayer extends ServerPlayer
	{
		TestPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
			super(server, level, profile, ClientInformation.createDefault());
		}

		// always in survival (26.x: gameMode(); this Minecraft asks these two)
		@Override
		public boolean isCreative() {
			return false;
		}

		@Override
		public boolean isSpectator() {
			return false;
		}

		/** Drinks or eats exactly the way the game finishes using an item (so other mods' use-item events fire too). */
		void consume(ItemStack stack) {
			this.setItemInHand(InteractionHand.MAIN_HAND, stack);
			this.startUsingItem(InteractionHand.MAIN_HAND);
			this.completeUsingItem();
		}

		void tickEffectsOnce() {
			this.tickEffects();
		}
	}

	/**
	 * A survival player of its own: each {@code index} stands 8 blocks above the last, out of Cheers range
	 * ({@link jexxme.vineandbarrel.drink.Cheers#RADIUS}), so two drinks in one test never toast each other.
	 */
	private static TestPlayer player(GameTestHelper helper, int index) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "compat-test-player"), false);
		TestPlayer player = new TestPlayer(level.getServer(), level, cookie.gameProfile());
		GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		// As a real client: the thirst mods sync their data to the player, which a bare test connection refuses
		PROBES.get().prepareMockConnection(connection);
		new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie);
		Vec3 pos = helper.absoluteVec(new Vec3(2.5, 1.0 + 8 * index, 2.5));
		player.setPos(pos.x, pos.y, pos.z);
		return player;
	}

	// ------------------------------------------------------------------ the runs and neoforge.mods.toml

	/** The compat run (vineandbarrel.expectCompat) has every compat mod, the other runs none; AppleSkin is in every dev run. */
	private static void rightMods(GameTestHelper helper) {
		boolean expected = Boolean.getBoolean("vineandbarrel.expectCompat");
		List<String> here = PROBES.get().compatRunMods();
		for (String mod : COMPAT_RUN) {
			helper.assertValueEqual(loaded(mod), expected && here.contains(mod), mod + " loaded (only the compat run installs it, where it has a build)");
		}
		if (Services.PLATFORM.isDevelopment()) {
			helper.assertTrue(loaded(AppleSkinCompat.MODID), "AppleSkin is in every dev run (localRuntime)");
		}
		// The compat run is told which versions gradle.properties names (the ones the wiki says we tested with)
		for (String pair : System.getProperty("vineandbarrel.compatVersions", "").split(",")) {
			if (!pair.isBlank()) {
				String[] idAndVersion = pair.split("=", 2);
				String installed = Services.PLATFORM.modVersion(idAndVersion[0]).orElse("not installed");
				helper.assertValueEqual(installed, idAndVersion[1], idAndVersion[0] + " version (gradle.properties)");
			}
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ thirst: our side

	/**
	 * Everything of ours that is eaten or drunk has a thirst row, nothing else has one (worts and buckets can't be
	 * drunk), and every mod's columns keep the design: juice above aged above young, all in the mod's ranges.
	 */
	private static void everyDrinkQuenches(GameTestHelper helper) {
		for (Item item : ourItems()) {
			helper.assertValueEqual(Quench.of(item).isPresent(), consumable(item), id(item) + " has a thirst row exactly when it is eaten or drunk");
		}
		for (DrinkItem drink : DrinkItems.allDrinks()) {
			helper.assertValueEqual(Quench.of(drink).orElseThrow(), drink.isAged() ? Quench.AGED : Quench.YOUNG, id(drink) + " row");
		}
		for (Item juice : JUICES.get()) {
			helper.assertValueEqual(Quench.of(juice).orElseThrow(), Quench.JUICE, id(juice) + " row");
		}
		for (Item notDrunk : List.of(BaseItems.BARLEY_WORT_BOTTLE.get(), BaseItems.HONEY_MUST_BOTTLE.get(), BaseItems.GRAPE_JUICE_BUCKET.get(),
				BaseItems.APPLE_JUICE_BUCKET.get(), DrinkItems.MUG.get(), VBItems.BARLEY.get())) {
			helper.assertTrue(Quench.of(notDrunk).isEmpty(), id(notDrunk) + " quenches nothing");
		}
		helper.assertValueEqual(Quench.of(FDCompat.WINE_BRAISED_BEEF.get()).orElseThrow(), Quench.STEW, "braised beef is a stew");
		helper.assertValueEqual(Quench.of(FDCompat.BEER_BATTERED_FISH.get()).orElseThrow(), Quench.MEAL, "battered fish is a meal");
		helper.assertValueEqual(Quench.of(VBItems.RED_GRAPES.get()).orElseThrow(), Quench.FRUIT, "grapes are fruit");
		helper.assertValueEqual(Quench.of(VBItems.WHITE_GRAPES.get()).orElseThrow(), Quench.FRUIT, "white grapes are fruit");

		Quench juice = Quench.JUICE, aged = Quench.AGED, young = Quench.YOUNG;
		helper.assertTrue(juice.tanThirst > aged.tanThirst && aged.tanThirst > young.tanThirst && young.tanThirst > 0, "TAN thirst: juice > aged > young > 0");
		helper.assertTrue(juice.tanHydration > aged.tanHydration && aged.tanHydration > young.tanHydration && young.tanHydration > 0, "TAN hydration order");
		helper.assertTrue(juice.homeostaticAmount > aged.homeostaticAmount && aged.homeostaticAmount > young.homeostaticAmount && young.homeostaticAmount > 0, "Homeostatic amount order");
		helper.assertTrue(juice.homeostaticSaturation > aged.homeostaticSaturation && aged.homeostaticSaturation > young.homeostaticSaturation, "Homeostatic saturation order");
		helper.assertTrue(juice.twtThirst > aged.twtThirst && aged.twtThirst > young.twtThirst && young.twtThirst > 0, "TWT thirst order");
		helper.assertTrue(juice.twtQuenched > aged.twtQuenched && aged.twtQuenched > young.twtQuenched, "TWT quenched order");
		for (Quench quench : Quench.values()) {
			helper.assertTrue(quench.tanDrink() ? quench.tanThirst <= 20 && quench.tanHydration % 10 == 0 && quench.tanHydration >= 10 && quench.tanHydration <= 100
				: quench.tanHydration == 0, quench + ": a Tough As Nails tag exists for its values");
			helper.assertTrue(quench.homeostaticAmount <= 20 && quench.homeostaticSaturation <= 5.0F, quench + ": in Homeostatic's range");
			helper.assertTrue(quench.twtThirst <= 20 && quench.twtQuenched <= 20, quench + ": in Thirst Was Taken's range");
		}
		helper.succeed();
	}

	private static final TagKey<Item> TAN_DRINKS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(CompatData.TAN, "drinks"));

	/** Each drink is in exactly one of Tough As Nails' thirst tags and one hydration tag, its row's; food in none. */
	private static void toughAsNailsTags(GameTestHelper helper) {
		for (Item item : ourItems()) {
			Optional<Quench> quench = Quench.of(item).filter(Quench::tanDrink);
			ItemStack stack = new ItemStack(item);
			for (int thirst = 1; thirst <= 20; thirst++) {
				boolean expected = quench.isPresent() && quench.get().tanThirst == thirst;
				helper.assertValueEqual(stack.is(CompatData.tanThirstTag(thirst)), expected, id(item) + " in toughasnails:thirst/" + thirst + "_thirst_drinks");
			}
			for (int hydration = 10; hydration <= 100; hydration += 10) {
				boolean expected = quench.isPresent() && quench.get().tanHydration == hydration;
				helper.assertValueEqual(stack.is(CompatData.tanHydrationTag(hydration)), expected, id(item) + " in toughasnails:hydration/" + hydration + "_hydration_drinks");
			}
			if (loaded(TOUGH_AS_NAILS)) {
				helper.assertValueEqual(stack.is(TAN_DRINKS), quench.isPresent(), id(item) + " in #toughasnails:drinks (TAN's tag of all its thirst tags)");
			}
		}
		helper.succeed();
	}

	/** Homeostatic's files: one per item of ours that quenches, in its format, with the row's values, and no others. */
	private static void homeostaticData(GameTestHelper helper) {
		for (Item item : Quench.items()) {
			Quench quench = Quench.of(item).orElseThrow();
			JsonObject json = readJson(helper, CompatData.homeostaticFile(item)).getAsJsonObject();
			helper.assertValueEqual(json.get("type").getAsString(), id(item), "Homeostatic file names its item");
			helper.assertValueEqual(json.get("amount").getAsInt(), quench.homeostaticAmount, id(item) + " Homeostatic amount");
			assertClose(helper, json.get("saturation").getAsFloat(), quench.homeostaticSaturation, id(item) + " Homeostatic saturation");
			helper.assertValueEqual(json.get("effect_chance").getAsFloat(), 0.0F, id(item) + " never makes you sick");
			helper.assertValueEqual(json.keySet(), Set.of("type", "amount", "saturation", "effect_chance", "effect_duration", "effect_potency"), "Homeostatic's fields");
		}
		int files = helper.getLevel().getServer().getResourceManager()
			.listResources(CompatData.HOMEOSTATIC_FOLDER, file -> file.getNamespace().equals(VineAndBarrel.MODID)).size();
		helper.assertValueEqual(files, Quench.items().size(), "one Homeostatic file per item that quenches");
		helper.succeed();
	}

	/** Thirst Was Taken 2's file: thirst and quenched for every item whose row counts there, and nothing else. */
	private static void thirstWasTakenData(GameTestHelper helper) {
		JsonObject values = readJson(helper, CompatData.TWT_FILE).getAsJsonObject().getAsJsonObject("values");
		List<Item> counted = Quench.items().stream().filter(item -> Quench.of(item).orElseThrow().twtDrink()).toList();
		helper.assertValueEqual(values.keySet(), counted.stream().map(ModCompatGameTests::id).collect(Collectors.toSet()), "the items in the file");
		for (Item item : counted) {
			Quench quench = Quench.of(item).orElseThrow();
			JsonObject entry = values.getAsJsonObject(id(item));
			helper.assertValueEqual(entry.get("thirst").getAsInt(), quench.twtThirst, id(item) + " TWT thirst");
			helper.assertValueEqual(entry.get("quenched").getAsInt(), quench.twtQuenched, id(item) + " TWT quenched");
		}
		helper.assertTrue(!values.has(id(FDCompat.BEER_BATTERED_FISH.get())), "the battered fish is food, not a drink, in TWT");
		helper.succeed();
	}

	// ------------------------------------------------------------------ nutrition and conventions: our side

	/** Everything eaten or drunk has a food group, and each group's tag holds exactly its items (plus raw barley). */
	private static void nutrientTags(GameTestHelper helper) {
		for (Item item : ourItems()) {
			if (consumable(item)) {
				helper.assertTrue(!Nutrient.of(item).isEmpty(), id(item) + " has a food group (add it to Nutrient.GROUPS)");
			}
			ItemStack stack = new ItemStack(item);
			for (Nutrient nutrient : Nutrient.values()) {
				helper.assertValueEqual(stack.is(nutrient.tag), Nutrient.of(item).contains(nutrient), id(item) + " in c:nutrients/" + nutrient.name);
			}
		}
		helper.assertValueEqual(Nutrient.of(DrinkItems.AGED_RED_WINE.get()), EnumSet.of(Nutrient.FRUITS), "wine is fruit");
		helper.assertValueEqual(Nutrient.of(DrinkItems.WHEAT_BEER.get()), EnumSet.of(Nutrient.CARBS), "beer is carbs");
		helper.assertValueEqual(Nutrient.of(DrinkItems.AGED_WHITE_WINE.get()), EnumSet.of(Nutrient.FRUITS), "white wine is fruit");
		helper.assertValueEqual(Nutrient.of(DrinkItems.MEAD.get()), EnumSet.of(Nutrient.SUGARS), "mead is honey: sugars");
		helper.assertValueEqual(Nutrient.of(BaseItems.APPLE_JUICE.get()), EnumSet.of(Nutrient.FRUITS), "apple juice is fruit");
		helper.assertValueEqual(Nutrient.of(VBItems.BARLEY.get()), EnumSet.of(Nutrient.CARBS), "raw barley is carbs, for recipes");
		helper.assertValueEqual(Nutrient.of(FDCompat.WINE_BRAISED_BEEF.get()), EnumSet.of(Nutrient.PROTEINS, Nutrient.VEGETABLES), "braised beef");
		helper.assertValueEqual(Nutrient.of(FDCompat.BEER_BATTERED_FISH.get()), EnumSet.of(Nutrient.PROTEINS, Nutrient.CARBS), "battered fish");
		helper.succeed();
	}

	/** Every juice, the drinkable kettle bases. */
	private static final java.util.function.Supplier<List<Item>> JUICES = () -> List.of(BaseItems.GRAPE_JUICE.get(), BaseItems.WHITE_GRAPE_JUICE.get(),
		BaseItems.SWEET_BERRY_JUICE.get(), BaseItems.APPLE_JUICE.get());

	/** The convention tags: juices are juice drinks in bottles, our wines and beers drinks, grapes fruit, the meals food. */
	private static void conventionTags(GameTestHelper helper) {
		for (Item juice : JUICES.get()) {
			ItemStack stack = new ItemStack(juice);
			helper.assertTrue(stack.is(Conventions.DRINKS_JUICE) && stack.is(Conventions.DRINKS) && stack.is(Conventions.DRINK_CONTAINING_BOTTLE), id(juice) + " is a juice drink in a bottle");
		}
		for (DrinkItem drink : DrinkItems.allDrinks()) {
			ItemStack stack = new ItemStack(drink);
			helper.assertTrue(stack.is(Conventions.DRINKS) && !stack.is(Conventions.DRINKS_JUICE), id(drink) + " is a drink, not a juice");
		}
		for (Item notDrunk : List.of(BaseItems.BARLEY_WORT_BOTTLE.get(), BaseItems.HONEY_MUST_BOTTLE.get(), BaseItems.GRAPE_JUICE_BUCKET.get(),
				DrinkItems.MUG.get())) {
			helper.assertTrue(!new ItemStack(notDrunk).is(Conventions.DRINKS), id(notDrunk) + " is no drink");
		}
		helper.assertTrue(new ItemStack(VBItems.RED_GRAPES.get()).is(Conventions.FOODS_FRUIT), "grapes are fruit");
		helper.assertTrue(new ItemStack(VBItems.WHITE_GRAPES.get()).is(Conventions.FOODS_FRUIT), "white grapes are fruit");
		helper.assertTrue(new ItemStack(FDCompat.WINE_BRAISED_BEEF.get()).is(Conventions.FOODS_SOUP), "braised beef is a stew");
		for (Item food : List.of(VBItems.RED_GRAPES.get(), VBItems.WHITE_GRAPES.get(), FDCompat.WINE_BRAISED_BEEF.get(), FDCompat.BEER_BATTERED_FISH.get())) {
			helper.assertTrue(new ItemStack(food).is(Conventions.FOODS), id(food) + " is food");
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ AppleSkin

	/**
	 * Wheat beer's food is its Saturation buff (young 5 food and 10 saturation, aged 8 and 16), and AppleSkin is told so;
	 * every other drink is left alone, and AppleSkin keeps reading juices and meals itself.
	 */
	private static void appleSkin(GameTestHelper helper) {
		assertFood(helper, DrinkFood.of(new ItemStack(DrinkItems.WHEAT_BEER.get())).orElseThrow(), 5, 10.0F, "young wheat beer");
		assertFood(helper, DrinkFood.of(new ItemStack(DrinkItems.AGED_WHEAT_BEER.get())).orElseThrow(), 8, 16.0F, "aged wheat beer");
		for (DrinkItem drink : DrinkItems.allDrinks()) {
			helper.assertValueEqual(DrinkFood.of(new ItemStack(drink)).isPresent(), drink.buff().is(MobEffects.SATURATION), id(drink) + " feeds through a buff");
		}
		helper.assertTrue(DrinkFood.of(new ItemStack(BaseItems.GRAPE_JUICE.get())).isEmpty(), "a juice feeds by its own food");

		if (loaded(AppleSkinCompat.MODID)) {
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			for (Item beer : List.of(DrinkItems.WHEAT_BEER.get(), DrinkItems.AGED_WHEAT_BEER.get())) {
				FoodProperties expected = DrinkFood.of(new ItemStack(beer)).orElseThrow();
				FoodProperties[] shown = PROBES.get().appleSkinFoodValues(player, new ItemStack(beer));
				assertFood(helper, shown[0], expected.nutrition(), expected.saturation(), "AppleSkin's default for " + id(beer));
				assertFood(helper, shown[1], expected.nutrition(), expected.saturation(), "AppleSkin's preview for " + id(beer));
			}
			for (Item untouched : List.of(DrinkItems.RED_WINE.get(), BaseItems.GRAPE_JUICE.get(), FDCompat.WINE_BRAISED_BEEF.get())) {
				FoodProperties own = untouched.components().get(DataComponents.FOOD);
				FoodProperties[] shown = PROBES.get().appleSkinFoodValues(player, new ItemStack(untouched));
				assertFood(helper, shown[1], own.nutrition(), own.saturation(), "AppleSkin shows " + id(untouched) + "'s own food");
			}
		}
		helper.succeed();
	}

	private static void assertFood(GameTestHelper helper, FoodProperties food, int nutrition, float saturation, String what) {
		helper.assertValueEqual(food.nutrition(), nutrition, what + " nutrition");
		assertClose(helper, food.saturation(), saturation, what + " saturation");
	}

	/**
	 * The preview is honest: drinking wheat beer feeds exactly what {@link DrinkFood} says, as eating a food with those
	 * values would (saturation never above the food level), from empty and from half full.
	 */
	private static void wheatBeerFeeds(GameTestHelper helper) {
		int players = 0;
		for (Item beer : List.of(DrinkItems.WHEAT_BEER.get(), DrinkItems.AGED_WHEAT_BEER.get())) {
			FoodProperties food = DrinkFood.of(new ItemStack(beer)).orElseThrow();
			for (int start : new int[] {0, 10}) {
				TestPlayer player = player(helper, players++);
				player.getFoodData().setFoodLevel(start);
				player.getFoodData().setSaturation(0.0F);
				player.consume(new ItemStack(beer));
				for (int tick = 0; tick < 40 && player.hasEffect(MobEffects.SATURATION); tick++) {
					player.tickEffectsOnce();
				}
				int fed = Math.min(start + food.nutrition(), 20);
				helper.assertValueEqual(player.getFoodData().getFoodLevel(), fed, id(beer) + " food from " + start);
				assertClose(helper, player.getFoodData().getSaturationLevel(), Math.min(food.saturation(), fed), id(beer) + " saturation from " + start);
			}
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the real mods (compat run)

	/** Drinking with Tough As Nails: thirst and hydration rise by exactly the row's values; a meal adds no thirst. */
	private static void toughAsNailsQuenches(GameTestHelper helper) {
		if (!loaded(TOUGH_AS_NAILS)) {
			helper.succeed();
			return;
		}
		helper.assertTrue(PROBES.get().toughAsNailsThirstOn(), "Tough As Nails' thirst is on (its default)");
		int players = 0;
		for (Item item : List.of(BaseItems.GRAPE_JUICE.get(), DrinkItems.AGED_RED_WINE.get(), DrinkItems.BARLEY_ALE.get(), FDCompat.WINE_BRAISED_BEEF.get())) {
			Quench quench = Quench.of(item).orElseThrow();
			TestPlayer player = player(helper, players++);
			PROBES.get().toughAsNailsSetThirst(player, 0, 0.0F);
			player.consume(new ItemStack(item));
			float[] after = PROBES.get().toughAsNailsThirst(player);
			helper.assertValueEqual((int) after[0], quench.tanThirst, id(item) + " TAN thirst");
			// Tough As Nails: hydration += thirst * (hydration % / 100) * 2, never above the thirst level
			assertClose(helper, after[1], Math.min(quench.tanThirst * quench.tanHydration / 100.0F * 2.0F, quench.tanThirst), id(item) + " TAN hydration");
		}
		helper.succeed();
	}

	/** Homeostatic read our files: its table gives every item of ours exactly the row's water and saturation. */
	private static void homeostaticReadsOurs(GameTestHelper helper) {
		if (!loaded(HOMEOSTATIC)) {
			helper.succeed();
			return;
		}
		for (Item item : Quench.items()) {
			Quench quench = Quench.of(item).orElseThrow();
			float[] values = PROBES.get().homeostaticValues(new ItemStack(item));
			helper.assertTrue(values != null, id(item) + " is drinkable in Homeostatic");
			helper.assertValueEqual((int) values[0], quench.homeostaticAmount, id(item) + " Homeostatic amount");
			assertClose(helper, values[1], quench.homeostaticSaturation, id(item) + " Homeostatic saturation");
			assertClose(helper, values[2], 0.0F, id(item) + " Homeostatic sickness chance");
		}
		helper.succeed();
	}

	/** Thirst Was Taken 2 read our file: its API gives every drink of ours exactly the row's thirst and quenched. */
	private static void thirstWasTakenReadsOurs(GameTestHelper helper) {
		if (!loaded(THIRST_WAS_TAKEN)) {
			helper.succeed();
			return;
		}
		for (Item item : Quench.items()) {
			Quench quench = Quench.of(item).orElseThrow();
			int[] values = PROBES.get().thirstWasTakenValues(new ItemStack(item));
			if (quench.twtDrink()) {
				helper.assertTrue(values.length >= 2, id(item) + " has TWT values");
				helper.assertValueEqual(values[0], quench.twtThirst, id(item) + " TWT thirst");
				helper.assertValueEqual(values[1], quench.twtQuenched, id(item) + " TWT quenched");
			} else {
				helper.assertTrue(!PROBES.get().thirstWasTakenRestoresThirst(new ItemStack(item)), id(item) + " restores no thirst in TWT");
			}
		}
		helper.succeed();
	}

	/** Nutritional Balance read our tags: every item of ours gets exactly its groups. */
	private static void nutritionalBalanceReadsOurs(GameTestHelper helper) {
		if (!loaded(NUTRITIONAL_BALANCE)) {
			helper.succeed();
			return;
		}
		for (Item item : ourItems()) {
			Set<String> expected = Nutrient.of(item).stream().map(nutrient -> nutrient.name).collect(Collectors.toSet());
			if (!expected.isEmpty()) {
				helper.assertValueEqual(PROBES.get().nutritionalBalanceGroups(item, helper.getLevel()), expected, id(item) + " in Nutritional Balance");
			}
		}
		helper.succeed();
	}

	/**
	 * Spice of Life needs nothing from us: it scores food by its own values, so the meals count for variety and a wine
	 * (no food of its own, like Farmer's Delight's drinks) adds none.
	 */
	private static void spiceOfLifeCountsMeals(GameTestHelper helper) {
		if (!loaded(SPICE_OF_LIFE)) {
			helper.succeed();
			return;
		}
		Player player = player(helper, 0);
		for (Item meal : List.of(FDCompat.WINE_BRAISED_BEEF.get(), FDCompat.BEER_BATTERED_FISH.get())) {
			helper.assertTrue(PROBES.get().spiceOfLifeDiversity(player, new ItemStack(meal)) > 0.0, id(meal) + " counts for variety");
		}
		helper.assertValueEqual(PROBES.get().spiceOfLifeDiversity(player, new ItemStack(DrinkItems.RED_WINE.get())), 0.0, "a wine adds no variety");
		helper.succeed();
	}
}
