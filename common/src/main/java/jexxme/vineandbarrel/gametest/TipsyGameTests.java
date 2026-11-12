package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.platform.ConfigValue;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.BlackoutSpots;
import jexxme.vineandbarrel.tipsy.DrinkBuffs;
import jexxme.vineandbarrel.tipsy.Rowdy;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.TipsyEvents;
import jexxme.vineandbarrel.tipsy.TipsyRegistry;
import jexxme.vineandbarrel.tipsy.TipsySlur;
import jexxme.vineandbarrel.tipsy.TipsyInfo;
import jexxme.vineandbarrel.tipsy.TipsyLegs;
import jexxme.vineandbarrel.tipsy.TipsyLevels;
import jexxme.vineandbarrel.tipsy.TipsyMotion;
import jexxme.vineandbarrel.tipsy.TipsyVision;
import jexxme.vineandbarrel.tipsy.TipsyWobble;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * M4 Tipsy: stacking, cap, decay, the level-5 effects, every cure, non-alcoholic mode, slurred chat, stumbling and
 * the wobble math. Headless ({@code ./gradlew runGameTestServer}).
 * <p>
 * Config switches are flipped and restored inside one synchronous test function, so tests running in the same batch
 * never see each other's settings.
 */
public final class TipsyGameTests
{
	private static final Identifier STRUCTURE = VineAndBarrel.id("gametest/empty_5x5");
	private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, VineAndBarrel.MODID);
	private static final List<ResourceKey<Consumer<GameTestHelper>>> TESTS = new ArrayList<>();
	private static final List<ResourceKey<Consumer<GameTestHelper>>> LEGS_TESTS = new ArrayList<>();

	static {
		test("tipsy_stacks_to_cap", TipsyGameTests::stacksToCap);
		test("tipsy_cap_is_configurable", TipsyGameTests::capIsConfigurable);
		test("tipsy_decays_one_level_per_interval", TipsyGameTests::decaysOneLevelPerInterval);
		test("tipsy_level5_slowness_and_fatigue", TipsyGameTests::level5SlownessAndFatigue);
		test("tipsy_level5_keeps_foreign_slowness", TipsyGameTests::level5KeepsForeignSlowness);
		test("tipsy_sober_keeps_command_slowness", TipsyGameTests::soberKeepsCommandSlowness);
		test("tipsy_milk_clears", TipsyGameTests::milkClears);
		test("tipsy_water_lowers_by_one", TipsyGameTests::waterLowersByOne);
		test("tipsy_sobering_tag_rules", TipsyGameTests::soberingTagRules);
		test("tipsy_sleep_clears", TipsyGameTests::sleepClears);
		test("tipsy_non_alcoholic_mode", TipsyGameTests::nonAlcoholicMode);
		test("tipsy_slur_deterministic", TipsyGameTests::slurDeterministic);
		test("tipsy_slur_level_and_config", TipsyGameTests::slurLevelAndConfig);
		test("tipsy_stumble", TipsyGameTests::stumble);
		test("tipsy_stumble_config_off", TipsyGameTests::stumbleConfigOff);
		test("tipsy_hiccups", TipsyGameTests::hiccups);
		test("tipsy_wobble_math", TipsyGameTests::wobbleMath);
		test("tipsy_vision_math", TipsyGameTests::visionMath);
		test("tipsy_effect_tooltip", TipsyGameTests::effectTooltip);
		test("tipsy_rowdy_swings_from_four", TipsyGameTests::rowdyRates);
		test("tipsy_rowdy_punches_what_is_in_front", TipsyGameTests::rowdyPunches);
		test("tipsy_rowdy_spares_pets_and_golems", TipsyGameTests::rowdySpares);
		test("tipsy_rowdy_cut_off_at_the_bar", TipsyGameTests::rowdyCutOff);
		test("tipsy_rowdy_cut_off_by_a_real_click", TipsyGameTests::rowdyCutOffClick);
		loaderTest("tipsy_server_config_per_world");
		test("tipsy_blackout_one_too_many", TipsyGameTests::blackoutOneTooMany);
		test("tipsy_blackout_config_off", TipsyGameTests::blackoutConfigOff);
		test("tipsy_blackout_never_onto_the_nether_roof", TipsyGameTests::blackoutNoNetherRoof);
		test("tipsy_too_drunk_for_buffs", TipsyGameTests::tooDrunkForBuffs);
		test("tipsy_too_drunk_keeps_absorption_hearts", TipsyGameTests::tooDrunkKeepsAbsorptionHearts);
		test("tipsy_grape_juice_lowers_by_one", TipsyGameTests::grapeJuiceLowersByOne);
		test("tipsy_courage_per_level", TipsyGameTests::couragePerLevel);
		test("tipsy_weakness_from_level4", TipsyGameTests::weaknessFromLevel4);
		test("tipsy_nausea_pulses_from_level3", TipsyGameTests::nauseaPulses);
		test("tipsy_keeps_foreign_side_effects", TipsyGameTests::keepsForeignSideEffects);
		test("tipsy_keeps_foreign_effect_under_its_own", TipsyGameTests::keepsForeignEffectUnderItsOwn);
		test("tipsy_motion_math", TipsyGameTests::motionMath);
		test("tipsy_blackout_damage_rules", TipsyGameTests::blackoutDamageRules);
		test("tipsy_blackout_pauses_tipsy", TipsyGameTests::blackoutPausesTipsy);
		test("tipsy_drink_buffs_forget_the_old_ones", TipsyGameTests::drinkBuffsForgetTheOldOnes);
		test("tipsy_non_alcoholic_mid_game", TipsyGameTests::nonAlcoholicMidGame);
		legsTest("tipsy_trip", TipsyGameTests::trip);
		legsTest("tipsy_trip_only_where_safe", TipsyGameTests::tripOnlyWhereSafe);
		legsTest("tipsy_trip_never_sliding", TipsyGameTests::tripNeverSliding);
		test("tipsy_drink_buffs_through_cheers_beacons_and_relogs", TipsyGameTests::drinkBuffsThroughCheersBeaconsAndRelogs);
	}

	private TipsyGameTests() {
	}

	private static void test(String name, Consumer<GameTestHelper> function) {
		FUNCTIONS.register(name, () -> function);
		TESTS.add(ResourceKey.create(Registries.TEST_FUNCTION, VineAndBarrel.id(name)));
	}

	/** A test only one loader has (it uses that loader's own API), registered under the same name where it exists. */
	private static void loaderTest(String name) {
		Consumer<GameTestHelper> function = jexxme.vineandbarrel.platform.Services.PLATFORM.loaderGameTest(name);
		if (function != null) {
			test(name, function);
		}
	}

	/** A trip test: in a batch of its own, so no other test's monster stands near (a trip never happens then). */
	private static void legsTest(String name, Consumer<GameTestHelper> function) {
		FUNCTIONS.register(name, () -> function);
		LEGS_TESTS.add(ResourceKey.create(Registries.TEST_FUNCTION, VineAndBarrel.id(name)));
	}

	public static void register() {
		FUNCTIONS.register();
		jexxme.vineandbarrel.platform.Services.PLATFORM.addGameTests(TipsyGameTests::registerTests);
	}

	private static void registerTests(GameTestRegistrar event) {
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(VineAndBarrel.id("tipsy"));
		for (ResourceKey<Consumer<GameTestHelper>> function : TESTS) {
			event.registerTest(function.identifier(), new FunctionGameTestInstance(function, new TestData<>(environment, STRUCTURE, 100, 0, true)));
		}
		Holder<TestEnvironmentDefinition<?>> legs = event.registerEnvironment(VineAndBarrel.id("tipsy_legs"));
		for (ResourceKey<Consumer<GameTestHelper>> function : LEGS_TESTS) {
			event.registerTest(function.identifier(), new FunctionGameTestInstance(function, new TestData<>(legs, STRUCTURE, 100, 0, true)));
		}
	}

	// ------------------------------------------------------------------ the mock player

	/**
	 * A mock server player with a connection to nowhere (an embedded channel, as vanilla's own in-level mock uses).
	 * Vanilla's plain mock has no connection, and a ServerPlayer sends every effect change through it. It is not
	 * added to the level, so tests drive its effect ticks themselves with {@link #tickEffectsOnce()}.
	 */
	static final class TestPlayer extends ServerPlayer
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

		/** Vanilla's in-level mock is never a spectator, so the spectator case sets this. */
		boolean spectator;

		@Override
		public boolean isSpectator() {
			return spectator;
		}

		/** One tick of vanilla's effect loop (durations, hidden-effect downgrades, our effect tick). */
		void tickEffectsOnce() {
			this.tickEffects();
		}

		/** Drinks what is in the main hand exactly the way the game finishes using an item. */
		void drinkMainHand() {
			this.startUsingItem(InteractionHand.MAIN_HAND);
			this.completeUsingItem();
		}
	}

	static TestPlayer player(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "tipsy-test-player"), false);
		TestPlayer player = new TestPlayer(level.getServer(), level, cookie.gameProfile());
		GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie);
		Vec3 pos = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.setPos(pos.x, pos.y, pos.z);
		return player;
	}

	/**
	 * Runs {@code body} with a config value temporarily at {@code setting}, also when an assertion fails. In memory
	 * only ({@link ConfigValue#setForTest}): the config file is never written.
	 */
	private static <T> void with(ConfigValue<T> value, T setting, Runnable body) {
		value.setForTest(setting);
		try {
			body.run();
		} finally {
			value.setForTest(null);
		}
	}

	static void drink(TestPlayer player, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.drinkMainHand();
	}

	// ------------------------------------------------------------------ stacking and decay

	/** Each drink adds exactly one level, up to 5 by default, and never past it. */
	private static void stacksToCap(GameTestHelper helper) {
		TestPlayer player = player(helper);
		helper.assertValueEqual(Tipsy.getLevel(player), 0, "level before drinking");
		with(TipsyConfig.MAX_LEVEL, 5, () -> {
			for (int i = 1; i <= 5; i++) {
				helper.assertTrue(Tipsy.addLevel(player), "addLevel should act while Tipsy is on");
				helper.assertValueEqual(Tipsy.getLevel(player), i, "level after drink " + i);
			}
			Tipsy.addLevel(player);
			Tipsy.addLevel(player);
		});
		helper.assertValueEqual(Tipsy.getLevel(player), 5, "level after 7 drinks (cap)");
		helper.succeed();
	}

	/** The server's max level caps stacking. */
	private static void capIsConfigurable(GameTestHelper helper) {
		TestPlayer player = player(helper);
		with(TipsyConfig.MAX_LEVEL, 3, () -> {
			for (int i = 0; i < 6; i++) Tipsy.addLevel(player);
		});
		helper.assertValueEqual(Tipsy.getLevel(player), 3, "level with maxLevel = 3");
		helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), "Level 3 must not slow you down");
		helper.succeed();
	}

	/** Each level lasts exactly secondsPerLevel, then drops by one, until Tipsy is gone. */
	private static void decaysOneLevelPerInterval(GameTestHelper helper) {
		TestPlayer player = player(helper);
		int perLevel = 2 * 20;
		with(TipsyConfig.SECONDS_PER_LEVEL, 2, () -> {
			Tipsy.addLevel(player);
			Tipsy.addLevel(player);
		});
		helper.assertValueEqual(Tipsy.getLevel(player), 2, "level after two drinks");
		helper.assertValueEqual(player.getEffect(TipsyRegistry.TIPSY).getDuration(), perLevel, "timer shown for the top level");

		tick(player, perLevel - 1);
		helper.assertValueEqual(Tipsy.getLevel(player), 2, "level one tick before the first drop");
		tick(player, 1);
		helper.assertValueEqual(Tipsy.getLevel(player), 1, "level right after " + perLevel + " ticks");
		helper.assertValueEqual(player.getEffect(TipsyRegistry.TIPSY).getDuration(), perLevel, "level 1 gets a full interval");
		tick(player, perLevel - 1);
		helper.assertValueEqual(Tipsy.getLevel(player), 1, "level one tick before sobering up");
		tick(player, 1);
		helper.assertValueEqual(Tipsy.getLevel(player), 0, "level after " + 2 * perLevel + " ticks");
		helper.succeed();
	}

	private static void tick(TestPlayer player, int ticks) {
		for (int i = 0; i < ticks; i++) player.tickEffectsOnce();
	}

	/** Slowness II and Mining Fatigue I come with level 5 and leave as soon as it drops below. */
	private static void level5SlownessAndFatigue(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 4);
		helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), "No Slowness at level 4");
		helper.assertFalse(player.hasEffect(MobEffects.MINING_FATIGUE), "No Mining Fatigue at level 4");

		Tipsy.addLevel(player);
		helper.assertValueEqual(Tipsy.getLevel(player), 5, "level");
		MobEffectInstance slowness = player.getEffect(MobEffects.SLOWNESS);
		MobEffectInstance fatigue = player.getEffect(MobEffects.MINING_FATIGUE);
		helper.assertTrue(slowness != null && slowness.getAmplifier() == 1, "Level 5 gives Slowness II: " + slowness);
		helper.assertTrue(fatigue != null && fatigue.getAmplifier() == 0, "Level 5 gives Mining Fatigue I: " + fatigue);

		// They are refreshed for as long as level 5 lasts, well past their own short duration
		for (int i = 0; i < 200; i++) {
			player.tickEffectsOnce();
			TipsyEvents.onEntityTick(player);
		}
		helper.assertValueEqual(Tipsy.getLevel(player), 5, "level after 10 s");
		helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "Slowness still there while at level 5");
		helper.assertTrue(player.hasEffect(MobEffects.MINING_FATIGUE), "Mining Fatigue still there while at level 5");

		Tipsy.lowerLevel(player);
		helper.assertValueEqual(Tipsy.getLevel(player), 4, "level after one sobering drink");
		helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), "Slowness gone below level 5");
		helper.assertFalse(player.hasEffect(MobEffects.MINING_FATIGUE), "Mining Fatigue gone below level 5");
		helper.succeed();
	}

	/**
	 * Dropping below 5 only takes away Tipsy's own Slowness, never one from a potion. A Slowness I potion drunk at
	 * level 5 waits under Tipsy's Slowness II (vanilla's hidden effect) and takes over within seconds.
	 */
	private static void level5KeepsForeignSlowness(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 1800, 0));
		Tipsy.lowerLevel(player);
		tick(player, 61);
		MobEffectInstance slowness = player.getEffect(MobEffects.SLOWNESS);
		helper.assertTrue(slowness != null && slowness.getAmplifier() == 0 && slowness.getDuration() > 1000, "A Slowness potion must survive Tipsy dropping below 5: " + slowness);
		helper.assertFalse(player.hasEffect(MobEffects.MINING_FATIGUE), "Tipsy's own Mining Fatigue is gone");
		helper.succeed();
	}

	/** A short particleless Slowness I from a command ({@code /effect give @s slowness 3 0 true}) is not Tipsy's to take. */
	private static void soberKeepsCommandSlowness(GameTestHelper helper) {
		TestPlayer player = player(helper);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, false, false, true));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "A sober player keeps a command's Slowness");
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ cures

	/** Drinking milk clears Tipsy and the level-5 effects, through vanilla's own milk mechanism. */
	private static void milkClears(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		drink(player, new ItemStack(Items.MILK_BUCKET));
		helper.assertValueEqual(Tipsy.getLevel(player), 0, "level after milk");
		helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), "Milk clears Slowness too");
		helper.assertFalse(player.hasEffect(MobEffects.MINING_FATIGUE), "Milk clears Mining Fatigue too");
		helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "The empty bucket comes back");
		helper.succeed();
	}

	/** A water bottle lowers Tipsy by exactly one, and the level below keeps the time it had left. */
	private static void waterLowersByOne(GameTestHelper helper) {
		TestPlayer player = player(helper);
		int perLevel = TipsyConfig.ticksPerLevel();
		Tipsy.setLevel(player, 3);
		drink(player, PotionContents.createItemStack(Items.POTION, Potions.WATER));
		helper.assertValueEqual(Tipsy.getLevel(player), 2, "level after a water bottle");
		helper.assertValueEqual(player.getEffect(TipsyRegistry.TIPSY).getDuration(), 2 * perLevel, "time left on level 2");
		helper.assertTrue(player.getMainHandItem().is(Items.GLASS_BOTTLE), "The glass bottle comes back");

		drink(player, PotionContents.createItemStack(Items.POTION, Potions.WATER));
		drink(player, PotionContents.createItemStack(Items.POTION, Potions.WATER));
		helper.assertValueEqual(Tipsy.getLevel(player), 0, "level after three water bottles");
		drink(player, PotionContents.createItemStack(Items.POTION, Potions.WATER));
		helper.assertValueEqual(Tipsy.getLevel(player), 0, "water when sober changes nothing");
		helper.succeed();
	}

	/** Only plain water counts among potions; the tag is the hook for M2's grape juice. */
	/** Cross-milestone (M2 + M4): M2's grape juice is a sobering drink, so drinking it lowers Tipsy by exactly one. */
	private static void grapeJuiceLowersByOne(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 3);
		drink(player, new ItemStack(BaseItems.GRAPE_JUICE.get()));
		helper.assertValueEqual(Tipsy.getLevel(player), 2, "level after grape juice");
		helper.assertTrue(player.getMainHandItem().is(Items.GLASS_BOTTLE), "The glass bottle comes back");
		// Every juice takes the edge off, one level each (Jexxme, new drinks); a wort or a must is not drunk at all
		for (Item juice : List.of(BaseItems.WHITE_GRAPE_JUICE.get(), BaseItems.SWEET_BERRY_JUICE.get(), BaseItems.APPLE_JUICE.get())) {
			helper.assertTrue(Tipsy.isSoberingDrink(new ItemStack(juice)), juice + " sobers you up");
			int before = Tipsy.getLevel(player);
			drink(player, new ItemStack(juice));
			helper.assertValueEqual(Tipsy.getLevel(player), Math.max(0, before - 1), "level after " + juice);
		}
		helper.assertValueEqual(Tipsy.getLevel(player), 0, "four juices from level 3 leave you sober, and no lower");
		helper.assertFalse(Tipsy.isSoberingDrink(new ItemStack(BaseItems.HONEY_MUST_BOTTLE.get())), "Honey must is not a juice");
		helper.succeed();
	}

	private static void soberingTagRules(GameTestHelper helper) {
		helper.assertTrue(Tipsy.isSoberingDrink(PotionContents.createItemStack(Items.POTION, Potions.WATER)), "Water bottle sobers");
		helper.assertFalse(Tipsy.isSoberingDrink(PotionContents.createItemStack(Items.POTION, Potions.AWKWARD)), "An awkward potion is not water");
		helper.assertFalse(Tipsy.isSoberingDrink(PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS)), "A potion of swiftness is not water");
		helper.assertFalse(Tipsy.isSoberingDrink(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER)), "A splash bottle isn't drunk");
		helper.assertFalse(Tipsy.isSoberingDrink(new ItemStack(Items.MILK_BUCKET)), "Milk clears via vanilla, it is not in the tag");

		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 2);
		drink(player, PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
		helper.assertValueEqual(Tipsy.getLevel(player), 2, "level after an awkward potion");
		helper.succeed();
	}

	/** T2: sleeping through the night clears Tipsy; getting up early does not. */
	private static void sleepClears(GameTestHelper helper) {
		BlockPos foot = new BlockPos(2, 1, 1);
		BlockPos head = foot.south();
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH).setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		helper.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH).setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 4);

		player.startSleeping(helper.absolutePos(head));
		helper.assertTrue(player.isSleeping(), "Player should be in bed");
		player.stopSleepInBed(true, true); // "Leave Bed" in the middle of the night
		helper.assertValueEqual(Tipsy.getLevel(player), 4, "level after leaving the bed early");

		player.startSleeping(helper.absolutePos(head));
		// What ServerLevel#wakeUpAllPlayers calls once the night has been slept through
		player.stopSleepInBed(false, false);
		helper.assertFalse(player.isSleeping(), "Player should be awake");
		helper.assertValueEqual(Tipsy.getLevel(player), 0, "level after sleeping through the night");
		helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), "No Slowness after sleeping");
		helper.succeed();
	}

	/** Non-alcoholic mode: drinks never make you tipsy. */
	private static void nonAlcoholicMode(GameTestHelper helper) {
		TestPlayer player = player(helper);
		with(TipsyConfig.ENABLED, false, () -> {
			for (int i = 0; i < 5; i++) {
				helper.assertFalse(Tipsy.addLevel(player), "addLevel must be a no-op in non-alcoholic mode");
			}
			helper.assertValueEqual(Tipsy.getLevel(player), 0, "level in non-alcoholic mode");
			helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), "No Slowness in non-alcoholic mode");
		});
		helper.assertTrue(Tipsy.addLevel(player), "addLevel works again with Tipsy on");
		helper.assertValueEqual(Tipsy.getLevel(player), 1, "level with Tipsy back on");
		helper.succeed();
	}

	// ------------------------------------------------------------------ level 4: slurred chat

	/**
	 * The slur is a pure function: same message and seed, same result (golden values pin it), and it always changes a
	 * message that fits (when no letter changes, by a trailing *hic*).
	 */
	private static void slurDeterministic(GameTestHelper helper) {
		String message = "I love this song so much, cheers everyone";
		helper.assertValueEqual(TipsySlur.slur(message, 1L), "I looove thiiis song shooo much, cheersh everyone", "slur, seed 1");
		helper.assertValueEqual(TipsySlur.slur(message, 42L), "I love this shong so much, cheeeers eveeeryone", "slur, seed 42");
		helper.assertValueEqual(TipsySlur.slur(message, 7L), "I love this sooong *hic* sho much, cheers everyone", "slur, seed 7");
		helper.assertValueEqual(TipsySlur.slur(message, 42L), TipsySlur.slur(message, 42L), "same seed twice");

		Set<String> outputs = new HashSet<>();
		for (long seed = 0; seed < 20; seed++) {
			String slurred = TipsySlur.slur(message, seed);
			helper.assertFalse(slurred.equals(message), "A slurred message always differs (seed " + seed + ")");
			helper.assertTrue(slurred.length() <= TipsySlur.MAX_LENGTH, "Slurred chat stays within the chat limit");
			outputs.add(slurred);
		}
		helper.assertTrue(outputs.size() > 10, "Different seeds slur differently");

		helper.assertValueEqual(TipsySlur.slur("go to https://example.com and /home now", 3L), "go *hic* tooo https://example.com and /home nooow", "links and commands stay intact");
		helper.assertValueEqual(TipsySlur.slur("   ", 3L), "   ", "blank stays blank");
		String longMessage = "s".repeat(TipsySlur.MAX_LENGTH);
		helper.assertValueEqual(TipsySlur.slur(longMessage, 3L), longMessage, "a full-length message is left alone");
		// When no letter happens to change, the slur still shows: a trailing *hic* (about 1 seed in 12 for this message,
		// seed 7 among them). The smoke test's chat check once took such a line for an unslurred one.
		String toast = "cheers to the cellar";
		helper.assertValueEqual(TipsySlur.slur(toast, 7L), toast + " " + TipsySlur.HIC, "no letter changed: a trailing *hic*");
		for (long seed = 0; seed < 2000; seed++) {
			helper.assertFalse(TipsySlur.slur(toast, seed).equals(toast), "a slurred message never comes back as typed (seed " + seed + ")");
		}
		helper.succeed();
	}

	/** Chat slurs from level 3 (peak drunk) on, and never with the server's slurred-chat switch off. */
	private static void slurLevelAndConfig(GameTestHelper helper) {
		TestPlayer player = player(helper);
		String message = "hello friends";
		helper.assertValueEqual(Tipsy.SLUR_LEVEL, 3, "slurring starts at III");
		Tipsy.setLevel(player, 2);
		helper.assertValueEqual(Tipsy.slurChat(player, message, 9L), message, "level 2 chat");
		Tipsy.setLevel(player, 3);
		helper.assertValueEqual(Tipsy.slurChat(player, message, 9L), TipsySlur.slur(message, 9L), "level 3 chat");
		// through the server's real chat path, as a typed message goes
		String decorated = jexxme.vineandbarrel.platform.Services.PLATFORM.decorateChat(player, Component.literal(message)).getString();
		helper.assertFalse(decorated.equals(message), "level 3 chat through the server's chat decorator is slurred: " + decorated);
		with(TipsyConfig.SLURRED_CHAT, false, () -> {
			Tipsy.setLevel(player, 5);
			helper.assertValueEqual(Tipsy.slurChat(player, message, 9L), message, "level 5 chat with slurred chat off");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ level 5: stumbling, level 3: hiccups

	/** At level 5 a stumble lurches sideways, at right angles to walking; below 5 or standing still it does nothing. */
	private static void stumble(GameTestHelper helper) {
		// Ground to step onto on both sides (a stumble never lurches over an edge)
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		TestPlayer player = player(helper);
		player.setOnGround(true);
		Vec3 walkingEast = new Vec3(0.2, 0.0, 0.0);
		RandomSource random = RandomSource.create(1L);

		Tipsy.setLevel(player, 4);
		helper.assertFalse(TipsyLegs.stumble(player, walkingEast, random), "No stumbling at level 4");
		Tipsy.setLevel(player, 5);
		helper.assertFalse(TipsyLegs.stumble(player, Vec3.ZERO, random), "No stumbling while standing still");
		helper.assertValueEqual(TipsyLegs.walkingMotion(player), Vec3.ZERO, "A player who isn't moving isn't walking");

		player.setDeltaMovement(Vec3.ZERO);
		int before = motion(player).stumbles();
		helper.assertTrue(TipsyLegs.stumble(player, walkingEast, random), "Level 5 walking stumbles");
		Vec3 nudge = player.getDeltaMovement();
		helper.assertTrue(Math.abs(nudge.x) < 1.0E-6 && Math.abs(nudge.y) < 1.0E-6, "The lurch is purely sideways, got " + nudge);
		helper.assertTrue(Math.abs(Math.abs(nudge.z) - TipsyLegs.STUMBLE_NUDGE) < 1.0E-6, "The lurch is about a step, got " + nudge);
		helper.assertTrue(player.hurtMarked, "The lurch is sent to the player's client");
		helper.assertValueEqual(motion(player).stumbles(), before + 1, "every client is told to play the stumble");
		helper.assertValueEqual(motion(player).stumbleLeft(), nudge.z > 0, "to the side it lurched");
		helper.succeed();
	}

	private static TipsyMotion.Counts motion(TestPlayer player) {
		TipsyMotion.Counts counts = TipsyRegistry.MOTION.getExisting(player);
		return counts == null ? TipsyMotion.Counts.NONE : counts;
	}

	/** With stumbling switched off on the server, level 5 never lurches or trips anyone. */
	private static void stumbleConfigOff(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		player.setDeltaMovement(Vec3.ZERO);
		RandomSource random = RandomSource.create(2L);
		with(TipsyConfig.STUMBLING, false, () -> {
			for (int i = 0; i < 50; i++) {
				helper.assertFalse(TipsyLegs.stumble(player, new Vec3(0.2, 0.0, 0.1), random), "Stumbling is off");
				helper.assertFalse(TipsyLegs.trip(player), "Tripping is off");
			}
		});
		helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "motion with stumbling off");
		helper.succeed();
	}

	/**
	 * Hiccups start at II, rare there and more often from III; they come in bouts (a few, 2-4 s apart, then quiet). A
	 * hiccup moves nothing and hurts nobody: every client just plays its jolt.
	 */
	private static void hiccups(GameTestHelper helper) {
		RandomSource random = RandomSource.create(3L);
		for (int i = 0; i < 24000; i++) {
			helper.assertFalse(Tipsy.rollsHiccupBout(0, random) || Tipsy.rollsHiccupBout(1, random), "No hiccups below II");
		}
		helper.assertValueEqual(Tipsy.HICCUP_LEVEL, 2, "hiccups start at II");
		int[] count = new int[6];
		int[] closeOnes = new int[6];
		for (int level = 2; level <= 3; level++) {
			TestPlayer player = player(helper);
			player.getRandom().setSeed(42L);
			Tipsy.setLevel(player, level);
			int last = -1;
			int seen = 0;
			for (int tick = 0; tick < 36000; tick++) {
				TipsyEvents.onEntityTick(player);
				int now = motion(player).hiccups();
				if (now != seen) {
					seen = now;
					count[level]++;
					if (last >= 0) {
						int gap = tick - last;
						helper.assertTrue(gap > Tipsy.HICCUP_GAP_MIN, "hiccups are at least 2 s apart, got " + gap);
						closeOnes[level] += gap <= Tipsy.HICCUP_GAP_MAX + 1 ? 1 : 0;
					}
					last = tick;
				}
			}
		}
		// 30 min: about one every 20-25 s at II (~75), one every 13 s at III (~130). Both players roll with the same seed,
		// so the comparison is the same every run, and it is what the game does, not the table read back
		helper.assertTrue(count[2] > 30 && count[2] < 160, "hiccups at II are rare: " + count[2] + " in 30 min");
		helper.assertTrue(count[3] > 60 && count[3] < 260, "hiccups at III: " + count[3] + " in 30 min");
		helper.assertTrue(count[3] > count[2] * 1.3, "clearly more at III: " + count[3] + " against " + count[2]);
		// In bouts: most hiccups come a few seconds after the one before
		helper.assertTrue(closeOnes[3] > count[3] / 2, "hiccups come in bouts: " + closeOnes[3] + " of " + count[3] + " close together");

		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 3);
		float health = player.getHealth();
		player.setOnGround(true);
		player.setDeltaMovement(Vec3.ZERO);
		int before = motion(player).hiccups();
		Tipsy.hiccup(player, 0);
		helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "A hiccup moves nothing (no hop any more)");
		helper.assertValueEqual(player.getHealth(), health, "health after a hiccup (harmless)");
		helper.assertValueEqual(motion(player).hiccups(), before + 1, "every client is told to play the jolt");
		helper.succeed();
	}

	// ------------------------------------------------------------------ playtest 1: courage and the downside ramp

	private static final double EPS = 1.0E-6;

	/** "Liquid courage": knockback resistance and less fall damage from I, the most at III, none from IV on. */
	private static void couragePerLevel(GameTestHelper helper) {
		TestPlayer player = player(helper);
		double[] knockback = {0.0, 0.10, 0.20, 0.30, 0.0, 0.0};
		double[] fall = {1.0, 0.85, 0.75, 0.65, 1.0, 1.0};
		for (int level = 0; level <= 5; level++) {
			Tipsy.setLevel(player, level);
			helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - knockback[level]) < EPS,
					"knockback resistance at level " + level + ": " + player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER) - fall[level]) < EPS,
					"fall damage multiplier at level " + level + ": " + player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
		}
		// Sobering down from 4 to 3 brings the courage back; milk takes it away with the effect
		Tipsy.setLevel(player, 4);
		Tipsy.lowerLevel(player);
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 0.30) < EPS, "courage back at level 3");
		drink(player, new ItemStack(Items.MILK_BUCKET));
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) < EPS, "no courage after milk");
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER) - 1.0) < EPS, "normal falls after milk");
		helper.succeed();
	}

	/** Weakness I comes with level 4, stays at 5, and leaves as soon as the level drops below 4. */
	private static void weaknessFromLevel4(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 3);
		helper.assertFalse(player.hasEffect(MobEffects.WEAKNESS), "No Weakness at level 3");
		Tipsy.addLevel(player);
		MobEffectInstance weakness = player.getEffect(MobEffects.WEAKNESS);
		helper.assertTrue(weakness != null && weakness.getAmplifier() == 0 && !weakness.isVisible(), "Level 4 gives Weakness I without particles: " + weakness);
		helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), "No Slowness yet at level 4");
		for (int i = 0; i < 200; i++) {
			player.tickEffectsOnce();
			TipsyEvents.onEntityTick(player);
		}
		helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "Weakness refreshed while at level 4");
		Tipsy.addLevel(player);
		helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "Weakness stays at level 5");
		// A random Nausea pulse may have come along in those 10 s; it isn't what this test is about
		player.removeEffect(MobEffects.NAUSEA);
		Tipsy.lowerLevel(player);
		Tipsy.lowerLevel(player);
		helper.assertValueEqual(Tipsy.getLevel(player), 3, "level");
		helper.assertFalse(player.hasEffect(MobEffects.WEAKNESS), "Weakness gone below level 4");
		helper.assertFalse(TipsyRegistry.HEAVY_EFFECTS_OWNED.has(player), "Tipsy owns nothing any more");
		helper.succeed();
	}

	/**
	 * No Nausea up to III (peak drunk feels good); from IV a pulse now and then, at V in waves, dizzy more often than
	 * not. A pulse runs out on its own and goes when the level drops below IV.
	 */
	private static void nauseaPulses(GameTestHelper helper) {
		RandomSource random = RandomSource.create(7L);
		int[] rolls = new int[6];
		double[] dizzy = new double[6];
		for (int level = 0; level <= 5; level++) {
			int pulse = 0;
			int dizzyTicks = 0;
			for (int i = 0; i < 60000; i++) {
				if (pulse > 0) {
					pulse--;
					dizzyTicks++;
				} else if (Tipsy.rollsNausea(level, random)) {
					rolls[level]++;
					pulse = Tipsy.NAUSEA_PULSE_TICKS;
				}
			}
			dizzy[level] = dizzyTicks / 60000.0;
		}
		helper.assertTrue(rolls[0] + rolls[1] + rolls[2] + rolls[3] == 0, "No Nausea up to III: " + java.util.Arrays.toString(rolls));
		helper.assertValueEqual(Tipsy.NAUSEA_LEVEL, 4, "Nausea starts at IV");
		helper.assertTrue(dizzy[4] > 0.2 && dizzy[4] < 0.5, "IV: dizzy now and then: " + dizzy[4]);
		helper.assertTrue(dizzy[5] > 0.5 && dizzy[5] < 0.7, "V: in waves, about 60% of the time: " + dizzy[5]);

		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 3);
		helper.assertFalse(Tipsy.nauseaPulse(player), "No pulse at level 3");
		Tipsy.setLevel(player, 4);
		helper.assertTrue(Tipsy.nauseaPulse(player), "A pulse at level 4");
		MobEffectInstance nausea = player.getEffect(MobEffects.NAUSEA);
		helper.assertTrue(nausea != null && nausea.getDuration() == Tipsy.NAUSEA_PULSE_TICKS && nausea.getAmplifier() == 0, "A short pulse: " + nausea);
		helper.assertFalse(Tipsy.nauseaPulse(player), "No second pulse on top of the first");
		// It runs out on its own
		tick(player, Tipsy.NAUSEA_PULSE_TICKS + 1);
		Tipsy.setLevel(player, 4);
		helper.assertFalse(player.hasEffect(MobEffects.NAUSEA), "The pulse is over after " + Tipsy.NAUSEA_PULSE_TICKS + " ticks");
		// Dropping below IV ends a pulse right away
		helper.assertTrue(Tipsy.nauseaPulse(player), "Another pulse");
		Tipsy.lowerLevel(player);
		helper.assertFalse(player.hasEffect(MobEffects.NAUSEA), "Nausea gone below level 4");
		helper.assertFalse(TipsyRegistry.HEAVY_EFFECTS_OWNED.has(player), "Tipsy owns nothing at III");
		helper.succeed();
	}

	/** A potion's Weakness or Nausea (and Slowness) stays when Tipsy drops; Tipsy never stacks a pulse onto foreign Nausea. */
	private static void keepsForeignSideEffects(GameTestHelper helper) {
		TestPlayer player = player(helper);
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1800, 0));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 1800, 0));
		Tipsy.setLevel(player, 5);
		helper.assertFalse(Tipsy.nauseaPulse(player), "No pulse while a foreign Nausea runs");
		Tipsy.setLevel(player, 1);
		MobEffectInstance weakness = player.getEffect(MobEffects.WEAKNESS);
		MobEffectInstance nausea = player.getEffect(MobEffects.NAUSEA);
		helper.assertTrue(weakness != null && weakness.getDuration() > 1000, "A Weakness potion survives Tipsy dropping: " + weakness);
		helper.assertTrue(nausea != null && nausea.getDuration() > 1000, "A foreign Nausea survives Tipsy dropping: " + nausea);
		helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), "Tipsy's own Slowness is gone");
		// A sober player's short particleless Weakness from a command is not Tipsy's either
		TestPlayer sober = player(helper);
		sober.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, true));
		TipsyEvents.onEntityTick(sober);
		helper.assertTrue(sober.hasEffect(MobEffects.WEAKNESS), "A sober player keeps a command's Weakness");
		helper.succeed();
	}

	/**
	 * A weaker, longer, particleless effect from another mod that lands on Tipsy's own (vanilla keeps it hidden
	 * underneath) comes back when Tipsy's runs out, instead of being removed with it.
	 */
	private static void keepsForeignEffectUnderItsOwn(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 1800, 0, false, false, true));
		helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 1, "Tipsy's Slowness II on top");
		Tipsy.lowerLevel(player);
		for (int i = 0; i < 80; i++) {
			player.tickEffectsOnce();
			TipsyEvents.onEntityTick(player);
		}
		MobEffectInstance slowness = player.getEffect(MobEffects.SLOWNESS);
		helper.assertTrue(slowness != null && slowness.getAmplifier() == 0 && slowness.getDuration() > 1000, "The other mod's Slowness I is back: " + slowness);
		helper.succeed();
	}

	// ------------------------------------------------------------------ camera wobble (pure math, client applies it)

	/** One drink more at Tipsy V: out cold, then awake somewhere, hungry and shaky, with a souvenir. */
	private static void blackoutOneTooMany(GameTestHelper helper) {
		TestPlayer player = player(helper);
		player.getFoodData().setFoodLevel(20);
		Tipsy.setLevel(player, 4);
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertFalse(Blackout.isOut(player), "Tipsy IV to V: no blackout yet");
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertTrue(Blackout.isOut(player), "one drink more at V: blacked out");
		int itemsBefore = countItems(player);
		// Up to the wake-up; then right here, so the test never reaches into its neighbours' areas (BlackoutGameTests
		// covers the draw and every ending)
		while (Blackout.ticksLeft(player) > Blackout.WAKE_AT + 1) {
			Blackout.tick(player);
		}
		jexxme.vineandbarrel.tipsy.TipsyRegistry.BLACKOUT.set(player, Blackout.WAKE_AT);
		Blackout.wakeUp(player, jexxme.vineandbarrel.tipsy.BlackoutSpots.find(jexxme.vineandbarrel.tipsy.BlackoutEnding.RIGHT_HERE, player,
			RandomSource.create(1L)), RandomSource.create(1L));
		for (int i = 0; i < Blackout.WAKE_AT; i++) {
			Blackout.tick(player);
		}
		helper.assertFalse(Blackout.isOut(player), "awake again after the blackout");
		helper.assertValueEqual(Tipsy.getLevel(player), 1, "Tipsy after waking");
		helper.assertTrue(player.getFoodData().getFoodLevel() <= 20 - Blackout.HUNGER_LOST, "woke up hungry: " + player.getFoodData().getFoodLevel());
		helper.assertTrue(player.hasEffect(MobEffects.NAUSEA) && player.hasEffect(MobEffects.SLOWNESS), "woke up shaky");
		helper.assertValueEqual(countItems(player), itemsBefore + 1, "a mystery souvenir in the pockets");
		helper.succeed();
	}

	/**
	 * Out cold (Jexxme): nobody can beat you up and falling over doesn't hurt, so mobs, players and falls do nothing;
	 * everything else still counts the way it would for anyone lying there (lava, the void, starving, {@code /kill}).
	 * Through the loader's own damage hook (NeoForge: the incoming-damage event, Fabric: allow-damage).
	 */
	private static void blackoutDamageRules(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestPlayer player = player(helper);
		// Until its client has loaded the world a player can't be hurt at all; this one has
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		Tipsy.setLevel(player, 5);
		Blackout.start(player);
		helper.assertTrue(Blackout.isOut(player), "out cold");
		net.minecraft.world.entity.monster.zombie.Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(1.5, 1.0, 1.5));
		TestPlayer other = player(helper);
		var sources = level.damageSources();
		float health = player.getHealth();
		player.hurtServer(level, sources.mobAttack(zombie), 4.0F);
		helper.assertValueEqual(player.getHealth(), health, "a zombie can't hurt you while you're out");
		player.hurtServer(level, sources.playerAttack(other), 4.0F);
		helper.assertValueEqual(player.getHealth(), health, "nor can another player");
		player.hurtServer(level, sources.fall(), 4.0F);
		helper.assertValueEqual(player.getHealth(), health, "nor the fall");
		zombie.discard();
		helper.assertFalse(TipsyEvents.cancelsIncomingDamage(player, sources.genericKill()), "/kill still kills");
		helper.assertFalse(TipsyEvents.cancelsIncomingDamage(player, sources.fellOutOfWorld()), "the void still counts");
		helper.assertFalse(TipsyEvents.cancelsIncomingDamage(player, sources.starve()), "starving still counts");
		player.hurtServer(level, sources.mobProjectile(zombie, zombie), 4.0F);
		helper.assertValueEqual(player.getHealth(), health, "nor a mob's arrow");
		player.hurtServer(level, sources.thrown(zombie, zombie), 4.0F);
		helper.assertValueEqual(player.getHealth(), health, "nor anything a mob throws");
		player.hurtServer(level, sources.lava(), 2.0F);
		helper.assertTrue(player.getHealth() < health, "lava still burns: " + player.getHealth());
		// Each a little harder than the last, so the half-second of hurt-immunity after a hit lets it through
		float afterLava = player.getHealth();
		player.hurtServer(level, sources.inFire(), 3.0F);
		helper.assertTrue(player.getHealth() < afterLava, "fire still burns: " + player.getHealth());
		float afterFire = player.getHealth();
		player.hurtServer(level, sources.drown(), 4.0F);
		helper.assertTrue(player.getHealth() < afterFire, "you can still drown: " + player.getHealth());
		TipsyRegistry.BLACKOUT.remove(player);
		helper.assertFalse(TipsyEvents.cancelsIncomingDamage(player, sources.mobAttack(other)), "awake, a hit is a hit");
		helper.succeed();
	}

	/** Out cold, Tipsy pauses: the Nausea swirl stops, no hiccups, no punches, no stumbles. */
	private static void blackoutPausesTipsy(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		helper.assertTrue(Tipsy.nauseaPulse(player), "dizzy before");
		TipsyRegistry.HICCUPS.set(player, new Tipsy.HiccupBout(3, 0, 0));
		Blackout.start(player);
		helper.assertFalse(player.hasEffect(MobEffects.NAUSEA), "the swirl stops when the eyes close");
		helper.assertFalse(TipsyRegistry.HICCUPS.has(player), "the bout of hiccups is over");
		helper.assertFalse(Tipsy.nauseaPulse(player), "no new swirl while out");
		TipsyMotion.Counts before = motion(player);
		// Not as far as the wake-up (that would move the player: BlackoutGameTests covers it)
		for (int i = 0; i < Blackout.TICKS - Blackout.WAKE_AT - 5; i++) {
			TipsyEvents.onEntityTick(player);
			helper.assertFalse(player.hasEffect(MobEffects.NAUSEA), "no swirl while out");
		}
		helper.assertValueEqual(motion(player), before, "no hiccup, stumble or trip while out");
		helper.assertTrue(Blackout.isOut(player), "still out");
		helper.succeed();
	}

	/** The items in the pockets (not what is worn: a blackout may add a pumpkin hat). */
	private static int countItems(TestPlayer player) {
		int count = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			count += stack.getCount();
		}
		return count;
	}

	/** In the Nether (and the End) there is no open-sky spot to wake at: never the bedrock roof. */
	private static void blackoutNoNetherRoof(GameTestHelper helper) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
		helper.assertTrue(nether != null, "the Nether exists");
		RandomSource random = RandomSource.create(5L);
		for (int i = 0; i < 20; i++) {
			helper.assertTrue(BlackoutSpots.inTheOpen(nether, BlockPos.ZERO.above(64), random) == null, "no wake-up spot in the Nether");
		}
		ServerLevel end = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.END);
		if (end != null) {
			helper.assertTrue(BlackoutSpots.inTheOpen(end, BlockPos.ZERO.above(64), random) == null, "no wake-up spot in the End");
		}
		helper.succeed();
	}

	/** With the blackout switched off, Tipsy V just stays Tipsy V. */
	private static void blackoutConfigOff(GameTestHelper helper) {
		TestPlayer player = player(helper);
		with(TipsyConfig.BLACKOUT, false, () -> {
			Tipsy.setLevel(player, 5);
			drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
			helper.assertFalse(Blackout.isOut(player), "no blackout when it is switched off");
			helper.assertValueEqual(Tipsy.getLevel(player), 5, "still Tipsy V");
		});
		helper.succeed();
	}

	/** Rowdy: no wild swings below IV, some at IV, about twice as many at V. */
	private static void rowdyRates(GameTestHelper helper) {
		RandomSource random = RandomSource.create(11L);
		int[] swings = new int[6];
		for (int i = 0; i < 60000; i++) {
			for (int level = 0; level <= 5; level++) {
				if (Rowdy.rollsSwing(level, random)) swings[level]++;
			}
		}
		helper.assertTrue(swings[0] + swings[1] + swings[2] + swings[3] == 0, "no wild swings below IV");
		helper.assertTrue(swings[4] > 50 && swings[4] < 160, "about one every 30 s at IV: " + swings[4]);
		helper.assertTrue(swings[5] > 1.5 * swings[4], "more at V: " + swings[5]);
		helper.succeed();
	}

	/** A player in front of a mob, looking at it. */
	private static TestPlayer facing(GameTestHelper helper, net.minecraft.world.entity.Entity target) {
		TestPlayer player = player(helper);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.0));
		player.setPos(at.x, at.y, at.z);
		player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
		Tipsy.setLevel(player, 4);
		return player;
	}

	/** A wild swing is a real punch: half a heart on the pig right in front. */
	private static void rowdyPunches(GameTestHelper helper) {
		net.minecraft.world.entity.animal.pig.Pig pig = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, new Vec3(2.5, 1.0, 3.0));
		TestPlayer player = facing(helper, pig);
		float before = pig.getHealth();
		helper.assertTrue(Rowdy.swing(player) == pig, "the swing hits the pig in front");
		helper.assertTrue(pig.getHealth() < before, "the pig took the punch: " + pig.getHealth());
		helper.assertTrue(before - pig.getHealth() <= Rowdy.PUNCH_DAMAGE + 1.0E-4, "a fist's punch, no more");
		helper.succeed();
	}

	/** Never pets, iron golems or armor stands; other players only where PvP allows it. */
	private static void rowdySpares(GameTestHelper helper) {
		net.minecraft.world.entity.animal.wolf.Wolf wolf = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.WOLF, new Vec3(2.5, 1.0, 3.0));
		TestPlayer owner = player(helper);
		wolf.tame(owner);
		TestPlayer player = facing(helper, wolf);
		float before = wolf.getHealth();
		helper.assertTrue(Rowdy.swing(player) == null, "a pet is never punched");
		helper.assertValueEqual(wolf.getHealth(), before, "the wolf's health");
		wolf.discard();
		net.minecraft.world.entity.animal.golem.IronGolem golem = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.IRON_GOLEM, new Vec3(2.5, 1.0, 3.5));
		helper.assertFalse(Rowdy.mayHit(player, golem), "iron golems are never punched");
		golem.discard();
		net.minecraft.world.entity.decoration.ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 3));
		helper.assertFalse(Rowdy.mayHit(player, stand), "armor stands are never punched");
		TestPlayer other = player(helper);
		helper.assertValueEqual(Rowdy.mayHit(player, other), player.canHarmPlayer(other), "another player only where PvP allows it");
		helper.succeed();
	}

	/** At the bar: from Tipsy IV villagers won't trade; at III they still do, and with the cut-off switched off always. */
	private static void rowdyCutOff(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 3);
		helper.assertFalse(Rowdy.cutOff(player), "Tipsy III still gets served");
		Tipsy.setLevel(player, 4);
		helper.assertTrue(Rowdy.cutOff(player), "Tipsy IV is cut off");
		with(TipsyConfig.CUT_OFF, false, () -> helper.assertFalse(Rowdy.cutOff(player), "cut-off off: always served"));
		// Its own switch: no punches doesn't mean no cut-off
		with(TipsyConfig.ROWDY_PUNCHES, false, () -> helper.assertTrue(Rowdy.cutOff(player), "punches off: still cut off"));
		// The old switch for all of Rowdy still turns both off (configs from before the split keep working)
		with(TipsyConfig.ROWDY, false, () -> {
			helper.assertFalse(Rowdy.cutOff(player), "rowdy off: no cut-off");
			helper.assertFalse(TipsyConfig.rowdy(), "rowdy off: no punches");
		});
		helper.succeed();
	}

	/**
	 * The cut-off through the loader's own wiring: a player's interact packet at a wandering trader (NeoForge: the entity
	 * interact event, Fabric: the use-entity callback). Tipsy III opens the trades, Tipsy IV gets a head shake and no
	 * trades, and a spectator at Tipsy IV is left alone (no refusal).
	 */
	private static void rowdyCutOffClick(GameTestHelper helper) {
		TestPlayer player = player(helper);
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(2, 1, 2));
		trader.setNoAi(true);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 3.8));
		player.snapTo(at.x, at.y, at.z, 180.0F, 0.0F);
		Runnable click = () -> player.connection.handleInteract(new ServerboundInteractPacket(trader.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, false));

		Tipsy.setLevel(player, 3);
		click.run();
		helper.assertTrue(player.containerMenu instanceof MerchantMenu, "Tipsy III: the trades open, the menu is " + player.containerMenu);
		helper.assertValueEqual(trader.getUnhappyCounter(), 0, "Tipsy III: no head shake");
		player.closeContainer();

		Tipsy.setLevel(player, 4);
		click.run();
		helper.assertFalse(player.containerMenu instanceof MerchantMenu, "Tipsy IV: no trades");
		helper.assertTrue(trader.getUnhappyCounter() > 0, "Tipsy IV: the trader shakes its head");

		trader.setUnhappyCounter(0);
		player.spectator = true;
		click.run();
		helper.assertValueEqual(trader.getUnhappyCounter(), 0, "a spectator at Tipsy IV is not refused");
		helper.succeed();
	}

	/**
	 * The inventory tooltip, vanilla style: the courage in blue with its numbers, the drawbacks you can't see yourself in
	 * red, at most three words a line, every key translated, and a drawback the server switched off left out.
	 */
	private static void effectTooltip(GameTestHelper helper) {
		helper.assertTrue(TipsyInfo.lines(0).isEmpty(), "sober: no lines");
		String[] expected = {
			"",
			"+10% Knockback Resistance | -15% Fall Damage",
			"+20% Knockback Resistance | -25% Fall Damage",
			"+30% Knockback Resistance | -35% Fall Damage | Slurring",
			"Slurring, Weakness, Rowdy | No buffs, Cut off",
			"Slurring, Weakness, Rowdy | No buffs, Cut off, Sluggish | Stumbling, Tripping"};
		for (int level = 1; level <= 5; level++) {
			helper.assertValueEqual(text(TipsyInfo.lines(level)), expected[level], "the tooltip at level " + level);
			for (Component line : TipsyInfo.lines(level)) {
				helper.assertTrue(line.getString().split(", ").length <= 3, "at most three words a line: " + line.getString());
			}
		}
		with(TipsyConfig.SLURRED_CHAT, false, () -> helper.assertFalse(text(TipsyInfo.lines(3)).contains("Slurring"), "no slurring when it is off"));
		with(TipsyConfig.ROWDY_PUNCHES, false, () -> helper.assertFalse(text(TipsyInfo.lines(4)).contains("Rowdy"), "no Rowdy when the punches are off"));
		with(TipsyConfig.ROWDY, false, () -> {
			String four = text(TipsyInfo.lines(4));
			helper.assertFalse(four.contains("Rowdy") || four.contains("Cut off"), "neither with all of Rowdy off: " + four);
		});
		with(TipsyConfig.CUT_OFF, false, () -> helper.assertFalse(text(TipsyInfo.lines(4)).contains("Cut off"), "no cut-off when it is off"));
		with(TipsyConfig.STUMBLING, false, () -> {
			String five = text(TipsyInfo.lines(5));
			helper.assertFalse(five.contains("Stumbling") || five.contains("Tripping"), "no stumbling or tripping when it is off: " + five);
		});
		helper.succeed();
	}

	private static String text(List<Component> lines) {
		return String.join(" | ", lines.stream().map(Component::getString).toList());
	}

	/** Tunnel vision from level 1, gentle up to III, harder after; blinks only from 3, long ones only from 4. */
	private static void visionMath(GameTestHelper helper) {
		helper.assertValueEqual(TipsyVision.vignette(0.0F), 0.0F, "sober: no tunnel vision");
		helper.assertTrue(TipsyVision.vignette(1.0F) > 0.0F, "level 1 already narrows the view a little");
		float last = 0.0F;
		for (int level = 1; level <= 5; level++) {
			float v = TipsyVision.vignette(level);
			helper.assertTrue(v > last && v < 1.0F, "tunnel vision grows at level " + level + ": " + v);
			last = v;
		}
		helper.assertTrue(TipsyVision.vignette(3.0F) <= 0.4F, "III, peak drunk, keeps the edges light: " + TipsyVision.vignette(3.0F));
		helper.assertTrue(TipsyVision.vignette(4.0F) - TipsyVision.vignette(3.0F) > TipsyVision.vignette(3.0F) - TipsyVision.vignette(2.0F),
			"the edges close in harder after the turn");
		helper.assertTrue(TipsyVision.vignette(2.5F) > TipsyVision.vignette(2.0F) && TipsyVision.vignette(2.5F) < TipsyVision.vignette(3.0F), "eases between levels");
		for (int level = 3; level <= 5; level++) {
			int shut = 0;
			int longestShut = 0;
			int run = 0;
			int open = 0;
			for (int tick = 0; tick < 20 * 60 * 5; tick++) {
				float closed = TipsyVision.eyelids(level, tick, 42);
				helper.assertTrue(closed >= 0.0F && closed <= 1.0F, "eyelids stay in 0..1");
				if (closed >= 1.0F) {
					shut++;
					longestShut = Math.max(longestShut, ++run);
				} else {
					run = 0;
				}
				open += closed == 0.0F ? 1 : 0;
			}
			helper.assertTrue(shut > 0, "level " + level + " blinks at all");
			helper.assertTrue(open > 20 * 60 * 5 * 0.8, "level " + level + ": the eyes are open most of the time (" + open + ")");
			if (level == 3) {
				helper.assertTrue(longestShut < 10, "level 3 only blinks briefly: " + longestShut);
			} else {
				helper.assertTrue(longestShut >= 10, "level " + level + " sometimes keeps the eyes shut for a moment: " + longestShut);
			}
		}
		for (int tick = 0; tick < 20 * 60 * 5; tick++) {
			helper.assertValueEqual(TipsyVision.eyelids(2, tick, 42), 0.0F, "no blinks below level 3");
		}
		helper.succeed();
	}

	/** Playtest 1: the drink that takes Tipsy to 4 or more gives no buff (a buff from before stays as it was). */
	private static void tooDrunkForBuffs(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 2);
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertTrue(player.hasEffect(MobEffects.HASTE), "level 2 to 3: the ale still gives Haste");
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertValueEqual(Tipsy.getLevel(player), Tipsy.NO_BUFF_LEVEL, "level after the fourth drink");
		helper.assertFalse(player.hasEffect(MobEffects.HASTE), "level 4: too drunk, and the earlier ale's Haste is gone too");
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 1000, 0));
		drink(player, new ItemStack(DrinkItems.AGED_SWEET_BERRY_WINE.get()));
		MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
		helper.assertTrue(speed != null && speed.getAmplifier() == 0 && speed.getDuration() <= 1000, "an earlier Speed stays as it was: " + speed);
		helper.succeed();
	}

	/**
	 * Audit m2 (2026-11-08): too drunk for buffs never tops up a golden apple's spent Absorption hearts, neither through the
	 * drink too many nor through taking away the buffs of earlier drinks; what was left stays.
	 */
	private static void tooDrunkKeepsAbsorptionHearts(GameTestHelper helper) {
		// A golden apple's Absorption II, its hearts gone, then hard cider at Tipsy III
		TestPlayer player = player(helper);
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 1));
		player.setAbsorptionAmount(0.0F);
		Tipsy.setLevel(player, 3);
		drink(player, new ItemStack(DrinkItems.HARD_CIDER.get()));
		helper.assertValueEqual(Tipsy.getLevel(player), Tipsy.NO_BUFF_LEVEL, "level after the drink too many");
		MobEffectInstance apple = player.getEffect(MobEffects.ABSORPTION);
		helper.assertTrue(apple != null && apple.getAmplifier() == 1, "the golden apple's Absorption stays: " + apple);
		helper.assertValueEqual(player.getAbsorptionAmount(), 0.0F, "no hearts back from the drink too many");

		// The same with half the hearts left: they stay as they were
		TestPlayer half = player(helper);
		half.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 1));
		half.setAbsorptionAmount(3.0F);
		Tipsy.setLevel(half, 3);
		drink(half, new ItemStack(DrinkItems.AGED_HARD_CIDER.get()));
		helper.assertValueEqual(half.getAbsorptionAmount(), 3.0F, "the hearts left stay as they were");

		// An earlier cider's Absorption over a weaker one, spent, then taken away with the other drinks' buffs
		TestPlayer earlier = player(helper);
		earlier.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
		drink(earlier, new ItemStack(DrinkItems.AGED_HARD_CIDER.get()));
		earlier.setAbsorptionAmount(1.0F);
		DrinkBuffs.removeAll(earlier);
		MobEffectInstance under = earlier.getEffect(MobEffects.ABSORPTION);
		helper.assertTrue(under != null && under.getAmplifier() == 0, "the other Absorption stays: " + under);
		helper.assertValueEqual(earlier.getAbsorptionAmount(), 1.0F, "no hearts back from taking the cider's away");
		helper.succeed();
	}

	/**
	 * Audit M1: a drink buff that is gone (it ran out, milk, a night's sleep) is forgotten, so a potion of the same effect
	 * drunk later is never taken away at Tipsy IV; and a stronger one on top of ours stays too.
	 */
	private static void drinkBuffsForgetTheOldOnes(GameTestHelper helper) {
		TestPlayer player = player(helper);
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertValueEqual(DrinkBuffs.list(player).size(), 1, "the ale's Haste is remembered");
		// The ale's Haste runs out (or milk): the next tick forgets it
		player.removeEffect(MobEffects.HASTE);
		TipsyEvents.onEntityTick(player);
		helper.assertTrue(DrinkBuffs.list(player).isEmpty(), "a buff that is gone is forgotten");
		// Later the same night: a Haste "potion", then one drink too many
		player.addEffect(new MobEffectInstance(MobEffects.HASTE, 6000, 0));
		Tipsy.setLevel(player, 3);
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertValueEqual(Tipsy.getLevel(player), 4, "level after the drink too many");
		MobEffectInstance potion = player.getEffect(MobEffects.HASTE);
		helper.assertTrue(potion != null && potion.getDuration() > 5000, "the potion's Haste stays: " + potion);

		// A stronger potion (or a beacon) on top of a drink's buff: the potion stays, and the drink's buff hidden under
		// it is taken away too, so it doesn't come back once the potion is over
		TestPlayer second = player(helper);
		drink(second, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		second.addEffect(new MobEffectInstance(MobEffects.HASTE, 200, 1));
		DrinkBuffs.removeAll(second);
		MobEffectInstance top = second.getEffect(MobEffects.HASTE);
		helper.assertTrue(top != null && top.getAmplifier() == 1, "the stronger potion stays: " + top);
		tick(second, 201);
		helper.assertFalse(second.hasEffect(MobEffects.HASTE), "and nothing of the drink comes back after it: " + second.getEffect(MobEffects.HASTE));

		// Sober again (a night's sleep): nothing is remembered, and the buff itself stays
		TestPlayer third = player(helper);
		drink(third, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		Tipsy.clear(third);
		helper.assertTrue(DrinkBuffs.list(third).isEmpty(), "sober: the night's buffs are forgotten");
		helper.assertTrue(third.hasEffect(MobEffects.HASTE), "but the buff itself stays");
		helper.succeed();
	}

	/**
	 * Audit M1: a cheers makes the buff last longer and it is still the drink's (taken away at IV); a potion waiting under
	 * a drink's buff comes back when the buff is taken; a beacon's refreshes change nothing; a relog (the saved entries
	 * read back) neither.
	 */
	private static void drinkBuffsThroughCheersBeaconsAndRelogs(GameTestHelper helper) {
		// Cheers: two players drinking together, then one of them a drink too many
		TestPlayer one = player(helper);
		TestPlayer two = player(helper);
		drink(one, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		int plain = one.getEffect(MobEffects.HASTE).getDuration();
		drink(two, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertTrue(one.getEffect(MobEffects.HASTE).getDuration() > plain, "the cheers made the ale's Haste last longer");
		for (int i = 0; i < 20; i++) {
			one.tickEffectsOnce();
			TipsyEvents.onEntityTick(one);
		}
		helper.assertValueEqual(DrinkBuffs.list(one).size(), 1, "still remembered as the ale's after the cheers");
		Tipsy.setLevel(one, 3);
		drink(one, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertFalse(one.hasEffect(MobEffects.HASTE), "at IV the cheered Haste goes too: " + one.getEffect(MobEffects.HASTE));

		// Our stronger buff with a weaker, longer potion waiting underneath: the potion comes back, as it was
		TestPlayer three = player(helper);
		Tipsy.setLevel(three, 1);
		MobEffectInstance strong = new MobEffectInstance(MobEffects.HASTE, 400, 1);
		three.addEffect(strong);
		DrinkBuffs.remember(three, strong);
		three.addEffect(new MobEffectInstance(MobEffects.HASTE, 6000, 0));
		DrinkBuffs.removeAll(three);
		MobEffectInstance back = three.getEffect(MobEffects.HASTE);
		helper.assertTrue(back != null && back.getAmplifier() == 0 && back.getDuration() > 5000, "the potion underneath is back: " + back);

		// A beacon's Haste II refreshed over the ale's Haste I, then a drink too many: the beacon's stays, the ale's goes
		TestPlayer four = player(helper);
		drink(four, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		for (int refresh = 0; refresh < 3; refresh++) {
			four.addEffect(new MobEffectInstance(MobEffects.HASTE, 260, 1, true, true));
			for (int i = 0; i < 80; i++) {
				four.tickEffectsOnce();
				TipsyEvents.onEntityTick(four);
			}
		}
		helper.assertValueEqual(DrinkBuffs.list(four).size(), 1, "the ale's Haste is still known under the beacon's");
		DrinkBuffs.removeAll(four);
		MobEffectInstance beacon = four.getEffect(MobEffects.HASTE);
		helper.assertTrue(beacon != null && beacon.getAmplifier() == 1 && beacon.isAmbient(), "the beacon's Haste stays: " + beacon);
		tick(four, 261);
		helper.assertFalse(four.hasEffect(MobEffects.HASTE), "and the ale's doesn't come back after it");

		// A relog: the saved entries read back (time only runs while the player is there, like the effect's)
		TestPlayer five = player(helper);
		drink(five, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		for (int i = 0; i < 100; i++) {
			five.tickEffectsOnce();
			TipsyEvents.onEntityTick(five);
		}
		var saved = DrinkBuffs.CODEC.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, DrinkBuffs.list(five)).getOrThrow();
		TipsyRegistry.DRINK_BUFFS.set(five, DrinkBuffs.CODEC.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow());
		DrinkBuffs.removeAll(five);
		helper.assertFalse(five.hasEffect(MobEffects.HASTE), "after a relog the ale's Haste is still known and taken away");
		helper.succeed();
	}

	/** Non-alcoholic mode switched on mid-game: nobody stays tipsy, and Tipsy's own side effects go with it. */
	private static void nonAlcoholicMidGame(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "Slowness at V");
		with(TipsyConfig.ENABLED, false, () -> {
			TipsyEvents.onEntityTick(player);
			TipsyEvents.onEntityTick(player);
			helper.assertValueEqual(Tipsy.getLevel(player), 0, "sober the moment it is switched on");
			helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), "Tipsy's Slowness is gone too");
			helper.assertTrue(TipsyRegistry.SYNCED_LEVEL.getExisting(player) == null, "the other clients stop the sway");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ level 5: the trip (own batch: no monsters from other tests near)

	/** Flat stone under the whole test area and the player standing on it, facing south along it. */
	private static TestPlayer onFlatGround(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		TestPlayer player = player(helper);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 0.5));
		player.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		player.setOnGround(true);
		Tipsy.setLevel(player, 5);
		return player;
	}

	/**
	 * A trip at V: the stumble's start, face down for a moment, and up again where you were. Nothing hurts, nothing moves
	 * you, every client is told, and the next one waits for the cooldown.
	 */
	private static void trip(GameTestHelper helper) {
		TestPlayer player = onFlatGround(helper);
		helper.assertTrue(TipsyLegs.canTrip(player), "flat open ground, nobody near: a trip is safe here");
		float health = player.getHealth();
		Vec3 at = player.position();
		int trips = motion(player).trips();
		helper.assertTrue(TipsyLegs.trip(player), "level 5 trips");
		helper.assertValueEqual(motion(player).trips(), trips + 1, "every client is told to play the trip");
		helper.assertTrue(TipsyLegs.isDown(player), "down");
		helper.assertFalse(TipsyLegs.stumble(player, new Vec3(0.2, 0.0, 0.0), RandomSource.create(1L)), "no stumble while down");
		for (int i = 0; i < TipsyMotion.TRIP_TICKS; i++) {
			TipsyEvents.onEntityTick(player);
		}
		helper.assertFalse(TipsyLegs.isDown(player), "up again after " + TipsyMotion.TRIP_TICKS + " ticks");
		helper.assertValueEqual(player.getHealth(), health, "a trip never hurts");
		helper.assertValueEqual(player.position(), at, "a trip moves you nowhere");
		helper.assertFalse(TipsyLegs.canTrip(player), "not again right away: the cooldown");
		helper.succeed();
	}

	/** Never where it could go wrong: a monster near, no room ahead, a step down, sneaking or below V. */
	private static void tripOnlyWhereSafe(GameTestHelper helper) {
		TestPlayer player = onFlatGround(helper);
		helper.assertTrue(TipsyLegs.canTrip(player), "safe to begin with");
		player.setShiftKeyDown(true);
		helper.assertFalse(TipsyLegs.canTrip(player), "never sneaking");
		player.setShiftKeyDown(false);
		player.setOnGround(false);
		helper.assertFalse(TipsyLegs.canTrip(player), "never in the air");
		player.setOnGround(true);
		// A wall ahead, where the face would land
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
		helper.assertFalse(TipsyLegs.canTrip(player), "no room to lie down");
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
		// A step down ahead
		helper.setBlock(new BlockPos(2, 0, 2), Blocks.AIR);
		helper.assertFalse(TipsyLegs.canTrip(player), "not at a step down");
		helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
		helper.assertTrue(TipsyLegs.canTrip(player), "safe again");
		net.minecraft.world.entity.monster.zombie.Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5, 1.0, 4.5));
		helper.assertFalse(TipsyLegs.canTrip(player), "never with a monster near");
		zombie.discard();
		Tipsy.setLevel(player, 4);
		helper.assertFalse(TipsyLegs.trip(player), "only at V");
		helper.succeed();
	}

	/** Never at speed or on ice: the body would slide on past where the ground was checked. */
	private static void tripNeverSliding(GameTestHelper helper) {
		TestPlayer player = onFlatGround(helper);
		helper.assertTrue(TipsyLegs.canTrip(player), "safe standing on stone");
		player.setKnownMovement(new Vec3(0.0, 0.0, 0.6));
		helper.assertFalse(TipsyLegs.canTrip(player), "never at a running slide");
		player.setKnownMovement(Vec3.ZERO);
		for (int z = 0; z < 5; z++) {
			helper.setBlock(new BlockPos(2, 0, z), Blocks.BLUE_ICE);
		}
		helper.assertFalse(TipsyLegs.canTrip(player), "never on ice");
		// A stumble on ice is fine (a step to the side), but never over an edge: here the ground drops away on both sides
		for (int z = 0; z < 5; z++) {
			helper.setBlock(new BlockPos(1, 0, z), Blocks.AIR);
			helper.setBlock(new BlockPos(3, 0, z), Blocks.AIR);
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.setKnownMovement(Vec3.ZERO);
		helper.assertTrue(TipsyLegs.stumble(player, new Vec3(0.0, 0.0, 0.2), RandomSource.create(3L)), "the legs still give");
		helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "but no lurch over the edge");
		helper.succeed();
	}

	private static void wobbleMath(GameTestHelper helper) {
		for (float t = 0; t < 400; t += 3.7F) {
			helper.assertTrue(TipsyWobble.camera(5, t, 0.0) == TipsyWobble.Angles.NONE, "Intensity 0 turns the wobble off");
			helper.assertTrue(TipsyWobble.camera(0, t, 1.0) == TipsyWobble.Angles.NONE, "Sober means no wobble");
			TipsyWobble.Angles one = TipsyWobble.camera(4, t, 1.0);
			TipsyWobble.Angles two = TipsyWobble.camera(4, t, 2.0);
			helper.assertTrue(Math.abs(two.roll() - 2 * one.roll()) < 1.0E-4, "Intensity scales the wobble");
			helper.assertValueEqual(TipsyWobble.camera(4, t, 1.0), one, "same inputs, same wobble");
			TipsyWobble.Angles strongest = TipsyWobble.camera(5, t, 1.0);
			helper.assertTrue(Math.abs(strongest.yaw()) < 1.0F && Math.abs(strongest.pitch()) < 1.0F, "Aim moves less than a degree");
			helper.assertTrue(Math.abs(strongest.roll()) <= TipsyWobble.amplitude(5) + 1.0E-4, "Roll stays within its amplitude");
			// The FOV breathes at V only, a few percent, and never with the wobble off
			float fov = TipsyWobble.fov(5, t, 1.0);
			helper.assertTrue(Math.abs(fov - 1.0F) <= TipsyWobble.FOV_BREATHING + 1.0E-4, "the FOV breathes a little at V: " + fov);
			helper.assertValueEqual(TipsyWobble.fov(4, t, 1.0), 1.0F, "no FOV breathing at IV");
			helper.assertValueEqual(TipsyWobble.fov(5, t, 0.0), 1.0F, "no FOV breathing with the wobble off");
		}
		for (int level = 1; level <= 5; level++) {
			helper.assertTrue(Math.abs(TipsyWobble.amplitude(level) - TipsyLevels.at(level).rollDegrees()) < 1.0E-4, "the roll comes from the level table");
		}
		helper.assertTrue(TipsyWobble.amplitude(1) > 0 && TipsyWobble.amplitude(2) > TipsyWobble.amplitude(1), "Level 1-2 wobble slightly");
		helper.assertTrue(TipsyWobble.amplitude(3) < 5.0F, "III, peak drunk, sways but doesn't spin: " + TipsyWobble.amplitude(3));
		helper.assertTrue(TipsyWobble.amplitude(5) >= 15.0F, "Level 5 sways properly drunk: " + TipsyWobble.amplitude(5));
		helper.assertTrue(TipsyWobble.amplitude(5) > TipsyWobble.amplitude(4) && TipsyWobble.amplitude(4) > TipsyWobble.amplitude(3), "Wobble grows with each level");
		float mid = TipsyWobble.amplitude(2.5F);
		helper.assertTrue(mid > TipsyWobble.amplitude(2) && mid < TipsyWobble.amplitude(3), "Easing between levels is smooth");

		float peak = 0;
		for (float t = 0; t < 2000; t += 1) peak = Math.max(peak, Math.abs(TipsyWobble.camera(3, t, 1.0).roll()));
		helper.assertTrue(peak > 0.8F * TipsyWobble.amplitude(3), "The wobble actually reaches its strength, peak " + peak);

		helper.assertValueEqual(TipsyWobble.bodySway(0, 10, 1), 0.0F, "No sway when sober");
		helper.assertFalse(TipsyWobble.bodySway(3, 10, 1) == TipsyWobble.bodySway(3, 10, 2), "Players don't sway in lockstep");
		helper.succeed();
	}

	/** The jolt, the lurch and the trip as every client plays them: short, back to nothing, and a trip lies a while. */
	private static void motionMath(GameTestHelper helper) {
		helper.assertValueEqual(TipsyMotion.hiccupJolt(0.0F), 0.0F, "a hiccup starts from rest");
		helper.assertValueEqual(TipsyMotion.hiccupJolt(TipsyMotion.HICCUP_RISE), 1.0F, "and jolts all at once");
		helper.assertValueEqual(TipsyMotion.hiccupJolt(TipsyMotion.HICCUP_RISE + TipsyMotion.HICCUP_SETTLE), 0.0F, "settled in a few ticks");
		helper.assertValueEqual(TipsyMotion.stumbleLurch(TipsyMotion.STUMBLE_RISE), 1.0F, "a stumble lurches");
		helper.assertValueEqual(TipsyMotion.stumbleLurch(20.0F), 0.0F, "and steadies within a second");
		helper.assertValueEqual(TipsyMotion.tripFallen(1.0F), 0.0F, "a trip starts like a stumble, still standing");
		helper.assertTrue(TipsyMotion.tripStumble(1.0F) > 0.0F, "with the stumble's lurch");
		helper.assertValueEqual(TipsyMotion.tripFallen(TipsyMotion.TRIP_THUD + 6.0F), 1.0F, "then face down after the thud");
		int down = 0;
		for (int tick = 0; tick < TipsyMotion.TRIP_TICKS + 5; tick++) {
			float fallen = TipsyMotion.tripFallen(tick);
			helper.assertTrue(fallen >= 0.0F && fallen <= 1.0F, "fallen stays in 0..1");
			down += fallen >= 0.9F ? 1 : 0;
		}
		helper.assertTrue(down >= 20 && down <= 30, "down for 1-1.5 s: " + down + " ticks");
		helper.assertValueEqual(TipsyMotion.tripFallen(TipsyMotion.TRIP_TICKS), 0.0F, "and up again");
		helper.assertTrue(TipsyMotion.tripHolds(TipsyMotion.TRIP_TICKS - 1) && !TipsyMotion.tripHolds(TipsyMotion.TRIP_TICKS), "keys held until standing");
		helper.assertTrue(TipsyMotion.tripShake(TipsyMotion.TRIP_THUD) > 0.0F && TipsyMotion.tripShake(TipsyMotion.TRIP_THUD + 10) == 0.0F, "a short shake at the thud");
		helper.succeed();
	}
}
