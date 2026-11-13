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
import jexxme.vineandbarrel.tipsy.TipsyEffect;
import jexxme.vineandbarrel.tipsy.TipsyEvents;
import jexxme.vineandbarrel.tipsy.TipsyRegistry;
import jexxme.vineandbarrel.tipsy.TipsySlur;
import jexxme.vineandbarrel.tipsy.TipsyInfo;
import jexxme.vineandbarrel.tipsy.TipsyLegs;
import jexxme.vineandbarrel.tipsy.TipsyLevels;
import jexxme.vineandbarrel.tipsy.TipsyMotion;
import jexxme.vineandbarrel.tipsy.TipsyWobble;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import jexxme.vineandbarrel.platform.GameTestRegistrar;

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
	private static final ResourceLocation STRUCTURE = VineAndBarrel.id("gametest/empty_5x5");
	private static final List<Entry> TESTS = new ArrayList<>();

	/** {@code batch}: the test's 26.x environment ("tipsy", or "tipsy_legs" for the trip tests). */
	private record Entry(String batch, String name, Consumer<GameTestHelper> function) {
	}

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
		test("tipsy_hiccups", TipsyGameTests::hiccups);
		test("tipsy_body_sway", TipsyGameTests::bodySway);
		test("tipsy_levels_table", TipsyGameTests::levelsTable);
		test("tipsy_no_side_effects", TipsyGameTests::noSideEffects);
		test("tipsy_renamed_settings", TipsyGameTests::renamedSettings);
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
		test("tipsy_motion_math", TipsyGameTests::motionMath);
		test("tipsy_blackout_damage_rules", TipsyGameTests::blackoutDamageRules);
		test("tipsy_blackout_pauses_tipsy", TipsyGameTests::blackoutPausesTipsy);
		test("tipsy_drink_buffs_forget_the_old_ones", TipsyGameTests::drinkBuffsForgetTheOldOnes);
		test("tipsy_non_alcoholic_mid_game", TipsyGameTests::nonAlcoholicMidGame);
		legsTest("tipsy_trip", TipsyGameTests::trip);
		legsTest("tipsy_trip_only_where_safe", TipsyGameTests::tripOnlyWhereSafe);
		legsTest("tipsy_trip_never_sliding", TipsyGameTests::tripNeverSliding);
		test("tipsy_drink_buffs_through_cheers_beacons_and_relogs", TipsyGameTests::drinkBuffsThroughCheersBeaconsAndRelogs);
		test("tipsy_comes_home_from_the_end", TipsyGameTests::comesHomeFromTheEnd);
	}

	private TipsyGameTests() {
	}

	private static void test(String name, Consumer<GameTestHelper> function) {
		TESTS.add(new Entry("tipsy", name, function));
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
		TESTS.add(new Entry("tipsy_legs", name, function));
	}

	public static void register() {
		jexxme.vineandbarrel.platform.Services.PLATFORM.addGameTests(TipsyGameTests::registerTests);
	}

	private static void registerTests(GameTestRegistrar event) {
		for (Entry test : TESTS) {
			VBGameTests.add(event, test.batch(), test.name(), STRUCTURE, 100, test.function());
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
			super(server, level, profile);
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

		/**
		 * The 60 ticks after joining in which a player can't be hurt at all, waited out with the player's own tick (26.x:
		 * until its client had loaded the world).
		 */
		void waitOutSpawnProtection() {
			for (int i = 0; i < 60; i++) {
				this.tick();
			}
		}

		/** Drinks what is in the main hand exactly the way the game finishes using an item. */
		void drinkMainHand() {
			this.startUsingItem(InteractionHand.MAIN_HAND);
			this.completeUsingItem();
		}
	}

	static TestPlayer player(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestPlayer player = new TestPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "tipsy-test-player"));
		GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		jexxme.vineandbarrel.platform.Services.PLATFORM.prepareMockConnection(connection);
		new ServerGamePacketListenerImpl(level.getServer(), connection, player);
		Vec3 pos = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.setPos(pos.x, pos.y, pos.z);
		return player;
	}

	/**
	 * Runs {@code body} with a config value temporarily at {@code setting}, also when an assertion fails. In memory
	 * only ({@link ConfigValue#setForTest}): the config file is never written.
	 */
	static <T> void with(ConfigValue<T> value, T setting, Runnable body) {
		value.setForTest(setting);
		try {
			body.run();
		} finally {
			value.setForTest(null);
		}
	}

	/** A water bottle (1.20.1: the potion is in the stack's tag). */
	static ItemStack water(Item item, net.minecraft.world.item.alchemy.Potion potion) {
		return PotionUtils.setPotion(new ItemStack(item), potion);
	}

	/** GameTestHelper#assertValueEqual of 1.21.1 (1.20.1 has none). */
	static <T> void assertValueEqual(GameTestHelper helper, T actual, T expected, String name) {
		if (!java.util.Objects.equals(actual, expected)) {
			throw VBGameTests.assertionException(helper, "Expected " + name + " to be " + expected + ", but was " + actual);
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
		assertValueEqual(helper, Tipsy.getLevel(player), 0, "level before drinking");
		with(TipsyConfig.MAX_LEVEL, 5, () -> {
			for (int i = 1; i <= 5; i++) {
				helper.assertTrue(Tipsy.addLevel(player), "addLevel should act while Tipsy is on");
				assertValueEqual(helper, Tipsy.getLevel(player), i, "level after drink " + i);
			}
			Tipsy.addLevel(player);
			Tipsy.addLevel(player);
		});
		assertValueEqual(helper, Tipsy.getLevel(player), 5, "level after 7 drinks (cap)");
		helper.succeed();
	}

	/** The server's max level caps stacking. */
	private static void capIsConfigurable(GameTestHelper helper) {
		TestPlayer player = player(helper);
		with(TipsyConfig.MAX_LEVEL, 3, () -> {
			for (int i = 0; i < 6; i++) Tipsy.addLevel(player);
		});
		assertValueEqual(helper, Tipsy.getLevel(player), 3, "level with maxLevel = 3");
		helper.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Level 3 must not slow you down");
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
		assertValueEqual(helper, Tipsy.getLevel(player), 2, "level after two drinks");
		assertValueEqual(helper, player.getEffect(TipsyRegistry.TIPSY.get()).getDuration(), perLevel, "timer shown for the top level");

		tick(player, perLevel - 1);
		assertValueEqual(helper, Tipsy.getLevel(player), 2, "level one tick before the first drop");
		tick(player, 1);
		assertValueEqual(helper, Tipsy.getLevel(player), 1, "level right after " + perLevel + " ticks");
		assertValueEqual(helper, player.getEffect(TipsyRegistry.TIPSY.get()).getDuration(), perLevel, "level 1 gets a full interval");
		tick(player, perLevel - 1);
		assertValueEqual(helper, Tipsy.getLevel(player), 1, "level one tick before sobering up");
		tick(player, 1);
		assertValueEqual(helper, Tipsy.getLevel(player), 0, "level after " + 2 * perLevel + " ticks");
		helper.succeed();
	}

	private static void tick(TestPlayer player, int ticks) {
		for (int i = 0; i < ticks; i++) player.tickEffectsOnce();
	}

	/** A short particleless Slowness I from a command ({@code /effect give @s slowness 3 0 true}) is not Tipsy's to take. */
	private static void soberKeepsCommandSlowness(GameTestHelper helper) {
		TestPlayer player = player(helper);
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, true));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "A sober player keeps a command's Slowness");
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ cures

	/** Drinking milk clears Tipsy, through vanilla's own milk mechanism. */
	private static void milkClears(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		drink(player, new ItemStack(Items.MILK_BUCKET));
		assertValueEqual(helper, Tipsy.getLevel(player), 0, "level after milk");
		helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "The empty bucket comes back");
		helper.succeed();
	}

	/** A water bottle lowers Tipsy by exactly one, and the level below keeps the time it had left. */
	private static void waterLowersByOne(GameTestHelper helper) {
		TestPlayer player = player(helper);
		int perLevel = TipsyConfig.ticksPerLevel();
		Tipsy.setLevel(player, 3);
		drink(player, water(Items.POTION, Potions.WATER));
		assertValueEqual(helper, Tipsy.getLevel(player), 2, "level after a water bottle");
		assertValueEqual(helper, player.getEffect(TipsyRegistry.TIPSY.get()).getDuration(), 2 * perLevel, "time left on level 2");
		helper.assertTrue(player.getMainHandItem().is(Items.GLASS_BOTTLE), "The glass bottle comes back");

		drink(player, water(Items.POTION, Potions.WATER));
		drink(player, water(Items.POTION, Potions.WATER));
		assertValueEqual(helper, Tipsy.getLevel(player), 0, "level after three water bottles");
		drink(player, water(Items.POTION, Potions.WATER));
		assertValueEqual(helper, Tipsy.getLevel(player), 0, "water when sober changes nothing");
		helper.succeed();
	}

	/** Only plain water counts among potions; the tag is the hook for M2's grape juice. */
	/** Cross-milestone (M2 + M4): M2's grape juice is a sobering drink, so drinking it lowers Tipsy by exactly one. */
	private static void grapeJuiceLowersByOne(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 3);
		drink(player, new ItemStack(BaseItems.GRAPE_JUICE.get()));
		assertValueEqual(helper, Tipsy.getLevel(player), 2, "level after grape juice");
		helper.assertTrue(player.getMainHandItem().is(Items.GLASS_BOTTLE), "The glass bottle comes back");
		// Every juice takes the edge off, one level each (Jexxme, new drinks); a wort or a must is not drunk at all
		for (Item juice : List.of(BaseItems.WHITE_GRAPE_JUICE.get(), BaseItems.SWEET_BERRY_JUICE.get(), BaseItems.APPLE_JUICE.get())) {
			helper.assertTrue(Tipsy.isSoberingDrink(new ItemStack(juice)), juice + " sobers you up");
			int before = Tipsy.getLevel(player);
			drink(player, new ItemStack(juice));
			assertValueEqual(helper, Tipsy.getLevel(player), Math.max(0, before - 1), "level after " + juice);
		}
		assertValueEqual(helper, Tipsy.getLevel(player), 0, "four juices from level 3 leave you sober, and no lower");
		helper.assertFalse(Tipsy.isSoberingDrink(new ItemStack(BaseItems.HONEY_MUST_BOTTLE.get())), "Honey must is not a juice");
		helper.succeed();
	}

	private static void soberingTagRules(GameTestHelper helper) {
		helper.assertTrue(Tipsy.isSoberingDrink(water(Items.POTION, Potions.WATER)), "Water bottle sobers");
		helper.assertFalse(Tipsy.isSoberingDrink(water(Items.POTION, Potions.AWKWARD)), "An awkward potion is not water");
		helper.assertFalse(Tipsy.isSoberingDrink(water(Items.POTION, Potions.SWIFTNESS)), "A potion of swiftness is not water");
		helper.assertFalse(Tipsy.isSoberingDrink(water(Items.SPLASH_POTION, Potions.WATER)), "A splash bottle isn't drunk");
		helper.assertFalse(Tipsy.isSoberingDrink(new ItemStack(Items.MILK_BUCKET)), "Milk clears via vanilla, it is not in the tag");

		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 2);
		drink(player, water(Items.POTION, Potions.AWKWARD));
		assertValueEqual(helper, Tipsy.getLevel(player), 2, "level after an awkward potion");
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
		assertValueEqual(helper, Tipsy.getLevel(player), 4, "level after leaving the bed early");

		player.startSleeping(helper.absolutePos(head));
		// What ServerLevel#wakeUpAllPlayers calls once the night has been slept through
		player.stopSleepInBed(false, false);
		helper.assertFalse(player.isSleeping(), "Player should be awake");
		assertValueEqual(helper, Tipsy.getLevel(player), 0, "level after sleeping through the night");
		helper.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "No Slowness after sleeping");
		helper.succeed();
	}

	/** Non-alcoholic mode: drinks never make you tipsy. */
	private static void nonAlcoholicMode(GameTestHelper helper) {
		TestPlayer player = player(helper);
		with(TipsyConfig.ENABLED, false, () -> {
			for (int i = 0; i < 5; i++) {
				helper.assertFalse(Tipsy.addLevel(player), "addLevel must be a no-op in non-alcoholic mode");
			}
			assertValueEqual(helper, Tipsy.getLevel(player), 0, "level in non-alcoholic mode");
			helper.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "No Slowness in non-alcoholic mode");
		});
		helper.assertTrue(Tipsy.addLevel(player), "addLevel works again with Tipsy on");
		assertValueEqual(helper, Tipsy.getLevel(player), 1, "level with Tipsy back on");
		helper.succeed();
	}

	// ------------------------------------------------------------------ level 4: slurred chat

	/**
	 * The slur is a pure function: same message and seed, same result (golden values pin it), and it always changes a
	 * message that fits (when no letter changes, by a trailing *hic*).
	 */
	private static void slurDeterministic(GameTestHelper helper) {
		String message = "I love this song so much, cheers everyone";
		assertValueEqual(helper, TipsySlur.slur(message, 1L), "I looove thiiis song shooo much, cheersh everyone", "slur, seed 1");
		assertValueEqual(helper, TipsySlur.slur(message, 42L), "I love this shong so much, cheeeers eveeeryone", "slur, seed 42");
		assertValueEqual(helper, TipsySlur.slur(message, 7L), "I love this sooong *hic* sho much, cheers everyone", "slur, seed 7");
		assertValueEqual(helper, TipsySlur.slur(message, 42L), TipsySlur.slur(message, 42L), "same seed twice");

		Set<String> outputs = new HashSet<>();
		for (long seed = 0; seed < 20; seed++) {
			String slurred = TipsySlur.slur(message, seed);
			helper.assertFalse(slurred.equals(message), "A slurred message always differs (seed " + seed + ")");
			helper.assertTrue(slurred.length() <= TipsySlur.MAX_LENGTH, "Slurred chat stays within the chat limit");
			outputs.add(slurred);
		}
		helper.assertTrue(outputs.size() > 10, "Different seeds slur differently");

		assertValueEqual(helper, TipsySlur.slur("go to https://example.com and /home now", 3L), "go *hic* tooo https://example.com and /home nooow", "links and commands stay intact");
		assertValueEqual(helper, TipsySlur.slur("   ", 3L), "   ", "blank stays blank");
		String longMessage = "s".repeat(TipsySlur.MAX_LENGTH);
		assertValueEqual(helper, TipsySlur.slur(longMessage, 3L), longMessage, "a full-length message is left alone");
		// When no letter happens to change, the slur still shows: a trailing *hic* (about 1 seed in 12 for this message,
		// seed 7 among them). The smoke test's chat check once took such a line for an unslurred one.
		String toast = "cheers to the cellar";
		assertValueEqual(helper, TipsySlur.slur(toast, 7L), toast + " " + TipsySlur.HIC, "no letter changed: a trailing *hic*");
		for (long seed = 0; seed < 2000; seed++) {
			helper.assertFalse(TipsySlur.slur(toast, seed).equals(toast), "a slurred message never comes back as typed (seed " + seed + ")");
		}
		helper.succeed();
	}

	/** Chat slurs from level 3 (peak drunk) on, and never with the server's slurred-chat switch off. */
	private static void slurLevelAndConfig(GameTestHelper helper) {
		TestPlayer player = player(helper);
		String message = "hello friends";
		assertValueEqual(helper, Tipsy.SLUR_LEVEL, 3, "slurring starts at III");
		Tipsy.setLevel(player, 2);
		assertValueEqual(helper, Tipsy.slurChat(player, message, 9L), message, "level 2 chat");
		Tipsy.setLevel(player, 3);
		assertValueEqual(helper, Tipsy.slurChat(player, message, 9L), TipsySlur.slur(message, 9L), "level 3 chat");
		// through the server's real chat path, as a typed message goes
		String decorated = jexxme.vineandbarrel.platform.Services.PLATFORM.decorateChat(player, Component.literal(message)).getString();
		helper.assertFalse(decorated.equals(message), "level 3 chat through the server's chat decorator is slurred: " + decorated);
		with(TipsyConfig.SLURRED_CHAT, false, () -> {
			Tipsy.setLevel(player, 5);
			assertValueEqual(helper, Tipsy.slurChat(player, message, 9L), message, "level 5 chat with slurred chat off");
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
		assertValueEqual(helper, TipsyLegs.walkingMotion(player), Vec3.ZERO, "A player who isn't moving isn't walking");

		player.setDeltaMovement(Vec3.ZERO);
		int before = motion(player).stumbles();
		helper.assertTrue(TipsyLegs.stumble(player, walkingEast, random), "Level 5 walking stumbles");
		Vec3 nudge = player.getDeltaMovement();
		helper.assertTrue(Math.abs(nudge.x) < 1.0E-6 && Math.abs(nudge.y) < 1.0E-6, "The lurch is purely sideways, got " + nudge);
		helper.assertTrue(Math.abs(Math.abs(nudge.z) - TipsyLegs.STUMBLE_NUDGE) < 1.0E-6, "The lurch is about a step, got " + nudge);
		helper.assertTrue(player.hurtMarked, "The lurch is sent to the player's client");
		assertValueEqual(helper, motion(player).stumbles(), before + 1, "every client is told to play the stumble");
		assertValueEqual(helper, motion(player).stumbleLeft(), nudge.z > 0, "to the side it lurched");
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
		assertValueEqual(helper, player.getDeltaMovement(), Vec3.ZERO, "motion with stumbling off");
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
		assertValueEqual(helper, Tipsy.HICCUP_LEVEL, 2, "hiccups start at II");
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
		assertValueEqual(helper, player.getDeltaMovement(), Vec3.ZERO, "A hiccup moves nothing (no hop any more)");
		assertValueEqual(helper, player.getHealth(), health, "health after a hiccup (harmless)");
		assertValueEqual(helper, motion(player).hiccups(), before + 1, "every client is told to play the jolt");
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
			helper.assertTrue(Math.abs(TipsyEffect.fallDamageFactor(player, 1.0) - fall[level]) < EPS,
					"fall damage multiplier at level " + level + ": " + TipsyEffect.fallDamageFactor(player, 1.0));
		}
		// Sobering down from 4 to 3 brings the courage back; milk takes it away with the effect
		Tipsy.setLevel(player, 4);
		Tipsy.lowerLevel(player);
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 0.30) < EPS, "courage back at level 3");
		drink(player, new ItemStack(Items.MILK_BUCKET));
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) < EPS, "no courage after milk");
		helper.assertTrue(Math.abs(TipsyEffect.fallDamageFactor(player, 1.0) - 1.0) < EPS, "normal falls after milk");
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
		assertValueEqual(helper, Tipsy.getLevel(player), 1, "Tipsy after waking");
		helper.assertTrue(player.getFoodData().getFoodLevel() <= 20 - Blackout.HUNGER_LOST, "woke up hungry: " + player.getFoodData().getFoodLevel());
		helper.assertFalse(player.hasEffect(MobEffects.CONFUSION), "no Nausea after waking");
		MobEffectInstance slowness = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(slowness == null || slowness.getAmplifier() > 0, "no shaky Slowness after waking (only the blackout's own, holding the body still): " + slowness);
		assertValueEqual(helper, countItems(player), itemsBefore + 1, "a mystery souvenir in the pockets");
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
		// For 60 ticks after joining a player can't be hurt at all; this one has waited them out
		player.waitOutSpawnProtection();
		Tipsy.setLevel(player, 5);
		Blackout.start(player);
		helper.assertTrue(Blackout.isOut(player), "out cold");
		net.minecraft.world.entity.monster.Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(1.5, 1.0, 1.5));
		TestPlayer other = player(helper);
		var sources = level.damageSources();
		float health = player.getHealth();
		player.hurt(sources.mobAttack(zombie), 4.0F);
		assertValueEqual(helper, player.getHealth(), health, "a zombie can't hurt you while you're out");
		player.hurt(sources.playerAttack(other), 4.0F);
		assertValueEqual(helper, player.getHealth(), health, "nor can another player");
		player.hurt(sources.fall(), 4.0F);
		assertValueEqual(helper, player.getHealth(), health, "nor the fall");
		zombie.discard();
		helper.assertFalse(TipsyEvents.cancelsIncomingDamage(player, sources.genericKill()), "/kill still kills");
		helper.assertFalse(TipsyEvents.cancelsIncomingDamage(player, sources.fellOutOfWorld()), "the void still counts");
		helper.assertFalse(TipsyEvents.cancelsIncomingDamage(player, sources.starve()), "starving still counts");
		player.hurt(sources.mobProjectile(zombie, zombie), 4.0F);
		assertValueEqual(helper, player.getHealth(), health, "nor a mob's arrow");
		player.hurt(sources.thrown(zombie, zombie), 4.0F);
		assertValueEqual(helper, player.getHealth(), health, "nor anything a mob throws");
		player.hurt(sources.lava(), 2.0F);
		helper.assertTrue(player.getHealth() < health, "lava still burns: " + player.getHealth());
		// Each a little harder than the last, so the half-second of hurt-immunity after a hit lets it through
		float afterLava = player.getHealth();
		player.hurt(sources.inFire(), 3.0F);
		helper.assertTrue(player.getHealth() < afterLava, "fire still burns: " + player.getHealth());
		float afterFire = player.getHealth();
		player.hurt(sources.drown(), 4.0F);
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
		assertValueEqual(helper, motion(player), before, "no hiccup, stumble or trip while out");
		helper.assertTrue(Blackout.isOut(player), "still out");
		helper.succeed();
	}

	/** The items in the pockets (not what is worn: a blackout may add a pumpkin hat). */
	private static int countItems(TestPlayer player) {
		int count = 0;
		for (ItemStack stack : player.getInventory().items) {
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
			assertValueEqual(helper, Tipsy.getLevel(player), 5, "still Tipsy V");
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
		net.minecraft.world.entity.animal.Pig pig = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, new Vec3(2.5, 1.0, 3.0));
		TestPlayer player = facing(helper, pig);
		float before = pig.getHealth();
		helper.assertTrue(Rowdy.swing(player) == pig, "the swing hits the pig in front");
		helper.assertTrue(pig.getHealth() < before, "the pig took the punch: " + pig.getHealth());
		helper.assertTrue(before - pig.getHealth() <= Rowdy.PUNCH_DAMAGE + 1.0E-4, "a fist's punch, no more");
		helper.succeed();
	}

	/** Never pets, iron golems or armor stands; other players only where PvP allows it. */
	private static void rowdySpares(GameTestHelper helper) {
		net.minecraft.world.entity.animal.Wolf wolf = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.WOLF, new Vec3(2.5, 1.0, 3.0));
		TestPlayer owner = player(helper);
		wolf.tame(owner);
		TestPlayer player = facing(helper, wolf);
		float before = wolf.getHealth();
		helper.assertTrue(Rowdy.swing(player) == null, "a pet is never punched");
		assertValueEqual(helper, wolf.getHealth(), before, "the wolf's health");
		wolf.discard();
		net.minecraft.world.entity.animal.IronGolem golem = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.IRON_GOLEM, new Vec3(2.5, 1.0, 3.5));
		helper.assertFalse(Rowdy.mayHit(player, golem), "iron golems are never punched");
		golem.discard();
		net.minecraft.world.entity.decoration.ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 3));
		helper.assertFalse(Rowdy.mayHit(player, stand), "armor stands are never punched");
		TestPlayer other = player(helper);
		assertValueEqual(helper, Rowdy.mayHit(player, other), player.canHarmPlayer(other), "another player only where PvP allows it");
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
		WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(2, 1, 2));
		trader.setNoAi(true);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 3.8));
		player.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
		// A click as the client sends it on this Minecraft: "interact at" first, and plain "interact" unless that already did
		// something (here: opened the trades or got the head shake)
		Runnable click = () -> {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(trader, false, InteractionHand.MAIN_HAND, Vec3.ZERO));
			if (!(player.containerMenu instanceof MerchantMenu) && trader.getUnhappyCounter() == 0) {
				player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(trader, false, InteractionHand.MAIN_HAND));
			}
		};

		Tipsy.setLevel(player, 3);
		click.run();
		helper.assertTrue(player.containerMenu instanceof MerchantMenu, "Tipsy III: the trades open, the menu is " + player.containerMenu);
		assertValueEqual(helper, trader.getUnhappyCounter(), 0, "Tipsy III: no head shake");
		player.closeContainer();

		Tipsy.setLevel(player, 4);
		click.run();
		helper.assertFalse(player.containerMenu instanceof MerchantMenu, "Tipsy IV: no trades");
		helper.assertTrue(trader.getUnhappyCounter() > 0, "Tipsy IV: the trader shakes its head");

		trader.setUnhappyCounter(0);
		player.spectator = true;
		click.run();
		assertValueEqual(helper, trader.getUnhappyCounter(), 0, "a spectator at Tipsy IV is not refused");
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
			"Slurring, Rowdy, No buffs | Cut off",
			"Slurring, Rowdy, No buffs | Cut off, Stumbling, Tripping"};
		for (int level = 1; level <= 5; level++) {
			assertValueEqual(helper, text(TipsyInfo.lines(level)), expected[level], "the tooltip at level " + level);
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
		helper.assertTrue(player.hasEffect(MobEffects.DIG_SPEED), "level 2 to 3: the ale still gives Haste");
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		assertValueEqual(helper, Tipsy.getLevel(player), Tipsy.NO_BUFF_LEVEL, "level after the fourth drink");
		helper.assertFalse(player.hasEffect(MobEffects.DIG_SPEED), "level 4: too drunk, and the earlier ale's Haste is gone too");
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1000, 0));
		drink(player, new ItemStack(DrinkItems.AGED_SWEET_BERRY_WINE.get()));
		MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
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
		assertValueEqual(helper, Tipsy.getLevel(player), Tipsy.NO_BUFF_LEVEL, "level after the drink too many");
		MobEffectInstance apple = player.getEffect(MobEffects.ABSORPTION);
		helper.assertTrue(apple != null && apple.getAmplifier() == 1, "the golden apple's Absorption stays: " + apple);
		assertValueEqual(helper, player.getAbsorptionAmount(), 0.0F, "no hearts back from the drink too many");

		// The same with half the hearts left: they stay as they were
		TestPlayer half = player(helper);
		half.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 1));
		half.setAbsorptionAmount(3.0F);
		Tipsy.setLevel(half, 3);
		drink(half, new ItemStack(DrinkItems.AGED_HARD_CIDER.get()));
		assertValueEqual(helper, half.getAbsorptionAmount(), 3.0F, "the hearts left stay as they were");

		// An earlier cider's Absorption over a weaker one, spent, then taken away with the other drinks' buffs
		TestPlayer earlier = player(helper);
		earlier.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
		drink(earlier, new ItemStack(DrinkItems.AGED_HARD_CIDER.get()));
		earlier.setAbsorptionAmount(1.0F);
		DrinkBuffs.removeAll(earlier);
		MobEffectInstance under = earlier.getEffect(MobEffects.ABSORPTION);
		helper.assertTrue(under != null && under.getAmplifier() == 0, "the other Absorption stays: " + under);
		assertValueEqual(helper, earlier.getAbsorptionAmount(), 1.0F, "no hearts back from taking the cider's away");

		// Our cider's Absorption the only one, hearts left, then the drink too many: the effect goes and its hearts with it
		TestPlayer only = player(helper);
		drink(only, new ItemStack(DrinkItems.AGED_HARD_CIDER.get()));
		helper.assertTrue(only.getAbsorptionAmount() > 0.0F, "the cider gives hearts: " + only.getAbsorptionAmount());
		Tipsy.setLevel(only, 3);
		drink(only, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		assertValueEqual(helper, Tipsy.getLevel(only), Tipsy.NO_BUFF_LEVEL, "level after the drink too many (cider only)");
		helper.assertTrue(only.getEffect(MobEffects.ABSORPTION) == null, "the cider's Absorption is gone: " + only.getEffect(MobEffects.ABSORPTION));
		assertValueEqual(helper, only.getAbsorptionAmount(), 0.0F, "and its hearts with it");
		helper.succeed();
	}

	/**
	 * Audit M1: a drink buff that is gone (it ran out, milk, a night's sleep) is forgotten, so a potion of the same effect
	 * drunk later is never taken away at Tipsy IV; and a stronger one on top of ours stays too.
	 */
	private static void drinkBuffsForgetTheOldOnes(GameTestHelper helper) {
		TestPlayer player = player(helper);
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		assertValueEqual(helper, DrinkBuffs.list(player).size(), 1, "the ale's Haste is remembered");
		// The ale's Haste runs out (or milk): the next tick forgets it
		player.removeEffect(MobEffects.DIG_SPEED);
		TipsyEvents.onEntityTick(player);
		helper.assertTrue(DrinkBuffs.list(player).isEmpty(), "a buff that is gone is forgotten");
		// Later the same night: a Haste "potion", then one drink too many
		player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 6000, 0));
		Tipsy.setLevel(player, 3);
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		assertValueEqual(helper, Tipsy.getLevel(player), 4, "level after the drink too many");
		MobEffectInstance potion = player.getEffect(MobEffects.DIG_SPEED);
		helper.assertTrue(potion != null && potion.getDuration() > 5000, "the potion's Haste stays: " + potion);

		// A stronger potion (or a beacon) on top of a drink's buff: the potion stays, and the drink's buff hidden under
		// it is taken away too, so it doesn't come back once the potion is over
		TestPlayer second = player(helper);
		drink(second, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		second.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 1));
		DrinkBuffs.removeAll(second);
		MobEffectInstance top = second.getEffect(MobEffects.DIG_SPEED);
		helper.assertTrue(top != null && top.getAmplifier() == 1, "the stronger potion stays: " + top);
		tick(second, 201);
		helper.assertFalse(second.hasEffect(MobEffects.DIG_SPEED), "and nothing of the drink comes back after it: " + second.getEffect(MobEffects.DIG_SPEED));

		// Sober again (a night's sleep): nothing is remembered, and the buff itself stays
		TestPlayer third = player(helper);
		drink(third, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		Tipsy.clear(third);
		helper.assertTrue(DrinkBuffs.list(third).isEmpty(), "sober: the night's buffs are forgotten");
		helper.assertTrue(third.hasEffect(MobEffects.DIG_SPEED), "but the buff itself stays");
		helper.succeed();
	}

	/**
	 * Home through the End's exit portal: the game makes a new player of the living one and, on 1.20.1, copies no
	 * effects. Tipsy (with the levels underneath and their time), the drinks' buffs and the hearts left come along;
	 * a potion's effect stays lost, as 1.20.1 has it.
	 */
	private static void comesHomeFromTheEnd(GameTestHelper helper) {
		net.minecraft.server.level.ServerPlayer player = VBGameTests.mockServerPlayerInLevel(helper);
		net.minecraft.server.players.PlayerList players = helper.getLevel().getServer().getPlayerList();
		net.minecraft.server.level.ServerPlayer home = player;
		try {
			new ItemStack(DrinkItems.AGED_HARD_CIDER.get()).finishUsingItem(helper.getLevel(), player);
			new ItemStack(DrinkItems.BARLEY_ALE.get()).finishUsingItem(helper.getLevel(), player);
			assertValueEqual(helper, Tipsy.getLevel(player), 2, "level after two drinks");
			player.setAbsorptionAmount(3.0F);
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2400, 0));
			int tipsyLeft = player.getEffect(TipsyRegistry.TIPSY.get()).getDuration();
			int hasteLeft = player.getEffect(MobEffects.DIG_SPEED).getDuration();

			home = players.respawn(player, true);
			helper.assertTrue(home != player, "the game made a new player");
			assertValueEqual(helper, Tipsy.getLevel(home), 2, "level at home");
			assertValueEqual(helper, home.getEffect(TipsyRegistry.TIPSY.get()).getDuration(), tipsyLeft, "Tipsy's time left");
			MobEffectInstance haste = home.getEffect(MobEffects.DIG_SPEED);
			helper.assertTrue(haste != null && haste.getDuration() == hasteLeft, "the ale's Haste with its time left: " + haste);
			helper.assertTrue(home.getEffect(MobEffects.ABSORPTION) != null, "the cider's Absorption");
			assertValueEqual(helper, home.getAbsorptionAmount(), 3.0F, "the hearts that were left, not fresh ones");
			assertValueEqual(helper, DrinkBuffs.list(home).size(), 2, "both buffs still remembered as the drinks'");
			helper.assertTrue(home.getEffect(MobEffects.NIGHT_VISION) == null, "a potion's effect is vanilla's business (lost on 1.20.1)");
			// the levels underneath came along: one down leaves Tipsy I
			helper.assertTrue(Tipsy.lowerLevel(home), "one level down");
			assertValueEqual(helper, Tipsy.getLevel(home), 1, "level after sobering one down");
		} finally {
			players.remove(home);
		}
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
		int plain = one.getEffect(MobEffects.DIG_SPEED).getDuration();
		drink(two, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertTrue(one.getEffect(MobEffects.DIG_SPEED).getDuration() > plain, "the cheers made the ale's Haste last longer");
		for (int i = 0; i < 20; i++) {
			one.tickEffectsOnce();
			TipsyEvents.onEntityTick(one);
		}
		assertValueEqual(helper, DrinkBuffs.list(one).size(), 1, "still remembered as the ale's after the cheers");
		Tipsy.setLevel(one, 3);
		drink(one, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertFalse(one.hasEffect(MobEffects.DIG_SPEED), "at IV the cheered Haste goes too: " + one.getEffect(MobEffects.DIG_SPEED));

		// Our stronger buff with a weaker, longer potion waiting underneath: the potion comes back, as it was
		TestPlayer three = player(helper);
		Tipsy.setLevel(three, 1);
		MobEffectInstance strong = new MobEffectInstance(MobEffects.DIG_SPEED, 400, 1);
		three.addEffect(strong);
		DrinkBuffs.remember(three, strong);
		three.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 6000, 0));
		DrinkBuffs.removeAll(three);
		MobEffectInstance back = three.getEffect(MobEffects.DIG_SPEED);
		helper.assertTrue(back != null && back.getAmplifier() == 0 && back.getDuration() > 5000, "the potion underneath is back: " + back);

		// A beacon's Haste II refreshed over the ale's Haste I, then a drink too many: the beacon's stays, the ale's goes
		TestPlayer four = player(helper);
		drink(four, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		for (int refresh = 0; refresh < 3; refresh++) {
			four.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 260, 1, true, true));
			for (int i = 0; i < 80; i++) {
				four.tickEffectsOnce();
				TipsyEvents.onEntityTick(four);
			}
		}
		assertValueEqual(helper, DrinkBuffs.list(four).size(), 1, "the ale's Haste is still known under the beacon's");
		DrinkBuffs.removeAll(four);
		MobEffectInstance beacon = four.getEffect(MobEffects.DIG_SPEED);
		helper.assertTrue(beacon != null && beacon.getAmplifier() == 1 && beacon.isAmbient(), "the beacon's Haste stays: " + beacon);
		tick(four, 261);
		helper.assertFalse(four.hasEffect(MobEffects.DIG_SPEED), "and the ale's doesn't come back after it");

		// A relog: the saved entries read back (time only runs while the player is there, like the effect's)
		TestPlayer five = player(helper);
		drink(five, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		for (int i = 0; i < 100; i++) {
			five.tickEffectsOnce();
			TipsyEvents.onEntityTick(five);
		}
		var saved = DrinkBuffs.CODEC.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, DrinkBuffs.list(five)).result().orElseThrow();
		TipsyRegistry.DRINK_BUFFS.set(five, DrinkBuffs.CODEC.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE, saved).result().orElseThrow());
		DrinkBuffs.removeAll(five);
		helper.assertFalse(five.hasEffect(MobEffects.DIG_SPEED), "after a relog the ale's Haste is still known and taken away");
		helper.succeed();
	}

	/** Non-alcoholic mode switched on mid-game: nobody stays tipsy, and the other clients stop the sway. */
	private static void nonAlcoholicMidGame(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		with(TipsyConfig.ENABLED, false, () -> {
			TipsyEvents.onEntityTick(player);
			TipsyEvents.onEntityTick(player);
			assertValueEqual(helper, Tipsy.getLevel(player), 0, "sober the moment it is switched on");
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
		player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
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
		assertValueEqual(helper, motion(player).trips(), trips + 1, "every client is told to play the trip");
		helper.assertTrue(TipsyLegs.isDown(player), "down");
		helper.assertFalse(TipsyLegs.stumble(player, new Vec3(0.2, 0.0, 0.0), RandomSource.create(1L)), "no stumble while down");
		for (int i = 0; i < TipsyMotion.TRIP_TICKS; i++) {
			TipsyEvents.onEntityTick(player);
		}
		helper.assertFalse(TipsyLegs.isDown(player), "up again after " + TipsyMotion.TRIP_TICKS + " ticks");
		assertValueEqual(helper, player.getHealth(), health, "a trip never hurts");
		assertValueEqual(helper, player.position(), at, "a trip moves you nowhere");
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
		net.minecraft.world.entity.monster.Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5, 1.0, 4.5));
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
		TipsyLegs.knownMovement(player, new Vec3(0.0, 0.0, 0.6));
		helper.assertFalse(TipsyLegs.canTrip(player), "never at a running slide");
		TipsyLegs.knownMovement(player, Vec3.ZERO);
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
		TipsyLegs.knownMovement(player, Vec3.ZERO);
		helper.assertTrue(TipsyLegs.stumble(player, new Vec3(0.0, 0.0, 0.2), RandomSource.create(3L)), "the legs still give");
		assertValueEqual(helper, player.getDeltaMovement(), Vec3.ZERO, "but no lurch over the edge");
		helper.succeed();
	}

	/** The sway friends see: none sober, within the level's degrees and reaching them, never in lockstep. */
	private static void bodySway(GameTestHelper helper) {
		assertValueEqual(helper, TipsyWobble.bodySway(0, 10, 1), 0.0F, "No sway when sober");
		helper.assertFalse(TipsyWobble.bodySway(3, 10, 1) == TipsyWobble.bodySway(3, 10, 2), "Players don't sway in lockstep");
		for (int level = 1; level <= Tipsy.ABSOLUTE_MAX_LEVEL; level++) {
			float limit = TipsyLevels.at(level).legs().swayDegrees();
			float peak = 0.0F;
			for (float t = 0; t < 2000; t += 1) peak = Math.max(peak, Math.abs(TipsyWobble.bodySway(level, t, 7)));
			helper.assertTrue(peak <= limit + 1.0E-4 && peak > 0.8F * limit, "the sway at " + level + " reaches its " + limit + " degrees and stays within: " + peak);
		}
		helper.succeed();
	}

	/**
	 * The level table (Tipsy overhaul): every section has one row per level and the levels are put together from them,
	 * each part gets worse level by level, and the thresholds the game and the wiki read.
	 */
	private static void levelsTable(GameTestHelper helper) {
		assertValueEqual(helper, TipsyLevels.sections().size(), TipsyLevels.Level.class.getRecordComponents().length - 1, "every section is listed");
		for (TipsyLevels.Section section : TipsyLevels.sections()) {
			assertValueEqual(helper, section.rows().size(), Tipsy.ABSOLUTE_MAX_LEVEL, "a row per level in " + section.name());
			for (TipsyLevels.Level level : TipsyLevels.LEVELS) {
				helper.assertTrue(section.of().apply(level) == section.rows().get(level.level() - 1), section.name() + "'s row of level " + level.level());
			}
		}
		for (int level = 2; level <= Tipsy.ABSOLUTE_MAX_LEVEL; level++) {
			TipsyLevels.Level at = TipsyLevels.at(level);
			TipsyLevels.Level below = TipsyLevels.at(level - 1);
			helper.assertTrue(at.legs().swayDegrees() > below.legs().swayDegrees() && at.legs().weave() > below.legs().weave(), "the legs get worse at " + level);
			helper.assertTrue(at.eyes().softEdges() >= below.eyes().softEdges() && at.eyes().doubleVision() >= below.eyes().doubleVision()
				&& at.eyes().signBlur() >= below.eyes().signBlur(), "the eyes get worse at " + level);
			helper.assertTrue(at.ears().muffle() >= below.ears().muffle(), "the ears get worse at " + level);
			assertValueEqual(helper, at.chat().stage(), level - 1, "the chat stage at " + level);
		}
		helper.assertTrue(TipsyLevels.at(3).courage().any() && !TipsyLevels.at(4).courage().any(), "courage up to III, none from IV");
		assertValueEqual(helper, Tipsy.HICCUP_LEVEL, 2, "hiccups from II");
		assertValueEqual(helper, Tipsy.SLUR_LEVEL, 3, "slurring from III");
		assertValueEqual(helper, Tipsy.NO_BUFF_LEVEL, 4, "no buffs from IV");
		assertValueEqual(helper, Rowdy.ROWDY_LEVEL, 4, "Rowdy from IV");
		assertValueEqual(helper, Rowdy.CUT_OFF_LEVEL, 4, "cut off from IV");
		assertValueEqual(helper, TipsyLevels.first(level -> level.nod().any()), 4, "nodding off from IV");
		assertValueEqual(helper, Tipsy.STUMBLE_LEVEL, 5, "unsteady legs at V");
		assertValueEqual(helper, jexxme.vineandbarrel.tipsy.TipsySounds.HEARTBEAT_LEVEL, 5, "the heartbeat at V");
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
		List<MobEffect> gone = List.of(MobEffects.CONFUSION, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.WEAKNESS);
		for (int level = 1; level <= Tipsy.ABSOLUTE_MAX_LEVEL; level++) {
			Tipsy.setLevel(player, level);
			for (int i = 0; i < 400; i++) {
				TipsyEvents.onEntityTick(player);
				for (MobEffect effect : gone) {
					helper.assertFalse(player.hasEffect(effect), "no " + net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect) + " at level " + level);
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
		assertValueEqual(helper, TipsyConfig.DRUNK_SCREEN.formerName(), "wobbleIntensity", "drunkScreen was wobbleIntensity");
		assertValueEqual(helper, TipsyConfig.DOUBLE_VISION.formerName(), "visionEffects", "doubleVision was visionEffects");
		String[][] wobble = {{"1.0", "100"}, {"1", "100"}, {"0.4", "40"}, {"0.0", "0"}, {"2.0", "100"}, {"1.5", "100"}};
		for (String[] pair : wobble) {
			assertValueEqual(helper, read.apply(TipsyConfig.DRUNK_SCREEN, java.util.Map.of("wobbleIntensity", pair[0])), pair[1], "wobbleIntensity " + pair[0]);
		}
		for (String bad : new String[] {"3.0", "-1", "lots"}) {
			helper.assertTrue(read.apply(TipsyConfig.DRUNK_SCREEN, java.util.Map.of("wobbleIntensity", bad)) == null, "a broken old value gives the default: " + bad);
		}
		assertValueEqual(helper, read.apply(TipsyConfig.DRUNK_SCREEN, java.util.Map.of("drunkScreen", "70", "wobbleIntensity", "0")), "70", "the new key wins");
		helper.assertTrue(read.apply(TipsyConfig.DRUNK_SCREEN, java.util.Map.of()) == null, "neither key: the default");
		assertValueEqual(helper, read.apply(TipsyConfig.DOUBLE_VISION, java.util.Map.of("visionEffects", "false")), "false", "vision effects off: double vision off");
		assertValueEqual(helper, read.apply(TipsyConfig.DOUBLE_VISION, java.util.Map.of("visionEffects", "TRUE")), "true", "vision effects on: double vision on");
		helper.assertTrue(read.apply(TipsyConfig.DOUBLE_VISION, java.util.Map.of("visionEffects", "maybe")) == null, "a broken old value gives the default");
		helper.assertTrue(read.apply(TipsyConfig.OTHER_PLAYERS_SWAY, java.util.Map.of("wobbleIntensity", "0")) == null, "a setting that was never renamed reads only its own key");
		helper.succeed();
	}

	/** The jolt, the lurch and the trip as every client plays them: short, back to nothing, and a trip lies a while. */
	private static void motionMath(GameTestHelper helper) {
		assertValueEqual(helper, TipsyMotion.hiccupJolt(0.0F), 0.0F, "a hiccup starts from rest");
		assertValueEqual(helper, TipsyMotion.hiccupJolt(TipsyMotion.HICCUP_RISE), 1.0F, "and jolts all at once");
		assertValueEqual(helper, TipsyMotion.hiccupJolt(TipsyMotion.HICCUP_RISE + TipsyMotion.HICCUP_SETTLE), 0.0F, "settled in a few ticks");
		assertValueEqual(helper, TipsyMotion.stumbleLurch(TipsyMotion.STUMBLE_RISE), 1.0F, "a stumble lurches");
		assertValueEqual(helper, TipsyMotion.stumbleLurch(20.0F), 0.0F, "and steadies within a second");
		assertValueEqual(helper, TipsyMotion.tripFallen(1.0F), 0.0F, "a trip starts like a stumble, still standing");
		helper.assertTrue(TipsyMotion.tripStumble(1.0F) > 0.0F, "with the stumble's lurch");
		assertValueEqual(helper, TipsyMotion.tripFallen(TipsyMotion.TRIP_THUD + 6.0F), 1.0F, "then face down after the thud");
		int down = 0;
		for (int tick = 0; tick < TipsyMotion.TRIP_TICKS + 5; tick++) {
			float fallen = TipsyMotion.tripFallen(tick);
			helper.assertTrue(fallen >= 0.0F && fallen <= 1.0F, "fallen stays in 0..1");
			down += fallen >= 0.9F ? 1 : 0;
		}
		helper.assertTrue(down >= 20 && down <= 30, "down for 1-1.5 s: " + down + " ticks");
		assertValueEqual(helper, TipsyMotion.tripFallen(TipsyMotion.TRIP_TICKS), 0.0F, "and up again");
		helper.assertTrue(TipsyMotion.tripHolds(TipsyMotion.TRIP_TICKS - 1) && !TipsyMotion.tripHolds(TipsyMotion.TRIP_TICKS), "keys held until standing");
		helper.assertTrue(TipsyMotion.tripShake(TipsyMotion.TRIP_THUD) > 0.0F && TipsyMotion.tripShake(TipsyMotion.TRIP_THUD + 10) == 0.0F, "a short shake at the thud");
		helper.succeed();
	}
}
