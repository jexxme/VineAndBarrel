package jexxme.vineandbarrel.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.Rowdy;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.TipsyEvents;
import jexxme.vineandbarrel.tipsy.TipsyRegistry;
import jexxme.vineandbarrel.tipsy.TipsySlur;
import jexxme.vineandbarrel.tipsy.TipsyInfo;
import jexxme.vineandbarrel.tipsy.TipsyVision;
import jexxme.vineandbarrel.tipsy.TipsyWobble;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

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
		test("tipsy_blackout_one_too_many", TipsyGameTests::blackoutOneTooMany);
		test("tipsy_blackout_config_off", TipsyGameTests::blackoutConfigOff);
		test("tipsy_blackout_never_onto_the_nether_roof", TipsyGameTests::blackoutNoNetherRoof);
		test("tipsy_too_drunk_for_buffs", TipsyGameTests::tooDrunkForBuffs);
		test("tipsy_grape_juice_lowers_by_one", TipsyGameTests::grapeJuiceLowersByOne);
		test("tipsy_courage_per_level", TipsyGameTests::couragePerLevel);
		test("tipsy_weakness_from_level4", TipsyGameTests::weaknessFromLevel4);
		test("tipsy_nausea_pulses_from_level3", TipsyGameTests::nauseaPulses);
		test("tipsy_keeps_foreign_side_effects", TipsyGameTests::keepsForeignSideEffects);
		test("tipsy_keeps_foreign_effect_under_its_own", TipsyGameTests::keepsForeignEffectUnderItsOwn);
	}

	private TipsyGameTests() {
	}

	private static void test(String name, Consumer<GameTestHelper> function) {
		FUNCTIONS.register(name, () -> function);
		TESTS.add(ResourceKey.create(Registries.TEST_FUNCTION, VineAndBarrel.id(name)));
	}

	public static void register(IEventBus modEventBus) {
		FUNCTIONS.register(modEventBus);
		modEventBus.addListener(TipsyGameTests::registerTests);
	}

	private static void registerTests(RegisterGameTestsEvent event) {
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(VineAndBarrel.id("tipsy"));
		for (ResourceKey<Consumer<GameTestHelper>> function : TESTS) {
			event.registerTest(function.identifier(), new FunctionGameTestInstance(function, new TestData<>(environment, STRUCTURE, 100, 0, true)));
		}
	}

	// ------------------------------------------------------------------ the mock player

	/**
	 * A mock server player with a connection to nowhere (an embedded channel, as vanilla's own in-level mock uses).
	 * Vanilla's plain mock has no connection, and a ServerPlayer sends every effect change through it. It is not
	 * added to the level, so tests drive its effect ticks themselves with {@link #tickEffectsOnce()}.
	 */
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

	private static TestPlayer player(GameTestHelper helper) {
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

	/** Runs {@code body} with a config value temporarily set, restoring it even when an assertion fails. */
	private static <T> void with(ModConfigSpec.ConfigValue<T> value, T setting, Runnable body) {
		T before = value.get();
		value.set(setting);
		try {
			body.run();
		} finally {
			value.set(before);
		}
	}

	private static void drink(TestPlayer player, ItemStack stack) {
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

	/** Slowness II and Mining Fatigue II come with level 5 and leave as soon as it drops below. */
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
		helper.assertTrue(fatigue != null && fatigue.getAmplifier() == 1, "Level 5 gives Mining Fatigue II: " + fatigue);

		// They are refreshed for as long as level 5 lasts, well past their own short duration
		for (int i = 0; i < 200; i++) {
			player.tickEffectsOnce();
			TipsyEvents.onEntityTick(new EntityTickEvent.Post(player));
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
		helper.assertFalse(Tipsy.isSoberingDrink(new ItemStack(BaseItems.SWEET_BERRY_JUICE.get())), "Only grape juice and water sober you up");
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

	/** The slur is a pure function: same message and seed, same result (golden values pin it). */
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
		helper.succeed();
	}

	/** Chat slurs from level 4 only, and never with the server's slurred-chat switch off. */
	private static void slurLevelAndConfig(GameTestHelper helper) {
		TestPlayer player = player(helper);
		String message = "hello friends";
		Tipsy.setLevel(player, 3);
		helper.assertValueEqual(Tipsy.slurChat(player, message, 9L), message, "level 3 chat");
		Tipsy.setLevel(player, 4);
		helper.assertValueEqual(Tipsy.slurChat(player, message, 9L), TipsySlur.slur(message, 9L), "level 4 chat");
		// through the server's real chat path, as a typed message goes
		String decorated = net.neoforged.neoforge.common.CommonHooks.getServerChatSubmittedDecorator().decorate(player, Component.literal(message)).getString();
		helper.assertFalse(decorated.equals(message), "level 4 chat through the server's chat decorator is slurred: " + decorated);
		with(TipsyConfig.SLURRED_CHAT, false, () -> {
			Tipsy.setLevel(player, 5);
			helper.assertValueEqual(Tipsy.slurChat(player, message, 9L), message, "level 5 chat with slurred chat off");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ level 5: stumbling, level 3: hiccups

	/** At level 5 a stumble nudges sideways, at right angles to walking; below 5 or standing still it does nothing. */
	private static void stumble(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Vec3 walkingEast = new Vec3(0.2, 0.0, 0.0);
		RandomSource random = RandomSource.create(1L);

		Tipsy.setLevel(player, 4);
		helper.assertFalse(Tipsy.stumble(player, walkingEast, random), "No stumbling at level 4");
		Tipsy.setLevel(player, 5);
		helper.assertFalse(Tipsy.stumble(player, Vec3.ZERO, random), "No stumbling while standing still");
		helper.assertValueEqual(Tipsy.walkingMotion(player), Vec3.ZERO, "A player who isn't moving isn't walking");

		player.setDeltaMovement(Vec3.ZERO);
		helper.assertTrue(Tipsy.stumble(player, walkingEast, random), "Level 5 walking stumbles");
		Vec3 nudge = player.getDeltaMovement();
		helper.assertTrue(Math.abs(nudge.x) < 1.0E-6 && Math.abs(nudge.y) < 1.0E-6, "The nudge is purely sideways, got " + nudge);
		helper.assertTrue(Math.abs(Math.abs(nudge.z) - Tipsy.STUMBLE_NUDGE) < 1.0E-6, "The nudge is small, got " + nudge);
		helper.assertTrue(player.syncVelocity, "The nudge is sent to the player's client");
		helper.succeed();
	}

	/** With stumbling switched off on the server, level 5 never nudges anyone. */
	private static void stumbleConfigOff(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 5);
		player.setDeltaMovement(Vec3.ZERO);
		RandomSource random = RandomSource.create(2L);
		with(TipsyConfig.STUMBLING, false, () -> {
			for (int i = 0; i < 50; i++) {
				helper.assertFalse(Tipsy.stumble(player, new Vec3(0.2, 0.0, 0.1), random), "Stumbling is off");
			}
		});
		helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "motion with stumbling off");
		helper.succeed();
	}

	/** Hiccups start at level 3; a hiccup is a small hop off the ground and nothing else. */
	private static void hiccups(GameTestHelper helper) {
		RandomSource random = RandomSource.create(3L);
		int atThree = 0;
		for (int i = 0; i < 24000; i++) {
			helper.assertFalse(Tipsy.rollsHiccup(1, random) || Tipsy.rollsHiccup(2, random), "No hiccups below level 3");
			if (Tipsy.rollsHiccup(3, random)) atThree++;
		}
		// About one every 12 s (240 ticks): ~100 in 24000 ticks
		helper.assertTrue(atThree > 50 && atThree < 150, "Hiccups at level 3 come every ~12 s, got " + atThree + " in 20 min");

		TestPlayer player = player(helper);
		float health = player.getHealth();
		player.setOnGround(true);
		player.setDeltaMovement(Vec3.ZERO);
		Tipsy.hiccup(player);
		helper.assertTrue(Math.abs(player.getDeltaMovement().y - Tipsy.HICCUP_HOP) < 1.0E-6, "A hiccup hops, got " + player.getDeltaMovement());
		helper.assertValueEqual(player.getHealth(), health, "health after a hiccup (harmless)");

		player.setOnGround(false);
		player.setDeltaMovement(Vec3.ZERO);
		Tipsy.hiccup(player);
		helper.assertValueEqual(player.getDeltaMovement(), Vec3.ZERO, "No hop in mid-air");
		helper.succeed();
	}

	// ------------------------------------------------------------------ playtest 1: courage and the downside ramp

	private static final double EPS = 1.0E-6;

	/** "Liquid courage": a little knockback resistance and less fall damage at levels 1-3, none from 4 on. */
	private static void couragePerLevel(GameTestHelper helper) {
		TestPlayer player = player(helper);
		double[] knockback = {0.0, 0.05, 0.10, 0.15, 0.0, 0.0};
		double[] fall = {1.0, 0.90, 0.85, 0.80, 1.0, 1.0};
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
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 0.15) < EPS, "courage back at level 3");
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
			TipsyEvents.onEntityTick(new EntityTickEvent.Post(player));
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
		helper.assertFalse(player.hasData(TipsyRegistry.HEAVY_EFFECTS_OWNED), "Tipsy owns nothing any more");
		helper.succeed();
	}

	/** From level 3 a short Nausea pulse comes now and then; it runs out on its own and goes when the level drops below 3. */
	private static void nauseaPulses(GameTestHelper helper) {
		RandomSource random = RandomSource.create(7L);
		int[] rolls = new int[6];
		for (int i = 0; i < 60000; i++) {
			for (int level = 0; level <= 5; level++) {
				if (Tipsy.rollsNausea(level, random)) rolls[level]++;
			}
		}
		helper.assertTrue(rolls[0] == 0 && rolls[1] == 0 && rolls[2] == 0, "No Nausea below level 3");
		helper.assertTrue(rolls[3] > 0 && rolls[4] > rolls[3] && rolls[5] > rolls[4], "Nausea comes more often with each level: " + java.util.Arrays.toString(rolls));
		// 60000 ticks = 50 min: at level 5 one roll per ~6 s, so ~500; level 3 stays occasional (~67)
		helper.assertTrue(rolls[5] > 1000, "Level 5 is dizzy almost all the time: " + rolls[5]);
		helper.assertTrue(rolls[3] < 120, "Level 3 stays occasional: " + rolls[3]);

		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 2);
		helper.assertFalse(Tipsy.nauseaPulse(player), "No pulse at level 2");
		Tipsy.setLevel(player, 3);
		helper.assertTrue(Tipsy.nauseaPulse(player), "A pulse at level 3");
		MobEffectInstance nausea = player.getEffect(MobEffects.NAUSEA);
		helper.assertTrue(nausea != null && nausea.getDuration() == Tipsy.NAUSEA_PULSE_TICKS && nausea.getAmplifier() == 0, "A short pulse: " + nausea);
		helper.assertFalse(Tipsy.nauseaPulse(player), "No second pulse on top of the first");
		// It runs out on its own and Tipsy lets go of it
		tick(player, Tipsy.NAUSEA_PULSE_TICKS + 1);
		Tipsy.setLevel(player, 3);
		helper.assertFalse(player.hasEffect(MobEffects.NAUSEA), "The pulse is over after " + Tipsy.NAUSEA_PULSE_TICKS + " ticks");
		helper.assertFalse(player.hasData(TipsyRegistry.HEAVY_EFFECTS_OWNED), "Nothing owned after the pulse");
		// Dropping below 3 ends a pulse right away
		helper.assertTrue(Tipsy.nauseaPulse(player), "Another pulse");
		Tipsy.lowerLevel(player);
		helper.assertFalse(player.hasEffect(MobEffects.NAUSEA), "Nausea gone below level 3");
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
		TipsyEvents.onEntityTick(new EntityTickEvent.Post(sober));
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
			TipsyEvents.onEntityTick(new EntityTickEvent.Post(player));
		}
		MobEffectInstance slowness = player.getEffect(MobEffects.SLOWNESS);
		helper.assertTrue(slowness != null && slowness.getAmplifier() == 0 && slowness.getDuration() > 1000, "The other mod's Slowness I is back: " + slowness);
		helper.succeed();
	}

	// ------------------------------------------------------------------ camera wobble (pure math, client applies it)

	/** One drink more at Tipsy V: out cold (nothing hurts), then awake somewhere, hungry and shaky, with a souvenir. */
	private static void blackoutOneTooMany(GameTestHelper helper) {
		TestPlayer player = player(helper);
		player.getFoodData().setFoodLevel(20);
		Tipsy.setLevel(player, 4);
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertFalse(Blackout.isOut(player), "Tipsy IV to V: no blackout yet");
		drink(player, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		helper.assertTrue(Blackout.isOut(player), "one drink more at V: blacked out");
		float health = player.getHealth();
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 5.0F);
		helper.assertValueEqual(player.getHealth(), health, "nothing hurts while out");
		int itemsBefore = countItems(player);
		for (int i = 0; i < Blackout.TICKS; i++) {
			Blackout.tick(player);
		}
		helper.assertFalse(Blackout.isOut(player), "awake again after the blackout");
		helper.assertValueEqual(Tipsy.getLevel(player), 1, "Tipsy after waking");
		helper.assertTrue(player.getFoodData().getFoodLevel() <= 12, "woke up hungry: " + player.getFoodData().getFoodLevel());
		helper.assertTrue(player.hasEffect(MobEffects.NAUSEA) && player.hasEffect(MobEffects.SLOWNESS), "woke up shaky");
		helper.assertValueEqual(countItems(player), itemsBefore + 1, "a mystery souvenir in the pockets");
		helper.succeed();
	}

	private static int countItems(TestPlayer player) {
		int count = 0;
		for (ItemStack stack : player.getInventory()) {
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
			helper.assertTrue(Blackout.randomSpot(nether, BlockPos.ZERO.above(64), random) == null, "no wake-up spot in the Nether");
		}
		ServerLevel end = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.END);
		if (end != null) {
			helper.assertTrue(Blackout.randomSpot(end, BlockPos.ZERO.above(64), random) == null, "no wake-up spot in the End");
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
		net.minecraft.world.entity.animal.pig.Pig pig = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.PIG, new Vec3(2.5, 1.0, 3.0));
		TestPlayer player = facing(helper, pig);
		float before = pig.getHealth();
		helper.assertTrue(Rowdy.swing(player) == pig, "the swing hits the pig in front");
		helper.assertTrue(pig.getHealth() < before, "the pig took the punch: " + pig.getHealth());
		helper.assertTrue(before - pig.getHealth() <= Rowdy.PUNCH_DAMAGE + 1.0E-4, "a fist's punch, no more");
		helper.succeed();
	}

	/** Never pets and never iron golems. */
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
		helper.succeed();
	}

	/** At the bar: from Tipsy IV villagers won't trade; at III they still do, and with rowdy off always. */
	private static void rowdyCutOff(GameTestHelper helper) {
		TestPlayer player = player(helper);
		Tipsy.setLevel(player, 3);
		helper.assertFalse(Rowdy.cutOff(player), "Tipsy III still gets served");
		Tipsy.setLevel(player, 4);
		helper.assertTrue(Rowdy.cutOff(player), "Tipsy IV is cut off");
		with(TipsyConfig.ROWDY, false, () -> helper.assertFalse(Rowdy.cutOff(player), "rowdy off: always served"));
		helper.succeed();
	}

	/** The inventory tooltip names what the current level does (only that), with every key translated. */
	private static void effectTooltip(GameTestHelper helper) {
		helper.assertTrue(TipsyInfo.lines(0).isEmpty(), "sober: no lines");
		for (int level = 1; level <= 5; level++) {
			String text = String.join(" | ", TipsyInfo.lines(level).stream().map(Component::getString).toList());
			helper.assertFalse(text.contains("effect.vineandbarrel"), "level " + level + " has an untranslated line: " + text);
			helper.assertValueEqual(text.contains("Liquid courage"), level <= 3, "courage at level " + level);
			helper.assertValueEqual(text.contains("No buffs"), level >= Tipsy.NO_BUFF_LEVEL, "no buffs at level " + level);
			helper.assertValueEqual(text.contains("Weakness"), level >= Tipsy.WEAKNESS_LEVEL, "Weakness at level " + level);
		}
		for (int level = 1; level <= 5; level++) {
			helper.assertTrue(TipsyInfo.lines(level).size() <= 5, "the tooltip stays short at level " + level);
		}
		helper.succeed();
	}

	/** Tunnel vision grows from level 2; blinks only from 3, now and then shut for a moment, long ones only from 4. */
	private static void visionMath(GameTestHelper helper) {
		helper.assertValueEqual(TipsyVision.vignette(0.0F), 0.0F, "sober: no tunnel vision");
		float step = TipsyVision.vignette(1.0F);
		helper.assertTrue(step > 0.0F, "level 1 already narrows the view a little");
		float last = 0.0F;
		for (int level = 1; level <= 5; level++) {
			helper.assertTrue(Math.abs(TipsyVision.vignette(level) - level * step) < 1.0E-4, "tunnel vision is linear, level " + level);
			float v = TipsyVision.vignette(level);
			helper.assertTrue(v > last && v < 1.0F, "tunnel vision grows at level " + level + ": " + v);
			last = v;
		}
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
		}
		helper.assertTrue(TipsyWobble.amplitude(1) > 0 && TipsyWobble.amplitude(2) > TipsyWobble.amplitude(1), "Level 1-2 wobble slightly");
		// Playtest 1: 30% stronger at every level than the M4 values (1.2, 2, 3.5, 4.5, 5.5 degrees)
		float[] m4 = {1.2F, 2.0F, 3.5F, 4.5F, 5.5F};
		for (int level = 1; level <= 5; level++) {
			helper.assertTrue(TipsyWobble.amplitude(level) >= 1.3F * m4[level - 1] - 1.0E-4, "Wobble at level " + level + " is at least 30% stronger");
		}
		helper.assertTrue(TipsyWobble.amplitude(5) >= 2.0F * m4[4], "Level 5 sways properly drunk: " + TipsyWobble.amplitude(5));
		helper.assertTrue(TipsyWobble.amplitude(3) >= 1.5F * TipsyWobble.amplitude(2), "Level 3 wobbles clearly more");
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
}
