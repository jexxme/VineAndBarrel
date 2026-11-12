package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.platform.SlotAccess;
import jexxme.vineandbarrel.block.VBFlammability;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.AgingBarrelItem;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.barrel.BarrelContents;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.barrel.BarrelStage;
import jexxme.vineandbarrel.barrel.BarrelStatus;
import jexxme.vineandbarrel.barrel.BungState;
import jexxme.vineandbarrel.drink.Cheers;
import jexxme.vineandbarrel.drink.DrankTogether;
import jexxme.vineandbarrel.drink.DrinkItem;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.drink.DrinkPlacing;
import jexxme.vineandbarrel.drink.PlacedDrink;
import jexxme.vineandbarrel.drink.PlacedDrinksBlock;
import jexxme.vineandbarrel.drink.PlacedDrinksBlockEntity;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * M3 Aging Barrel and drinks GameTests: the fill rule, exact ferment and aging times on game time, tapping, undo,
 * buckets and bottles, item conservation through every path (hand, hoppers, dispensers, breaking, save/load), the
 * comparator, racks and stacks, placed drinks (D1), cheers (D2), Tipsy from drinking, and the FD-only recipes being
 * absent without Farmer's Delight. Run headless with {@code ./gradlew runGameTestServer}.
 */
public final class BarrelGameTests
{
	private static final Identifier SMALL = VineAndBarrel.id("gametest/empty_5x5");
	private static final Identifier LARGE = VineAndBarrel.id("gametest/empty_32x6x32");
	private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, VineAndBarrel.MODID);
	private static final List<Entry> TESTS = new ArrayList<>();

	private static final int FERMENT = AgingRecipe.DEFAULT_FERMENTING_TIME;
	private static final int AGE = AgingRecipe.DEFAULT_AGING_TIME;
	private static final BlockPos POS = new BlockPos(2, 1, 2);

	private record Entry(ResourceKey<Consumer<GameTestHelper>> function, Identifier structure, int maxTicks) {
	}

	static {
		test("barrel_fills_empty", 100, BarrelGameTests::fillsEmpty);
		test("barrel_tops_up_same_base", 100, BarrelGameTests::topsUpSameBase);
		test("barrel_refuses_other_base", 100, BarrelGameTests::refusesOtherBase);
		test("barrel_bucket_four_bottle_one", 100, BarrelGameTests::bucketFourBottleOne);
		test("barrel_undo_before_drink", 100, BarrelGameTests::undoBeforeDrink);
		test("barrel_exact_ferment_and_age_times", 100, BarrelGameTests::exactTimes);
		test("barrel_ages_on_game_time", 100, BarrelGameTests::agesOnGameTime);
		test("barrel_stages_tick_in_real_time", 300, BarrelGameTests::stagesTickInRealTime);
		test("barrel_tapping_counts_down_and_resets", 100, BarrelGameTests::tappingCountsDown);
		test("barrel_wrong_vessel_uses_nothing", 100, BarrelGameTests::wrongVesselUsesNothing);
		test("barrel_player_interactions", 100, BarrelGameTests::playerInteractions);
		test("barrel_hopper_automation", 600, BarrelGameTests::hopperAutomation);
		test("barrel_hopper_never_jams_other_base", 200, BarrelGameTests::hopperNeverJams);
		test("barrel_dispensers", 100, BarrelGameTests::dispensers);
		test("barrel_dispenser_redstone_pulse", 100, BarrelGameTests::dispenserRedstonePulse);
		test("barrel_keeps_batch_when_broken_and_pauses", 200, BarrelGameTests::keepsBatchWhenBroken);
		test("barrel_save_load_roundtrip", 100, BarrelGameTests::saveLoadRoundtrip);
		test("barrel_conservation_random_actions", 400, BarrelGameTests::conservationRandomActions);
		test("barrel_comparator_output", 100, BarrelGameTests::comparatorOutput);
		test("barrel_hopper_taps_move_the_comparator", 400, BarrelGameTests::hopperTapsMoveTheComparator);
		test("barrel_slot_change_is_saved", 100, BarrelGameTests::slotChangeIsSaved);
		test("barrel_racks_and_stacking", 100, BarrelGameTests::racksAndStacking);
		test("barrel_bung_states", 100, BarrelGameTests::bungStates);
		test("barrel_status_line", 100, BarrelGameTests::statusLine);
		test("barrel_tap_handle_turns_back", 100, BarrelGameTests::tapHandleTurnsBack);
		test("barrel_wood_properties", 100, BarrelGameTests::woodProperties);
		test("barrel_recipes_and_fd_absent", 100, BarrelGameTests::recipesAndFdAbsent);
		test("drinks_items_and_vessels", 100, BarrelGameTests::drinkItems);
		testLarge("barrel_white_wine_ages_and_taps", 100, h -> newDrinkAgesAndTaps(h, BaseItems.WHITE_GRAPE_JUICE.get(), BaseItems.WHITE_GRAPE_JUICE_BUCKET.get(),
			DrinkItems.WHITE_WINE.get(), DrinkItems.AGED_WHITE_WINE.get(), DrinkItems.WINE_GLASS.get(), DrinkItems.MUG.get()));
		testLarge("barrel_mead_ages_and_taps", 100, h -> newDrinkAgesAndTaps(h, BaseItems.HONEY_MUST_BOTTLE.get(), BaseItems.HONEY_MUST_BUCKET.get(),
			DrinkItems.MEAD.get(), DrinkItems.AGED_MEAD.get(), DrinkItems.MUG.get(), DrinkItems.WINE_GLASS.get()));
		testLarge("barrel_apple_juice_becomes_hard_cider", 100, h -> newDrinkAgesAndTaps(h, BaseItems.APPLE_JUICE.get(), BaseItems.APPLE_JUICE_BUCKET.get(),
			DrinkItems.HARD_CIDER.get(), DrinkItems.AGED_HARD_CIDER.get(), DrinkItems.MUG.get(), DrinkItems.WINE_GLASS.get()));
		test("drinks_place_up_to_four", 100, BarrelGameTests::placeUpToFour);
		loaderTest("drinks_cancelled_place_keeps_no_duplicate", 100);
		loaderTest("drinks_cheers_reaches_the_neoforge_bus", 20);
		test("drinks_break_knocks_off_one", 100, BarrelGameTests::breakKnocksOffOne);
		test("drinks_creative_break_drops_nothing", 100, BarrelGameTests::creativeBreakDropsNothing);
		test("drinks_placed_under_water_keep_it", 100, BarrelGameTests::placedUnderWater);
		test("drinks_more_under_water_keep_it", 100, BarrelGameTests::moreUnderWater);
		test("drinks_last_broken_under_water_leaves_water", 100, BarrelGameTests::lastBrokenUnderWater);
		test("drinks_unsupported_under_water_turn_to_water", 100, BarrelGameTests::unsupportedUnderWater);
		test("drinks_full_one_knocked_off_spills_and_drops", 100, BarrelGameTests::fullKnockedOff);
		test("drinks_placed_shape_per_state", 100, BarrelGameTests::placedShapes);
		test("drinks_sound_like_their_vessel", 100, BarrelGameTests::soundLikeTheirVessel);
		test("drinks_pick_up_and_drops", 100, BarrelGameTests::pickUpAndDrops);
		test("barrel_ages_with_sleep_and_time", 100, BarrelGameTests::agesWithSleepAndTime);
		test("barrel_ages_with_daylight_cycle_off", 100, BarrelGameTests::agesWithDaylightCycleOff);
		test("barrel_creative_break_drops_no_barrel", 100, BarrelGameTests::creativeBreak);
		test("barrel_explosion_makes_up_nothing", 100, BarrelGameTests::explosion);
		test("barrel_fire_never_burns_it", 300, BarrelGameTests::fireNeverBurns);
		test("barrel_full_inventory_drops_the_drink", 100, BarrelGameTests::fullInventory);
		test("barrel_fill_gauge_follows_servings", 100, BarrelGameTests::fillGauge);
		test("barrel_client_and_server_agree_on_clicks", 100, BarrelGameTests::clickRules);
		test("barrel_recipe_validation_and_duplicates", 100, BarrelGameTests::recipeValidation);
		test("drinks_piston_pops_placed_drinks", 100, BarrelGameTests::pistonPopsDrinks);
		test("drinks_placing_respects_entities_and_passes", 100, BarrelGameTests::placingRespectsEntities);
		test("barrel_batch_survives_missing_base", 100, BarrelGameTests::survivesMissingBase);
		testLarge("drinks_tipsy_from_drinking", 100, BarrelGameTests::tipsyFromDrinking);
		testLarge("drinks_cheers_window", 300, BarrelGameTests::cheersWindow);
		testLarge("drinks_cheers_too_drunk_no_buff", 100, BarrelGameTests::cheersTooDrunk);
		testLarge("drinks_cheers_extends_aged_level_two", 100, BarrelGameTests::cheersAgedLevelTwo);
		test("drinks_placed_aged_glows", 100, BarrelGameTests::placedAgedGlows);
	}

	private BarrelGameTests() {
	}

	/** A test the loader writes with its own API ({@code Platform#loaderGameTest}); left out on a loader that has none. */
	private static void loaderTest(String name, int timeout) {
		Consumer<GameTestHelper> function = jexxme.vineandbarrel.platform.Services.PLATFORM.loaderGameTest(name);
		if (function != null) {
			test(name, timeout, function);
		}
	}

	private static void test(String name, int timeout, Consumer<GameTestHelper> function) {
		register(name, SMALL, timeout, function);
	}

	/** In the 32x32 structure, so players drinking here are far from every other test (cheers reach 5 blocks). */
	private static void testLarge(String name, int timeout, Consumer<GameTestHelper> function) {
		register(name, LARGE, timeout, function);
	}

	private static void register(String name, Identifier structure, int timeout, Consumer<GameTestHelper> function) {
		FUNCTIONS.register(name, () -> function);
		TESTS.add(new Entry(ResourceKey.create(Registries.TEST_FUNCTION, VineAndBarrel.id(name)), structure, timeout));
	}

	public static void register() {
		FUNCTIONS.register();
		jexxme.vineandbarrel.platform.Services.PLATFORM.addGameTests(BarrelGameTests::registerTests);
	}

	private static void registerTests(GameTestRegistrar event) {
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(VineAndBarrel.id("barrel"));
		for (Entry test : TESTS) {
			event.registerTest(test.function().identifier(), new FunctionGameTestInstance(test.function(), new TestData<>(environment,
				test.structure(), test.maxTicks(), 0, true, Rotation.NONE, false, 1, 1, false, 0)));
		}
	}

	// ------------------------------------------------------------------ helpers

	private static AgingBarrelBlockEntity placeBarrel(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, BarrelRegistry.AGING_BARREL.get().defaultBlockState().setValue(AgingBarrelBlock.FACING, facing));
		return helper.getBlockEntity(pos, AgingBarrelBlockEntity.class);
	}

	private static AgingBarrelBlockEntity placeBarrel(GameTestHelper helper, BlockPos pos) {
		return placeBarrel(helper, pos, Direction.NORTH);
	}

	/** Pours {@code times} items of {@code base} in, asserting each is taken. */
	private static void pour(GameTestHelper helper, AgingBarrelBlockEntity barrel, Item base, int times) {
		for (int i = 0; i < times; i++) {
			ItemStack stack = new ItemStack(base);
			helper.assertTrue(barrel.pour(stack, null) != null, "pour " + base + " #" + i);
			helper.assertTrue(stack.isEmpty(), "the poured item is used up");
		}
	}

	/** A barrel of {@code servings} red wine that is {@code age} ticks old. */
	private static AgingBarrelBlockEntity wineBarrel(GameTestHelper helper, BlockPos pos, int servings, long age) {
		AgingBarrelBlockEntity barrel = placeBarrel(helper, pos);
		pour(helper, barrel, BaseItems.GRAPE_JUICE.get(), servings);
		barrel.setAge(age);
		return barrel;
	}

	private static void tickBarrel(GameTestHelper helper, BlockPos pos) {
		AgingBarrelBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), helper.getBlockEntity(pos, AgingBarrelBlockEntity.class));
	}

	/** Places a barrel item the way a player does (on top of {@code below}), so placement rules (B1-B3) apply. */
	private static void placeBarrelItem(GameTestHelper helper, Player player, BlockPos below, Direction face) {
		ItemStack stack = new ItemStack(BarrelRegistry.AGING_BARREL_ITEM.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		helper.placeAt(player, stack, below, face);
	}

	private static List<ItemEntity> dropsAround(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(2.0D));
	}

	private static Player playerHolding(GameTestHelper helper, ItemStack stack, GameType type) {
		Player player = helper.makeMockPlayer(type);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		return player;
	}

	private static int count(Player player, Item item) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) n += stack.getCount();
		}
		return n;
	}

	private static String key(Component component) {
		return component.getContents() instanceof TranslatableContents t ? t.getKey() : component.getString();
	}

	private static Object[] args(Component component) {
		return component.getContents() instanceof TranslatableContents t ? t.getArgs() : new Object[0];
	}

	/** A real server player (vanilla's own drinking code runs on it), kept out of the player list. */
	private static final class TestPlayer extends ServerPlayer
	{
		TestPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
			super(server, level, profile, ClientInformation.createDefault());
		}

		@Override
		public GameType gameMode() {
			return GameType.SURVIVAL;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}

		void drink(ItemStack stack) {
			this.setItemInHand(InteractionHand.MAIN_HAND, stack);
			this.startUsingItem(InteractionHand.MAIN_HAND);
			this.completeUsingItem();
		}
	}

	private static TestPlayer drinker(GameTestHelper helper, Vec3 relative) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "barrel-test-player"), false);
		TestPlayer player = new TestPlayer(level.getServer(), level, cookie.gameProfile());
		GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie);
		Vec3 pos = helper.absoluteVec(relative);
		player.setPos(pos.x, pos.y, pos.z);
		return player;
	}

	/** Cheers composes with the playtest-1 buffs: two aged drinks together give Haste II for 90 s plus the bonus. */
	private static void cheersAgedLevelTwo(GameTestHelper helper) {
		TestPlayer a = drinker(helper, new Vec3(8.5, 1.0, 8.5));
		TestPlayer b = drinker(helper, new Vec3(11.5, 1.0, 8.5));
		int base = DrinkItems.AGED_BARLEY_ALE.get().buff().getDuration();
		int bonus = base * Cheers.BONUS_PERCENT / 100;
		a.drink(new ItemStack(DrinkItems.AGED_BARLEY_ALE.get()));
		b.drink(new ItemStack(DrinkItems.AGED_BARLEY_ALE.get()));
		for (TestPlayer player : List.of(a, b)) {
			MobEffectInstance haste = player.getEffect(MobEffects.HASTE);
			helper.assertTrue(haste != null && haste.getAmplifier() == 1 && haste.getDuration() >= base + bonus - 1,
				"Haste II for 90 s plus " + Cheers.BONUS_PERCENT + "%: " + haste);
		}
		helper.succeed();
	}

	/**
	 * Playtest 1: an aged drink on a table has its own state, whose model renders the liquid surface fullbright
	 * (own block models; the enchantment shimmer comes from PlacedDrinksRenderer, never from light_emission). The item keeps its own model.
	 */
	private static void placedAgedGlows(GameTestHelper helper) {
		helper.assertValueEqual(PlacedDrink.of(new ItemStack(DrinkItems.RED_WINE.get())), PlacedDrink.GLASS_FULL, "young wine");
		helper.assertValueEqual(PlacedDrink.of(new ItemStack(DrinkItems.AGED_RED_WINE.get())), PlacedDrink.GLASS_AGED, "aged wine");
		helper.assertValueEqual(PlacedDrink.of(new ItemStack(DrinkItems.BARLEY_ALE.get())), PlacedDrink.MUG_FULL, "young ale");
		helper.assertValueEqual(PlacedDrink.of(new ItemStack(DrinkItems.AGED_BARLEY_ALE.get())), PlacedDrink.MUG_AGED, "aged ale");
		helper.assertTrue(PlacedDrink.MUG_AGED.isFull() && PlacedDrink.MUG_AGED.isMug() && !PlacedDrink.GLASS_AGED.isMug(), "aged kinds are full");

		// Put down: the state shows the aged kind, and the shape is the vessel's
		BlockPos table = new BlockPos(2, 1, 2);
		BlockPos spot = table.above();
		helper.setBlock(table, Blocks.OAK_PLANKS);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.AGED_BARLEY_ALE.get()));
		helper.assertTrue(DrinkPlacing.place(sneakUse(helper, player, table, new Vec3(0.25, 1.0, 0.25))).consumesAction(), "placed");
		helper.assertValueEqual(helper.getBlockState(spot).getValue(PlacedDrinksBlock.SPOT.get(0)), PlacedDrink.MUG_AGED, "the aged mug's state");
		helper.assertTrue(helper.getBlockState(spot).getShape(helper.getLevel(), helper.absolutePos(spot)).max(Direction.Axis.Y) > 0.0, "it has its mug shape");

		// The models: every aged one glows on its liquid surface, no young one does
		for (int i = 0; i < PlacedDrinksBlock.SPOTS; i++) {
			for (String vessel : List.of("mug", "glass")) {
				helper.assertValueEqual(emissiveElements(helper, "placed_" + vessel + "_aged_" + i), 0, vessel + " aged model " + i + " is lit normally (the shimmer is PlacedDrinksRenderer's)");
				helper.assertValueEqual(emissiveElements(helper, "placed_" + vessel + "_full_" + i), 0, vessel + " young model " + i + " doesn't glow");
			}
		}
		String blockstate = resource(helper, "assets/vineandbarrel/blockstates/placed_drinks.json");
		for (PlacedDrink kind : PlacedDrink.values()) {
			if (kind != PlacedDrink.NONE) {
				helper.assertTrue(blockstate.contains("\"" + kind.getSerializedName() + "\""), "blockstate covers " + kind);
			}
		}
		helper.succeed();
	}

	private static int emissiveElements(GameTestHelper helper, String model) {
		com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(resource(helper, "assets/vineandbarrel/models/block/" + model + ".json")).getAsJsonObject();
		int glowing = 0;
		for (com.google.gson.JsonElement element : json.getAsJsonArray("elements")) {
			com.google.gson.JsonObject object = element.getAsJsonObject();
			if (object.has("light_emission") && object.get("light_emission").getAsInt() == 15) {
				helper.assertTrue(object.get("name").getAsString().endsWith("_liquid") || object.get("name").getAsString().endsWith("_foam"),
					model + ": only the drink glows, not " + object.get("name"));
				glowing++;
			}
		}
		return glowing;
	}

	/** Client assets aren't loaded on a server, but they are on the classpath. */
	private static String resource(GameTestHelper helper, String path) {
		try (java.io.InputStream in = BarrelGameTests.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw helper.assertionException("missing resource " + path);
			}
			return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			throw helper.assertionException("can't read " + path + ": " + e);
		}
	}

	// ------------------------------------------------------------------ fill rule (DESIGN §4.2)

	/** An empty barrel takes any base; the first fill starts the timer and the batch is the recipe's. */
	private static void fillsEmpty(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = placeBarrel(helper, POS);
		helper.assertValueEqual(barrel.stage(), BarrelStage.EMPTY, "new barrel");
		ItemStack juice = new ItemStack(BaseItems.GRAPE_JUICE.get());
		ItemStack left = barrel.pour(juice, null);
		helper.assertTrue(left != null && left.is(Items.GLASS_BOTTLE), "a bottle of juice leaves its glass bottle: " + left);
		helper.assertValueEqual(barrel.servings(), 1, "servings");
		helper.assertValueEqual(barrel.stage(), BarrelStage.FERMENTING, "stage");
		helper.assertValueEqual(barrel.age(), 0L, "the timer starts at the first fill");
		helper.assertTrue(barrel.drink().is(DrinkItems.RED_WINE.get()), "grape juice becomes red wine");
		helper.assertTrue(barrel.content().is(BaseItems.GRAPE_JUICE.get()), "it is still juice while fermenting");
		helper.succeed();
	}

	/**
	 * Top-ups with the same base add servings and leave the timer alone, whatever the first bottle was called (a renamed
	 * one fills like a plain one, and plain ones top it up, as the recipe matches by item).
	 */
	private static void topsUpSameBase(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = placeBarrel(helper, POS);
		ItemStack renamed = new ItemStack(BaseItems.BARLEY_WORT_BOTTLE.get());
		renamed.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Grandpa's wort"));
		helper.assertTrue(barrel.pour(renamed, null) != null, "a renamed bottle of wort fills the barrel");
		barrel.setAge(5000);
		pour(helper, barrel, BaseItems.BARLEY_WORT_BUCKET.get(), 1);
		pour(helper, barrel, BaseItems.BARLEY_WORT_BOTTLE.get(), 2);
		helper.assertValueEqual(barrel.servings(), 7, "1 + 4 + 2 servings");
		helper.assertValueEqual(barrel.age(), 5000L, "a top-up doesn't restart the timer");
		helper.assertTrue(barrel.drink().is(DrinkItems.BARLEY_ALE.get()), "barley wort becomes pale ale");
		helper.succeed();
	}

	/** Another base, a non-base, a full barrel and a barrel that has turned into a drink all refuse, and nothing is used up. */
	private static void refusesOtherBase(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 5, 100);
		for (Item other : List.of(BaseItems.BARLEY_WORT_BOTTLE.get(), BaseItems.WHEAT_WORT_BUCKET.get(), BaseItems.SWEET_BERRY_JUICE.get())) {
			ItemStack stack = new ItemStack(other);
			helper.assertValueEqual(barrel.pourRefusal(stack), AgingBarrelBlockEntity.Refusal.OTHER_BASE, "refusal for " + other);
			helper.assertTrue(barrel.pour(stack, null) == null && stack.getCount() == 1, other + " is refused and kept");
		}
		ItemStack stone = new ItemStack(Items.STONE);
		helper.assertValueEqual(barrel.pourRefusal(stone), AgingBarrelBlockEntity.Refusal.NOT_A_BASE, "stone");
		helper.assertTrue(barrel.pour(stone, null) == null, "stone is not poured");
		// 5 + 4 > 8: the bucket doesn't fit, three more bottles do, then it is full
		ItemStack bucket = new ItemStack(BaseItems.GRAPE_JUICE_BUCKET.get());
		helper.assertValueEqual(barrel.pourRefusal(bucket), AgingBarrelBlockEntity.Refusal.FULL, "a bucket into 5 servings");
		pour(helper, barrel, BaseItems.GRAPE_JUICE.get(), 3);
		helper.assertValueEqual(barrel.pourRefusal(new ItemStack(BaseItems.GRAPE_JUICE.get())), AgingBarrelBlockEntity.Refusal.FULL, "8 servings");
		// once it is wine, only an empty barrel takes a new batch
		barrel.setAge(FERMENT);
		helper.assertValueEqual(barrel.pourRefusal(new ItemStack(BaseItems.GRAPE_JUICE.get())), AgingBarrelBlockEntity.Refusal.ALREADY_A_DRINK, "full of wine");
		barrel.tap(new ItemStack(DrinkItems.WINE_GLASS.get()), null);
		ItemStack juice = new ItemStack(BaseItems.GRAPE_JUICE.get());
		helper.assertValueEqual(barrel.pourRefusal(juice), AgingBarrelBlockEntity.Refusal.ALREADY_A_DRINK, "juice into wine");
		helper.assertTrue(barrel.pour(juice, null) == null && juice.getCount() == 1, "no juice into wine");
		helper.assertValueEqual(barrel.servings(), 7, "servings unchanged by refusals");
		helper.succeed();
	}

	/** A bucket is 4 servings and leaves an empty bucket, a bottle 1 and leaves a glass bottle; 2 buckets fill a barrel. */
	private static void bucketFourBottleOne(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = placeBarrel(helper, POS);
		ItemStack bucket = new ItemStack(BaseItems.SWEET_BERRY_JUICE_BUCKET.get());
		ItemStack left = barrel.pour(bucket, null);
		helper.assertTrue(left != null && left.is(Items.BUCKET), "an empty bucket comes back: " + left);
		helper.assertValueEqual(barrel.servings(), 4, "one bucket");
		pour(helper, barrel, BaseItems.SWEET_BERRY_JUICE_BUCKET.get(), 1);
		helper.assertValueEqual(barrel.servings(), AgingBarrelBlockEntity.CAPACITY, "two buckets fill the barrel");
		AgingBarrelBlockEntity bottles = placeBarrel(helper, POS.east());
		for (int i = 1; i <= 8; i++) {
			ItemStack bottle = new ItemStack(BaseItems.WHEAT_WORT_BOTTLE.get());
			ItemStack glass = bottles.pour(bottle, null);
			helper.assertTrue(glass != null && glass.is(Items.GLASS_BOTTLE), "glass bottle back");
			helper.assertValueEqual(bottles.servings(), i, "bottles poured");
		}
		helper.assertTrue(bottles.pour(new ItemStack(BaseItems.WHEAT_WORT_BOTTLE.get()), null) == null, "the 9th bottle doesn't fit");
		helper.succeed();
	}

	/** B8: until it is a drink, a bottle takes 1 serving back, a bucket 4 (only with 4 left); then the barrel is empty again. */
	private static void undoBeforeDrink(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 6, 0);
		ItemStack buckets = new ItemStack(Items.BUCKET, 2);
		ItemStack first = barrel.takeBack(buckets, null);
		helper.assertTrue(first.is(BaseItems.GRAPE_JUICE_BUCKET.get()), "a bucket of juice back: " + first);
		helper.assertValueEqual(barrel.servings(), 2, "6 - 4");
		helper.assertValueEqual(buckets.getCount(), 1, "one bucket used");
		helper.assertTrue(barrel.takeBack(buckets, null).isEmpty(), "a bucket needs 4 servings");
		helper.assertValueEqual(buckets.getCount(), 1, "the refused bucket is kept");
		ItemStack glass = new ItemStack(Items.GLASS_BOTTLE, 5);
		helper.assertTrue(barrel.takeBack(glass, null).is(BaseItems.GRAPE_JUICE.get()), "a bottle of juice back");
		helper.assertTrue(barrel.takeBack(glass, null).is(BaseItems.GRAPE_JUICE.get()), "and another");
		helper.assertValueEqual(glass.getCount(), 3, "two bottles used");
		helper.assertValueEqual(barrel.stage(), BarrelStage.EMPTY, "emptied by undo");
		helper.assertTrue(barrel.contents().isEmpty(), "the batch is forgotten");
		// a fresh batch of something else is fine now
		pour(helper, barrel, BaseItems.WHEAT_WORT_BOTTLE.get(), 1);
		helper.assertTrue(barrel.drink().is(DrinkItems.WHEAT_BEER.get()), "new batch");
		// once it is a drink, undo is over
		AgingBarrelBlockEntity wine = wineBarrel(helper, POS.east(), 4, FERMENT);
		ItemStack bottles = new ItemStack(Items.GLASS_BOTTLE);
		helper.assertTrue(wine.takeBack(bottles, null).isEmpty() && bottles.getCount() == 1, "wine can't be taken back as juice");
		helper.assertValueEqual(wine.servings(), 4, "wine untouched");
		helper.succeed();
	}

	// ------------------------------------------------------------------ time (1 day ferment, +3 days aging, game time)

	/** Exactly 24000 ticks to the young drink and 72000 more to the aged one, to the tick. */
	private static void exactTimes(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 2, 0);
		barrel.setAge(FERMENT - 1);
		helper.assertValueEqual(barrel.stage(), BarrelStage.FERMENTING, "tick 23999");
		helper.assertValueEqual(barrel.ticksToNextStage(), 1L, "one tick to go");
		barrel.setAge(FERMENT);
		helper.assertValueEqual(barrel.stage(), BarrelStage.YOUNG, "tick 24000");
		helper.assertTrue(barrel.content().is(DrinkItems.RED_WINE.get()), "red wine");
		helper.assertValueEqual(barrel.ticksToNextStage(), (long) AGE, "3 days to aged");
		barrel.setAge(FERMENT + AGE - 1);
		helper.assertValueEqual(barrel.stage(), BarrelStage.YOUNG, "tick 95999");
		barrel.setAge(FERMENT + AGE);
		helper.assertValueEqual(barrel.stage(), BarrelStage.AGED, "tick 96000");
		helper.assertTrue(barrel.content().is(DrinkItems.AGED_RED_WINE.get()), "aged red wine");
		barrel.setAge(10L * FERMENT + AGE);
		helper.assertValueEqual(barrel.stage(), BarrelStage.AGED, "aged stays aged");
		helper.succeed();
	}

	/**
	 * The age is game time since the first fill, so it runs on its own (no ticking needed) and keeps running while a
	 * chunk is unloaded: a saved barrel loaded later is as old as the time that passed.
	 */
	private static void agesOnGameTime(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 3, 100);
		long start = helper.getLevel().getGameTime();
		var saved = barrel.saveWithFullMetadata(helper.getLevel().registryAccess());
		helper.runAfterDelay(20, () -> {
			long passed = helper.getLevel().getGameTime() - start;
			helper.assertValueEqual(barrel.age(), 100L + passed, "age follows game time");
			// "unloaded" for those ticks: load what was saved before them
			AgingBarrelBlockEntity reloaded = barrel;
			reloaded.loadWithComponents(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
			helper.assertValueEqual(reloaded.age(), 100L + passed, "the time while unloaded counts");
			helper.assertValueEqual(reloaded.servings(), 3, "servings after loading");
			helper.succeed();
		});
	}

	/** The test-only quick recipe (40 + 60 ticks, src/gametest) turns young and aged while the barrel just ticks. */
	private static void stagesTickInRealTime(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = placeBarrel(helper, POS);
		pour(helper, barrel, Items.HONEY_BOTTLE, 2);
		helper.startSequence()
			.thenExecuteAfter(2, () -> helper.assertValueEqual(helper.getBlockState(POS).getValue(AgingBarrelBlock.BUNG), BungState.AIRLOCK, "airlock while fermenting"))
			.thenWaitUntil(() -> helper.assertValueEqual(barrel.stage(), BarrelStage.YOUNG, "young after 40 ticks"))
			.thenExecute(() -> {
				helper.assertTrue(barrel.age() >= 40 && barrel.age() < 45, "turned young on time: " + barrel.age());
				helper.assertTrue(barrel.content().is(DrinkItems.BARLEY_ALE.get()), "young drink");
			})
			.thenWaitUntil(() -> helper.assertValueEqual(helper.getBlockState(POS).getValue(AgingBarrelBlock.BUNG), BungState.CORK, "cork when young"))
			.thenWaitUntil(() -> helper.assertValueEqual(barrel.stage(), BarrelStage.AGED, "aged after 100 ticks"))
			.thenExecute(() -> helper.assertTrue(barrel.age() >= 100 && barrel.age() < 105, "turned aged on time: " + barrel.age()))
			.thenWaitUntil(() -> helper.assertValueEqual(helper.getBlockState(POS).getValue(AgingBarrelBlock.BUNG), BungState.WAX, "wax seal when aged"))
			.thenSucceed();
	}

	// ------------------------------------------------------------------ tapping (B9)

	/** Each tap takes one vessel and one serving and gives one drink of the current stage; an empty barrel resets. */
	private static void tappingCountsDown(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 3, FERMENT + AGE);
		ItemStack glasses = new ItemStack(DrinkItems.WINE_GLASS.get(), 5);
		for (int left = 2; left >= 0; left--) {
			ItemStack drink = barrel.tap(glasses, null);
			helper.assertTrue(drink.is(DrinkItems.AGED_RED_WINE.get()) && drink.getCount() == 1, "one aged red wine: " + drink);
			helper.assertValueEqual(barrel.servings(), left, "servings left");
		}
		helper.assertValueEqual(glasses.getCount(), 2, "one glass per serving");
		helper.assertTrue(barrel.tap(glasses, null).isEmpty(), "nothing left to tap");
		helper.assertValueEqual(glasses.getCount(), 2, "no glass used on an empty barrel");
		helper.assertValueEqual(barrel.stage(), BarrelStage.EMPTY, "empty barrel resets");
		helper.assertValueEqual(barrel.age(), 0L, "the timer resets");
		tickBarrel(helper, POS);
		helper.assertValueEqual(helper.getBlockState(POS).getValue(AgingBarrelBlock.BUNG), BungState.CORK, "cork again");
		pour(helper, barrel, BaseItems.BARLEY_WORT_BUCKET.get(), 1);
		helper.assertValueEqual(barrel.age(), 0L, "a new batch starts its own timer");
		// a young beer goes into mugs
		barrel.setAge(FERMENT);
		ItemStack mugs = new ItemStack(DrinkItems.MUG.get(), 4);
		helper.assertTrue(barrel.tap(mugs, null).is(DrinkItems.BARLEY_ALE.get()), "young pale ale in a mug");
		helper.succeed();
	}

	/** The wrong vessel, or tapping while it still ferments, uses nothing up. */
	private static void wrongVesselUsesNothing(GameTestHelper helper) {
		AgingBarrelBlockEntity wine = wineBarrel(helper, POS, 4, FERMENT);
		ItemStack mugs = new ItemStack(DrinkItems.MUG.get(), 3);
		helper.assertTrue(wine.tap(mugs, null).isEmpty(), "wine isn't served in a mug");
		helper.assertTrue(wine.vesselNeeded() == DrinkItems.WINE_GLASS.get(), "wine needs a wine glass");
		helper.assertTrue(wine.tap(new ItemStack(Items.GLASS_BOTTLE), null).isEmpty(), "nor in a glass bottle");
		helper.assertValueEqual(mugs.getCount(), 3, "mugs kept");
		helper.assertValueEqual(wine.servings(), 4, "servings kept");
		// by hand: the mug stays in the hand
		Player player = playerHolding(helper, new ItemStack(DrinkItems.MUG.get(), 2), GameType.SURVIVAL);
		helper.useBlock(POS, player);
		helper.assertTrue(player.getMainHandItem().is(DrinkItems.MUG.get()) && player.getMainHandItem().getCount() == 2, "the hint uses nothing up");
		helper.assertValueEqual(count(player, DrinkItems.RED_WINE.get()), 0, "no wine in a mug");
		AgingBarrelBlockEntity fermenting = wineBarrel(helper, POS.east(), 4, 10);
		ItemStack glasses = new ItemStack(DrinkItems.WINE_GLASS.get(), 2);
		helper.assertTrue(fermenting.tap(glasses, null).isEmpty(), "still fermenting");
		helper.assertValueEqual(glasses.getCount(), 2, "glasses kept while fermenting");
		helper.succeed();
	}

	/** By hand, exactly like the cauldron and FD's feasts: the hand ends up holding what came out; creative uses nothing. */
	private static void playerInteractions(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = placeBarrel(helper, POS);
		Player player = playerHolding(helper, new ItemStack(BaseItems.GRAPE_JUICE_BUCKET.get()), GameType.SURVIVAL);
		helper.useBlock(POS, player);
		helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "the hand holds the empty bucket");
		helper.assertValueEqual(barrel.servings(), 4, "a bucket by hand");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BaseItems.GRAPE_JUICE.get(), 3));
		helper.useBlock(POS, player);
		helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "one bottle poured");
		helper.assertValueEqual(count(player, Items.GLASS_BOTTLE), 1, "its glass bottle in the inventory");
		helper.assertValueEqual(barrel.servings(), 5, "a bottle by hand");
		// undo by hand
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
		helper.useBlock(POS, player);
		helper.assertTrue(player.getMainHandItem().is(BaseItems.GRAPE_JUICE.get()), "undo fills the bottle in the hand");
		helper.assertValueEqual(barrel.servings(), 4, "after undo");
		// empty hand: the status line, nothing changes
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.useBlock(POS, player);
		helper.assertValueEqual(barrel.servings(), 4, "status changes nothing");
		// tap by hand
		barrel.setAge(FERMENT);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.WINE_GLASS.get(), 2));
		helper.useBlock(POS, player);
		helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "one glass used");
		helper.assertValueEqual(count(player, DrinkItems.RED_WINE.get()), 1, "a red wine in the inventory");
		helper.assertValueEqual(barrel.servings(), 3, "after tapping by hand");
		// an unrelated item is not taken by the barrel (it may be placed against it instead)
		ItemStack stone = new ItemStack(Items.STONE);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(POS)), Direction.UP, helper.absolutePos(POS), false);
		InteractionResult result = helper.getBlockState(POS).useItemOn(stone, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(result == InteractionResult.PASS, "stone passes: " + result);
		// creative: nothing is used up
		Player creative = playerHolding(helper, new ItemStack(DrinkItems.WINE_GLASS.get()), GameType.CREATIVE);
		GameType.CREATIVE.updatePlayerAbilities(creative.getAbilities());
		helper.useBlock(POS, creative);
		helper.assertTrue(creative.getMainHandItem().is(DrinkItems.WINE_GLASS.get()), "creative keeps the glass");
		helper.assertValueEqual(barrel.servings(), 2, "a serving still goes");
		helper.succeed();
	}

	// ------------------------------------------------------------------ automation (B13, B14)

	/**
	 * A hopper above pours in two buckets; the empty buckets come out below. A hopper at the side feeds glasses, which
	 * come out below as wine. Every item is accounted for.
	 */
	private static void hopperAutomation(GameTestHelper helper) {
		BlockPos barrelPos = new BlockPos(2, 2, 2);
		BlockPos below = barrelPos.below();
		BlockPos above = barrelPos.above();
		BlockPos side = barrelPos.east();
		helper.setBlock(below, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
		AgingBarrelBlockEntity barrel = placeBarrel(helper, barrelPos);
		helper.setBlock(above, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		HopperBlockEntity top = helper.getBlockEntity(above, HopperBlockEntity.class);
		top.setItem(0, new ItemStack(BaseItems.GRAPE_JUICE_BUCKET.get()));
		top.setItem(1, new ItemStack(BaseItems.GRAPE_JUICE_BUCKET.get()));
		top.setItem(2, new ItemStack(BaseItems.GRAPE_JUICE.get()));
		HopperBlockEntity bottom = helper.getBlockEntity(below, HopperBlockEntity.class);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(barrel.servings(), 8, "two buckets poured in by the hopper"))
			.thenWaitUntil(() -> helper.assertValueEqual(countIn(bottom, Items.BUCKET), 2, "empty buckets pulled out below"))
			.thenExecute(() -> {
				helper.assertValueEqual(countIn(top, BaseItems.GRAPE_JUICE.get()), 1, "the 9th serving waits in the hopper");
				barrel.setAge(FERMENT);
				helper.setBlock(side, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
				helper.getBlockEntity(side, HopperBlockEntity.class).setItem(0, new ItemStack(DrinkItems.WINE_GLASS.get(), 3));
			})
			.thenWaitUntil(() -> helper.assertValueEqual(countIn(bottom, DrinkItems.RED_WINE.get()), 3, "three wines tapped into the hopper below"))
			.thenExecute(() -> {
				helper.assertValueEqual(barrel.servings(), 5, "servings after three");
				helper.assertValueEqual(countIn(helper.getBlockEntity(side, HopperBlockEntity.class), DrinkItems.WINE_GLASS.get()), 0, "glasses used");
				for (int slot = 0; slot < AgingBarrelBlockEntity.INVENTORY_SIZE; slot++) {
					if (slot != AgingBarrelBlockEntity.INPUT_SLOT) {
						helper.assertTrue(barrel.stackIn(slot).isEmpty(), "slot " + slot + " empty: " + barrel.stackIn(slot));
					}
				}
			})
			.thenSucceed();
	}

	private static int countIn(HopperBlockEntity hopper, Item item) {
		int n = 0;
		for (int i = 0; i < hopper.getContainerSize(); i++) {
			if (hopper.getItem(i).is(item)) n += hopper.getItem(i).getCount();
		}
		return n;
	}

	/** A hopper can't push another base (or junk) into a barrel, so it never jams; the sides take only vessels. */
	private static void hopperNeverJams(GameTestHelper helper) {
		BlockPos barrelPos = new BlockPos(2, 1, 2);
		AgingBarrelBlockEntity barrel = wineBarrel(helper, barrelPos, 2, 0);
		helper.setBlock(barrelPos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		HopperBlockEntity top = helper.getBlockEntity(barrelPos.above(), HopperBlockEntity.class);
		top.setItem(0, new ItemStack(BaseItems.BARLEY_WORT_BOTTLE.get(), 4));
		top.setItem(1, new ItemStack(Items.STONE, 4));
		SlotAccess sideHandler = Services.PLATFORM.itemAccess(helper.getLevel(), helper.absolutePos(barrelPos), Direction.EAST);
		helper.assertTrue(sideHandler != null, "side handler");
		try (SlotAccess.Tx tx = Services.PLATFORM.openTransaction()) {
			int inserted = 0;
			for (int i = 0; i < sideHandler.size(); i++) {
				inserted += sideHandler.insert(i, SlotAccess.of(BaseItems.GRAPE_JUICE.get()), 1, tx);
			}
			helper.assertValueEqual(inserted, 0, "bases don't go in from the side");
		}
		helper.runAfterDelay(60, () -> {
			helper.assertValueEqual(countIn(top, BaseItems.BARLEY_WORT_BOTTLE.get()), 4, "other base stays in the hopper");
			helper.assertValueEqual(countIn(top, Items.STONE), 4, "stone stays in the hopper");
			helper.assertValueEqual(barrel.servings(), 2, "barrel unchanged");
			helper.assertTrue(barrel.stackIn(AgingBarrelBlockEntity.INPUT_SLOT).isEmpty(), "input slot never jammed");
			helper.succeed();
		});
	}

	/** B13: a dispenser pours bases in and taps with empty vessels; the wrong vessel stays; elsewhere it dispenses as before. */
	private static void dispensers(GameTestHelper helper) {
		BlockPos dispenserPos = new BlockPos(1, 1, 2);
		BlockPos barrelPos = dispenserPos.east();
		helper.setBlock(dispenserPos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
		AgingBarrelBlockEntity barrel = placeBarrel(helper, barrelPos);
		DispenserBlockEntity dispenser = helper.getBlockEntity(dispenserPos, DispenserBlockEntity.class);
		BlockSource source = new BlockSource(helper.getLevel(), helper.absolutePos(dispenserPos), helper.getBlockState(dispenserPos), dispenser);

		ItemStack bucket = new ItemStack(BaseItems.GRAPE_JUICE_BUCKET.get());
		ItemStack afterBucket = DispenserBlock.DISPENSER_REGISTRY.get(bucket.getItem()).dispense(source, bucket);
		helper.assertTrue(afterBucket.is(Items.BUCKET), "the dispenser keeps the empty bucket: " + afterBucket);
		ItemStack bottles = new ItemStack(BaseItems.GRAPE_JUICE.get(), 3);
		ItemStack afterBottle = DispenserBlock.DISPENSER_REGISTRY.get(bottles.getItem()).dispense(source, bottles);
		helper.assertValueEqual(afterBottle.getCount(), 2, "one bottle poured");
		helper.assertValueEqual(barrel.servings(), 5, "4 + 1 by dispenser");
		int glassInDispenser = 0;
		for (int i = 0; i < dispenser.getContainerSize(); i++) {
			if (dispenser.getItem(i).is(Items.GLASS_BOTTLE)) glassInDispenser += dispenser.getItem(i).getCount();
		}
		helper.assertValueEqual(glassInDispenser, 1, "the glass bottle went into the dispenser");

		barrel.setAge(FERMENT);
		ItemStack mugs = new ItemStack(DrinkItems.MUG.get(), 2);
		ItemStack afterMug = DispenserBlock.DISPENSER_REGISTRY.get(mugs.getItem()).dispense(source, mugs);
		helper.assertValueEqual(afterMug.getCount(), 2, "wrong vessel: nothing used");
		ItemStack glass = new ItemStack(DrinkItems.WINE_GLASS.get());
		ItemStack afterGlass = DispenserBlock.DISPENSER_REGISTRY.get(glass.getItem()).dispense(source, glass);
		helper.assertTrue(afterGlass.is(DrinkItems.RED_WINE.get()), "the last glass comes back as wine: " + afterGlass);
		helper.assertValueEqual(barrel.servings(), 4, "one serving tapped");

		// not facing a barrel: a mug is just dropped, like any item
		BlockPos lone = new BlockPos(1, 1, 0);
		helper.setBlock(lone, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.UP));
		DispenserBlockEntity loneDispenser = helper.getBlockEntity(lone, DispenserBlockEntity.class);
		BlockSource loneSource = new BlockSource(helper.getLevel(), helper.absolutePos(lone), helper.getBlockState(lone), loneDispenser);
		ItemStack dropped = DispenserBlock.DISPENSER_REGISTRY.get(DrinkItems.MUG.get()).dispense(loneSource, new ItemStack(DrinkItems.MUG.get(), 2));
		helper.assertValueEqual(dropped.getCount(), 1, "the default behaviour throws one mug");
		helper.succeed();
	}

	/** The real redstone path: a pulse makes the dispenser pour its bottle in. */
	private static void dispenserRedstonePulse(GameTestHelper helper) {
		BlockPos dispenserPos = new BlockPos(1, 1, 2);
		helper.setBlock(dispenserPos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
		AgingBarrelBlockEntity barrel = placeBarrel(helper, dispenserPos.east());
		helper.getBlockEntity(dispenserPos, DispenserBlockEntity.class).setItem(0, new ItemStack(BaseItems.WHEAT_WORT_BOTTLE.get(), 2));
		helper.setBlock(dispenserPos.west(), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> {
			helper.assertValueEqual(barrel.servings(), 1, "one bottle per pulse");
			helper.assertTrue(barrel.drink().is(DrinkItems.WHEAT_BEER.get()), "wheat wort in the barrel");
		});
	}

	// ------------------------------------------------------------------ breaking, save/load, conservation (B12)

	/** Broken, it drops itself with the batch (servings, stage, age); the age stays put while it is an item. */
	private static void keepsBatchWhenBroken(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 5, 30000);
		barrel.setStack(AgingBarrelBlockEntity.VESSEL_SLOT, new ItemStack(DrinkItems.WINE_GLASS.get(), 2));
		helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
		ItemStack barrelItem = ItemStack.EMPTY;
		int glasses = 0;
		for (ItemEntity entity : dropsAround(helper, POS)) {
			ItemStack stack = entity.getItem();
			if (stack.is(BarrelRegistry.AGING_BARREL_ITEM.get())) barrelItem = stack.copy();
			else if (stack.is(DrinkItems.WINE_GLASS.get())) glasses += stack.getCount();
			else helper.fail("unexpected drop " + stack);
			entity.discard();
		}
		helper.assertValueEqual(glasses, 2, "slot items drop");
		BarrelContents contents = AgingBarrelItem.contentsOf(barrelItem);
		helper.assertValueEqual(contents.servings(), 5, "servings in the item");
		helper.assertValueEqual(contents.stageAt(contents.elapsed()), BarrelStage.YOUNG, "stage in the item");
		helper.assertTrue(contents.elapsed() >= 30000 && contents.elapsed() < 30010, "age in the item: " + contents.elapsed());
		long elapsed = contents.elapsed();
		ItemStack item = barrelItem;
		helper.runAfterDelay(40, () -> {
			helper.assertValueEqual(AgingBarrelItem.contentsOf(item).elapsed(), elapsed, "no aging as an item");
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, item);
			helper.placeAt(player, item, POS.below(), Direction.UP);
			AgingBarrelBlockEntity placed = helper.getBlockEntity(POS, AgingBarrelBlockEntity.class);
			helper.assertValueEqual(placed.servings(), 5, "servings after placing");
			helper.assertValueEqual(placed.age(), elapsed, "carries on from the age it had (paused while an item)");
			helper.assertTrue(placed.content().is(DrinkItems.RED_WINE.get()), "still red wine");
			helper.succeed();
		});
	}

	/** Save and load keep the batch, its fill time, the slots and the name. */
	private static void saveLoadRoundtrip(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 6, FERMENT + 10);
		barrel.setStack(AgingBarrelBlockEntity.VESSEL_SLOT, new ItemStack(DrinkItems.WINE_GLASS.get(), 3));
		barrel.setStack(AgingBarrelBlockEntity.CONTAINER_SLOT, new ItemStack(Items.BUCKET));
		ItemStack named = new ItemStack(BarrelRegistry.AGING_BARREL_ITEM.get());
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Wedding Wine"));
		named.set(BarrelRegistry.BARREL_CONTENTS.get(), barrel.contents());
		barrel.applyComponentsFromItemStack(named);
		var tag = barrel.saveWithFullMetadata(helper.getLevel().registryAccess());
		long age = barrel.age();
		AgingBarrelBlockEntity copy = new AgingBarrelBlockEntity(helper.absolutePos(POS), helper.getBlockState(POS));
		copy.setLevel(helper.getLevel());
		copy.loadWithComponents(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
		helper.assertValueEqual(copy.servings(), 6, "servings");
		helper.assertValueEqual(copy.age(), age, "age");
		helper.assertValueEqual(copy.stage(), BarrelStage.YOUNG, "stage");
		helper.assertValueEqual(copy.stackIn(AgingBarrelBlockEntity.VESSEL_SLOT).getCount(), 3, "vessel slot");
		helper.assertTrue(copy.stackIn(AgingBarrelBlockEntity.CONTAINER_SLOT).is(Items.BUCKET), "container slot");
		helper.assertTrue(copy.getCustomName() != null && copy.getCustomName().getString().equals("Wedding Wine"), "name");
		helper.succeed();
	}

	/**
	 * Random actions through every real path: a player's clicks on the block ({@code useItemOn}), a dispenser facing it,
	 * a real hopper above feeding it and a real hopper below pulling from it, raw capability inserts and extracts on
	 * every side (some aborted), time jumps, barrel ticks and save/load, with grape juice. After every step: liquid
	 * (juice bottles + 4 per juice bucket + wines + servings) and each kind of container are exactly what they were,
	 * wherever they are (player, hoppers, dispenser, barrel slots). Breaking at the end drops the rest.
	 */
	private static void conservationRandomActions(GameTestHelper helper) {
		BlockPos barrelPos = new BlockPos(2, 2, 2);
		BlockPos topPos = barrelPos.above();
		BlockPos bottomPos = barrelPos.below();
		BlockPos dispenserPos = barrelPos.west();
		helper.setBlock(bottomPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
		AgingBarrelBlockEntity barrel = placeBarrel(helper, barrelPos);
		helper.setBlock(topPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		helper.setBlock(dispenserPos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
		HopperBlockEntity top = helper.getBlockEntity(topPos, HopperBlockEntity.class);
		HopperBlockEntity bottom = helper.getBlockEntity(bottomPos, HopperBlockEntity.class);
		DispenserBlockEntity dispenser = helper.getBlockEntity(dispenserPos, DispenserBlockEntity.class);
		BlockSource source = new BlockSource(helper.getLevel(), helper.absolutePos(dispenserPos), helper.getBlockState(dispenserPos), dispenser);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos absolute = helper.absolutePos(barrelPos);
		Map<Item, Integer> outside = new HashMap<>();
		outside.put(BaseItems.GRAPE_JUICE.get(), 24);
		outside.put(BaseItems.GRAPE_JUICE_BUCKET.get(), 5);
		outside.put(Items.GLASS_BOTTLE, 6);
		outside.put(Items.BUCKET, 3);
		outside.put(DrinkItems.WINE_GLASS.get(), 14);
		outside.put(DrinkItems.MUG.get(), 4);
		List<Item> pool = List.copyOf(outside.keySet());
		List<Item> dispensable = List.of(BaseItems.GRAPE_JUICE.get(), BaseItems.GRAPE_JUICE_BUCKET.get(), DrinkItems.WINE_GLASS.get(), DrinkItems.MUG.get());
		Direction[] sides = {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
		Random random = new Random(20261106L);
		int[] expected = totals(everywhere(outside, top, bottom, dispenser), barrel);
		for (int step = 0; step < 400; step++) {
			int action = random.nextInt(10);
			Item item = pool.get(random.nextInt(pool.size()));
			if (action < 3) {
				// a player's right-click on the block, holding one of the item
				if (outside.getOrDefault(item, 0) > 0) {
					take(outside, item, 1);
					player.getInventory().clearContent();
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
					helper.useBlock(barrelPos, player);
					for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
						ItemStack stack = player.getInventory().getItem(i);
						if (!stack.isEmpty()) outside.merge(stack.getItem(), stack.getCount(), Integer::sum);
					}
					player.getInventory().clearContent();
				}
			} else if (action < 5) {
				// the dispenser facing the barrel
				Item fed = dispensable.get(random.nextInt(dispensable.size()));
				if (outside.getOrDefault(fed, 0) > 0) {
					take(outside, fed, 1);
					ItemStack result = DispenserBlock.DISPENSER_REGISTRY.get(fed).dispense(source, new ItemStack(fed));
					if (!result.isEmpty()) outside.merge(result.getItem(), result.getCount(), Integer::sum);
					drain(dispenser, outside);
				}
			} else if (action < 7) {
				// the real hoppers: one item in at the top, eight hopper ticks, everything below taken out
				if (outside.getOrDefault(item, 0) > 0 && top.getItem(0).isEmpty()) {
					take(outside, item, 1);
					top.setItem(0, new ItemStack(item));
				}
				for (int t = 0; t < 8; t++) {
					HopperBlockEntity.pushItemsTick(helper.getLevel(), helper.absolutePos(topPos), helper.getBlockState(topPos), top);
					HopperBlockEntity.pushItemsTick(helper.getLevel(), helper.absolutePos(bottomPos), helper.getBlockState(bottomPos), bottom);
					tickBarrel(helper, barrelPos);
				}
				drain(bottom, outside);
				if (random.nextBoolean()) drain(top, outside);
			} else if (action < 8) {
				Direction side = sides[random.nextInt(sides.length)];
				SlotAccess handler = Services.PLATFORM.itemAccess(helper.getLevel(), absolute, side);
				boolean insert = random.nextBoolean();
				int amount = 1 + random.nextInt(4);
				try (SlotAccess.Tx tx = Services.PLATFORM.openTransaction()) {
					int done = 0;
					for (int i = 0; i < handler.size() && done < amount; i++) {
						int want = insert ? Math.min(amount - done, outside.getOrDefault(item, 0) - done) : amount - done;
						if (want <= 0) break;
						done += insert ? handler.insert(i, SlotAccess.of(item), want, tx) : handler.extract(i, SlotAccess.of(item), want, tx);
					}
					if (random.nextInt(5) > 0) {
						tx.commit();
						if (insert) take(outside, item, done);
						else outside.merge(item, done, Integer::sum);
					}
				}
			} else if (action < 9) {
				barrel.setAge(barrel.age() + (random.nextBoolean() ? random.nextInt(200) : FERMENT));
				tickBarrel(helper, barrelPos);
			} else {
				var tag = barrel.saveWithFullMetadata(helper.getLevel().registryAccess());
				barrel.loadWithComponents(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
			}
			int[] now = totals(everywhere(outside, top, bottom, dispenser), barrel);
			for (int k = 0; k < now.length; k++) {
				helper.assertValueEqual(now[k], expected[k], "step " + step + " (action " + action + ", " + item + ") total #" + k);
			}
		}
		// what is left in the slots drops; the batch stays in the barrel item
		Map<Item, Integer> inSlots = new HashMap<>();
		for (int i = 0; i < AgingBarrelBlockEntity.INVENTORY_SIZE; i++) {
			ItemStack stack = barrel.stackIn(i);
			if (!stack.isEmpty()) inSlots.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		int servings = barrel.servings();
		drain(top, outside);
		drain(bottom, outside);
		helper.setBlock(topPos, Blocks.AIR);
		helper.setBlock(bottomPos, Blocks.STONE);
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0D)).forEach(ItemEntity::discard);
		helper.getLevel().destroyBlock(absolute, true);
		Map<Item, Integer> dropped = new HashMap<>();
		int barrelServings = 0;
		for (ItemEntity entity : dropsAround(helper, barrelPos)) {
			ItemStack stack = entity.getItem();
			if (stack.is(BarrelRegistry.AGING_BARREL_ITEM.get())) barrelServings += AgingBarrelItem.contentsOf(stack).servings();
			else dropped.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		helper.assertValueEqual(dropped, inSlots, "slot items dropped");
		helper.assertValueEqual(barrelServings, servings, "servings kept in the barrel item");
		helper.succeed();
	}

	private static void drain(net.minecraft.world.Container container, Map<Item, Integer> outside) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				outside.merge(stack.getItem(), stack.getCount(), Integer::sum);
				container.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	private static Map<Item, Integer> everywhere(Map<Item, Integer> outside, net.minecraft.world.Container... containers) {
		Map<Item, Integer> all = new HashMap<>(outside);
		for (net.minecraft.world.Container container : containers) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (!stack.isEmpty()) all.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return all;
	}

	private static void take(Map<Item, Integer> outside, Item item, int amount) {
		outside.merge(item, -amount, Integer::sum);
	}

	/** Liquid, glass bottles (empty + juice), buckets (empty + juice), wine glasses (empty + wine), mugs: outside + inside. */
	private static int[] totals(Map<Item, Integer> outside, AgingBarrelBlockEntity barrel) {
		Map<Item, Integer> all = new HashMap<>(outside);
		for (int i = 0; i < AgingBarrelBlockEntity.INVENTORY_SIZE; i++) {
			ItemStack stack = barrel.stackIn(i);
			if (!stack.isEmpty()) all.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		int juice = all.getOrDefault(BaseItems.GRAPE_JUICE.get(), 0);
		int juiceBuckets = all.getOrDefault(BaseItems.GRAPE_JUICE_BUCKET.get(), 0);
		int wine = all.getOrDefault(DrinkItems.RED_WINE.get(), 0) + all.getOrDefault(DrinkItems.AGED_RED_WINE.get(), 0);
		return new int[]{
			juice + 4 * juiceBuckets + wine + barrel.servings(),
			all.getOrDefault(Items.GLASS_BOTTLE, 0) + juice,
			all.getOrDefault(Items.BUCKET, 0) + juiceBuckets,
			all.getOrDefault(DrinkItems.WINE_GLASS.get(), 0) + wine,
			all.getOrDefault(DrinkItems.MUG.get(), 0)
		};
	}

	// ------------------------------------------------------------------ comparator (B11), racks and stacks (B2, B3), bungs (B4)

	private static void comparatorOutput(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = placeBarrel(helper, POS);
		int[] expected = {0, 1, 3, 5, 7, 9, 11, 13, 15};
		helper.assertValueEqual(barrel.comparatorSignal(), 0, "empty");
		for (int servings = 1; servings <= 8; servings++) {
			pour(helper, barrel, BaseItems.GRAPE_JUICE.get(), 1);
			helper.assertValueEqual(barrel.comparatorSignal(), expected[servings], servings + " servings");
		}
		// a real comparator next to it reads 15, and drops as servings are tapped
		BlockPos comparatorPos = POS.south();
		helper.setBlock(comparatorPos.below(), Blocks.STONE);
		helper.setBlock(comparatorPos, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.NORTH));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(helper.getBlockEntity(comparatorPos, ComparatorBlockEntity.class).getOutputSignal(), 15, "full barrel"))
			.thenExecute(() -> {
				barrel.setAge(FERMENT);
				ItemStack glasses = new ItemStack(DrinkItems.WINE_GLASS.get(), 4);
				for (int i = 0; i < 4; i++) barrel.tap(glasses, null);
			})
			.thenWaitUntil(() -> helper.assertValueEqual(helper.getBlockEntity(comparatorPos, ComparatorBlockEntity.class).getOutputSignal(), 7, "4 servings left"))
			.thenSucceed();
	}

	/**
	 * Hopper automation and a real comparator: glasses fed from the side are tapped into the hopper below, and the
	 * comparator follows the servings down (moves between the automation slots alone never change it).
	 */
	private static void hopperTapsMoveTheComparator(GameTestHelper helper) {
		BlockPos barrelPos = new BlockPos(2, 2, 2);
		helper.setBlock(barrelPos.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
		AgingBarrelBlockEntity barrel = placeBarrel(helper, barrelPos);
		// The comparator first, as built in play: a comparator only reads its input when a neighbour update reaches it
		BlockPos comparatorPos = barrelPos.south();
		helper.setBlock(comparatorPos.below(), Blocks.STONE);
		helper.setBlock(comparatorPos, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.NORTH));
		pour(helper, barrel, BaseItems.GRAPE_JUICE.get(), 8);
		barrel.setAge(FERMENT);
		BlockPos side = barrelPos.east();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(helper.getBlockEntity(comparatorPos, ComparatorBlockEntity.class).getOutputSignal(), 15, "full barrel"))
			.thenExecute(() -> {
				helper.setBlock(side, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
				helper.getBlockEntity(side, HopperBlockEntity.class).setItem(0, new ItemStack(DrinkItems.WINE_GLASS.get(), 4));
			})
			.thenWaitUntil(() -> helper.assertValueEqual(countIn(helper.getBlockEntity(barrelPos.below(), HopperBlockEntity.class), DrinkItems.RED_WINE.get()), 4,
				"four wines tapped into the hopper below"))
			.thenWaitUntil(() -> {
				helper.assertValueEqual(barrel.servings(), 4, "servings after four");
				helper.assertValueEqual(helper.getBlockEntity(comparatorPos, ComparatorBlockEntity.class).getOutputSignal(), 7, "4 servings left");
			})
			.thenSucceed();
	}

	/** A change to an automation slot alone (no batch change) still marks the barrel's chunk for saving. */
	private static void slotChangeIsSaved(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = placeBarrel(helper, POS);
		var chunk = helper.getLevel().getChunkAt(helper.absolutePos(POS));
		chunk.tryMarkSaved();
		helper.assertFalse(chunk.isUnsaved(), "chunk saved");
		barrel.setStack(AgingBarrelBlockEntity.VESSEL_SLOT, new ItemStack(DrinkItems.WINE_GLASS.get(), 2));
		helper.assertTrue(chunk.isUnsaved(), "chunk unsaved after a slot change");
		helper.assertValueEqual(barrel.stackIn(AgingBarrelBlockEntity.VESSEL_SLOT).getCount(), 2, "vessels in the slot");
		helper.succeed();
	}

	/** B2: same-facing neighbours on the ground join their cradles; B3: a barrel on a barrel sits on chocks. */
	private static void racksAndStacking(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		// placed by a player looking south (facing = toward the player = north): three in a row along x
		player.setYRot(0.0F);
		for (int x = 1; x <= 3; x++) {
			placeBarrelItem(helper, player, new BlockPos(x, 0, 2), Direction.UP);
		}
		BlockState left = helper.getBlockState(new BlockPos(1, 1, 2));
		BlockState middle = helper.getBlockState(new BlockPos(2, 1, 2));
		BlockState right = helper.getBlockState(new BlockPos(3, 1, 2));
		helper.assertValueEqual(middle.getValue(AgingBarrelBlock.FACING), Direction.NORTH, "B1: the tap faces the player");
		Direction facing = middle.getValue(AgingBarrelBlock.FACING);
		// left of a north-facing barrel (counter-clockwise) is west, -x
		helper.assertTrue(middle.getValue(AgingBarrelBlock.LEFT) && middle.getValue(AgingBarrelBlock.RIGHT), "the middle joins both sides");
		helper.assertTrue(!left.getValue(AgingBarrelBlock.LEFT) && left.getValue(AgingBarrelBlock.RIGHT), "the west end joins only east: " + left);
		helper.assertTrue(right.getValue(AgingBarrelBlock.LEFT) && !right.getValue(AgingBarrelBlock.RIGHT), "the east end joins only west: " + right);
		helper.assertValueEqual(facing.getCounterClockWise(), Direction.WEST, "left is west");
		// another facing doesn't join
		helper.setBlock(new BlockPos(4, 1, 2), BarrelRegistry.AGING_BARREL.get().defaultBlockState().setValue(AgingBarrelBlock.FACING, Direction.EAST));
		helper.assertFalse(helper.getBlockState(new BlockPos(3, 1, 2)).getValue(AgingBarrelBlock.RIGHT), "a barrel facing east doesn't join the rack");
		// stacking: one on the middle
		placeBarrelItem(helper, player, new BlockPos(2, 1, 2), Direction.UP);
		BlockState top = helper.getBlockState(new BlockPos(2, 2, 2));
		helper.assertTrue(top.is(BarrelRegistry.AGING_BARREL.get()) && top.getValue(AgingBarrelBlock.STACKED), "on a barrel: chocks");
		helper.assertTrue(!top.getValue(AgingBarrelBlock.LEFT) && !top.getValue(AgingBarrelBlock.RIGHT), "stacked barrels have no cradle to join");
		helper.assertFalse(helper.getBlockState(new BlockPos(2, 1, 2)).getValue(AgingBarrelBlock.STACKED), "the bottom one keeps its cradle");
		// a second stacked one next to it still doesn't make a rack up there
		placeBarrelItem(helper, player, new BlockPos(3, 1, 2), Direction.UP);
		helper.assertFalse(helper.getBlockState(new BlockPos(2, 2, 2)).getValue(AgingBarrelBlock.RIGHT), "no cradle between stacked barrels");
		// take the bottom one away: the top one drops onto... nothing, and gets its cradle back
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
		helper.assertFalse(helper.getBlockState(new BlockPos(2, 2, 2)).getValue(AgingBarrelBlock.STACKED), "no barrel below: cradle again");
		helper.assertFalse(helper.getBlockState(new BlockPos(1, 1, 2)).getValue(AgingBarrelBlock.RIGHT), "the rack is broken where the middle was");
		helper.succeed();
	}

	/** B4: cork when empty, airlock while fermenting, cork when young, wax once aged; the block state follows each tick. */
	private static void bungStates(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = placeBarrel(helper, POS);
		tickBarrel(helper, POS);
		helper.assertValueEqual(helper.getBlockState(POS).getValue(AgingBarrelBlock.BUNG), BungState.CORK, "empty");
		pour(helper, barrel, BaseItems.GRAPE_JUICE.get(), 1);
		tickBarrel(helper, POS);
		helper.assertValueEqual(helper.getBlockState(POS).getValue(AgingBarrelBlock.BUNG), BungState.AIRLOCK, "fermenting");
		barrel.setAge(FERMENT);
		tickBarrel(helper, POS);
		helper.assertValueEqual(helper.getBlockState(POS).getValue(AgingBarrelBlock.BUNG), BungState.CORK, "young");
		barrel.setAge(FERMENT + AGE);
		tickBarrel(helper, POS);
		helper.assertValueEqual(helper.getBlockState(POS).getValue(AgingBarrelBlock.BUNG), BungState.WAX, "aged");
		for (BarrelStage stage : BarrelStage.values()) {
			helper.assertValueEqual(stage.bung(), switch (stage) {
				case FERMENTING -> BungState.AIRLOCK;
				case AGED -> BungState.WAX;
				default -> BungState.CORK;
			}, "bung of " + stage);
		}
		helper.succeed();
	}

	/** B10/B15: drink, servings and state, with the custom name first; the "when" words follow the day. */
	private static void statusLine(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = placeBarrel(helper, POS);
		helper.assertValueEqual(key(BarrelStatus.of(barrel)), "message.vineandbarrel.aging_barrel.empty", "empty");
		pour(helper, barrel, BaseItems.GRAPE_JUICE_BUCKET.get(), 1);
		pour(helper, barrel, BaseItems.GRAPE_JUICE.get(), 1);
		Component status = BarrelStatus.of(barrel);
		helper.assertValueEqual(key(status), "message.vineandbarrel.aging_barrel.status", "status");
		Object[] parts = args(status);
		helper.assertValueEqual(((Component) parts[0]).getString(), new ItemStack(DrinkItems.RED_WINE.get()).getHoverName().getString(), "it names the drink it becomes");
		helper.assertValueEqual(args((Component) parts[1])[0], 5, "5 servings");
		helper.assertValueEqual(key((Component) parts[2]), "message.vineandbarrel.aging_barrel.fermenting", "fermenting");
		// renamed in an anvil: the name leads
		ItemStack named = barrel.getAsItem();
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Wedding Wine"));
		barrel.applyComponentsFromItemStack(named);
		barrel.setAge(FERMENT);
		Component renamed = BarrelStatus.of(barrel);
		Component title = (Component) args(renamed)[0];
		helper.assertValueEqual(key(title), "message.vineandbarrel.aging_barrel.named", "named title");
		helper.assertValueEqual(((Component) args(title)[0]).getString(), "Wedding Wine", "the custom name");
		helper.assertValueEqual(key((Component) args(renamed)[2]), "message.vineandbarrel.aging_barrel.aging", "aging");
		helper.assertValueEqual(key((Component) args((Component) args(renamed)[2])[0]), "message.vineandbarrel.aging_barrel.when.in_days", "aged in 3 days");
		// the "when" words: days start at sunrise (0) and keep their night
		helper.assertValueEqual(key(BarrelStatus.when(1000L, 24000L)), "message.vineandbarrel.aging_barrel.when.tomorrow.morning", "a day from morning");
		helper.assertValueEqual(key(BarrelStatus.when(12500L, 24000L)), "message.vineandbarrel.aging_barrel.when.tomorrow.evening", "a day from evening");
		helper.assertValueEqual(key(BarrelStatus.when(1000L, 10000L)), "message.vineandbarrel.aging_barrel.when.today.afternoon", "later today");
		helper.assertValueEqual(key(BarrelStatus.when(6000L, 10000L)), "message.vineandbarrel.aging_barrel.when.today.night", "tonight");
		helper.assertValueEqual(key(BarrelStatus.when(20000L, 6000L)), "message.vineandbarrel.aging_barrel.when.tomorrow.morning", "after midnight is tomorrow morning");
		helper.assertValueEqual(key(BarrelStatus.when(1000L, 600L)), "message.vineandbarrel.aging_barrel.when.soon", "under a minute");
		helper.assertValueEqual(key(BarrelStatus.when(-1L, 30000L)), "message.vineandbarrel.aging_barrel.when.in_days", "no day in the Nether");
		helper.assertValueEqual(BarrelStatus.partOfDay(23999L), "night", "late night");
		helper.succeed();
	}

	/** B9: the tap handle turns while tapping and turns back by itself. */
	private static void tapHandleTurnsBack(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 2, FERMENT);
		barrel.tap(new ItemStack(DrinkItems.WINE_GLASS.get()), null);
		helper.assertTrue(helper.getBlockState(POS).getValue(AgingBarrelBlock.TAPPING), "the handle turns");
		helper.succeedWhen(() -> helper.assertFalse(helper.getBlockState(POS).getValue(AgingBarrelBlock.TAPPING), "and turns back"));
	}

	/**
	 * B16: wood sounds, an axe is the tool; like the vanilla barrel it is lit by lava but fire doesn't catch on it (it
	 * would burn a batch), and it is furnace fuel only while it holds no batch.
	 */
	private static void woodProperties(GameTestHelper helper) {
		BlockState barrel = BarrelRegistry.AGING_BARREL.get().defaultBlockState();
		helper.assertTrue(barrel.is(BlockTags.MINEABLE_WITH_AXE), "axe");
		helper.assertValueEqual(barrel.getSoundType(), Blocks.BARREL.defaultBlockState().getSoundType(), "wood sounds like the vanilla barrel");
		helper.assertTrue(barrel.ignitedByLava(), "lava sets things around it alight, like a vanilla barrel");
		helper.setBlock(POS, barrel);
		helper.assertValueEqual(VBFlammability.burnOdds(helper.getBlockState(POS)), 0, "not flammable");
		helper.assertValueEqual(VBFlammability.igniteOdds(helper.getBlockState(POS)), 0, "fire doesn't catch");
		helper.assertTrue(burns(helper, new ItemStack(BarrelRegistry.AGING_BARREL_ITEM.get())), "an empty barrel is fuel");
		helper.assertValueEqual(new ItemStack(BarrelRegistry.AGING_BARREL_ITEM.get()).getMaxStackSize(), 1, "keeps contents: stacks to 1");
		// broken full: no fuel; emptied and broken again: fuel again
		AgingBarrelBlockEntity full = wineBarrel(helper, POS.east(), 2, 0);
		helper.assertTrue(!burns(helper, full.getAsItem()), "picked full: not fuel");
		helper.getLevel().destroyBlock(helper.absolutePos(POS.east()), true);
		ItemStack fullItem = barrelDrop(helper, POS.east());
		helper.assertTrue(!AgingBarrelItem.contentsOf(fullItem).isEmpty() && !burns(helper, fullItem), "a barrel with a batch is never fuel");
		AgingBarrelBlockEntity emptied = wineBarrel(helper, POS.east(), 1, 0);
		emptied.takeBack(new ItemStack(Items.GLASS_BOTTLE), null);
		helper.getLevel().destroyBlock(helper.absolutePos(POS.east()), true);
		helper.assertTrue(burns(helper, barrelDrop(helper, POS.east())), "emptied: fuel again");
		helper.succeed();
	}

	/** 26.2: whether a furnace takes it, by its burn time (26.3: the cooking_fuel component). */
	private static boolean burns(GameTestHelper helper, ItemStack stack) {
		return Services.PLATFORM.burnTime(stack, helper.getLevel()) > 0;
	}

	/** The one barrel item dropped around {@code pos} (discarded from the world). */
	private static ItemStack barrelDrop(GameTestHelper helper, BlockPos pos) {
		ItemStack found = ItemStack.EMPTY;
		for (ItemEntity entity : dropsAround(helper, pos)) {
			if (entity.getItem().is(BarrelRegistry.AGING_BARREL_ITEM.get())) {
				helper.assertTrue(found.isEmpty(), "only one barrel drops");
				found = entity.getItem().copy();
				entity.discard();
			}
		}
		helper.assertFalse(found.isEmpty(), "a barrel dropped");
		return found;
	}

	/** Data-driven recipes: the four base drinks are there; the FD ones are behind mod_loaded and absent without FD. */
	private static void recipesAndFdAbsent(GameTestHelper helper) {
		var recipes = helper.getLevel().recipeAccess();
		for (String id : List.of("red_wine", "sweet_berry_wine", "barley_ale", "wheat_beer", "white_wine", "mead", "hard_cider_from_apple_juice")) {
			var recipe = recipes.byKey(ResourceKey.create(Registries.RECIPE, VineAndBarrel.id("aging/" + id)));
			helper.assertTrue(recipe.isPresent() && recipe.get().value() instanceof AgingRecipe, "aging recipe " + id);
			AgingRecipe aging = (AgingRecipe) recipe.get().value();
			helper.assertValueEqual(aging.fermentingTime(), FERMENT, id + " ferments a day");
			helper.assertValueEqual(aging.agingTime(), AGE, id + " ages 3 more days");
			helper.assertTrue(aging.bucket().isPresent(), id + " takes buckets");
		}
		// FD's apple cider -> hard cider and melon wine load only with FD (both runs: see FdCompatGameTests); our own
		// apple juice makes the same hard cider in every run
		boolean fd = jexxme.vineandbarrel.platform.Services.PLATFORM.isModLoaded("farmersdelight");
		for (String id : List.of("hard_cider", "melon_wine")) {
			helper.assertValueEqual(recipes.byKey(ResourceKey.create(Registries.RECIPE, VineAndBarrel.id("aging/" + id))).isPresent(), fd, id + " loads exactly when FD is installed");
		}
		// the items exist anyway, so worlds and loot stay stable
		for (Item item : List.of(DrinkItems.HARD_CIDER.get(), DrinkItems.AGED_HARD_CIDER.get(), DrinkItems.MELON_WINE.get(), DrinkItems.AGED_MELON_WINE.get())) {
			helper.assertTrue(item instanceof DrinkItem, item + " exists");
		}
		helper.assertTrue(AgingBarrelBlockEntity.recipeFor(helper.getLevel(), new ItemStack(Items.APPLE)) == null, "no recipe for apples");
		helper.succeed();
	}

	// ------------------------------------------------------------------ drinks (§5)

	/**
	 * Stack 16, FD drinks, vessel back, one buff; young = level I for 45 s, aged = level II for 90 s + glint (no FD
	 * Comfort, see FdCompatGameTests). Wheat beer's Saturation is instant, so it has its own few ticks.
	 */
	private static void drinkItems(GameTestHelper helper) {
		TagKey<Item> fdDrinks = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("farmersdelight", "drinks"));
		for (DrinkItem drink : DrinkItems.allDrinks()) {
			ItemStack stack = new ItemStack(drink);
			helper.assertValueEqual(stack.getMaxStackSize(), 16, drink + " stacks to 16");
			helper.assertTrue(stack.is(fdDrinks), drink + " is an FD drink");
			Item vessel = AgingBarrelBlockEntity.vesselOf(stack);
			helper.assertTrue(vessel == DrinkItems.MUG.get() || vessel == DrinkItems.WINE_GLASS.get(), drink + " comes back as a vessel");
			helper.assertTrue(stack.getItem().getCraftingRemainder() != null && stack.getItem().getCraftingRemainder().item().value() == vessel, drink + " remainder");
			helper.assertValueEqual(Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), drink.isAged(), drink + " glint");
			helper.assertTrue(stack.is(BarrelRegistry.DRINK_VESSELS) == false, "a full drink is not an empty vessel");
		}
		for (DrinkItem drink : DrinkItems.allDrinks()) {
			MobEffectInstance buff = drink.buff();
			helper.assertValueEqual(buff.getAmplifier(), drink.isAged() ? 1 : 0, drink + " buff level");
			boolean saturation = buff.getEffect() == MobEffects.SATURATION;
			int ticks = saturation ? (drink.isAged() ? DrinkItems.WHEAT_BEER_AGED_TICKS : DrinkItems.WHEAT_BEER_YOUNG_TICKS)
				: (drink.isAged() ? 90 * 20 : 45 * 20);
			helper.assertValueEqual(buff.getDuration(), ticks, drink + " buff duration");
		}
		// Saturation feeds (level + 1) food per tick: young 5, aged 8, filling but never a whole bar
		int youngFood = DrinkItems.WHEAT_BEER.get().buff().getDuration() * (DrinkItems.WHEAT_BEER.get().buff().getAmplifier() + 1);
		int agedFood = DrinkItems.AGED_WHEAT_BEER.get().buff().getDuration() * (DrinkItems.AGED_WHEAT_BEER.get().buff().getAmplifier() + 1);
		helper.assertTrue(youngFood < agedFood && agedFood <= 10, "wheat beer feeds " + youngFood + " -> " + agedFood);
		helper.assertTrue(DrinkItems.RED_WINE.get().buff().getEffect() == MobEffects.REGENERATION, "red wine: regeneration");
		helper.assertTrue(DrinkItems.SWEET_BERRY_WINE.get().buff().getEffect() == MobEffects.SPEED, "sweet berry wine: speed");
		helper.assertTrue(DrinkItems.BARLEY_ALE.get().buff().getEffect() == MobEffects.HASTE, "pale ale: haste");
		helper.assertTrue(DrinkItems.WHEAT_BEER.get().buff().getEffect() == MobEffects.SATURATION, "wheat beer: saturation");
		helper.assertTrue(DrinkItems.HARD_CIDER.get().buff().getEffect() == MobEffects.ABSORPTION, "hard cider: absorption");
		helper.assertTrue(DrinkItems.MELON_WINE.get().buff().getEffect() == MobEffects.FIRE_RESISTANCE, "melon wine: fire resistance");
		helper.assertTrue(DrinkItems.WHITE_WINE.get().buff().getEffect() == MobEffects.NIGHT_VISION, "white wine: night vision");
		helper.assertTrue(DrinkItems.MEAD.get().buff().getEffect() == MobEffects.RESISTANCE, "mead: resistance");
		helper.assertTrue(AgingBarrelBlockEntity.vesselOf(new ItemStack(DrinkItems.WHITE_WINE.get())) == DrinkItems.WINE_GLASS.get(), "white wine: wine glass");
		helper.assertTrue(AgingBarrelBlockEntity.vesselOf(new ItemStack(DrinkItems.MEAD.get())) == DrinkItems.MUG.get(), "mead: mug (Jexxme)");
		helper.assertTrue(new ItemStack(DrinkItems.MUG.get()).is(BarrelRegistry.DRINK_VESSELS) && new ItemStack(DrinkItems.WINE_GLASS.get()).is(BarrelRegistry.DRINK_VESSELS), "vessels");
		helper.succeed();
	}

	/**
	 * A new drink, end to end in a real barrel: a bucket and a bottle of its base pour 5 servings, a day makes the young
	 * drink, 3 more days the aged one; each is tapped into its own vessel only, and drinking one gives that vessel back
	 * with the drink's buff.
	 */
	private static void newDrinkAgesAndTaps(GameTestHelper helper, Item bottle, Item bucket, DrinkItem young, DrinkItem aged, Item vessel, Item otherVessel) {
		AgingBarrelBlockEntity barrel = placeBarrel(helper, POS);
		ItemStack fullBucket = new ItemStack(bucket);
		ItemStack emptied = barrel.pour(fullBucket, null);
		helper.assertTrue(emptied != null && emptied.is(Items.BUCKET), "a bucket of " + bottle + " pours in and comes back empty");
		pour(helper, barrel, bottle, 1);
		helper.assertValueEqual(barrel.servings(), 5, "a bucket and a bottle");
		helper.assertValueEqual(barrel.stage(), BarrelStage.FERMENTING, "fermenting at first");
		barrel.setAge(FERMENT);
		helper.assertValueEqual(barrel.stage(), BarrelStage.YOUNG, "young after a day");
		helper.assertTrue(barrel.content().is(young), "the young drink is " + young);
		helper.assertTrue(barrel.tap(new ItemStack(otherVessel), null).isEmpty(), young + " is not tapped into a " + otherVessel);
		ItemStack drink = barrel.tap(new ItemStack(vessel), null);
		helper.assertTrue(drink.is(young) && AgingBarrelBlockEntity.vesselOf(drink) == vessel, "tapped " + drink);
		barrel.setAge(FERMENT + AGE);
		helper.assertValueEqual(barrel.stage(), BarrelStage.AGED, "aged after 3 more days");
		ItemStack agedDrink = barrel.tap(new ItemStack(vessel), null);
		helper.assertTrue(agedDrink.is(aged), "the aged drink is " + aged + ", got " + agedDrink);
		helper.assertValueEqual(barrel.servings(), 3, "servings left");
		TestPlayer player = drinker(helper, new Vec3(2.5, 2.0, 2.5));
		player.drink(agedDrink);
		helper.assertTrue(player.getMainHandItem().is(vessel), "drinking gives the " + vessel + " back");
		MobEffectInstance buff = player.getEffect(aged.buff().getEffect());
		helper.assertTrue(buff != null && buff.getAmplifier() == 1, "the aged buff, level II: " + buff);
		helper.succeed();
	}

	/** Drinking: the buff, the vessel back (one at a time from a stack), Tipsy +1 each; non-alcoholic mode leaves Tipsy out. */
	private static void tipsyFromDrinking(GameTestHelper helper) {
		TestPlayer player = drinker(helper, new Vec3(16.5, 1.0, 16.5));
		player.drink(new ItemStack(DrinkItems.BARLEY_ALE.get(), 3));
		helper.assertValueEqual(Tipsy.getLevel(player), 1, "one drink, one level");
		MobEffectInstance haste = player.getEffect(MobEffects.HASTE);
		helper.assertTrue(haste != null && haste.getAmplifier() == 0 && haste.getDuration() > DrinkItems.YOUNG_TICKS - 100, "the buff, Haste I for 45 s: " + haste);
		helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "one drunk from the stack");
		helper.assertValueEqual(count(player, DrinkItems.MUG.get()), 1, "the mug comes back");
		player.drink(new ItemStack(DrinkItems.AGED_RED_WINE.get()));
		player.drink(new ItemStack(DrinkItems.WHEAT_BEER.get()));
		helper.assertValueEqual(Tipsy.getLevel(player), 3, "three drinks");
		helper.assertTrue(player.getMainHandItem().is(DrinkItems.MUG.get()), "the last one leaves its mug in the hand");
		MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == 1 && regen.getDuration() > DrinkItems.AGED_TICKS - 100, "aged red wine: Regeneration II for 90 s: " + regen);
		// grape juice still sobers by one (M4)
		player.drink(new ItemStack(BaseItems.GRAPE_JUICE.get()));
		helper.assertValueEqual(Tipsy.getLevel(player), 2, "grape juice lowers it");
		// non-alcoholic mode: buffs stay, Tipsy doesn't come
		TestPlayer sober = drinker(helper, new Vec3(28.5, 1.0, 4.5));
		try {
			TipsyConfig.ENABLED.setForTest(false);
			sober.drink(new ItemStack(DrinkItems.AGED_HARD_CIDER.get()));
			helper.assertValueEqual(Tipsy.getLevel(sober), 0, "non-alcoholic mode");
			helper.assertTrue(sober.getEffect(MobEffects.ABSORPTION) != null, "the buff still comes");
		} finally {
			TipsyConfig.ENABLED.setForTest(null);
		}
		helper.succeed();
	}

	/** From Tipsy IV a drink gives no buff, and clinking glasses then brings none back either (it is still a cheers). */
	private static void cheersTooDrunk(GameTestHelper helper) {
		AtomicInteger events = new AtomicInteger();
		TestPlayer a = drinker(helper, new Vec3(8.5, 1.0, 20.5));
		TestPlayer b = drinker(helper, new Vec3(10.5, 1.0, 20.5));
		List<Player> ours = List.of(a, b);
		Consumer<DrankTogether> listener = event -> {
			if (event.players().stream().anyMatch(ours::contains)) {
				events.incrementAndGet();
			}
		};
		DrankTogether.addListener(listener);
		try {
			jexxme.vineandbarrel.tipsy.Tipsy.setLevel(a, 3);
			jexxme.vineandbarrel.tipsy.Tipsy.setLevel(b, 3);
			a.drink(new ItemStack(DrinkItems.BARLEY_ALE.get()));
			b.drink(new ItemStack(DrinkItems.BARLEY_ALE.get()));
			helper.assertValueEqual(events.get(), 1, "still a cheers");
			helper.assertFalse(a.hasEffect(MobEffects.HASTE) || b.hasEffect(MobEffects.HASTE), "no Haste for either: too drunk, and the cheers adds none");
		} finally {
			DrankTogether.removeListener(listener);
		}
		helper.succeed();
	}

	/** D2: two players drinking within 3 s and 5 blocks clink (longer buffs, one event); too far or too late doesn't. */
	private static void cheersWindow(GameTestHelper helper) {
		AtomicInteger events = new AtomicInteger();
		TestPlayer a = drinker(helper, new Vec3(8.5, 1.0, 8.5));
		TestPlayer b = drinker(helper, new Vec3(11.5, 1.0, 8.5));
		TestPlayer far = drinker(helper, new Vec3(24.5, 1.0, 24.5));
		TestPlayer farB = drinker(helper, new Vec3(24.5, 1.0, 16.5));
		// Only this test's cheers: other tests run at the same time and cheer too
		List<Player> ours = List.of(a, b, far, farB);
		Consumer<DrankTogether> listener = event -> {
			if (event.players().stream().anyMatch(ours::contains)) {
				events.incrementAndGet();
			}
		};
		DrankTogether.addListener(listener);
		int base = DrinkItems.BARLEY_ALE.get().buff().getDuration();
		int bonus = base * Cheers.BONUS_PERCENT / 100;
		a.drink(new ItemStack(DrinkItems.BARLEY_ALE.get()));
		far.drink(new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.startSequence()
			.thenExecuteAfter(20, () -> {
				b.drink(new ItemStack(DrinkItems.BARLEY_ALE.get()));
				helper.assertValueEqual(events.get(), 1, "one cheers");
				helper.assertTrue(b.getEffect(MobEffects.HASTE).getDuration() >= base + bonus - 1, "b's buff is longer: " + b.getEffect(MobEffects.HASTE));
				helper.assertTrue(a.getEffect(MobEffects.HASTE).getDuration() > base - 20 + bonus - 2, "a's buff is longer too: " + a.getEffect(MobEffects.HASTE));
				// 8 blocks away: no cheers
				farB.drink(new ItemStack(DrinkItems.BARLEY_ALE.get()));
				helper.assertValueEqual(events.get(), 1, "too far apart");
				helper.assertTrue(farB.getEffect(MobEffects.HASTE).getDuration() <= base, "no bonus far away");
			})
			.thenExecuteAfter(Cheers.WINDOW_TICKS + 5, () -> {
				// a drinks again long after b: too late for a cheers
				int before = events.get();
				a.drink(new ItemStack(DrinkItems.BARLEY_ALE.get()));
				helper.assertValueEqual(events.get(), before, "more than 3 s later");
			})
			.thenExecute(() -> DrankTogether.removeListener(listener))
			.thenSucceed();
	}

	// ------------------------------------------------------------------ D1 placed drinks

	static UseOnContext sneakUse(GameTestHelper helper, Player player, BlockPos clicked, Vec3 local) {
		BlockPos absolute = helper.absolutePos(clicked);
		Vec3 hit = Vec3.atLowerCornerOf(absolute).add(local);
		return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, absolute, false));
	}

	/** Breaking a table of four knocks off one drink at a time (it drops); the last one takes the block with it. */
	private static void breakKnocksOffOne(GameTestHelper helper) {
		BlockPos table = new BlockPos(2, 1, 2);
		BlockPos spot = table.above();
		helper.setBlock(table, Blocks.OAK_PLANKS);
		Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
		placer.setShiftKeyDown(true);
		Vec3[] where = {new Vec3(0.25, 1.0, 0.25), new Vec3(0.75, 1.0, 0.25), new Vec3(0.25, 1.0, 0.75), new Vec3(0.75, 1.0, 0.75)};
		for (Vec3 at : where) {
			placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.MUG.get()));
			DrinkPlacing.place(sneakUse(helper, placer, table, at));
		}
		PlacedDrinksBlockEntity drinks = helper.getBlockEntity(spot, PlacedDrinksBlockEntity.class);
		helper.assertValueEqual(drinks.count(), 4, "four mugs on the table");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		for (int left = 3; left >= 1; left--) {
			player.gameMode.destroyBlock(helper.absolutePos(spot));
			helper.assertBlockPresent(BarrelRegistry.PLACED_DRINKS.get(), spot);
			helper.assertValueEqual(helper.getBlockEntity(spot, PlacedDrinksBlockEntity.class).count(), left, "mugs left after a break");
		}
		player.gameMode.destroyBlock(helper.absolutePos(spot));
		helper.assertBlockNotPresent(BarrelRegistry.PLACED_DRINKS.get(), spot);
		int mugs = 0;
		for (ItemEntity entity : dropsAround(helper, spot)) {
			if (entity.getItem().is(DrinkItems.MUG.get())) mugs += entity.getItem().getCount();
		}
		helper.assertValueEqual(mugs, 4, "every mug dropped, one per break");
		player.discard();
		helper.succeed();
	}

	/** Audit m10: in creative, breaking drinks off a table drops none of them, the last one included. */
	private static void creativeBreakDropsNothing(GameTestHelper helper) {
		BlockPos table = new BlockPos(2, 1, 2);
		BlockPos spot = table.above();
		helper.setBlock(table, Blocks.OAK_PLANKS);
		Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
		placer.setShiftKeyDown(true);
		placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.RED_WINE.get()));
		DrinkPlacing.place(sneakUse(helper, placer, table, new Vec3(0.25, 1.0, 0.25)));
		placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.MUG.get()));
		DrinkPlacing.place(sneakUse(helper, placer, table, new Vec3(0.75, 1.0, 0.75)));
		helper.assertValueEqual(helper.getBlockEntity(spot, PlacedDrinksBlockEntity.class).count(), 2, "two drinks on the table");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.CREATIVE);
		player.gameMode.destroyBlock(helper.absolutePos(spot));
		helper.assertValueEqual(helper.getBlockEntity(spot, PlacedDrinksBlockEntity.class).count(), 1, "one knocked off");
		player.gameMode.destroyBlock(helper.absolutePos(spot));
		helper.assertBlockNotPresent(BarrelRegistry.PLACED_DRINKS.get(), spot);
		helper.assertValueEqual(dropsAround(helper, spot).size(), 0, "nothing dropped in creative");
		player.discard();
		helper.succeed();
	}

	/** Audit m8: a drink put down in water keeps the water source (waterlogged), and taking it leaves the water. */
	private static void placedUnderWater(GameTestHelper helper) {
		BlockPos table = new BlockPos(2, 1, 2);
		BlockPos spot = table.above();
		helper.setBlock(table, Blocks.OAK_PLANKS);
		helper.setBlock(spot, Blocks.WATER);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.WINE_GLASS.get()));
		helper.assertTrue(DrinkPlacing.place(sneakUse(helper, player, table, new Vec3(0.25, 1.0, 0.25))).consumesAction(), "placed in water");
		BlockState state = helper.getBlockState(spot);
		helper.assertTrue(state.is(BarrelRegistry.PLACED_DRINKS.get()), "a placed-drinks block");
		helper.assertTrue(state.getValue(PlacedDrinksBlock.WATERLOGGED), "waterlogged");
		helper.assertTrue(state.getFluidState().isSource(), "the water source is kept");
		helper.assertFalse(PlacedDrinksBlock.takeFrom(helper.getLevel(), helper.absolutePos(spot), 0).isEmpty(), "taken back");
		helper.assertTrue(helper.getBlockState(spot).is(Blocks.WATER) && helper.getBlockState(spot).getFluidState().isSource(), "water left behind");
		// On dry land it isn't waterlogged
		BlockPos dry = new BlockPos(4, 1, 2);
		helper.setBlock(dry, Blocks.OAK_PLANKS);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.MUG.get()));
		DrinkPlacing.place(sneakUse(helper, player, dry, new Vec3(0.25, 1.0, 0.25)));
		helper.assertFalse(helper.getBlockState(dry.above()).getValue(PlacedDrinksBlock.WATERLOGGED), "dry");
		helper.succeed();
	}

	/** A table of drinks in a water source (helper for the tests below): the table and one wine glass in the water. */
	private static BlockPos drinkUnderWater(GameTestHelper helper, Player player) {
		BlockPos table = new BlockPos(2, 1, 2);
		helper.setBlock(table, Blocks.OAK_PLANKS);
		helper.setBlock(table.above(), Blocks.WATER);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.WINE_GLASS.get()));
		helper.assertTrue(DrinkPlacing.place(sneakUse(helper, player, table, new Vec3(0.25, 1.0, 0.25))).consumesAction(), "placed in water");
		return table.above();
	}

	private static void assertWaterSource(GameTestHelper helper, BlockPos pos, String what) {
		helper.assertTrue(helper.getBlockState(pos).getFluidState().isSource()
			&& helper.getBlockState(pos).getFluidState().getType() == net.minecraft.world.level.material.Fluids.WATER, what + ": " + helper.getBlockState(pos));
	}

	/** Review: a second, third and fourth drink put down in the same waterlogged block keep the water. */
	private static void moreUnderWater(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos spot = drinkUnderWater(helper, player);
		Vec3[] where = {new Vec3(0.75, 0.2, 0.25), new Vec3(0.25, 0.2, 0.75), new Vec3(0.75, 0.2, 0.75)};
		for (Vec3 at : where) {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.MUG.get()));
			helper.assertTrue(DrinkPlacing.place(sneakUse(helper, player, spot, at)).consumesAction(), "another one placed");
		}
		helper.assertValueEqual(helper.getBlockEntity(spot, PlacedDrinksBlockEntity.class).count(), 4, "four drinks");
		helper.assertTrue(helper.getBlockState(spot).getValue(PlacedDrinksBlock.WATERLOGGED), "still waterlogged");
		assertWaterSource(helper, spot, "the water is kept");
		helper.succeed();
	}

	/** Review: a player breaking the last drink under water leaves the water (and gets the drink). */
	private static void lastBrokenUnderWater(GameTestHelper helper) {
		BlockPos spot = drinkUnderWater(helper, helper.makeMockPlayer(GameType.SURVIVAL));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.gameMode.destroyBlock(helper.absolutePos(spot));
		helper.assertBlockNotPresent(BarrelRegistry.PLACED_DRINKS.get(), spot);
		assertWaterSource(helper, spot, "water left where the drink stood");
		int glasses = 0;
		for (ItemEntity entity : dropsAround(helper, spot)) {
			if (entity.getItem().is(DrinkItems.WINE_GLASS.get())) glasses += entity.getItem().getCount();
		}
		helper.assertValueEqual(glasses, 1, "the glass dropped");
		player.discard();
		helper.succeed();
	}

	/** Review: a waterlogged drink whose table goes turns into its water (and drops the drink). */
	private static void unsupportedUnderWater(GameTestHelper helper) {
		BlockPos spot = drinkUnderWater(helper, helper.makeMockPlayer(GameType.SURVIVAL));
		// a solid floor under the table, so the water has nowhere to fall to in this tick
		helper.setBlock(spot.below(2), Blocks.STONE);
		helper.setBlock(spot.below(), Blocks.AIR);
		helper.assertBlockNotPresent(BarrelRegistry.PLACED_DRINKS.get(), spot);
		helper.assertTrue(helper.getBlockState(spot).is(Blocks.WATER), "turned into water: " + helper.getBlockState(spot));
		helper.assertValueEqual(dropsAround(helper, spot).size(), 1, "the glass dropped");
		helper.succeed();
	}

	/** Review: knocking a full drink off a table of two spills it (the splash) and drops it; the other stays. */
	private static void fullKnockedOff(GameTestHelper helper) {
		BlockPos table = new BlockPos(2, 1, 2);
		BlockPos spot = table.above();
		helper.setBlock(table, Blocks.OAK_PLANKS);
		Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
		placer.setShiftKeyDown(true);
		placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.RED_WINE.get()));
		DrinkPlacing.place(sneakUse(helper, placer, table, new Vec3(0.25, 1.0, 0.25)));
		placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.AGED_BARLEY_ALE.get()));
		DrinkPlacing.place(sneakUse(helper, placer, table, new Vec3(0.75, 1.0, 0.75)));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.gameMode.destroyBlock(helper.absolutePos(spot));
		PlacedDrinksBlockEntity drinks = helper.getBlockEntity(spot, PlacedDrinksBlockEntity.class);
		helper.assertValueEqual(drinks.count(), 1, "one knocked off, one left");
		List<ItemEntity> drops = dropsAround(helper, spot);
		helper.assertValueEqual(drops.size(), 1, "one drop");
		ItemStack dropped = drops.getFirst().getItem();
		helper.assertTrue(dropped.is(DrinkItems.RED_WINE.get()) || dropped.is(DrinkItems.AGED_BARLEY_ALE.get()), "a full drink dropped: " + dropped);
		helper.assertTrue(!drinks.getItem(0).isEmpty() || !drinks.getItem(3).isEmpty(), "the other one still stands");
		player.discard();
		helper.succeed();
	}

	/** Audit m6: each state's shape is made once (the same object every time), and water doesn't change it. */
	private static void placedShapes(GameTestHelper helper) {
		BlockState state = BarrelRegistry.PLACED_DRINKS.get().defaultBlockState()
			.setValue(PlacedDrinksBlock.SPOT.get(0), PlacedDrink.MUG).setValue(PlacedDrinksBlock.SPOT.get(3), PlacedDrink.GLASS_FULL);
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		var shape = state.getShape(helper.getLevel(), at);
		helper.assertTrue(shape == state.getShape(helper.getLevel(), at), "cached: the same shape object");
		helper.assertTrue(shape == state.setValue(PlacedDrinksBlock.WATERLOGGED, true).getShape(helper.getLevel(), at), "water doesn't change it");
		helper.assertValueEqual(shape.bounds(), new AABB(2 / 16.0, 0, 2 / 16.0, 14 / 16.0, 6 / 16.0, 14 / 16.0), "a mug and a glass in opposite corners");
		helper.succeed();
	}

	/** Drinking sounds like the vessel (mug deeper, glass lighter), and so does pouring: wine light, beer deep, cider between. */
	private static void soundLikeTheirVessel(GameTestHelper helper) {
		for (DrinkItem drink : DrinkItems.allDrinks()) {
			PlacedDrink vessel = PlacedDrink.of(new ItemStack(drink));
			var consumable = new ItemStack(drink).get(DataComponents.CONSUMABLE);
			helper.assertTrue(consumable != null && consumable.sound().value() == (vessel.isMug() ? BarrelRegistry.MUG_DRINK : BarrelRegistry.WINE_GLASS_DRINK).value(),
				drink + " drinks with its vessel's sound");
		}
		float wine = DrinkItems.pourPitch(new ItemStack(DrinkItems.RED_WINE.get()));
		float cider = DrinkItems.pourPitch(new ItemStack(DrinkItems.HARD_CIDER.get()));
		float ale = DrinkItems.pourPitch(new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertTrue(wine > cider && cider > ale, "wine pours lighter than cider, cider than ale: " + wine + " " + cider + " " + ale);
		helper.assertValueEqual(DrinkItems.pourPitch(new ItemStack(Items.WATER_BUCKET)), 1.0F, "anything else as it is");
		helper.succeed();
	}

	/** Sneak-use on top of a block puts the mug or glass on the spot pointed at; four fit, each keeps its own item. */
	private static void placeUpToFour(GameTestHelper helper) {
		BlockPos table = new BlockPos(2, 1, 2);
		BlockPos spot = table.above();
		helper.setBlock(table, Blocks.OAK_PLANKS);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);
		ItemStack wine = new ItemStack(DrinkItems.AGED_RED_WINE.get(), 2);
		wine.set(DataComponents.CUSTOM_NAME, Component.literal("The good one"));
		player.setItemInHand(InteractionHand.MAIN_HAND, wine);
		helper.assertTrue(DrinkPlacing.place(sneakUse(helper, player, table, new Vec3(0.75, 1.0, 0.25))).consumesAction(), "placed");
		helper.assertTrue(helper.getBlockState(spot).is(BarrelRegistry.PLACED_DRINKS.get()), "a placed-drinks block");
		helper.assertValueEqual(helper.getBlockState(spot).getValue(PlacedDrinksBlock.SPOT.get(1)), PlacedDrink.GLASS_AGED, "north-east spot holds a glass of aged wine");
		helper.assertValueEqual(wine.getCount(), 1, "one used");
		// more onto the same block, pointing at the block itself
		ItemStack[] more = {new ItemStack(DrinkItems.MUG.get()), new ItemStack(DrinkItems.WHEAT_BEER.get()), new ItemStack(DrinkItems.WINE_GLASS.get())};
		Vec3[] where = {new Vec3(0.25, 0.1, 0.25), new Vec3(0.25, 0.1, 0.75), new Vec3(0.75, 0.1, 0.75)};
		for (int i = 0; i < more.length; i++) {
			player.setItemInHand(InteractionHand.MAIN_HAND, more[i]);
			helper.assertTrue(DrinkPlacing.place(sneakUse(helper, player, spot, where[i])).consumesAction(), "placed #" + (i + 2));
			helper.assertTrue(more[i].isEmpty(), "used #" + (i + 2));
		}
		BlockState state = helper.getBlockState(spot);
		helper.assertValueEqual(state.getValue(PlacedDrinksBlock.SPOT.get(0)), PlacedDrink.MUG, "spot 0");
		helper.assertValueEqual(state.getValue(PlacedDrinksBlock.SPOT.get(2)), PlacedDrink.MUG_FULL, "spot 2");
		helper.assertValueEqual(state.getValue(PlacedDrinksBlock.SPOT.get(3)), PlacedDrink.GLASS, "spot 3");
		ItemStack fifth = new ItemStack(DrinkItems.BARLEY_ALE.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, fifth);
		helper.assertFalse(DrinkPlacing.place(sneakUse(helper, player, spot, new Vec3(0.5, 0.1, 0.5))).consumesAction(), "a fifth doesn't fit");
		helper.assertValueEqual(fifth.getCount(), 1, "the fifth is kept");
		PlacedDrinksBlockEntity drinks = helper.getBlockEntity(spot, PlacedDrinksBlockEntity.class);
		ItemStack kept = drinks.getItem(1);
		helper.assertTrue(kept.is(DrinkItems.AGED_RED_WINE.get()) && kept.getHoverName().getString().equals("The good one"), "keeps its own item: " + kept);
		// not sneaking: drinking stays drinking (the item use isn't taken over)
		Player walker = helper.makeMockPlayer(GameType.SURVIVAL);
		walker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertTrue(walker.getMainHandItem().useOn(sneakUse(helper, walker, new BlockPos(0, 1, 0), new Vec3(0.5, 1, 0.5))) == InteractionResult.PASS, "no placing without sneaking");
		helper.succeed();
	}

	/** Right-click picks up the one pointed at; the block goes with the last one; breaking or losing the support drops all. */
	private static void pickUpAndDrops(GameTestHelper helper) {
		BlockPos table = new BlockPos(2, 1, 2);
		BlockPos spot = table.above();
		helper.setBlock(table, Blocks.OAK_PLANKS);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.RED_WINE.get()));
		DrinkPlacing.place(sneakUse(helper, player, table, new Vec3(0.25, 1.0, 0.25)));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		DrinkPlacing.place(sneakUse(helper, player, table, new Vec3(0.75, 1.0, 0.75)));
		player.setShiftKeyDown(false);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		BlockPos absolute = helper.absolutePos(spot);
		helper.useBlock(spot, player, new BlockHitResult(Vec3.atLowerCornerOf(absolute).add(0.8, 0.2, 0.8), Direction.UP, absolute, false));
		helper.assertValueEqual(count(player, DrinkItems.BARLEY_ALE.get()), 1, "picked up the ale pointed at");
		helper.assertValueEqual(helper.getBlockState(spot).getValue(PlacedDrinksBlock.SPOT.get(3)), PlacedDrink.NONE, "its spot is free");
		// holding a drink, a right-click drinks it rather than touching the table (placing always needs sneaking)
		BlockHitResult hit = new BlockHitResult(Vec3.atLowerCornerOf(absolute).add(0.8, 0.2, 0.8), Direction.UP, absolute, false);
		helper.assertTrue(helper.getBlockState(spot).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS,
			"a drink in hand passes to drinking");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.useBlock(spot, player, new BlockHitResult(Vec3.atLowerCornerOf(absolute).add(0.8, 0.2, 0.8), Direction.UP, absolute, false));
		helper.assertValueEqual(count(player, DrinkItems.RED_WINE.get()), 1, "then the nearest other one");
		helper.assertTrue(helper.getBlockState(spot).isAir(), "the block goes with the last drink");
		// breaking the table drops what stands on it
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.AGED_WHEAT_BEER.get()));
		DrinkPlacing.place(sneakUse(helper, player, table, new Vec3(0.5, 1.0, 0.5)));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.WINE_GLASS.get()));
		DrinkPlacing.place(sneakUse(helper, player, table, new Vec3(0.2, 1.0, 0.8)));
		helper.setBlock(table, Blocks.AIR);
		helper.assertTrue(helper.getBlockState(spot).isAir(), "no support, no drinks");
		int beers = 0;
		int glasses = 0;
		for (ItemEntity entity : dropsAround(helper, spot)) {
			if (entity.getItem().is(DrinkItems.AGED_WHEAT_BEER.get())) beers += entity.getItem().getCount();
			if (entity.getItem().is(DrinkItems.WINE_GLASS.get())) glasses += entity.getItem().getCount();
		}
		helper.assertValueEqual(beers, 1, "the beer dropped");
		helper.assertValueEqual(glasses, 1, "the glass dropped");
		helper.succeed();
	}

	// ------------------------------------------------------------------ review round: time, fire, explosions, edge paths

	/** The day clock, as {@code /time} and sleeping move it. */
	private static Holder<net.minecraft.world.clock.WorldClock> clock(GameTestHelper helper) {
		return helper.getLevel().dimensionType().defaultClock().orElseThrow();
	}

	/**
	 * Sleeping through the night and {@code /time add} move a barrel along; {@code /time set} backwards never un-ages
	 * it. All within one tick, and the clock is put back, so no other test sees the jumps.
	 */
	private static void agesWithSleepAndTime(GameTestHelper helper) {
		var clocks = helper.getLevel().getServer().clockManager();
		var clock = clock(helper);
		long before = clocks.getTotalTicks(clock);
		try {
			AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 2, 100);
			// a night slept: vanilla (and NeoForge's default sleep adjustment) moves the clock to the wake-up marker
			clocks.moveToTimeMarker(clock, net.minecraft.world.clock.ClockTimeMarkers.WAKE_UP_FROM_SLEEP);
			long slept = clocks.getTotalTicks(clock) - before;
			helper.assertTrue(slept > 0, "the night was skipped: " + slept);
			helper.assertValueEqual(barrel.age(), 100L + slept, "sleeping ages the barrel");
			tickBarrel(helper, POS);
			long afterSleep = barrel.age();
			// /time set back to before the night: no un-aging
			clocks.setTotalTicks(clock, before);
			tickBarrel(helper, POS);
			helper.assertValueEqual(barrel.age(), afterSleep, "setting the time back doesn't un-age it");
			// /time add a day: a day older
			clocks.addTicks(clock, FERMENT);
			helper.assertValueEqual(barrel.age(), afterSleep + FERMENT, "/time add ages it");
			tickBarrel(helper, POS);
			helper.assertValueEqual(barrel.stage(), BarrelStage.YOUNG, "a day later it is wine");
			helper.assertValueEqual(key((Component) args(BarrelStatus.of(barrel))[2]), "message.vineandbarrel.aging_barrel.aging", "the status line agrees");
			helper.succeed();
		} finally {
			clocks.setTotalTicks(clock, before);
		}
	}

	/** With the daylight cycle off the clock stands still, but game time goes on and so does the barrel. */
	private static void agesWithDaylightCycleOff(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		var rules = server.getGlobalGameRules();
		boolean was = rules.get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME);
		rules.set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, false, server);
		AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 2, 100);
		long clockBefore = server.clockManager().getTotalTicks(clock(helper));
		long gameBefore = helper.getLevel().getGameTime();
		helper.runAfterDelay(20, () -> {
			try {
				helper.assertValueEqual(server.clockManager().getTotalTicks(clock(helper)), clockBefore, "the clock stood still");
				long passed = helper.getLevel().getGameTime() - gameBefore;
				helper.assertTrue(passed >= 20, "game time went on");
				helper.assertValueEqual(barrel.age(), 100L + passed, "the barrel aged on game time");
			} finally {
				rules.set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, was, server);
			}
			helper.succeed();
		});
	}

	/** Decided: breaking in creative drops no barrel (like FD's cooking pot and the Brew Kettle); slot items still drop like a chest's. */
	private static void creativeBreak(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 4, 0);
		barrel.setStack(AgingBarrelBlockEntity.VESSEL_SLOT, new ItemStack(DrinkItems.WINE_GLASS.get(), 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.CREATIVE);
		player.gameMode.destroyBlock(helper.absolutePos(POS));
		helper.assertBlockNotPresent(BarrelRegistry.AGING_BARREL.get(), POS);
		int glasses = 0;
		for (ItemEntity entity : dropsAround(helper, POS)) {
			helper.assertFalse(entity.getItem().is(BarrelRegistry.AGING_BARREL_ITEM.get()), "no barrel item in creative");
			if (entity.getItem().is(DrinkItems.WINE_GLASS.get())) glasses += entity.getItem().getCount();
		}
		helper.assertValueEqual(glasses, 2, "slot items drop");
		player.discard();
		helper.succeed();
	}

	/** An explosion breaks it; whatever drops is never more than was there (a dropped barrel keeps its batch whole). */
	private static void explosion(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 5, 0);
		barrel.setStack(AgingBarrelBlockEntity.VESSEL_SLOT, new ItemStack(DrinkItems.WINE_GLASS.get(), 2));
		Vec3 at = helper.absoluteVec(Vec3.atCenterOf(POS));
		helper.getLevel().explode(null, at.x, at.y, at.z, 1.5F, Level.ExplosionInteraction.BLOCK);
		helper.assertBlockNotPresent(BarrelRegistry.AGING_BARREL.get(), POS);
		int barrels = 0;
		int glasses = 0;
		for (ItemEntity entity : dropsAround(helper, POS)) {
			ItemStack stack = entity.getItem();
			if (stack.is(BarrelRegistry.AGING_BARREL_ITEM.get())) {
				barrels += stack.getCount();
				helper.assertValueEqual(AgingBarrelItem.contentsOf(stack).servings(), 5, "a dropped barrel keeps its whole batch");
			} else if (stack.is(DrinkItems.WINE_GLASS.get())) {
				glasses += stack.getCount();
			}
		}
		helper.assertTrue(barrels <= 1, "at most one barrel: " + barrels);
		helper.assertTrue(glasses <= 2, "no glasses made up: " + glasses);
		helper.succeed();
	}

	/** Fire right next to it for a while: the barrel and its batch are still there. */
	private static void fireNeverBurns(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 3, 0);
		for (Direction side : List.of(Direction.EAST, Direction.WEST)) {
			helper.setBlock(POS.relative(side).below(), Blocks.NETHERRACK);
			helper.setBlock(POS.relative(side), Blocks.FIRE);
		}
		helper.runAfterDelay(240, () -> {
			helper.assertBlockPresent(BarrelRegistry.AGING_BARREL.get(), POS);
			helper.assertTrue(barrel == helper.getBlockEntity(POS, AgingBarrelBlockEntity.class), "the same barrel");
			helper.assertValueEqual(barrel.servings(), 3, "batch intact");
			helper.succeed();
		});
	}

	/** Through the block, with a full inventory: the drink drops at the player, nothing is lost. */
	private static void fullInventory(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = wineBarrel(helper, POS, 2, FERMENT);
		TestPlayer player = drinker(helper, new Vec3(2.5, 1.0, 0.5));
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.WINE_GLASS.get(), 2));
		helper.useBlock(POS, player);
		helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "one glass used");
		helper.assertValueEqual(barrel.servings(), 1, "one tapped");
		int wines = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3.0D))) {
			if (entity.getItem().is(DrinkItems.RED_WINE.get())) wines += entity.getItem().getCount();
		}
		helper.assertValueEqual(wines, 1, "the wine drops at the player");
		helper.succeed();
	}

	/** The fill gauge (block state) shows the servings at once, for every way they change. */
	private static void fillGauge(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = placeBarrel(helper, POS);
		pour(helper, barrel, BaseItems.GRAPE_JUICE_BUCKET.get(), 1);
		helper.assertValueEqual(helper.getBlockState(POS).getValue(AgingBarrelBlock.FILL), 4, "a bucket: 4");
		pour(helper, barrel, BaseItems.GRAPE_JUICE.get(), 3);
		helper.assertValueEqual(helper.getBlockState(POS).getValue(AgingBarrelBlock.FILL), 7, "7");
		barrel.takeBack(new ItemStack(Items.GLASS_BOTTLE), null);
		helper.assertValueEqual(helper.getBlockState(POS).getValue(AgingBarrelBlock.FILL), 6, "undo: 6");
		barrel.setAge(FERMENT);
		ItemStack glasses = new ItemStack(DrinkItems.WINE_GLASS.get(), 6);
		for (int i = 5; i >= 0; i--) {
			barrel.tap(glasses, null);
			helper.assertValueEqual(helper.getBlockState(POS).getValue(AgingBarrelBlock.FILL), i, "tapped down to " + i);
		}
		// placed from an item with a batch: the gauge shows it after the first tick
		AgingBarrelBlockEntity other = wineBarrel(helper, POS.east(), 5, 0);
		ItemStack item = new ItemStack(BarrelRegistry.AGING_BARREL_ITEM.get());
		item.set(BarrelRegistry.BARREL_CONTENTS.get(), other.contents());
		helper.setBlock(POS.west(), BarrelRegistry.AGING_BARREL.get());
		helper.getBlockEntity(POS.west(), AgingBarrelBlockEntity.class).applyComponentsFromItemStack(item);
		tickBarrel(helper, POS.west());
		helper.assertValueEqual(helper.getBlockState(POS.west()).getValue(AgingBarrelBlock.FILL), 5, "placed with 5");
		helper.succeed();
	}

	/**
	 * What a held item does at a barrel is one rule (the same on the client), so the client claims exactly the clicks
	 * the server takes: a drink, a potion, a water or milk bucket, a block all pass to the item's own use.
	 */
	private static void clickRules(GameTestHelper helper) {
		AgingBarrelBlockEntity empty = placeBarrel(helper, POS);
		AgingBarrelBlockEntity wine = wineBarrel(helper, POS.east(), 3, FERMENT);
		AgingBarrelBlockEntity juice = wineBarrel(helper, POS.west(), 3, 0);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		record Case(AgingBarrelBlockEntity barrel, ItemStack held, boolean claimed, String what) {
		}
		List<Case> cases = List.of(
			new Case(empty, new ItemStack(DrinkItems.RED_WINE.get()), false, "a drink at an empty barrel"),
			new Case(wine, new ItemStack(DrinkItems.BARLEY_ALE.get()), false, "a drink at a wine barrel"),
			new Case(empty, net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER), false, "a water bottle"),
			new Case(empty, new ItemStack(Items.WATER_BUCKET), false, "a water bucket"),
			new Case(wine, new ItemStack(Items.MILK_BUCKET), false, "a milk bucket"),
			new Case(empty, new ItemStack(Items.STONE), false, "a block"),
			new Case(empty, new ItemStack(Items.BUCKET), false, "an empty bucket at an empty barrel"),
			new Case(empty, new ItemStack(BaseItems.GRAPE_JUICE.get()), true, "a base into an empty barrel"),
			new Case(juice, new ItemStack(BaseItems.BARLEY_WORT_BOTTLE.get()), true, "another base: a hint"),
			new Case(juice, new ItemStack(Items.GLASS_BOTTLE), true, "undo"),
			new Case(wine, new ItemStack(DrinkItems.WINE_GLASS.get()), true, "tap"),
			new Case(wine, new ItemStack(Items.GLASS_BOTTLE), true, "a bottle at wine: a hint"));
		for (Case c : cases) {
			BlockState state = c.barrel().getBlockState();
			BlockHitResult at = new BlockHitResult(Vec3.atCenterOf(c.barrel().getBlockPos()), Direction.NORTH, c.barrel().getBlockPos(), false);
			InteractionResult result = state.useItemOn(c.held().copy(), helper.getLevel(), player, InteractionHand.MAIN_HAND, at);
			helper.assertValueEqual(result.consumesAction(), c.claimed(), c.what());
		}
		helper.succeed();
	}

	/** A data-pack drink without a vessel is rejected with a clear message; two recipes on one base: the lowest id, always. */
	private static void recipeValidation(GameTestHelper helper) {
		var ops = helper.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
		com.google.gson.JsonObject good = new com.google.gson.JsonObject();
		good.addProperty("base", "minecraft:apple");
		good.addProperty("young", "vineandbarrel:hard_cider");
		good.addProperty("aged", "vineandbarrel:aged_hard_cider");
		com.google.gson.JsonObject bad = good.deepCopy();
		bad.addProperty("young", "minecraft:stone");
		var goodResult = AgingRecipe.CODEC.codec().parse(ops, good);
		helper.assertTrue(goodResult.isSuccess(), "a valid recipe parses: " + goodResult);
		helper.assertValueEqual(goodResult.getOrThrow().fermentingTime(), FERMENT, "default fermenting time");
		helper.assertTrue(goodResult.getOrThrow().problem().isEmpty(), "and is usable");
		AgingRecipe badRecipe = AgingRecipe.CODEC.codec().parse(ops, bad).getOrThrow();
		helper.assertTrue(badRecipe.problem().isPresent() && badRecipe.problem().get().contains("use_remainder"), "a drink without a vessel: " + badRecipe.problem());
		List<String> reports = new ArrayList<>();
		var holders = List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(ResourceKey.create(Registries.RECIPE, VineAndBarrel.id("test/bad")), badRecipe),
			new net.minecraft.world.item.crafting.RecipeHolder<>(ResourceKey.create(Registries.RECIPE, VineAndBarrel.id("test/good")), goodResult.getOrThrow()));
		var usable = jexxme.vineandbarrel.barrel.AgingRecipes.usable(holders, reports::add);
		helper.assertValueEqual(usable.size(), 1, "the bad one is left out");
		helper.assertTrue(reports.size() == 1 && reports.get(0).contains("vineandbarrel:test/bad") && reports.get(0).contains("use_remainder"), "with a clear message: " + reports);
		// test data: aging/test_duplicate_a (ale) and _b (wheat beer) both start from sugar
		for (int i = 0; i < 3; i++) {
			AgingRecipe chosen = AgingBarrelBlockEntity.recipeFor(helper.getLevel(), new ItemStack(Items.SUGAR));
			helper.assertTrue(chosen != null && chosen.young().is(DrinkItems.BARLEY_ALE.get()), "the lowest id wins, every time");
		}
		helper.succeed();
	}

	/** A piston pushing into placed drinks pops them: every drink drops once, none twice. */
	private static void pistonPopsDrinks(GameTestHelper helper) {
		BlockPos table = new BlockPos(2, 1, 2);
		BlockPos spot = table.above();
		helper.setBlock(table, Blocks.OAK_PLANKS);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.RED_WINE.get()));
		DrinkPlacing.place(sneakUse(helper, player, table, new Vec3(0.25, 1.0, 0.25)));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.MUG.get()));
		DrinkPlacing.place(sneakUse(helper, player, table, new Vec3(0.75, 1.0, 0.75)));
		helper.assertTrue(helper.getBlockState(spot).is(BarrelRegistry.PLACED_DRINKS.get()), "two drinks on the table");
		helper.setBlock(spot.west().below(), Blocks.STONE);
		helper.setBlock(spot.west(), Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, Direction.EAST));
		helper.setBlock(spot.west().west(), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> {
			helper.assertFalse(helper.getBlockState(spot).is(BarrelRegistry.PLACED_DRINKS.get()), "popped");
			int wines = 0;
			int mugs = 0;
			for (ItemEntity entity : dropsAround(helper, spot)) {
				if (entity.getItem().is(DrinkItems.RED_WINE.get())) wines += entity.getItem().getCount();
				if (entity.getItem().is(DrinkItems.MUG.get())) mugs += entity.getItem().getCount();
			}
			helper.assertValueEqual(wines, 1, "the wine dropped once");
			helper.assertValueEqual(mugs, 1, "the mug dropped once");
		});
	}

	/** Like BlockItem: no drink into an entity standing there; and refusals pass, so sneak-drinking still works. */
	private static void placingRespectsEntities(GameTestHelper helper) {
		BlockPos table = new BlockPos(2, 1, 2);
		helper.setBlock(table, Blocks.OAK_PLANKS);
		helper.setBlock(table.east(), Blocks.OAK_PLANKS);
		var sheep = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.SHEEP, new Vec3(2.5, 2.0, 2.5));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);
		ItemStack wine = new ItemStack(DrinkItems.RED_WINE.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, wine);
		helper.assertTrue(DrinkPlacing.place(sneakUse(helper, player, table, new Vec3(0.5, 1.0, 0.5))) == InteractionResult.PASS, "a sheep is in the way: pass");
		helper.assertTrue(helper.getBlockState(table.above()).isAir(), "nothing placed");
		helper.assertValueEqual(wine.getCount(), 1, "nothing used");
		sheep.discard();
		// a side face, or no sturdy top: pass as well (sneak-drinking)
		BlockPos absolute = helper.absolutePos(table.east());
		UseOnContext side = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false));
		helper.assertTrue(DrinkPlacing.place(side) == InteractionResult.PASS, "side face: pass");
		placeBarrel(helper, new BlockPos(0, 1, 0));
		helper.assertTrue(DrinkPlacing.place(sneakUse(helper, player, new BlockPos(0, 1, 0), new Vec3(0.5, 1.0, 0.5))) == InteractionResult.PASS, "no sturdy top: pass");
		helper.succeed();
	}

	/**
	 * Farmer's Delight removed: a saved hard cider batch names {@code farmersdelight:apple_cider} as its base, which no
	 * longer exists. The drinks and servings survive (in the codec, a placed barrel's save and a barrel item); only the
	 * undo is gone. An unfermented batch keeps fermenting into its drink.
	 */
	private static void survivesMissingBase(GameTestHelper helper) {
		var ops = helper.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
		// Stands in for farmersdelight:apple_cider after FD was removed; an id no run ever has, so this works with FD too
		String gone = "removedmod:apple_cider";
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(Identifier.parse(gone)).isEmpty(), "the stand-in base must not exist");
		for (long elapsed : new long[]{FERMENT + AGE + 5L, 10L}) {
			BarrelContents saved = new BarrelContents(new ItemStack(Items.APPLE), ItemStack.EMPTY, new ItemStack(DrinkItems.HARD_CIDER.get()),
				new ItemStack(DrinkItems.AGED_HARD_CIDER.get()), 6, elapsed, FERMENT, AGE);
			net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag) BarrelContents.CODEC.encodeStart(ops, saved).getOrThrow();
			tag.getCompound("base").orElseThrow().putString("id", gone);
			BarrelContents loaded = BarrelContents.CODEC.parse(ops, tag).getOrThrow();
			helper.assertTrue(loaded.base().isEmpty(), "the missing base is dropped");
			helper.assertValueEqual(loaded.servings(), 6, "servings survive");
			helper.assertTrue(loaded.young().is(DrinkItems.HARD_CIDER.get()) && loaded.aged().is(DrinkItems.AGED_HARD_CIDER.get()), "the drinks survive");
			helper.assertValueEqual(loaded.elapsed(), elapsed, "its age survives");

			// a barrel item (B12): the whole stack still loads
			ItemStack item = new ItemStack(BarrelRegistry.AGING_BARREL_ITEM.get());
			item.set(BarrelRegistry.BARREL_CONTENTS.get(), saved);
			net.minecraft.nbt.CompoundTag itemTag = (net.minecraft.nbt.CompoundTag) ItemStack.CODEC.encodeStart(ops, item).getOrThrow();
			itemTag.getCompound("components").orElseThrow().getCompound("vineandbarrel:barrel_contents").orElseThrow()
				.getCompound("base").orElseThrow().putString("id", gone);
			ItemStack loadedItem = ItemStack.CODEC.parse(ops, itemTag).getOrThrow();
			helper.assertValueEqual(AgingBarrelItem.contentsOf(loadedItem).servings(), 6, "the barrel item keeps its servings");

			// a placed barrel's save
			BlockPos pos = elapsed > FERMENT ? POS : POS.east();
			AgingBarrelBlockEntity barrel = placeBarrel(helper, pos);
			barrel.applyComponentsFromItemStack(item);
			net.minecraft.nbt.CompoundTag be = barrel.saveWithFullMetadata(helper.getLevel().registryAccess());
			be.getCompound("Batch").orElseThrow().getCompound("base").orElseThrow().putString("id", gone);
			barrel.loadWithComponents(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), be));
			helper.assertValueEqual(barrel.servings(), 6, "the placed barrel keeps its servings");
			if (elapsed > FERMENT) {
				helper.assertTrue(barrel.content().is(DrinkItems.AGED_HARD_CIDER.get()), "still aged hard cider");
			} else {
				helper.assertValueEqual(barrel.stage(), BarrelStage.FERMENTING, "still fermenting");
				helper.assertValueEqual(barrel.undoServings(new ItemStack(Items.GLASS_BOTTLE)), 0, "no undo without its base");
				barrel.setAge(FERMENT);
				helper.assertTrue(barrel.tap(new ItemStack(DrinkItems.MUG.get()), null).is(DrinkItems.HARD_CIDER.get()), "it ferments into hard cider");
				helper.assertValueEqual(barrel.servings(), 5, "and taps");
			}
		}
		helper.succeed();
	}
}
