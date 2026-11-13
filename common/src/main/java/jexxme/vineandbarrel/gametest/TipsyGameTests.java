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
import jexxme.vineandbarrel.tipsy.DrunkWalk;
import jexxme.vineandbarrel.tipsy.GoingUnder;
import jexxme.vineandbarrel.tipsy.Hangover;
import jexxme.vineandbarrel.tipsy.TipsyEyes;
import jexxme.vineandbarrel.tipsy.TipsySlur;
import jexxme.vineandbarrel.tipsy.TipsyInfo;
import jexxme.vineandbarrel.tipsy.TipsyGait;
import jexxme.vineandbarrel.tipsy.TipsyLegs;
import jexxme.vineandbarrel.tipsy.TipsyLevels;
import jexxme.vineandbarrel.tipsy.TipsyNod;
import jexxme.vineandbarrel.tipsy.TipsyMotion;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.EntityTypes;
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
import net.minecraft.world.effect.MobEffect;
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
import net.minecraft.world.phys.Vec2;
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
 * Tipsy: stacking, cap, decay, the level table, no side effects, every cure, non-alcoholic mode, slurred chat, the legs,
 * the renamed settings and the sway math. Headless ({@code ./gradlew runGameTestServer}).
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
		test("tipsy_stumble_at_an_edge_never_pushes", TipsyGameTests::stumbleAtAnEdgeNeverPushes);
		test("tipsy_hiccups", TipsyGameTests::hiccups);
		test("tipsy_gait_math", TipsyGameTests::gaitMath);
		test("tipsy_weave_only_where_safe", TipsyGameTests::weaveOnlyWhereSafe);
		test("tipsy_late_legs_stop_at_an_edge", TipsyGameTests::lateLegsStopAtAnEdge);
		test("tipsy_nodding_off_wakes_on_anything", TipsyGameTests::noddingOffWakesOnAnything);
		test("tipsy_nod_math", TipsyGameTests::nodMath);
		test("tipsy_eyes_math", TipsyGameTests::eyesMath);
		test("tipsy_hangover", TipsyGameTests::hangover);
		test("tipsy_hangover_config_off", TipsyGameTests::hangoverConfigOff);
		test("tipsy_nodding_off", TipsyGameTests::noddingOff);
		test("tipsy_levels_table", TipsyGameTests::levelsTable);
		test("tipsy_no_side_effects", TipsyGameTests::noSideEffects);
		test("tipsy_renamed_settings", TipsyGameTests::renamedSettings);
		test("tipsy_effect_tooltip", TipsyGameTests::effectTooltip);
		test("tipsy_rowdy_swings_from_four", TipsyGameTests::rowdyRates);
		test("tipsy_rowdy_punches_what_is_in_front", TipsyGameTests::rowdyPunches);
		test("tipsy_rowdy_only_at_someone", TipsyGameTests::rowdyOnlyAtSomeone);
		test("tipsy_rowdy_a_glance_is_enough", TipsyGameTests::rowdyAGlanceIsEnough);
		test("tipsy_rowdy_walls_and_edges", TipsyGameTests::rowdyWallsAndEdges);
		test("tipsy_going_under_waits_for_ground", TipsyGameTests::goingUnderWaitsForGround);
		test("tipsy_rowdy_spares_pets_and_golems", TipsyGameTests::rowdySpares);
		test("tipsy_rowdy_cut_off_at_the_bar", TipsyGameTests::rowdyCutOff);
		test("tipsy_rowdy_cut_off_by_a_real_click", TipsyGameTests::rowdyCutOffClick);
		loaderTest("tipsy_server_config_per_world");
		loaderTest("tipsy_config_carry_over");
		loaderTest("tipsy_chat_on_the_neoforge_bus");
		test("tipsy_blackout_one_too_many", TipsyGameTests::blackoutOneTooMany);
		test("tipsy_blackout_config_off", TipsyGameTests::blackoutConfigOff);
		test("tipsy_going_under_sobered_up", TipsyGameTests::goingUnderSoberedUp);
		test("tipsy_blackout_never_onto_the_nether_roof", TipsyGameTests::blackoutNoNetherRoof);
		test("tipsy_too_drunk_for_buffs", TipsyGameTests::tooDrunkForBuffs);
		test("tipsy_too_drunk_keeps_absorption_hearts", TipsyGameTests::tooDrunkKeepsAbsorptionHearts);
		test("tipsy_grape_juice_lowers_by_one", TipsyGameTests::grapeJuiceLowersByOne);
		test("tipsy_courage_per_level", TipsyGameTests::couragePerLevel);
		test("tipsy_heavy_legs", TipsyGameTests::heavyLegs);
		test("tipsy_motion_math", TipsyGameTests::motionMath);
		test("tipsy_blackout_damage_rules", TipsyGameTests::blackoutDamageRules);
		test("tipsy_blackout_pauses_tipsy", TipsyGameTests::blackoutPausesTipsy);
		test("tipsy_drink_buffs_forget_the_old_ones", TipsyGameTests::drinkBuffsForgetTheOldOnes);
		test("tipsy_non_alcoholic_mid_game", TipsyGameTests::nonAlcoholicMidGame);
		legsTest("tipsy_trip", TipsyGameTests::trip);
		legsTest("tipsy_trip_only_where_safe", TipsyGameTests::tripOnlyWhereSafe);
		legsTest("tipsy_trip_never_sliding", TipsyGameTests::tripNeverSliding);
		legsTest("tipsy_trip_cooldown_per_level", TipsyGameTests::tripCooldownPerLevel);
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

	/** Drinking milk clears Tipsy, through vanilla's own milk mechanism. */
	private static void milkClears(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		drink(player, new ItemStack(Items.MILK_BUCKET));
		helper.assertValueEqual(Tipsy.getLevel(player), 0, "level after milk");
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
		helper.setBlock(foot, Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH).setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		helper.setBlock(head, Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH).setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
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
		helper.assertTrue(Hangover.has(player), "a night's sleep at IV: the morning after");
		helper.succeed();
	}

	/**
	 * Drunk eyes (Tipsy overhaul, step 4): soft edges from I, double vision from III and more with each level, blurred
	 * signs from III; squinting pulls the two images together and sharpens the signs; a new level or a squint eases in,
	 * never a jump; the morning after's daylight glares only outdoors and hungover, less in the rain and squinting.
	 */
	private static void eyesMath(GameTestHelper helper) {
		helper.assertTrue(TipsyEyes.look(TipsyLevels.at(1).eyes(), 0.0F).edges() > 0.0F, "soft edges at I");
		helper.assertValueEqual(TipsyEyes.look(TipsyLevels.at(2).eyes(), 0.0F).split(), 0.0F, "no double vision at II");
		float before = 0.0F;
		for (int level = 3; level <= Tipsy.ABSOLUTE_MAX_LEVEL; level++) {
			TipsyEyes.Look look = TipsyEyes.look(TipsyLevels.at(level).eyes(), 0.0F);
			helper.assertTrue(look.split() > before && look.split() <= 1.0F, "double vision grows at " + level);
			helper.assertTrue(look.ghost() > 0.0F && look.ghost() < 0.5F, "the second image never outshines the first at " + level);
			TipsyEyes.Look squinting = TipsyEyes.look(TipsyLevels.at(level).eyes(), 1.0F);
			helper.assertTrue(squinting.split() < (level == 3 ? 0.2F : 0.7F) * look.split(), "squinting pulls the images together at " + level);
			if (level == 5) {
				helper.assertTrue(squinting.split() > 0.4F * look.split(), "but at V never quite into one");
			}
			helper.assertTrue(TipsyEyes.signBlur(TipsyLevels.at(level).eyes(), 0.0F) > TipsyEyes.signBlur(TipsyLevels.at(level).eyes(), 1.0F),
				"squinting sharpens the signs at " + level);
			before = look.split();
		}
		helper.assertValueEqual(TipsyEyes.signBlur(TipsyLevels.at(2).eyes(), 0.0F), 0.0F, "signs sharp at II");
		helper.assertValueEqual(TipsyEyes.signBlur(TipsyLevels.at(5).eyes(), 0.0F), 1.0F, "signs unreadable at V");
		helper.assertTrue(TipsyEyes.sharpAlpha(1.0F) < 0.5F && TipsyEyes.sharpAlpha(0.0F) == 1.0F,
			"blurred writing is the writing, fading into the wood");
		helper.assertFalse(TipsyEyes.look(TipsyLevels.SOBER.eyes(), 0.0F).any(), "sober, nothing");
		TipsyEyes.Look look = TipsyEyes.Look.NONE;
		TipsyEyes.Look target = TipsyEyes.look(TipsyLevels.at(5).eyes(), 0.0F);
		TipsyEyes.Look next = look.toward(target);
		helper.assertTrue(next.split() <= TipsyEyes.EASE + 1.0E-6, "a new level eases in");
		for (int i = 0; i < 40; i++) {
			look = look.toward(target);
		}
		helper.assertValueEqual(look, target, "and gets there within two seconds");
		helper.assertValueEqual(TipsyEyes.glare(true, false, 0.0F, 0.0F, 0.0F), 0.0F, "no glare indoors once it has worn in");
		helper.assertTrue(TipsyEyes.glare(true, false, 1.0F, 0.0F, 0.0F) > 0.0F, "but fresh, even indoors");
		helper.assertValueEqual(TipsyEyes.glare(false, true, 1.0F, 0.0F, 0.0F), 0.0F, "no glare without a hangover");
		helper.assertTrue(TipsyEyes.glare(true, true, 1.0F, 0.0F, 0.0F) > TipsyEyes.glare(true, true, 0.0F, 0.0F, 0.0F)
			&& TipsyEyes.glare(true, true, 1.0F, 0.0F, 0.0F) <= TipsyEyes.MOST_GLARE, "hardest the moment the eyes open");
		helper.assertTrue(TipsyEyes.fresh(0.0F) == 1.0F && TipsyEyes.fresh(TipsyEyes.FRESH_TICKS) == 0.0F
			&& TipsyEyes.fresh(TipsyEyes.FRESH_TICKS * 0.5F) < 0.5F, "and wears off over half a minute");
		float sun = TipsyEyes.glare(true, true, 0.0F, 0.0F, 0.0F);
		helper.assertTrue(sun > 0.0F && TipsyEyes.glare(true, true, 0.0F, 1.0F, 0.0F) < sun && TipsyEyes.glare(true, true, 0.0F, 0.0F, 1.0F) < sun,
			"glare in the sun, less in the rain or squinting");
		helper.succeed();
	}

	/**
	 * The morning after (Tipsy overhaul, step 7): after a blackout sober and hungover; a night's sleep at IV or V
	 * leaves you hungover, longer at V, not at III; you wake up hungry; a water bottle takes a good part of it away.
	 */
	private static void hangover(GameTestHelper helper) {
		helper.assertValueEqual(Hangover.afterSleep(3), 0, "no hangover after sleeping at III");
		helper.assertTrue(Hangover.afterSleep(5) > Hangover.afterSleep(4) && Hangover.afterSleep(4) > 0, "a longer one at V than at IV");
		TestPlayer player = player(helper);
		Hangover.start(player, Hangover.AFTER_BLACKOUT);
		helper.assertTrue(Hangover.has(player), "hungover");
		Hangover.start(player, Hangover.AFTER_SLEEP_IV);
		helper.assertValueEqual(player.getEffect(TipsyRegistry.HANGOVER).getDuration(), Hangover.AFTER_BLACKOUT, "a shorter one never cuts it short");
		helper.assertValueEqual(player.getFoodData().getFoodLevel(), Hangover.HUNGRY_FOOD, "waking up hungry: three drumsticks");
		helper.assertValueEqual(player.getFoodData().getSaturationLevel(), 0.0F, "and nothing to spare");
		player.getFoodData().setFoodLevel(20);
		Hangover.start(player, Hangover.AFTER_BLACKOUT);
		helper.assertValueEqual(player.getFoodData().getFoodLevel(), Hangover.HUNGRY_FOOD, "every wake-up leaves you hungry, hungover already or not");
		int left = player.getEffect(TipsyRegistry.HANGOVER).getDuration();
		drink(player, PotionContents.createItemStack(Items.POTION, Potions.WATER));
		helper.assertTrue(player.getEffect(TipsyRegistry.HANGOVER).getDuration() < left * 0.6F, "water takes a good part of it away");
		helper.succeed();
	}

	/** With the server's hangover switch off: no hangover at all, and a blackout still wakes you at Tipsy I. */
	private static void hangoverConfigOff(GameTestHelper helper) {
		TestPlayer player = player(helper);
		with(TipsyConfig.HANGOVER, false, () -> {
			Hangover.start(player, Hangover.AFTER_BLACKOUT);
			helper.assertFalse(Hangover.has(player), "no hangover when it is switched off");
			Tipsy.setLevel(player, 5);
			Blackout.start(player);
			TipsyRegistry.BLACKOUT.set(player, Blackout.WAKE_AT);
			Blackout.wakeUp(player, BlackoutSpots.find(jexxme.vineandbarrel.tipsy.BlackoutEnding.RIGHT_HERE, player, RandomSource.create(3L)),
				RandomSource.create(3L));
			helper.assertValueEqual(Tipsy.getLevel(player), 1, "after a blackout: Tipsy I, as before");
			helper.assertFalse(Hangover.has(player), "and not hungover");
			TipsyRegistry.BLACKOUT.remove(player);
		});
		helper.succeed();
	}

	/** Heavy legs: from IV a little slower (sprinting too), more at V; up to III and sober, full speed. */
	private static void heavyLegs(GameTestHelper helper) {
		TestPlayer player = player(helper);
		double base = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		for (int level = 1; level <= 3; level++) {
			Tipsy.setLevel(player, level);
			helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - base) < 1.0E-6, "full speed at " + level);
		}
		Tipsy.setLevel(player, 4);
		double four = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		Tipsy.setLevel(player, 5);
		double five = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(four < base && five < four && five > base * 0.75, "a little slower at IV, more at V, never crawling: " + four + ", " + five);
		Tipsy.clear(player);
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - base) < 1.0E-6, "full speed again when sober");
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
		String longMessage = "so so ".repeat(TipsySlur.MAX_LENGTH / 6) + "so";
		for (int stage = 2; stage <= 4; stage++) {
			String slurred = TipsySlur.slur(longMessage, 3L, stage);
			helper.assertTrue(slurred.length() <= TipsySlur.MAX_LENGTH && slurred.length() >= TipsySlur.MAX_LENGTH - 1,
				"a long message is cut at the limit at stage " + stage + ": " + slurred.length());
			helper.assertFalse(longMessage.startsWith(slurred), "and still goes out slurred at stage " + stage);
		}
		// When no letter happens to change, the slur still shows: a trailing *hic* (about 1 seed in 12 for this message,
		// seed 7 among them). The smoke test's chat check once took such a line for an unslurred one.
		String toast = "cheers to the cellar";
		helper.assertValueEqual(TipsySlur.slur(toast, 7L), toast + " " + TipsySlur.HIC, "no letter changed: a trailing *hic*");
		for (long seed = 0; seed < 2000; seed++) {
			helper.assertFalse(TipsySlur.slur(toast, seed).equals(toast), "a slurred message never comes back as typed (seed " + seed + ")");
		}
		helper.succeed();
	}

	/**
	 * Drunk chat gets worse with each level (golden lines per stage, the same seed always the same line): I as typed, II
	 * now and then a *hic*, III slurred but easy to read, IV hard to read, V gibberish; links and mentions always as typed;
	 * never with the server's slurred-chat switch off. (The client slurs its own message before signing it.)
	 */
	private static void slurLevelAndConfig(GameTestHelper helper) {
		String[][] golden = {
			{"where is my horse", "where is my horse", "where is my horse *hic*", "where ish *hic* myhoosrheeee",
				"whublus *hic* iiiis my gligl *hic*"},
			{"Cheers everyone, meet @Steve at https://example.com", "Cheers everyone, meet @Steve at https://example.com",
				"Cheers everyone, meet @Steve at https://example.com *hic*",
				"Cheersh eevryonnee, *hic* eeeemet @Steve *hic* aat https://example.com",
				"Whublush *hic* hmihmiffag *hic* hmow @Steve at *hic* https://example.com"}};
		for (String[] line : golden) {
			for (int stage = 0; stage <= 4; stage++) {
				helper.assertValueEqual(TipsySlur.slur(line[0], 9L, stage), line[stage], "chat stage " + stage);
			}
		}
		for (long seed = 0; seed < 40; seed++) {
			String hic = TipsySlur.slur("hello friends", seed, 1);
			helper.assertTrue(hic.equals("hello friends") || hic.equals("hello friends " + TipsySlur.HIC), "stage 1 only ever adds a *hic*: " + hic);
		}
		helper.assertValueEqual(Tipsy.SLUR_LEVEL, 3, "slurring starts at III");
		TestPlayer player = player(helper);
		String message = "where is my horse";
		for (int level = 1; level <= Tipsy.ABSOLUTE_MAX_LEVEL; level++) {
			Tipsy.setLevel(player, level);
			helper.assertValueEqual(Tipsy.slurChat(player, message, 9L), golden[0][level - 1], "level " + level + " chat");
		}
		with(TipsyConfig.SLURRED_CHAT, false, () -> {
			Tipsy.setLevel(player, 5);
			helper.assertValueEqual(Tipsy.slurChat(player, message, 9L), message, "level 5 chat with slurred chat off");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ level 5: stumbling, level 3: hiccups

	/**
	 * At level 5 a stumble lurches sideways, at right angles to walking; below IV or standing still it does nothing. A
	 * player's give to the side comes from their own client, never as a speed the server sends.
	 */
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

		Tipsy.setLevel(player, 3);
		helper.assertFalse(TipsyLegs.stumble(player, walkingEast, random), "No stumbling at level 3");
		Tipsy.setLevel(player, 5);
		helper.assertFalse(TipsyLegs.stumble(player, Vec3.ZERO, random), "No stumbling while standing still");
		helper.assertValueEqual(TipsyLegs.walkingMotion(player), Vec3.ZERO, "A player who isn't moving isn't walking");

		player.setDeltaMovement(Vec3.ZERO);
		player.syncVelocity = false;
		int before = motion(player).stumbles();
		helper.assertTrue(TipsyLegs.stumble(player, walkingEast, random), "Level 5 walking stumbles");
		helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "the server pushes no player");
		helper.assertFalse(player.syncVelocity, "no speed is sent to the player's client");
		helper.assertValueEqual(motion(player).stumbles(), before + 1, "every client is told to play the stumble");
		// the player's own client gives the lurch, from how it moved last
		boolean left = motion(player).stumbleLeft();
		Vec3 nudge = TipsyLegs.ownStumble(player, walkingEast, left);
		helper.assertTrue(Math.abs(nudge.x) < 1.0E-6 && Math.abs(nudge.y) < 1.0E-6, "The lurch is purely sideways, got " + nudge);
		helper.assertTrue(Math.abs(Math.abs(nudge.z) - TipsyLegs.STUMBLE_NUDGE) < 1.0E-6, "The lurch is about a step, got " + nudge);
		// walking east, left is north (-z): the nudge goes the way every client shows the misstep
		helper.assertValueEqual(left, nudge.z < 0, "to the side it lurched");
		helper.assertValueEqual(TipsyLegs.ownStumble(player, Vec3.ZERO, left), Vec3.ZERO, "standing still: no give");
		helper.succeed();
	}

	/**
	 * Audit 1.2: a player who has just stopped at a cliff edge still has, on the server, the last movement their client
	 * reported, toward the edge. A stumble then must not send that back with the give (it carried them half a block over
	 * the edge): the server sends a player no speed at all, and their own client gives only the push to the side, only
	 * where that side is safe.
	 */
	private static void stumbleAtAnEdgeNeverPushes(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		// a drop right ahead (south) and to the left of the walk (east, walking south)
		for (int x = 0; x < 5; x++) {
			helper.setBlock(new BlockPos(x, 0, 3), Blocks.AIR);
			helper.setBlock(new BlockPos(x, 0, 4), Blocks.AIR);
		}
		helper.setBlock(new BlockPos(3, 0, 2), Blocks.AIR);
		TestPlayer player = player(helper);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.8));
		player.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		player.setOnGround(true);
		Tipsy.setLevel(player, 5);
		// stopped at the edge; the server still knows the last step toward it
		Vec3 towardEdge = new Vec3(0.0, 0.0, 0.25);
		player.setKnownMovement(towardEdge);
		player.setDeltaMovement(Vec3.ZERO);
		player.syncVelocity = false;
		RandomSource random = RandomSource.create(4L);
		for (int i = 0; i < 20; i++) {
			TipsyLegs.stumble(player, towardEdge, random);
			helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "nothing carries the player on toward the edge");
			helper.assertFalse(player.syncVelocity, "and nothing is sent to their client");
		}
		// on their client, walking south: the give to the left (east) has a drop there, the right (west) is safe
		helper.assertValueEqual(TipsyLegs.ownStumble(player, towardEdge, true), Vec3.ZERO, "no give toward a drop");
		Vec3 right = TipsyLegs.ownStumble(player, towardEdge, false);
		helper.assertTrue(right.x < 0.0 && Math.abs(right.z) < 1.0E-6, "the safe side still gives, purely sideways: " + right);
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

	// ------------------------------------------------------------------ camera wobble (pure math, client applies it)

	/** One drink more at Tipsy V: out cold, then awake somewhere, sober, hungover and hungry, with a souvenir. */
	private static void blackoutOneTooMany(GameTestHelper helper) {
		TestPlayer player = player(helper);
		player.getFoodData().setFoodLevel(20);
		Tipsy.setLevel(player, 4);
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertFalse(Blackout.isOut(player), "Tipsy IV to V: no blackout yet");
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertFalse(Blackout.isOut(player), "one drink more at V: not on the spot");
		long now = helper.getLevel().getGameTime();
		long at = GoingUnder.hitAt(player);
		helper.assertTrue(at >= now + GoingUnder.SOONEST && at <= now + GoingUnder.LATEST, "it catches up a little later: " + (at - now));
		helper.assertValueEqual(GoingUnder.under(GoingUnder.TICKS + 1.0F), 0.0F, "no going under until its last seconds");
		helper.assertTrue(GoingUnder.under(GoingUnder.TICKS * 0.5F) > 0.0F && GoingUnder.under(0.0F) == 1.0F, "then deeper and deeper");
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertTrue(GoingUnder.hitAt(player) <= at, "another drink never puts it off");
		// its time has come, standing on your feet (a player who has been here a while)
		player.tickCount = 100;
		player.setOnGround(true);
		GoingUnder.comeAt(player, now);
		TipsyEvents.onEntityTick(player);
		helper.assertTrue(Blackout.isOut(player), "and then: blacked out");
		helper.assertValueEqual(GoingUnder.hitAt(player), -1L, "and it is over");
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
		helper.assertValueEqual(Tipsy.getLevel(player), 0, "sober after waking");
		helper.assertValueEqual(player.getEffect(TipsyRegistry.HANGOVER).getDuration(), Hangover.AFTER_BLACKOUT, "and hungover");
		helper.assertTrue(player.getFoodData().getFoodLevel() <= 20 - Blackout.HUNGER_LOST, "woke up hungry: " + player.getFoodData().getFoodLevel());
		helper.assertFalse(player.hasEffect(MobEffects.NAUSEA), "no Nausea after waking");
		MobEffectInstance slowness = player.getEffect(MobEffects.SLOWNESS);
		helper.assertTrue(slowness == null || slowness.getAmplifier() > 0, "no shaky Slowness after waking (only the blackout's own, holding the body still): " + slowness);
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
		net.minecraft.world.entity.monster.zombie.Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(1.5, 1.0, 1.5));
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

	/** Out cold, Tipsy pauses: no hiccups, no punches, no stumbles. */
	private static void blackoutPausesTipsy(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		TipsyRegistry.HICCUPS.set(player, new Tipsy.HiccupBout(3, 0, 0));
		Blackout.start(player);
		helper.assertFalse(TipsyRegistry.HICCUPS.has(player), "the bout of hiccups is over");
		TipsyMotion.Counts before = motion(player);
		// Not as far as the wake-up (that would move the player: BlackoutGameTests covers it)
		for (int i = 0; i < Blackout.TICKS - Blackout.WAKE_AT - 5; i++) {
			TipsyEvents.onEntityTick(player);
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

	/** Going under can be stopped: sobered up before it hits (milk), no blackout. */
	private static void goingUnderSoberedUp(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertTrue(GoingUnder.hitAt(player) >= 0L, "it is coming");
		drink(player, new ItemStack(Items.MILK_BUCKET));
		player.tickCount = 100;
		player.setOnGround(true);
		GoingUnder.comeAt(player, helper.getLevel().getGameTime());
		TipsyEvents.onEntityTick(player);
		helper.assertFalse(Blackout.isOut(player), "sobered up in time: no blackout");
		helper.assertValueEqual(GoingUnder.hitAt(player), -1L, "it is off");
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
		helper.assertTrue(swings[4] > 100 && swings[4] < 210, "about one every 20 s at IV: " + swings[4]);
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
		net.minecraft.world.entity.animal.pig.Pig pig = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.PIG, new Vec3(2.5, 1.0, 3.0));
		TestPlayer player = facing(helper, pig);
		float before = pig.getHealth();
		helper.assertTrue(Rowdy.swing(player) == pig, "the swing hits the pig in front");
		helper.assertTrue(pig.getHealth() < before, "the pig took the punch: " + pig.getHealth());
		helper.assertTrue(before - pig.getHealth() <= Rowdy.PUNCH_DAMAGE + 1.0E-4, "a fist's punch, no more");
		helper.succeed();
	}

	/** Never at thin air: with nobody in front no wild swing at all; with a pig in front, now and then. */
	private static void rowdyOnlyAtSomeone(GameTestHelper helper) {
		TestPlayer alone = player(helper);
		Tipsy.setLevel(alone, 5);
		int swung = 0;
		for (int i = 0; i < 6000; i++) {
			if (jexxme.vineandbarrel.tipsy.RowdyAccess.tick(alone)) swung++;
		}
		helper.assertValueEqual(swung, 0, "nobody in front: never a swing");
		net.minecraft.world.entity.animal.pig.Pig pig = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.PIG, new Vec3(2.5, 1.0, 3.0));
		TestPlayer player = facing(helper, pig);
		Tipsy.setLevel(player, 5);
		for (int i = 0; i < 6000 && Rowdy.windupStart(player) < 0L; i++) {
			if (jexxme.vineandbarrel.tipsy.RowdyAccess.tick(player)) swung++;
		}
		helper.assertValueEqual(swung, 0, "a pig in front: first the arm draws back, no swing yet");
		helper.assertTrue(Rowdy.windupStart(player) >= 0L, "now and then the arm draws back");
		float before = pig.getHealth();
		// the wind-up takes its time: the swing comes once it is over (the world's own ticks, not faked)
		helper.runAfterDelay(Rowdy.WINDUP, () -> {
			helper.assertTrue(jexxme.vineandbarrel.tipsy.RowdyAccess.tick(player), "then the swing");
			helper.assertTrue(pig.getHealth() < before, "and the pig takes the punch");
			helper.assertTrue(Rowdy.windupStart(player) < 0L, "the arm comes forward with it");
			helper.succeed();
		});
	}

	/**
	 * Spoiling for a fight, a glance is enough: the first moment anyone stands in front of you the arm draws back; with
	 * nobody there the itch passes after a while.
	 */
	private static void rowdyAGlanceIsEnough(GameTestHelper helper) {
		net.minecraft.world.entity.animal.pig.Pig pig = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.PIG, new Vec3(2.5, 1.0, 3.0));
		TestPlayer player = facing(helper, pig);
		Tipsy.setLevel(player, 5);
		player.setYRot(player.getYRot() + 180.0F);
		jexxme.vineandbarrel.tipsy.RowdyAccess.itch(player);
		for (int i = 0; i < 40; i++) {
			jexxme.vineandbarrel.tipsy.RowdyAccess.tick(player);
		}
		helper.assertTrue(Rowdy.windupStart(player) < 0L, "looking away: no wind-up");
		player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
		jexxme.vineandbarrel.tipsy.RowdyAccess.tick(player);
		helper.assertTrue(Rowdy.windupStart(player) >= 0L, "one glance at the pig: the arm draws back");
		helper.succeed();
	}

	/**
	 * Rowdy behind a wall picks no fight (no punches through walls or glass), and the swing's step forward never takes
	 * you toward an edge.
	 */
	private static void rowdyWallsAndEdges(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 6; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		net.minecraft.world.entity.animal.pig.Pig pig = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.PIG, new Vec3(2.5, 1.0, 3.5));
		TestPlayer player = facing(helper, pig);
		helper.assertTrue(Rowdy.inFrontForTest(player) == pig, "in the open: the pig is in front");
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.GLASS);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.GLASS);
		helper.assertTrue(Rowdy.inFrontForTest(player) == null, "behind glass: nobody to pick a fight with");
		pig.discard();
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
		// a drop right ahead
		helper.setBlock(new BlockPos(2, 0, 2), Blocks.AIR);
		helper.setBlock(new BlockPos(2, 0, 3), Blocks.AIR);
		player.setOnGround(true);
		player.setDeltaMovement(Vec3.ZERO);
		// the server still knows the last steps sideways and back: never sent back with the swing
		player.setKnownMovement(new Vec3(0.2, 0.0, -0.2));
		player.syncVelocity = false;
		int lunges = motion(player).lunges();
		Rowdy.swing(player);
		helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "the server pushes no player");
		helper.assertFalse(player.syncVelocity, "no speed is sent to the player's client");
		helper.assertValueEqual(motion(player).lunges(), lunges + 1, "the own client is told to take the step");
		helper.assertValueEqual(Rowdy.ownLunge(player), Vec3.ZERO, "no step forward toward the edge");
		// the ground back: a step forward, as far as a lunge goes
		helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 0, 3), Blocks.STONE);
		Vec3 facingFlat = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z).normalize();
		Vec3 step = Rowdy.ownLunge(player);
		helper.assertTrue(Math.abs(step.dot(facingFlat) - Rowdy.LUNGE) < 1.0E-6, "on safe ground the step forward: " + step);
		helper.succeed();
	}

	/** The delayed blackout waits for steady ground: not while gliding or swimming; on your feet it hits. */
	private static void goingUnderWaitsForGround(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		player.tickCount = 100;
		GoingUnder.comeAt(player, helper.getLevel().getGameTime());
		player.setOnGround(false);
		TipsyEvents.onEntityTick(player);
		helper.assertFalse(Blackout.isOut(player), "in the air: it waits");
		helper.assertTrue(GoingUnder.hitAt(player) >= 0L, "still coming");
		player.setOnGround(true);
		TipsyEvents.onEntityTick(player);
		helper.assertTrue(Blackout.isOut(player), "on your feet: it hits");
		TipsyRegistry.BLACKOUT.remove(player);
		// back after time away: the going under plays again instead of an instant blackout
		Tipsy.setLevel(player, 5);
		player.tickCount = 5;
		GoingUnder.comeAt(player, Math.max(0L, helper.getLevel().getGameTime() - 50L));
		TipsyEvents.onEntityTick(player);
		helper.assertFalse(Blackout.isOut(player), "just back: not on the spot");
		helper.assertValueEqual(GoingUnder.hitAt(player), helper.getLevel().getGameTime() + GoingUnder.TICKS, "the going under first");
		helper.succeed();
	}

	/** Never pets, iron golems or armor stands; other players only where PvP allows it. */
	private static void rowdySpares(GameTestHelper helper) {
		net.minecraft.world.entity.animal.wolf.Wolf wolf = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.WOLF, new Vec3(2.5, 1.0, 3.0));
		TestPlayer owner = player(helper);
		wolf.tame(owner);
		TestPlayer player = facing(helper, wolf);
		float before = wolf.getHealth();
		helper.assertTrue(Rowdy.swing(player) == null, "a pet is never punched");
		helper.assertValueEqual(wolf.getHealth(), before, "the wolf's health");
		wolf.discard();
		net.minecraft.world.entity.animal.golem.IronGolem golem = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.IRON_GOLEM, new Vec3(2.5, 1.0, 3.5));
		helper.assertFalse(Rowdy.mayHit(player, golem), "iron golems are never punched");
		golem.discard();
		net.minecraft.world.entity.decoration.ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 3));
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
		WanderingTrader trader = helper.spawn(EntityTypes.WANDERING_TRADER, new BlockPos(2, 1, 2));
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
			"Slurring, Rowdy, No buffs | Slower, Cut off, Stumbling | Tripping",
			"Slurring, Rowdy, No buffs | Slower, Cut off, Stumbling | Tripping"};
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

	/** Non-alcoholic mode switched on mid-game: nobody stays tipsy, and the other clients stop the sway. */
	private static void nonAlcoholicMidGame(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		with(TipsyConfig.ENABLED, false, () -> {
			TipsyEvents.onEntityTick(player);
			TipsyEvents.onEntityTick(player);
			helper.assertValueEqual(Tipsy.getLevel(player), 0, "sober the moment it is switched on");
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

	/** Never where it could go wrong: a monster near, no room ahead, a step down, sneaking or below IV. */
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
		net.minecraft.world.entity.monster.zombie.Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(4.5, 1.0, 4.5));
		helper.assertFalse(TipsyLegs.canTrip(player), "never with a monster near");
		zombie.discard();
		Tipsy.setLevel(player, 3);
		helper.assertFalse(TipsyLegs.trip(player), "never below IV");
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

	/**
	 * The drunk walk's math (Tipsy overhaul, step 2): steering arrives late but arrives, the curve stays within its degrees
	 * and turns both ways, the body tips onto each foot in step with the legs, every other step is slower, the lean leans
	 * into the curve and never past the level's degrees, and two bodies never sway in step.
	 */
	private static void gaitMath(GameTestHelper helper) {
		helper.assertValueEqual(TipsyGait.steer(0.0F, 1.0F, 1.0F), 1.0F, "sober legs take what you press at once");
		for (int level = 1; level <= Tipsy.ABSOLUTE_MAX_LEVEL; level++) {
			TipsyLevels.Legs legs = TipsyLevels.at(level).legs();
			float move = 0.0F;
			int ticks = 0;
			while (move < 0.95F && ticks < 200) {
				move = TipsyGait.steer(move, 1.0F, legs.steer());
				ticks++;
			}
			helper.assertTrue(ticks > 1 && ticks <= 30, "at " + level + " you get going late, but within a second and a half: " + ticks + " ticks");
			float most = 0.0F;
			float least = 0.0F;
			for (float walked = 0.0F; walked < 20.0F; walked += 0.05F) {
				float weave = TipsyGait.weave(walked, TipsyGait.phase(7), legs.weave());
				most = Math.max(most, weave);
				least = Math.min(least, weave);
			}
			helper.assertTrue(most <= legs.weave() + 1.0E-4 && least >= -legs.weave() - 1.0E-4, "the curve stays within its degrees at " + level);
			helper.assertTrue(most > 0.6F * legs.weave() && least < -0.6F * legs.weave(), "and turns both ways at " + level);
			helper.assertValueEqual(TipsyGait.leanTarget(-100.0F, legs.leanDegrees()), legs.leanDegrees(), "the lean stops at the level's degrees");
			helper.assertTrue(TipsyGait.leanTarget(-1.0F, legs.leanDegrees()) > 0.0F, "curving left, the lean goes left");
			helper.assertTrue(TipsyGait.leanTarget(1.0F, legs.leanDegrees()) < 0.0F, "curving right, the lean goes right");
			// a quarter swing after the right leg is forward it carries the body, a half swing later the left one
			float quarter = Mth.HALF_PI / TipsyGait.LEG_SWING;
			helper.assertTrue(TipsyGait.stepRoll(quarter, 1.0F, legs.stepRoll()) < 0.0F
				&& TipsyGait.stepRoll(3.0F * quarter, 1.0F, legs.stepRoll()) > 0.0F, "the body tips onto each foot in turn at " + level);
			helper.assertTrue(Math.abs(TipsyGait.stepRoll(quarter, 1.0F, legs.stepRoll())) <= legs.stepRoll() + 1.0E-4, "never past its degrees at " + level);
			helper.assertTrue(Math.abs(TipsyGait.stepRoll(quarter, 0.0F, legs.stepRoll())) == 0.0F, "standing, no steps");
			float slowest = 1.0F;
			for (float pos = 0.0F; pos < 20.0F; pos += 0.1F) {
				slowest = Math.min(slowest, TipsyGait.stride(pos, legs.stride()));
			}
			helper.assertTrue(slowest >= 1.0F - legs.stride() - 1.0E-4 && slowest > 0.5F, "a slow step is never a stop at " + level);
		}
		helper.assertTrue(TipsyLevels.at(5).legs().steer() < TipsyLevels.at(1).legs().steer(), "the later the level, the later the legs");
		helper.assertValueEqual(TipsyGait.weave(3.0F, 1.0F, 0.0F), 0.0F, "no weave, no swerve");
		helper.assertValueEqual(TipsyGait.leanTarget(5.0F, 0.0F), 0.0F, "sober, no lean");
		helper.assertValueEqual(TipsyGait.stride(3.0F, 0.0F), 1.0F, "sober, even steps");
		helper.assertTrue(Math.abs(TipsyGait.heading(0.0, 1.0, 99.0F)) < 1.0E-3, "walking south is yaw 0");
		helper.assertTrue(Math.abs(TipsyGait.heading(-1.0, 0.0, 99.0F) - 90.0F) < 1.0E-3, "walking west is yaw 90");
		helper.assertValueEqual(TipsyGait.heading(0.0, 0.0, 42.0F), 42.0F, "standing, the heading stays");
		helper.assertFalse(TipsyGait.standingSway(4.0F, 10.0F, TipsyGait.phase(1)) == TipsyGait.standingSway(4.0F, 10.0F, TipsyGait.phase(2)),
			"two bodies don't sway in step");
		float lean = 0.0F;
		for (int i = 0; i < 40; i++) {
			lean = TipsyGait.ease(lean, 3.0F);
		}
		helper.assertTrue(lean > 2.9F && lean <= 3.0F, "a lean gets where it is going within two seconds: " + lean);
		helper.succeed();
	}

	/**
	 * The weave pulls only where it is sure the ground is safe: flat floor at the same height with room to stand, a body
	 * width to the side and on ahead. Never over a drop, into water, lava or fire, against a wall, or onto a block in a
	 * chunk that isn't there.
	 */
	private static void weaveOnlyWhereSafe(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		TestPlayer player = player(helper);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		player.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		player.setOnGround(true);
		// Facing south (+z): left is east (+x), right is west (-x)
		Vec3 ahead = new Vec3(0.0, 0.0, 1.0);
		Vec3 left = new Vec3(1.0, 0.0, 0.0);
		Vec3 right = new Vec3(-1.0, 0.0, 0.0);
		helper.assertTrue(TipsyLegs.safeToWeave(player, left, ahead) && TipsyLegs.safeToWeave(player, right, ahead), "flat stone: both ways");
		helper.setBlock(new BlockPos(3, 0, 1), Blocks.AIR);
		helper.setBlock(new BlockPos(3, 0, 2), Blocks.AIR);
		helper.assertFalse(TipsyLegs.safeToWeave(player, left, ahead), "never over a drop");
		helper.assertTrue(TipsyLegs.safeToWeave(player, right, ahead), "the other way is fine");
		helper.setBlock(new BlockPos(3, 0, 1), Blocks.WATER);
		helper.setBlock(new BlockPos(3, 0, 2), Blocks.WATER);
		helper.assertFalse(TipsyLegs.safeToWeave(player, left, ahead), "never into water");
		helper.setBlock(new BlockPos(3, 0, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 0, 2), Blocks.LAVA);
		helper.assertFalse(TipsyLegs.safeToWeave(player, left, ahead), "never toward lava ahead");
		helper.setBlock(new BlockPos(3, 0, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
		helper.assertFalse(TipsyLegs.safeToWeave(player, right, ahead), "never into a wall");
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.FIRE);
		helper.assertFalse(TipsyLegs.safeToWeave(player, right, ahead), "never into fire");
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
		helper.assertTrue(TipsyLegs.safeToWeave(player, left, ahead), "flat again: fine");
		// Far out, where no chunk is loaded, nothing counts as safe
		player.snapTo(at.x + 100_000.0, at.y, at.z, 0.0F, 0.0F);
		helper.assertFalse(TipsyLegs.safeToWeave(player, left, ahead), "not where the chunk isn't loaded");
		helper.succeed();
	}

	/**
	 * The drunk walk itself ({@link DrunkWalk}, what the client runs each tick) at Tipsy V: on flat ground the late legs
	 * carry on a little after letting go of forward, but at an edge, toward lava or backing off from an edge they stop as
	 * a sober player would; what is pressed always goes.
	 */
	private static void lateLegsStopAtAnEdge(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		TestPlayer player = player(helper);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		// facing south (+z): forward is +z
		player.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		player.setOnGround(true);
		Tipsy.setLevel(player, 5);
		Vec2 forward = new Vec2(0.0F, 1.0F), none = Vec2.ZERO, back = new Vec2(0.0F, -1.0F);
		DrunkWalk walk = new DrunkWalk();
		for (int i = 0; i < 40; i++) {
			walk.step(player, forward, 0.0F);
		}
		helper.assertTrue(walk.step(player, none, 0.0F).y > 0.1F, "flat ground: the late legs carry on a little after letting go");
		// a drop right ahead
		helper.setBlock(new BlockPos(2, 0, 2), Blocks.AIR);
		helper.setBlock(new BlockPos(2, 0, 3), Blocks.AIR);
		walk = new DrunkWalk();
		for (int i = 0; i < 40; i++) {
			helper.assertTrue(walk.step(player, forward, 0.0F).y > 0.0F, "what is pressed always goes, also toward an edge");
		}
		Vec2 letGo = walk.step(player, none, 0.0F);
		helper.assertTrue(Math.abs(letGo.x) < 1.0E-4F && Math.abs(letGo.y) < 1.0E-4F, "letting go at an edge: a sober stop, got " + letGo);
		for (int i = 0; i < 40; i++) {
			walk.step(player, forward, 0.0F);
		}
		helper.assertTrue(walk.step(player, back, 0.0F).y <= 0.0F, "backing off from an edge never carries on toward it");
		// lava ahead
		helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.LAVA);
		walk = new DrunkWalk();
		for (int i = 0; i < 40; i++) {
			walk.step(player, forward, 0.0F);
		}
		Vec2 atLava = walk.step(player, none, 0.0F);
		helper.assertTrue(Math.abs(atLava.y) < 1.0E-4F, "letting go before lava: a sober stop, got " + atLava);
		helper.succeed();
	}

	/**
	 * Nodding off: anything the player does wakes them (an action the server marks: a swing of the arm, mining, attacking,
	 * placing), and so does a slow turn or a slow creep that adds up.
	 */
	private static void noddingOffWakesOnAnything(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		int still = TipsyLevels.at(5).nod().stillTicks();
		Runnable standStill = () -> {
			for (int i = 0; i < still + 2; i++) {
				TipsyEvents.onEntityTick(player);
			}
		};
		standStill.run();
		helper.assertTrue(TipsyNod.start(player) >= 0, "nodding off");
		long before = net.minecraft.util.Util.getMillis();
		while (net.minecraft.util.Util.getMillis() == before) {
			Thread.onSpinWait();
		}
		player.resetLastActionTime();
		TipsyEvents.onEntityTick(player);
		helper.assertValueEqual(TipsyNod.start(player), -1L, "an action (a swing, mining, attacking, placing, a key) wakes you");
		standStill.run();
		for (int i = 0; i < 12; i++) {
			player.setYRot(player.getYRot() + 1.0F);
			TipsyEvents.onEntityTick(player);
		}
		helper.assertValueEqual(TipsyNod.start(player), -1L, "a slow turn adds up and wakes you");
		standStill.run();
		for (int i = 0; i < 20; i++) {
			player.setPos(player.getX() + 0.02, player.getY(), player.getZ());
			TipsyEvents.onEntityTick(player);
		}
		helper.assertValueEqual(TipsyNod.start(player), -1L, "a slow creep adds up and wakes you");
		helper.succeed();
	}

	/** After a trip, the next one waits for the level's own cooldown: longer at IV, shorter at V. */
	private static void tripCooldownPerLevel(GameTestHelper helper) {
		for (int level = 4; level <= 5; level++) {
			TestPlayer player = onFlatGround(helper);
			Tipsy.setLevel(player, level);
			helper.assertTrue(TipsyLegs.trip(player), "a trip at level " + level);
			int cooldown = TipsyLevels.at(level).legs().tripCooldown();
			for (int i = 0; i < TipsyMotion.TRIP_TICKS; i++) {
				TipsyEvents.onEntityTick(player);
			}
			for (int i = TipsyMotion.TRIP_TICKS; i < cooldown - 1; i++) {
				TipsyEvents.onEntityTick(player);
			}
			helper.assertFalse(TipsyLegs.canTrip(player), "still cooling down just before " + cooldown + " ticks at " + level);
			TipsyEvents.onEntityTick(player);
			TipsyEvents.onEntityTick(player);
			helper.assertTrue(TipsyLegs.canTrip(player), "after " + cooldown + " ticks at " + level + " another trip may come");
		}
		helper.assertTrue(TipsyLevels.at(4).legs().tripCooldown() > TipsyLevels.at(5).legs().tripCooldown(), "trips come more often at V");
		helper.succeed();
	}

	/** One nod: sinks slowly, all the way down just before the jerk, the jerk throws the head back up, then a pause awake. */
	private static void nodMath(GameTestHelper helper) {
		int cycle = TipsyLevels.at(5).nod().cycleTicks();
		int jerk = TipsyNod.jerkTick(cycle);
		helper.assertValueEqual(TipsyNod.nod(0.0F, cycle), 0.0F, "a nod starts awake");
		helper.assertTrue(TipsyNod.nod(jerk * 0.25F, cycle) < TipsyNod.nod(jerk * 0.75F, cycle), "and sinks further and further");
		helper.assertTrue(TipsyNod.nod(jerk - 0.5F, cycle) > 0.95F, "all the way down just before the jerk");
		helper.assertTrue(TipsyNod.nod(jerk + cycle * TipsyNod.JERK_SHARE * 0.9F, cycle) < -0.5F, "the jerk throws the head back up");
		helper.assertValueEqual(TipsyNod.nod(cycle - 1.0F, cycle), 0.0F, "then a moment awake");
		helper.assertValueEqual(TipsyNod.nod(cycle + jerk * 0.5F, cycle), TipsyNod.nod(jerk * 0.5F, cycle), "and it comes back");
		helper.assertValueEqual(TipsyNod.nod(-3.0F, cycle), 0.0F, "not before it started");
		helper.succeed();
	}

	/**
	 * Nodding off at IV and V: standing still the level's time starts it, for everyone (the synced start); moving wakes
	 * you; never below IV, never with the server's switch off, and it ends with Tipsy.
	 */
	private static void noddingOff(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 4);
		int still = TipsyLevels.at(4).nod().stillTicks();
		for (int i = 0; i < still + 2; i++) {
			TipsyEvents.onEntityTick(player);
		}
		helper.assertTrue(TipsyNod.start(player) >= 0, "standing still at IV: nodding off");
		player.setPos(player.getX() + 1.0, player.getY(), player.getZ());
		TipsyEvents.onEntityTick(player);
		helper.assertValueEqual(TipsyNod.start(player), -1L, "a step wakes you");
		for (int i = 0; i < still + 2; i++) {
			TipsyEvents.onEntityTick(player);
		}
		helper.assertTrue(TipsyNod.start(player) >= 0, "standing on, it comes back");
		player.setYRot(player.getYRot() + 30.0F);
		TipsyEvents.onEntityTick(player);
		helper.assertValueEqual(TipsyNod.start(player), -1L, "turning the head wakes you too");
		Tipsy.setLevel(player, 3);
		for (int i = 0; i < 400; i++) {
			TipsyEvents.onEntityTick(player);
		}
		helper.assertValueEqual(TipsyNod.start(player), -1L, "never below IV");
		Tipsy.setLevel(player, 5);
		with(TipsyConfig.NODDING_OFF, false, () -> {
			for (int i = 0; i < 400; i++) {
				TipsyEvents.onEntityTick(player);
			}
			helper.assertValueEqual(TipsyNod.start(player), -1L, "never with nodding off switched off");
		});
		for (int i = 0; i < TipsyLevels.at(5).nod().stillTicks() + 2; i++) {
			TipsyEvents.onEntityTick(player);
		}
		helper.assertTrue(TipsyNod.start(player) >= 0, "at V too");
		Tipsy.clear(player);
		helper.assertValueEqual(TipsyNod.start(player), -1L, "sober: awake");
		helper.succeed();
	}

	/**
	 * The level table (Tipsy overhaul): every section has one row per level and the levels are put together from them,
	 * each part gets worse level by level, and the thresholds the game and the wiki read.
	 */
	private static void levelsTable(GameTestHelper helper) {
		helper.assertValueEqual(TipsyLevels.sections().size(), TipsyLevels.Level.class.getRecordComponents().length - 1, "every section is listed");
		for (TipsyLevels.Section section : TipsyLevels.sections()) {
			helper.assertValueEqual(section.rows().size(), Tipsy.ABSOLUTE_MAX_LEVEL, "a row per level in " + section.name());
			for (TipsyLevels.Level level : TipsyLevels.LEVELS) {
				helper.assertTrue(section.of().apply(level) == section.rows().get(level.level() - 1), section.name() + "'s row of level " + level.level());
			}
		}
		for (int level = 2; level <= Tipsy.ABSOLUTE_MAX_LEVEL; level++) {
			TipsyLevels.Level at = TipsyLevels.at(level);
			TipsyLevels.Level below = TipsyLevels.at(level - 1);
			helper.assertTrue(at.legs().swayDegrees() > below.legs().swayDegrees() && at.legs().weave() > below.legs().weave()
				&& at.legs().steer() < below.legs().steer() && at.legs().leanDegrees() > below.legs().leanDegrees(), "the legs get worse at " + level);
			helper.assertTrue(at.eyes().softEdges() >= below.eyes().softEdges() && at.eyes().doubleVision() >= below.eyes().doubleVision()
				&& at.eyes().signBlur() >= below.eyes().signBlur(), "the eyes get worse at " + level);
			helper.assertTrue(at.ears().muffle() >= below.ears().muffle(), "the ears get worse at " + level);
			helper.assertValueEqual(at.chat().stage(), level - 1, "the chat stage at " + level);
		}
		helper.assertTrue(TipsyLevels.at(3).courage().any() && !TipsyLevels.at(4).courage().any(), "courage up to III, none from IV");
		helper.assertValueEqual(Tipsy.HICCUP_LEVEL, 2, "hiccups from II");
		helper.assertValueEqual(Tipsy.SLUR_LEVEL, 3, "slurring from III");
		helper.assertValueEqual(Tipsy.NO_BUFF_LEVEL, 4, "no buffs from IV");
		helper.assertValueEqual(Rowdy.ROWDY_LEVEL, 4, "Rowdy from IV");
		helper.assertValueEqual(Rowdy.CUT_OFF_LEVEL, 4, "cut off from IV");
		helper.assertValueEqual(TipsyLevels.first(level -> level.nod().any()), 4, "nodding off from IV");
		helper.assertValueEqual(Tipsy.STUMBLE_LEVEL, 4, "unsteady legs from IV");
		helper.assertValueEqual(jexxme.vineandbarrel.tipsy.TipsySounds.HEARTBEAT_LEVEL, 5, "the heartbeat at V");
		helper.assertTrue(TipsyLevels.at(0) == TipsyLevels.SOBER && TipsyLevels.at(9) == TipsyLevels.at(5), "sober below I, V above V");
		helper.succeed();
	}

	/**
	 * Being drunk costs control, never stats (Tipsy overhaul): at no level does Tipsy give Nausea, Slowness, Mining
	 * Fatigue or Weakness, a potion's stays as it was when Tipsy ends, and an old save's mark of Tipsy's own side
	 * effects goes.
	 */
	private static void noSideEffects(GameTestHelper helper) {
		TestPlayer player = player(helper);
		List<Holder<MobEffect>> gone = List.of(MobEffects.NAUSEA, MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS);
		for (int level = 1; level <= Tipsy.ABSOLUTE_MAX_LEVEL; level++) {
			Tipsy.setLevel(player, level);
			for (int i = 0; i < 400; i++) {
				TipsyEvents.onEntityTick(player);
				for (Holder<MobEffect> effect : gone) {
					helper.assertFalse(player.hasEffect(effect), "no " + effect.getRegisteredName() + " at level " + level);
				}
			}
		}
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1800, 0));
		TipsyEvents.onEntityTick(player);
		Tipsy.clear(player);
		TipsyEvents.onEntityTick(player);
		MobEffectInstance weakness = player.getEffect(MobEffects.WEAKNESS);
		helper.assertTrue(weakness != null && weakness.getDuration() > 1000, "a potion's Weakness stays when Tipsy ends: " + weakness);
		Tipsy.setLevel(player, 1);
		TipsyRegistry.HEAVY_EFFECTS_OWNED.set(player, 0b1111);
		TipsyEvents.onEntityTick(player);
		helper.assertFalse(TipsyRegistry.HEAVY_EFFECTS_OWNED.has(player), "an old save's mark of Tipsy's own side effects goes");
		helper.succeed();
	}

	/**
	 * The two renamed client settings take an old file's value, once: {@code wobbleIntensity} (0 to 2, 1 the default)
	 * as {@code drunkScreen} in percent, {@code visionEffects} as {@code doubleVision}. The new key wins when both are there.
	 */
	private static void renamedSettings(GameTestHelper helper) {
		java.util.function.BiFunction<ConfigValue<?>, java.util.Map<String, String>, String> read = (value, saved) -> value.savedText(saved::get);
		helper.assertValueEqual(TipsyConfig.DRUNK_SCREEN.formerName(), "wobbleIntensity", "drunkScreen was wobbleIntensity");
		helper.assertValueEqual(TipsyConfig.DOUBLE_VISION.formerName(), "visionEffects", "doubleVision was visionEffects");
		String[][] wobble = {{"1.0", "100"}, {"1", "100"}, {"0.4", "40"}, {"0.0", "0"}, {"2.0", "100"}, {"1.5", "100"}};
		for (String[] pair : wobble) {
			helper.assertValueEqual(read.apply(TipsyConfig.DRUNK_SCREEN, java.util.Map.of("wobbleIntensity", pair[0])), pair[1], "wobbleIntensity " + pair[0]);
		}
		for (String bad : new String[] {"3.0", "-1", "lots"}) {
			helper.assertTrue(read.apply(TipsyConfig.DRUNK_SCREEN, java.util.Map.of("wobbleIntensity", bad)) == null, "a broken old value gives the default: " + bad);
		}
		helper.assertValueEqual(read.apply(TipsyConfig.DRUNK_SCREEN, java.util.Map.of("drunkScreen", "70", "wobbleIntensity", "0")), "70", "the new key wins");
		helper.assertTrue(read.apply(TipsyConfig.DRUNK_SCREEN, java.util.Map.of()) == null, "neither key: the default");
		helper.assertValueEqual(read.apply(TipsyConfig.DOUBLE_VISION, java.util.Map.of("visionEffects", "false")), "false", "vision effects off: double vision off");
		helper.assertValueEqual(read.apply(TipsyConfig.DOUBLE_VISION, java.util.Map.of("visionEffects", "TRUE")), "true", "vision effects on: double vision on");
		helper.assertTrue(read.apply(TipsyConfig.DOUBLE_VISION, java.util.Map.of("visionEffects", "maybe")) == null, "a broken old value gives the default");
		helper.assertTrue(read.apply(TipsyConfig.OTHER_PLAYERS_SWAY, java.util.Map.of("wobbleIntensity", "0")) == null, "a setting that was never renamed reads only its own key");
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
