package jexxme.vineandbarrel.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.advancement.BlackoutTrigger;
import jexxme.vineandbarrel.advancement.VBAdvancementGenerator;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.BlackoutEnding;
import jexxme.vineandbarrel.tipsy.BlackoutPose;
import jexxme.vineandbarrel.tipsy.BlackoutSpots;
import jexxme.vineandbarrel.tipsy.TipsyEvents;
import jexxme.vineandbarrel.tipsy.TipsyRegistry;
import jexxme.vineandbarrel.world.village.VillageStructures;
import jexxme.vineandbarrel.world.village.VillageType;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The blackout's ending catalog: every ending's condition and spot (safe, in its pose), the draw (unseen endings
 * preferred, never the same twice in a row), the bed staying under vanilla's deep sleep, the boat, the villager's head
 * shake, that nothing is ever lost, and Lost Weekend. Headless ({@code ./gradlew runGameTestServer}).
 * <p>
 * Each test builds its scene on a stone floor in its own 32x32 area and looks for spots only within it
 * ({@link #NEAR}), so the neighbouring tests' blocks and animals never count.
 */
public final class BlackoutGameTests
{
	private static final Identifier LARGE = VineAndBarrel.id("gametest/empty_32x6x32");
	private static final Identifier TALL = VineAndBarrel.id("gametest/empty_16x14x16");
	private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, VineAndBarrel.MODID);
	private static final List<Entry> TESTS = new ArrayList<>();
	/** The middle of the test area, one above the floor: where the player stands. */
	private static final BlockPos MIDDLE = new BlockPos(16, 1, 16);
	/** How far the scoped finders look from the middle: well inside the test area. */
	private static final int NEAR = 12;
	private static final BlockPos TAVERN_AT = new BlockPos(2, 0, 2);

	private record Entry(ResourceKey<Consumer<GameTestHelper>> function, Identifier structure, int maxTicks) {
	}

	static {
		test("blackout_draw_prefers_unseen", LARGE, 100, BlackoutGameTests::drawPrefersUnseen);
		test("blackout_draw_never_repeats", LARGE, 100, BlackoutGameTests::drawNeverRepeats);
		test("blackout_lines_never_repeat", LARGE, 100, BlackoutGameTests::linesNeverRepeat);
		test("blackout_lines_translated", LARGE, 100, BlackoutGameTests::linesTranslated);
		test("blackout_pose_math", LARGE, 100, BlackoutGameTests::poseMath);
		test("blackout_sounds_fit_the_place", LARGE, 100, BlackoutGameTests::soundsFitThePlace);
		test("blackout_spot_safety", LARGE, 100, BlackoutGameTests::spotSafety);
		test("blackout_ending_home", LARGE, 100, BlackoutGameTests::home);
		test("blackout_bed_under_deep_sleep", LARGE, 300, BlackoutGameTests::bedUnderDeepSleep);
		test("blackout_ending_in_the_open", LARGE, 100, BlackoutGameTests::inTheOpen);
		test("blackout_ending_farmyard", LARGE, 300, BlackoutGameTests::farmyard);
		test("blackout_ending_in_the_hay", LARGE, 100, BlackoutGameTests::inTheHay);
		test("blackout_ending_tavern", TALL, 100, BlackoutGameTests::tavern);
		test("blackout_ending_someone_elses_bed", LARGE, 300, BlackoutGameTests::someoneElsesBed);
		test("blackout_ending_on_the_roof", LARGE, 100, BlackoutGameTests::onTheRoof);
		test("blackout_ending_in_a_boat", LARGE, 300, BlackoutGameTests::inABoat);
		test("blackout_ending_right_here", LARGE, 300, BlackoutGameTests::rightHere);
		test("blackout_nothing_lost", LARGE, 300, BlackoutGameTests::nothingLost);
		test("blackout_pumpkin_hat", LARGE, 100, BlackoutGameTests::pumpkinHat);
		test("blackout_lost_weekend", LARGE, 100, BlackoutGameTests::lostWeekend);
		test("blackout_memory_survives_death", LARGE, 100, BlackoutGameTests::memorySurvivesDeath);
		test("blackout_no_open_sky_in_nether_or_end", LARGE, 100, BlackoutGameTests::noOpenSkyInNetherOrEnd);
		test("blackout_never_inside_sealed_builds", LARGE, 100, BlackoutGameTests::neverInsideSealedBuilds);
		test("blackout_boat_safe_or_right_here", LARGE, 100, BlackoutGameTests::boatSafeOrRightHere);
		test("blackout_bed_is_not_a_nights_sleep", LARGE, 100, BlackoutGameTests::bedIsNotANightsSleep);
		test("blackout_roof_never_a_cliff", TALL, 100, BlackoutGameTests::roofNeverACliff);
		test("blackout_unloaded_entities_count_as_unsafe", LARGE, 100, BlackoutGameTests::unloadedEntitiesUnsafe);
	}

	private BlackoutGameTests() {
	}

	private static void test(String name, Identifier structure, int maxTicks, Consumer<GameTestHelper> function) {
		FUNCTIONS.register(name, () -> function);
		TESTS.add(new Entry(ResourceKey.create(Registries.TEST_FUNCTION, VineAndBarrel.id(name)), structure, maxTicks));
	}

	public static void register() {
		FUNCTIONS.register();
		jexxme.vineandbarrel.platform.Services.PLATFORM.addGameTests(BlackoutGameTests::registerTests);
	}

	private static void registerTests(GameTestRegistrar event) {
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(VineAndBarrel.id("blackout"));
		for (Entry test : TESTS) {
			// Under the open sky: the surface finders read the heightmaps
			event.registerTest(test.function().identifier(), new FunctionGameTestInstance(test.function(), new TestData<>(environment,
				test.structure(), test.maxTicks(), 0, true, Rotation.NONE, false, 1, 1, true, 0)));
		}
	}

	// ------------------------------------------------------------------ helpers

	/** A stone floor under the whole test area (relative y 0), so every spot stands on something known. */
	private static void floor(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** A real server player in the level (advancements, ticking), standing in the middle; removed by {@link #done}. */
	private static ServerPlayer player(GameTestHelper helper) {
		@SuppressWarnings("removal")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// Survival pockets: a creative inventory swallows what doesn't fit instead of dropping it
		net.minecraft.world.level.GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE));
		player.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		return player;
	}

	private static void done(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static void withPlayer(GameTestHelper helper, BiConsumer<GameTestHelper, ServerPlayer> body) {
		ServerPlayer player = player(helper);
		try {
			body.accept(helper, player);
		} finally {
			done(helper, player);
		}
	}

	/** Blacks the player out and wakes them up at {@code wake} right away (the level then ticks the rest). */
	private static void blackOutTo(ServerPlayer player, BlackoutSpots.Wake wake, RandomSource random) {
		Blackout.start(player);
		// Past the draw: this test picks the ending
		TipsyRegistry.BLACKOUT.set(player, Blackout.WAKE_AT);
		Blackout.wakeUp(player, wake, random);
	}

	/** {@return a bed (foot here, head toward {@code facing})}; its head position */
	private static BlockPos bed(GameTestHelper helper, BlockPos foot, Direction facing) {
		BlockState state = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, facing);
		helper.setBlock(foot, state.setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.relative(facing), state.setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.relative(facing));
	}

	private static void assertSafe(GameTestHelper helper, BlackoutSpots.Wake wake, String what) {
		helper.assertTrue(BlackoutSpots.isSafe(helper.getLevel(), wake.spot()), what + ": the spot " + wake.spot() + " is safe");
	}

	private static AdvancementHolder advancement(GameTestHelper helper, String name) {
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(VBAdvancementGenerator.id(name));
		helper.assertTrue(holder != null, "advancement " + name + " is loaded");
		return holder;
	}

	private static boolean earned(GameTestHelper helper, ServerPlayer player, String name) {
		return player.getAdvancements().getOrStartProgress(advancement(helper, name)).isDone();
	}

	// ------------------------------------------------------------------ the draw

	/**
	 * By weight among the possible endings, endings not seen yet three times as likely: with nothing seen the shares
	 * follow the weights; once everything but the barn was seen, the barn comes up three times as often as its weight.
	 */
	private static void drawPrefersUnseen(GameTestHelper helper) {
		RandomSource random = RandomSource.create(42L);
		int draws = 30000;
		Map<BlackoutEnding, Integer> fresh = firstDraws(Blackout.Memory.NONE, draws, random);
		int total = BlackoutEnding.COLLECTABLE.stream().mapToInt(BlackoutEnding::weight).sum();
		for (BlackoutEnding ending : BlackoutEnding.COLLECTABLE) {
			double expected = (double) ending.weight() / total;
			double share = fresh.getOrDefault(ending, 0) / (double) draws;
			helper.assertTrue(Math.abs(share - expected) < 0.015, ending + " share " + share + ", expected " + expected);
		}
		List<String> allButBarn = BlackoutEnding.COLLECTABLE.stream().filter(e -> e != BlackoutEnding.FARMYARD).map(BlackoutEnding::id).toList();
		Blackout.Memory seen = new Blackout.Memory(allButBarn, "", "");
		Map<BlackoutEnding, Integer> later = firstDraws(seen, draws, random);
		double barnWeight = BlackoutEnding.FARMYARD.weight() * Blackout.UNSEEN_BONUS;
		double expected = barnWeight / (total - BlackoutEnding.FARMYARD.weight() + barnWeight);
		double share = later.getOrDefault(BlackoutEnding.FARMYARD, 0) / (double) draws;
		helper.assertTrue(Math.abs(share - expected) < 0.015, "unseen barn share " + share + ", expected " + expected);
		helper.assertTrue(share > 2.0 * BlackoutEnding.FARMYARD.weight() / total, "the unseen ending is clearly preferred: " + share);
		helper.assertFalse(fresh.containsKey(BlackoutEnding.RIGHT_HERE), "right here is never drawn, only the fallback");

		// Only possible endings are drawn: with just two possible, it is one of them, by weight
		Map<BlackoutEnding, Integer> two = new EnumMap<>(BlackoutEnding.class);
		for (int i = 0; i < 4000; i++) {
			BlackoutSpots.Wake wake = Blackout.choose(e -> e == BlackoutEnding.TAVERN || e == BlackoutEnding.IN_A_BOAT ? fake(e) : null,
				Blackout.Memory.NONE, random, () -> fake(BlackoutEnding.RIGHT_HERE));
			two.merge(wake.ending(), 1, Integer::sum);
		}
		helper.assertValueEqual(two.keySet(), Set.of(BlackoutEnding.TAVERN, BlackoutEnding.IN_A_BOAT), "only the possible endings");
		double tavern = two.get(BlackoutEnding.TAVERN) / 4000.0;
		helper.assertTrue(Math.abs(tavern - 10.0 / 18.0) < 0.04, "tavern 10 : boat 8, got " + tavern);
		// Nothing possible: right here
		helper.assertValueEqual(Blackout.choose(e -> null, Blackout.Memory.NONE, random, () -> fake(BlackoutEnding.RIGHT_HERE)).ending(),
			BlackoutEnding.RIGHT_HERE, "nothing possible");
		helper.succeed();
	}

	private static Map<BlackoutEnding, Integer> firstDraws(Blackout.Memory memory, int draws, RandomSource random) {
		Map<BlackoutEnding, Integer> counts = new EnumMap<>(BlackoutEnding.class);
		for (int i = 0; i < draws; i++) {
			counts.merge(Blackout.drawOrder(memory, random).getFirst(), 1, Integer::sum);
		}
		return counts;
	}

	private static BlackoutSpots.Wake fake(BlackoutEnding ending) {
		return new BlackoutSpots.Wake(ending, Vec3.ZERO, 0.0F, null, null);
	}

	/**
	 * Never the same ending twice in a row while there is another: across many blackouts each one differs from the
	 * last, every ending turns up, and the last one is tried only after every other. When it is the only one possible,
	 * it repeats.
	 */
	private static void drawNeverRepeats(GameTestHelper helper) {
		RandomSource random = RandomSource.create(7L);
		Blackout.Memory memory = Blackout.Memory.NONE;
		BlackoutEnding last = null;
		Map<BlackoutEnding, Integer> seen = new EnumMap<>(BlackoutEnding.class);
		for (int i = 0; i < 2000; i++) {
			List<BlackoutEnding> order = Blackout.drawOrder(memory, random);
			helper.assertValueEqual(order.size(), BlackoutEnding.COLLECTABLE.size(), "every ending is in the order once");
			if (last != null) {
				helper.assertValueEqual(order.getLast(), last, "the last ending is tried last");
			}
			BlackoutEnding next = order.getFirst();
			helper.assertTrue(next != last, "the same ending twice in a row: " + next);
			seen.merge(next, 1, Integer::sum);
			memory = new Blackout.Memory(memory.seen(), next.id(), "");
			last = next;
		}
		helper.assertValueEqual(seen.keySet(), Set.copyOf(BlackoutEnding.COLLECTABLE), "every ending came up");
		// The only one possible: it repeats rather than nothing
		Blackout.Memory homeLast = new Blackout.Memory(List.of("home"), "home", "");
		BlackoutSpots.Wake wake = Blackout.choose(e -> e == BlackoutEnding.HOME ? fake(e) : null, homeLast, random, () -> fake(BlackoutEnding.RIGHT_HERE));
		helper.assertValueEqual(wake.ending(), BlackoutEnding.HOME, "home again when it is the only option");
		helper.succeed();
	}

	/** The same line never twice in a row (also when the same ending comes again, like right here in the Nether). */
	private static void linesNeverRepeat(GameTestHelper helper) {
		RandomSource random = RandomSource.create(3L);
		for (BlackoutEnding ending : BlackoutEnding.values()) {
			helper.assertTrue(ending.lineCount() >= 3, ending + " has at least three lines");
			String last = "";
			Set<String> used = new java.util.HashSet<>();
			for (int i = 0; i < 400; i++) {
				String line = Blackout.line(ending, last, random);
				helper.assertTrue(!line.equals(last), ending + ": the same line twice in a row");
				used.add(line);
				last = line;
			}
			helper.assertValueEqual(used.size(), ending.lineCount(), ending + ": every line comes up");
		}
		helper.succeed();
	}

	/** Every line, the souvenirs and the new advancement are translated; the barn's lines name the animal. */
	private static void linesTranslated(GameTestHelper helper) {
		JsonObject lang;
		try (InputStream in = VineAndBarrel.class.getResourceAsStream("/assets/vineandbarrel/lang/en_us.json")) {
			helper.assertTrue(in != null, "en_us.json is in the jar");
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		for (BlackoutEnding ending : BlackoutEnding.values()) {
			for (int i = 0; i < ending.lineCount(); i++) {
				helper.assertTrue(lang.has(ending.lineKey(i)), "missing line " + ending.lineKey(i));
			}
			for (Identifier souvenir : ending.souvenirs()) {
				helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(souvenir), ending + " souvenir " + souvenir + " exists");
			}
			helper.assertFalse(ending.souvenir(RandomSource.create(1L)).isEmpty(), ending + " always gives a souvenir");
		}
		helper.assertTrue(lang.get(BlackoutEnding.FARMYARD.lineKey(0)).getAsString().contains("%s"), "the barn's first line names the animal");
		helper.assertFalse(lang.keySet().stream().anyMatch(k -> k.startsWith("blackout.vineandbarrel.wake.")), "the old lines are gone");
		helper.succeed();
	}

	/**
	 * Polish #9/#10: waking up sounds like the place (the sheets of a bed, the water under the boat, the ground you lie
	 * on), getting up is a thud on the ground (none while sitting in the boat), and the heartbeat is Tipsy V's, on the
	 * client only, with its own volume setting.
	 */
	private static void soundsFitThePlace(GameTestHelper helper) {
		var hay = net.minecraft.world.level.block.Blocks.HAY_BLOCK.defaultBlockState();
		var planks = net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState();
		for (BlackoutEnding ending : BlackoutEnding.values()) {
			var sound = jexxme.vineandbarrel.tipsy.TipsySounds.wakeSound(ending, hay);
			var expected = switch (ending.pose()) {
				case BED -> TipsyRegistry.WAKE_IN_BED_SOUND.value();
				case SEAT -> net.minecraft.sounds.SoundEvents.BOAT_PADDLE_WATER;
				case GROUND -> hay.getSoundType().getStepSound();
			};
			helper.assertTrue(sound.event() == expected, ending + " wakes up to " + sound.event().location());
			helper.assertTrue(sound.volume() > 0.0F && sound.volume() <= 1.0F, ending + " is quiet: " + sound.volume());
		}
		helper.assertTrue(jexxme.vineandbarrel.tipsy.TipsySounds.wakeSound(BlackoutEnding.TAVERN, planks).event() == planks.getSoundType().getStepSound(),
			"the tavern's floorboards");
		helper.assertTrue(jexxme.vineandbarrel.tipsy.TipsySounds.getUpSound(BlackoutEnding.Pose.SEAT, planks) == null, "no thud in the boat");
		helper.assertTrue(jexxme.vineandbarrel.tipsy.TipsySounds.getUpSound(BlackoutEnding.Pose.GROUND, planks).event() == planks.getSoundType().getFallSound(),
			"a thud on the floor");
		helper.assertTrue(jexxme.vineandbarrel.tipsy.TipsySounds.getUpSound(BlackoutEnding.Pose.BED, planks) != null, "out of bed onto the floor");
		helper.assertValueEqual(jexxme.vineandbarrel.tipsy.TipsySounds.HEARTBEAT_LEVEL, jexxme.vineandbarrel.tipsy.Tipsy.ABSOLUTE_MAX_LEVEL, "the heartbeat is Tipsy V's");
		var volume = jexxme.vineandbarrel.tipsy.TipsyConfig.HEARTBEAT_VOLUME;
		helper.assertTrue(volume.side() == jexxme.vineandbarrel.platform.ConfigValue.Side.CLIENT && volume.min() == 0.0 && volume.getDefault() == 1.0,
			"heartbeat volume: a client setting, 0 turns it off");
		// On the server the heartbeat plays nothing (it is the local player's own, on their client)
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		jexxme.vineandbarrel.tipsy.TipsySounds.heartbeat(player, 1.0);
		player.discard();
		helper.succeed();
	}

	/** The client's timeline: lids fall, stay shut until the eyes open, the body tips over, lies, and gets up. */
	private static void poseMath(GameTestHelper helper) {
		helper.assertValueEqual(BlackoutPose.eyesClosed(Blackout.TICKS), 0.0F, "eyes open at the last drink");
		helper.assertValueEqual(BlackoutPose.eyesClosed(Blackout.TICKS - Blackout.FADE_OUT), 1.0F, "shut after the fade");
		helper.assertValueEqual(BlackoutPose.eyesClosed(Blackout.WAKE_AT), 1.0F, "shut while waking up somewhere");
		helper.assertValueEqual(BlackoutPose.eyesClosed(Blackout.EYES_OPEN), 1.0F, "still shut when they start to open");
		helper.assertValueEqual(BlackoutPose.eyesClosed(Blackout.EYES_UP), 0.0F, "open");
		float peek = BlackoutPose.eyesClosed(Blackout.EYES_OPEN - 0.45F * (Blackout.EYES_OPEN - Blackout.EYES_UP));
		float droop = BlackoutPose.eyesClosed(Blackout.EYES_OPEN - 0.6F * (Blackout.EYES_OPEN - Blackout.EYES_UP));
		helper.assertTrue(peek < droop && droop < 1.0F, "they peek, droop and open: " + peek + " " + droop);
		helper.assertValueEqual(BlackoutPose.lying(Blackout.TICKS), 0.0F, "standing at the last drink");
		helper.assertValueEqual(BlackoutPose.lying(Blackout.TICKS - Blackout.TIP), 1.0F, "down after the tip");
		helper.assertValueEqual(BlackoutPose.lying(Blackout.WAKE_AT), 1.0F, "lying");
		helper.assertValueEqual(BlackoutPose.lying(Blackout.GET_UP), 1.0F, "lying until getting up");
		helper.assertValueEqual(BlackoutPose.lying(0.0F), 0.0F, "up at the end");
		helper.assertTrue(BlackoutPose.lying(Blackout.GET_UP / 2.0F) > 0.0F && BlackoutPose.lying(Blackout.GET_UP / 2.0F) < 1.0F, "getting up slowly");
		// The head ends up a body's length to the side, near the ground
		Vec3 head = BlackoutPose.tipped(0.0F, 90.0F, 1.0F, 1.6);
		helper.assertTrue(Math.abs(head.y - BlackoutPose.LIFT) < 1.0E-4 && Math.abs(Math.hypot(head.x, head.z) - 1.6) < 1.0E-4, "head lying " + head);
		helper.assertTrue(BlackoutPose.side(1) != BlackoutPose.side(2), "players fall to both sides");
		helper.succeed();
	}

	// ------------------------------------------------------------------ safety

	/** A spot is safe on solid ground with room to stand; not on water, next to lava or fire, on a cactus or near a monster. */
	private static void spotSafety(GameTestHelper helper) {
		floor(helper, 32);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BlackoutSpots.standAt(level, helper.absolutePos(new BlockPos(4, 1, 4))) != null, "plain stone floor");
		helper.setBlock(new BlockPos(8, 0, 4), Blocks.WATER);
		helper.assertTrue(BlackoutSpots.standAt(level, helper.absolutePos(new BlockPos(8, 1, 4))) == null, "on water");
		helper.setBlock(new BlockPos(13, 1, 4), Blocks.LAVA);
		helper.assertTrue(BlackoutSpots.standAt(level, helper.absolutePos(new BlockPos(12, 1, 4))) == null, "next to lava");
		helper.setBlock(new BlockPos(17, 1, 4), Blocks.FIRE);
		helper.assertTrue(BlackoutSpots.standAt(level, helper.absolutePos(new BlockPos(16, 1, 4))) == null, "next to fire");
		helper.setBlock(new BlockPos(20, 0, 4), Blocks.MAGMA_BLOCK);
		helper.assertTrue(BlackoutSpots.standAt(level, helper.absolutePos(new BlockPos(20, 1, 4))) == null, "on magma");
		helper.setBlock(new BlockPos(24, 1, 4), Blocks.SWEET_BERRY_BUSH);
		helper.assertTrue(BlackoutSpots.standAt(level, helper.absolutePos(new BlockPos(24, 1, 4))) == null, "in a berry bush");
		helper.setBlock(new BlockPos(4, 2, 10), Blocks.STONE);
		helper.assertTrue(BlackoutSpots.standAt(level, helper.absolutePos(new BlockPos(4, 1, 10))) == null, "no room to stand");
		helper.setBlock(new BlockPos(8, 0, 10), Blocks.AIR);
		helper.setBlock(new BlockPos(8, -1, 10), Blocks.AIR);
		helper.assertTrue(BlackoutSpots.standAt(level, helper.absolutePos(new BlockPos(8, 1, 10))) == null, "nothing to stand on");
		helper.setBlock(new BlockPos(12, 1, 10), Blocks.STONE_SLAB);
		Vec3 slab = BlackoutSpots.standAt(level, helper.absolutePos(new BlockPos(12, 1, 10)));
		helper.assertTrue(slab != null && Math.abs(slab.y - helper.absolutePos(new BlockPos(12, 1, 10)).getY() - 0.5) < 1.0E-4, "on top of a slab: " + slab);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(20, 1, 20));
		zombie.setNoAi(true);
		helper.assertTrue(BlackoutSpots.standAt(level, helper.absolutePos(new BlockPos(24, 1, 20))) == null, "a zombie nearby");
		zombie.discard();
		helper.assertTrue(BlackoutSpots.standAt(level, helper.absolutePos(new BlockPos(24, 1, 20))) != null, "once it's gone");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the endings

	/**
	 * Home: the player's own bed when it stands and is free; not when someone lies in it, it is gone, it is in another
	 * dimension or too far away. Waking there is lying in it, without touching the spawn point or the phantom counter.
	 */
	private static void home(GameTestHelper helper) {
		floor(helper, 32);
		withPlayer(helper, (h, player) -> {
			ServerLevel level = h.getLevel();
			helper.assertTrue(BlackoutSpots.home(player) == null, "no bed, no home");
			BlockPos head = bed(h, new BlockPos(6, 1, 6), Direction.NORTH);
			// Spawn set at the foot half, as a click on it would
			BlockPos foot = head.south();
			ServerPlayer.RespawnConfig spawn = new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), foot, 0.0F, 0.0F), false);
			player.setRespawnPosition(spawn, false);
			BlackoutSpots.Wake wake = BlackoutSpots.home(player);
			helper.assertTrue(wake != null && head.equals(wake.bed()), "home is the bed's head: " + wake);
			helper.assertValueEqual(wake.ending().pose(), BlackoutEnding.Pose.BED, "home is lain in");

			level.setBlockAndUpdate(head, level.getBlockState(head).setValue(BedBlock.OCCUPIED, true));
			helper.assertTrue(BlackoutSpots.home(player) == null, "someone lies in it");
			level.setBlockAndUpdate(head, level.getBlockState(head).setValue(BedBlock.OCCUPIED, false));

			player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.NETHER, foot, 0.0F, 0.0F), false), false);
			helper.assertTrue(BlackoutSpots.home(player) == null, "a bed in another dimension");
			player.setRespawnPosition(spawn, false);

			// Too far to stumble home: a blackout is no way to travel
			Vec3 here = player.position();
			player.snapTo(head.getX() + BlackoutSpots.HOME_RANGE + 2, here.y, head.getZ());
			helper.assertTrue(BlackoutSpots.home(player) == null, "a bed more than " + BlackoutSpots.HOME_RANGE + " blocks away");
			player.snapTo(head.getX() + BlackoutSpots.HOME_RANGE - 2, here.y, head.getZ());
			helper.assertTrue(BlackoutSpots.home(player) != null, "a bed just within reach");
			player.snapTo(here.x, here.y, here.z);

			Stat<?> rest = Stats.CUSTOM.get(Stats.TIME_SINCE_REST);
			player.getStats().setValue(player, rest, 50000);
			blackOutTo(player, BlackoutSpots.home(player), RandomSource.create(1L));
			helper.assertTrue(player.isSleeping() && head.equals(player.getSleepingPos().orElse(null)), "lying in the own bed");
			helper.assertValueEqual(Blackout.pose(player), BlackoutEnding.Pose.BED, "pose");
			helper.assertValueEqual(player.getStats().getValue(rest), 50000, "phantoms keep counting");
			helper.assertValueEqual(player.getRespawnConfig(), spawn, "the spawn point stays");
			helper.assertValueEqual(level.getBlockState(head).getValue(BedBlock.OCCUPIED), true, "the bed is taken while lying in it");
			TipsyRegistry.BLACKOUT.remove(player);
			player.stopSleepInBed(true, true);

			h.destroyBlock(head.subtract(h.absolutePos(BlockPos.ZERO)));
			helper.assertTrue(BlackoutSpots.home(player) == null, "the bed is gone");
		});
		helper.succeed();
	}

	/**
	 * The bed is lain in by day too (vanilla would wake you), the sleep counter never reaches vanilla's 100 ticks of deep
	 * sleep, so the night can't be skipped; the blackout gets you up at {@link Blackout#GET_UP} and the bed is free again.
	 */
	private static void bedUnderDeepSleep(GameTestHelper helper) {
		floor(helper, 32);
		helper.assertTrue(Blackout.WAKE_AT - Blackout.GET_UP < 100, "the timeline keeps the bed under 100 ticks");
		ServerPlayer player = player(helper);
		ServerLevel level = helper.getLevel();
		BlockPos head = bed(helper, new BlockPos(20, 1, 20), Direction.EAST);
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), head, 0.0F, 0.0F), false), false);
		blackOutTo(player, BlackoutSpots.home(player), RandomSource.create(2L));
		helper.assertTrue(player.isSleeping(), "in bed");
		// By day vanilla asks whether you may stay in bed ("you can only sleep at night"): out cold, you may
		helper.assertTrue(TipsyEvents.keepsSleeping(player), "stays in bed by day while out");
		int[] most = {0};
		int[] inBed = {0};
		helper.onEachTick(() -> {
			if (player.isRemoved()) {
				return;
			}
			// The mock player isn't driven by a connection: tick it as one would
			player.doTick();
			most[0] = Math.max(most[0], player.getSleepTimer());
			helper.assertFalse(player.isSleepingLongEnough(), "deep asleep after " + player.getSleepTimer() + " ticks");
			if (player.isSleeping()) {
				inBed[0]++;
			}
		});
		helper.runAfterDelay(Blackout.WAKE_AT + 5, () -> {
			helper.assertFalse(Blackout.isOut(player), "awake");
			helper.assertFalse(player.isSleeping(), "out of bed");
			helper.assertTrue(most[0] < 100 && most[0] > 50, "slept " + most[0] + " ticks, under 100");
			helper.assertTrue(inBed[0] >= Blackout.WAKE_AT - Blackout.GET_UP - 2, "stayed in bed until getting up: " + inBed[0]);
			helper.assertValueEqual(level.getBlockState(head).getValue(BedBlock.OCCUPIED), false, "the bed is free again");
			done(helper, player);
			helper.succeed();
		});
	}

	/** In the open: a safe surface spot 20 to 60 blocks away. */
	private static void inTheOpen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos from = helper.absolutePos(MIDDLE);
		RandomSource random = RandomSource.create(5L);
		int found = 0;
		for (int i = 0; i < 10; i++) {
			BlackoutSpots.Wake wake = BlackoutSpots.inTheOpen(level, from, random);
			if (wake == null) {
				continue;
			}
			found++;
			assertSafe(helper, wake, "in the open");
			double distance = Math.hypot(wake.spot().x - from.getX() - 0.5, wake.spot().z - from.getZ() - 0.5);
			helper.assertTrue(distance >= BlackoutSpots.OPEN_MIN - 1.5 && distance <= BlackoutSpots.OPEN_MAX + 1.5, "20-60 away: " + distance);
			helper.assertTrue(level.canSeeSky(BlockPos.containing(wake.spot())), "under the open sky");
		}
		helper.assertTrue(found > 0, "found a spot in the open");
		helper.succeed();
	}

	/**
	 * Farmyard: next to an animal, facing it; the animal then stares at the player. A wolf (it might bite) or an animal
	 * in water doesn't count. The line names the animal.
	 */
	private static void farmyard(GameTestHelper helper) {
		floor(helper, 32);
		ServerLevel level = helper.getLevel();
		Vec3 middle = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE));
		RandomSource random = RandomSource.create(9L);
		helper.spawn(EntityTypes.WOLF, new BlockPos(14, 1, 14)).setNoAi(true);
		helper.assertTrue(BlackoutSpots.farmyard(level, middle, NEAR, random) == null, "a wolf is no farmyard");
		Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(20, 1, 16));
		helper.runAfterDelay(5, () -> {
			BlackoutSpots.Wake wake = BlackoutSpots.farmyard(level, middle, NEAR, random);
			helper.assertTrue(wake != null && wake.friend() == cow, "next to the cow: " + wake);
			assertSafe(helper, wake, "farmyard");
			double distance = wake.spot().distanceTo(cow.position());
			helper.assertTrue(distance > 1.2 && distance < 3.2, "right next to it: " + distance);
			Vec3 look = Vec3.directionFromRotation(0.0F, wake.yaw());
			Vec3 toCow = cow.position().subtract(wake.spot()).multiply(1, 0, 1).normalize();
			helper.assertTrue(look.dot(toCow) > 0.95, "facing the cow");
			ServerPlayer player = player(helper);
			blackOutTo(player, wake, random);
			helper.assertValueEqual(Blackout.pose(player), BlackoutEnding.Pose.GROUND, "lying on the ground");
			helper.assertTrue(player.position().distanceTo(wake.spot()) < 0.01, "moved there");
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(cow.getLookControl().isLookingAtTarget(), "the cow stares");
				helper.assertTrue(Math.abs(cow.getLookControl().getWantedX() - player.getX()) < 0.01, "at the player");
				helper.assertTrue(cow.getNavigation().isDone(), "and stands still");
				Blackout.Scene scene = TipsyRegistry.BLACKOUT_SCENE.get(player);
				Component line = Component.translatable(BlackoutEnding.FARMYARD.lineKey(0),
					EntityTypes.COW.getDescription());
				helper.assertTrue(line.getString().contains("Cow"), "the line names it: " + line.getString());
				helper.assertValueEqual(scene.friendType(), Optional.of("minecraft:cow"), "the scene remembers the animal");
				done(helper, player);
				helper.succeed();
			});
		});
	}

	/** In the hay: on top of a hay or barley bale under the open sky; never on plain ground, never in a barn. */
	private static void inTheHay(GameTestHelper helper) {
		floor(helper, 32);
		ServerLevel level = helper.getLevel();
		BlockPos from = helper.absolutePos(MIDDLE);
		RandomSource random = RandomSource.create(11L);
		helper.assertTrue(BlackoutSpots.inTheHay(level, from, NEAR, random) == null, "no bale, no hay");
		helper.setBlock(new BlockPos(10, 1, 10), VBBlocks.BARLEY_BALE.get());
		BlackoutSpots.Wake wake = BlackoutSpots.inTheHay(level, from, NEAR, random);
		BlockPos bale = helper.absolutePos(new BlockPos(10, 1, 10));
		helper.assertTrue(wake != null && BlockPos.containing(wake.spot()).below().equals(bale), "on the barley bale: " + wake + " (state "
			+ level.getBlockState(bale) + ", tagged " + level.getBlockState(bale).is(jexxme.vineandbarrel.tag.VBTags.HAY_BALES) + ", stand "
			+ BlackoutSpots.standAt(level, bale.above()) + ", surface " + level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bale.getX(), bale.getZ()) + ")");
		assertSafe(helper, wake, "in the hay");
		helper.assertTrue(BlackoutSpots.underOpenSky(level, wake.spot()), "under the open sky");
		// A hay bale in a barn (under a roof) is someone's build: it doesn't count
		helper.setBlock(new BlockPos(10, 1, 10), Blocks.STONE);
		helper.setBlock(new BlockPos(20, 1, 20), Blocks.HAY_BLOCK);
		for (int x = 19; x <= 21; x++) {
			for (int z = 19; z <= 21; z++) {
				helper.setBlock(new BlockPos(x, 5, z), Blocks.OAK_PLANKS);
			}
		}
		helper.assertTrue(BlackoutSpots.inTheHay(level, from, NEAR, random) == null, "never on the hay in a barn");
		helper.succeed();
	}

	/**
	 * Tavern: in front of the barrels of one of our taverns, facing them. A real tavern is placed and registered as a
	 * piece of a village (tests can't generate one); barrels that aren't in a tavern don't count.
	 */
	private static void tavern(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageType type = VillageType.PLAINS;
		SinglePoolElement element = tavernElement(helper, type);
		BlockPos origin = helper.absolutePos(TAVERN_AT);
		boolean placed = element.place(level.getStructureManager(), level, level.structureManager(), level.getChunkSource().getGenerator(),
			origin, origin, Rotation.NONE, BoundingBox.fromCorners(origin.offset(-2, -2 - VillageStructures.CELLAR_HEIGHT, -2),
				origin.offset(level.getStructureManager().get(type.tavern()).orElseThrow().getSize()).offset(1, 1, 1)), RandomSource.create(5L),
			LiquidSettings.APPLY_WATERLOGGING, false);
		helper.assertTrue(placed, "the tavern places");
		BlockPos from = helper.absolutePos(new BlockPos(7, 2, 7));
		helper.assertTrue(BlackoutSpots.tavernBarrels(level, from, 16).isEmpty(), "barrels outside a village don't count");

		BoundingBox box = element.getBoundingBox(level.getStructureManager(), origin, Rotation.NONE);
		inVillage(helper, box, () -> {
			List<BlockPos> barrels = BlackoutSpots.tavernBarrels(level, from, 16);
			// the tavern's casks, its cellar under its box included
			long inTavern = BlockPos.betweenClosedStream(VillageStructures.withCellar(box)).filter(pos -> level.getBlockState(pos).is(BarrelRegistry.AGING_BARREL.get())).count();
			helper.assertTrue(inTavern >= 6, "the tavern has its casks: " + inTavern);
			helper.assertValueEqual((long) barrels.size(), inTavern, "every cask of the tavern");
			BlackoutSpots.Wake wake = BlackoutSpots.tavern(level, from, 16, RandomSource.create(13L));
			helper.assertTrue(wake != null, "a spot in front of the barrels");
			assertSafe(helper, wake, "tavern");
			BlockPos feet = BlockPos.containing(wake.spot());
			boolean inFront = barrels.stream().anyMatch(barrel -> {
				Direction front = level.getBlockState(barrel).getValue(AgingBarrelBlock.FACING);
				return feet.equals(barrel.relative(front)) || feet.equals(barrel.relative(front, 2))
					? Math.abs(net.minecraft.util.Mth.wrapDegrees(wake.yaw() - front.getOpposite().toYRot())) < 1.0F : false;
			});
			helper.assertTrue(inFront, "in front of a barrel, facing it: " + wake);
		});
		helper.succeed();
	}

	/**
	 * Runs {@code body} with {@code box} registered as a piece of a plains village (tests can't generate one), the way
	 * world generation records it in the chunks; everything is put back afterwards.
	 */
	static void inVillage(GameTestHelper helper, BoundingBox box, Runnable body) {
		inVillage(helper, box, tavernElement(helper, VillageType.PLAINS), body);
	}

	/** {@link #inVillage(GameTestHelper, BoundingBox, Runnable)} with this pool element as the piece. */
	static void inVillage(GameTestHelper helper, BoundingBox box, net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement element, Runnable body) {
		ServerLevel level = helper.getLevel();
		Structure village = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValueOrThrow(BuiltinStructures.VILLAGE_PLAINS);
		BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
		PoolElementStructurePiece piece = new PoolElementStructurePiece(level.getStructureManager(), element,
			origin, 0, Rotation.NONE, box, LiquidSettings.APPLY_WATERLOGGING);
		ChunkPos startChunk = ChunkPos.containing(origin);
		StructureStart start = new StructureStart(village, startChunk, 0, new PiecesContainer(List.of(piece)));
		Map<LevelChunk, Map<Structure, LongSet>> referencesBefore = new HashMap<>();
		LevelChunk startAt = level.getChunk(startChunk.x(), startChunk.z());
		Map<Structure, StructureStart> startsBefore = new HashMap<>(startAt.getAllStarts());
		try {
			startAt.setStartForStructure(village, start);
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
				for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					LevelChunk chunk = level.getChunk(cx, cz);
					Map<Structure, LongSet> copy = new HashMap<>();
					chunk.getAllReferences().forEach((s, refs) -> copy.put(s, new LongOpenHashSet(refs)));
					referencesBefore.put(chunk, copy);
					chunk.addReferenceForStructure(village, startChunk.pack());
				}
			}
			body.run();
		} finally {
			startAt.setAllStarts(startsBefore);
			referencesBefore.forEach(LevelChunk::setAllReferences);
		}
	}

	private static SinglePoolElement tavernElement(GameTestHelper helper, VillageType type) {
		StructureTemplatePool pool = helper.getLevel().registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getValue(type.housesPool());
		helper.assertTrue(pool != null, type.housesPool() + " exists");
		return pool.rawTemplates.stream().map(e -> e.getFirst()).filter(e -> VillageStructures.isTemplate(e, type.tavern()))
			.map(e -> (SinglePoolElement) e).findFirst().orElseThrow(() -> helper.assertionException("no tavern in " + type.housesPool()));
	}

	/**
	 * Someone else's bed: a free villager's bed with the villager close by. Not an occupied bed, not the player's own;
	 * the player lies in it and the villager shakes its head as the eyes open, and the bed is free again afterwards.
	 */
	private static void someoneElsesBed(GameTestHelper helper) {
		floor(helper, 32);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		RandomSource random = RandomSource.create(17L);
		helper.assertTrue(BlackoutSpots.someoneElsesBed(level, player, NEAR, random) == null, "no villager, no bed");
		BlockPos head = bed(helper, new BlockPos(10, 1, 10), Direction.NORTH);
		Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 10));
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
		helper.assertTrue(level.getPoiManager().take(t -> t.is(PoiTypes.HOME), (t, pos) -> pos.equals(head), head, 1).isPresent(),
			"the villager has claimed the bed");
		helper.assertTrue(BlackoutSpots.someoneElsesBed(level, player, NEAR, random) == null, "a villager bed outside a village (a player's build)");
		BoundingBox house = BoundingBox.fromCorners(head.offset(-4, -1, -4), head.offset(4, 4, 4));
		BlackoutSpots.Wake[] found = new BlackoutSpots.Wake[1];
		inVillage(helper, house, () -> {
			level.setBlockAndUpdate(head, level.getBlockState(head).setValue(BedBlock.OCCUPIED, true));
			helper.assertTrue(BlackoutSpots.someoneElsesBed(level, player, NEAR, random) == null, "the bed is occupied");
			level.setBlockAndUpdate(head, level.getBlockState(head).setValue(BedBlock.OCCUPIED, false));
			player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), head, 0.0F, 0.0F), false), false);
			helper.assertTrue(BlackoutSpots.someoneElsesBed(level, player, NEAR, random) == null, "the player's own bed isn't someone else's");
			player.setRespawnPosition(null, false);
			found[0] = BlackoutSpots.someoneElsesBed(level, player, NEAR, random);
		});
		BlackoutSpots.Wake wake = found[0];
		helper.assertTrue(wake != null && head.equals(wake.bed()) && wake.friend() == villager, "the villager's bed in the village: " + wake);
		blackOutTo(player, wake, random);
		helper.assertTrue(player.isSleeping() && head.equals(player.getSleepingPos().orElse(null)), "lying in it");
		helper.assertValueEqual(villager.getUnhappyCounter(), 0, "the villager doesn't know yet");
		// Vanilla's night check (the REST schedule's ValidateNearbyPoi for HOME), run for real on the villager's brain while
		// it stands awake next to its occupied bed: the villager keeps its home and its claim
		var validateHome = ValidateNearbyPoi.create(t -> t.is(PoiTypes.HOME), MemoryModuleType.HOME);
		validateHome.tryStart(level, villager, level.getGameTime());
		helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent(), "the night check keeps the villager's home");
		helper.assertTrue(claimed(level, head), "and its claim on the bed");
		// From here its brain ticks as usual, whatever the time of day
		villager.setNoAi(false);
		helper.runAfterDelay(Blackout.WAKE_AT - Blackout.HEAD_SHAKE_AT + 2, () -> {
			helper.assertTrue(villager.getUnhappyCounter() > 0, "the villager shakes its head as the eyes open");
			helper.assertTrue(player.isSleeping(), "still lying there");
			validateHome.tryStart(level, villager, level.getGameTime());
		});
		helper.runAfterDelay(Blackout.WAKE_AT + 3, () -> {
			helper.assertFalse(player.isSleeping(), "up again");
			helper.assertValueEqual(level.getBlockState(head).getValue(BedBlock.OCCUPIED), false, "the bed is free again");
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).filter(head::equals).isPresent(),
				"it is still the villager's bed");
			helper.assertTrue(claimed(level, head), "still claimed");
			// The check itself is real: a bed occupied by anyone else does cost the villager its home
			villager.setNoAi(true);
			level.setBlockAndUpdate(head, level.getBlockState(head).setValue(BedBlock.OCCUPIED, true));
			validateHome.tryStart(level, villager, level.getGameTime());
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).isEmpty(), "vanilla's check without a blackout");
			done(helper, player);
			helper.succeed();
		});
	}

	private static boolean claimed(ServerLevel level, BlockPos bed) {
		return level.getPoiManager().getCountInRange(t -> t.is(PoiTypes.HOME), bed, 1, PoiManager.Occupancy.IS_OCCUPIED) == 1;
	}

	/** On the roof: the top of something 4-8 above the ground around it; not a step, not a tower too high to fall from. */
	private static void onTheRoof(GameTestHelper helper) {
		floor(helper, 32);
		ServerLevel level = helper.getLevel();
		BlockPos from = helper.absolutePos(MIDDLE);
		RandomSource random = RandomSource.create(19L);
		// A step 2 high is no roof
		helper.setBlock(new BlockPos(12, 1, 12), Blocks.STONE);
		helper.setBlock(new BlockPos(12, 2, 12), Blocks.STONE);
		helper.assertTrue(BlackoutSpots.onTheRoof(level, from, NEAR, random) == null, "a step isn't a roof");
		// A 3x3 hut 5 high with a plank roof
		for (int x = 18; x <= 20; x++) {
			for (int z = 18; z <= 20; z++) {
				for (int y = 1; y <= 5; y++) {
					helper.setBlock(new BlockPos(x, y, z), y == 5 ? Blocks.OAK_PLANKS : Blocks.STONE_BRICKS);
				}
			}
		}
		BlackoutSpots.Wake wake = BlackoutSpots.onTheRoof(level, from, NEAR, random);
		helper.assertTrue(wake != null, "the hut's roof");
		assertSafe(helper, wake, "on the roof");
		BlockPos below = BlockPos.containing(wake.spot()).below();
		helper.assertTrue(level.getBlockState(below).is(Blocks.OAK_PLANKS), "standing on the roof: " + level.getBlockState(below));
		helper.assertTrue(wake.spot().y - helper.absolutePos(BlockPos.ZERO).getY() >= 1 + BlackoutSpots.ROOF_MIN_HEIGHT, "at least 4 up");
		helper.succeed();
	}

	/** In a boat: on calm water, sitting in a brand new oak boat, which stays after waking (a gift). Not on a puddle. */
	private static void inABoat(GameTestHelper helper) {
		floor(helper, 32);
		ServerLevel level = helper.getLevel();
		BlockPos from = helper.absolutePos(MIDDLE);
		RandomSource random = RandomSource.create(23L);
		helper.setBlock(new BlockPos(8, 1, 8), Blocks.WATER);
		helper.assertTrue(BlackoutSpots.inABoat(level, from, NEAR, random) == null, "one block of water is too small");
		// A 4x4 pond with a rim, on the floor
		for (int x = 19; x <= 24; x++) {
			for (int z = 19; z <= 24; z++) {
				boolean rim = x == 19 || x == 24 || z == 19 || z == 24;
				helper.setBlock(new BlockPos(x, 1, z), rim ? Blocks.STONE : Blocks.WATER);
			}
		}
		BlackoutSpots.Wake wake = BlackoutSpots.inABoat(level, from, NEAR, random);
		helper.assertTrue(wake != null, "the pond");
		BlockPos water = BlockPos.containing(wake.spot().x - 0.5, wake.spot().y - 1.0, wake.spot().z - 0.5);
		helper.assertTrue(BlackoutSpots.calmWater(level, water) && BlackoutSpots.calmWater(level, water.east())
			&& BlackoutSpots.calmWater(level, water.south()) && BlackoutSpots.calmWater(level, water.south().east()), "on calm water");
		ServerPlayer player = player(helper);
		blackOutTo(player, wake, random);
		helper.assertTrue(player.getVehicle() instanceof Boat, "sitting in a boat");
		Boat boat = (Boat) player.getVehicle();
		helper.assertValueEqual(boat.getType(), EntityTypes.OAK_BOAT, "an oak boat");
		helper.assertValueEqual(Blackout.pose(player), BlackoutEnding.Pose.SEAT, "pose");
		helper.runAfterDelay(Blackout.WAKE_AT + 3, () -> {
			helper.assertFalse(Blackout.isOut(player), "awake");
			helper.assertTrue(player.getVehicle() == boat, "still sitting in it after waking");
			helper.assertTrue(boat.isAlive(), "the boat stays");
			done(helper, player);
			helper.succeed();
		});
	}

	/** Right here: nothing else possible, you wake where you fell (not moved at all), with a souvenir from any pool. */
	private static void rightHere(GameTestHelper helper) {
		floor(helper, 32);
		ServerPlayer player = player(helper);
		Vec3 before = player.position();
		BlackoutSpots.Wake wake = BlackoutSpots.find(BlackoutEnding.RIGHT_HERE, player, RandomSource.create(29L));
		helper.assertTrue(wake != null && wake.ending() == BlackoutEnding.RIGHT_HERE, "always possible");
		blackOutTo(player, wake, RandomSource.create(29L));
		helper.assertTrue(player.position().distanceTo(before) < 1.0E-6, "not moved");
		helper.runAfterDelay(Blackout.WAKE_AT + 3, () -> {
			helper.assertFalse(Blackout.isOut(player), "awake");
			helper.assertValueEqual(Blackout.memory(player).last(), "right_here", "remembered as the last");
			done(helper, player);
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ nothing lost

	/**
	 * Nothing is ever lost: a full inventory and armour come through a whole blackout untouched, the souvenir lands at
	 * the feet when the pockets are full, and a helmet is never swapped for the pumpkin.
	 */
	private static void nothingLost(GameTestHelper helper) {
		floor(helper, 32);
		ServerPlayer player = player(helper);
		List<Item> stock = List.of(Items.DIAMOND, Items.BREAD, Items.TORCH, Items.OAK_LOG, Items.IRON_INGOT, Items.APPLE);
		for (int slot = 0; slot < 36; slot++) {
			player.getInventory().setItem(slot, new ItemStack(stock.get(slot % stock.size()), 1 + slot));
		}
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
		List<ItemStack> before = new ArrayList<>();
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			before.add(player.getInventory().getItem(slot).copy());
		}
		// Woken up on a bale a few blocks away: moved, and still nothing lost
		helper.setBlock(new BlockPos(10, 1, 10), Blocks.HAY_BLOCK);
		RandomSource random = RandomSource.create(37L);
		blackOutTo(player, BlackoutSpots.inTheHay(helper.getLevel(), helper.absolutePos(MIDDLE), NEAR, random), random);
		helper.assertTrue(player.position().distanceTo(helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE))) > 3.0, "moved to the bale");
		helper.runAfterDelay(Blackout.WAKE_AT + 3, () -> {
			helper.assertFalse(Blackout.isOut(player), "awake");
			for (int slot = 0; slot < before.size(); slot++) {
				ItemStack now = player.getInventory().getItem(slot);
				helper.assertTrue(ItemStack.matches(before.get(slot), now), "slot " + slot + " changed: " + before.get(slot) + " -> " + now);
			}
			List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3.0));
			helper.assertValueEqual(dropped.size(), 1, "the souvenir lies at the feet (player at " + player.position() + ", items around: "
				+ helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(40.0)).stream().map(e -> e.getItem() + "@" + e.position()).toList() + ")");
			done(helper, player);
			helper.succeed();
		});
	}

	/** The pumpkin hat: about one wake-up in six, only on an empty head, and it is a carved pumpkin. */
	private static void pumpkinHat(GameTestHelper helper) {
		floor(helper, 32);
		withPlayer(helper, (h, player) -> {
			int pumpkins = 0;
			int runs = 600;
			RandomSource random = RandomSource.create(31L);
			BlackoutSpots.Wake here = BlackoutSpots.find(BlackoutEnding.RIGHT_HERE, player, random);
			for (int i = 0; i < runs; i++) {
				player.getInventory().clearContent();
				Blackout.wakeUp(player, here, random);
				ItemStack hat = player.getItemBySlot(EquipmentSlot.HEAD);
				if (!hat.isEmpty()) {
					helper.assertTrue(hat.is(Items.CARVED_PUMPKIN), "the hat is a carved pumpkin: " + hat);
					pumpkins++;
				}
			}
			double share = pumpkins / (double) runs;
			helper.assertTrue(Math.abs(share - 1.0 / Blackout.PUMPKIN_ONE_IN) < 0.05, "about one in six: " + share);
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
			for (int i = 0; i < 60; i++) {
				Blackout.wakeUp(player, here, random);
				helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "a helmet stays on");
			}
			TipsyRegistry.BLACKOUT_SCENE.remove(player);
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ Lost Weekend and memory

	/**
	 * Lost Weekend: a challenge under Where Am I?, one criterion per ending (right here isn't one), done once every
	 * ending was woken up from; Where Am I? comes with the first.
	 */
	private static void lostWeekend(GameTestHelper helper) {
		AdvancementHolder holder = advancement(helper, "lost_weekend");
		helper.assertValueEqual(holder.value().display().orElseThrow().getType(), AdvancementType.CHALLENGE, "a challenge");
		helper.assertValueEqual(holder.value().parent().orElseThrow(), VBAdvancementGenerator.id("where_am_i"), "after Where Am I?");
		helper.assertValueEqual(holder.value().criteria().keySet(),
			Set.copyOf(BlackoutEnding.COLLECTABLE.stream().map(BlackoutEnding::id).toList()), "one criterion per ending");
		withPlayer(helper, (h, player) -> {
			BlackoutTrigger.award(player, BlackoutEnding.RIGHT_HERE);
			helper.assertFalse(earned(h, player, "lost_weekend"), "right here doesn't count");
			for (BlackoutEnding ending : BlackoutEnding.COLLECTABLE) {
				helper.assertFalse(earned(h, player, "lost_weekend"), "not before " + ending);
				BlackoutTrigger.award(player, ending);
			}
			helper.assertTrue(earned(h, player, "lost_weekend"), "every ending once");
		});
		// The real path: a whole blackout earns Where Am I? and the ending's criterion
		floor(helper, 32);
		withPlayer(helper, (h, player) -> {
			helper.assertFalse(earned(h, player, "where_am_i"), "before");
			Blackout.start(player);
			TipsyRegistry.BLACKOUT.set(player, Blackout.WAKE_AT);
			Blackout.wakeUp(player, BlackoutSpots.find(BlackoutEnding.RIGHT_HERE, player, RandomSource.create(1L)), RandomSource.create(1L));
			TipsyRegistry.BLACKOUT.set(player, 1);
			Blackout.tick(player);
			helper.assertTrue(earned(h, player, "where_am_i"), "Where Am I? after the first blackout");
		});
		helper.succeed();
	}

	/** What a player has seen of their blackouts is saved and kept through death. */
	private static void memorySurvivesDeath(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Blackout.Memory memory = new Blackout.Memory(List.of("home", "tavern"), "tavern", "blackout.vineandbarrel.tavern.2");
		TipsyRegistry.BLACKOUT_MEMORY.set(player, memory);
		TipsyRegistry.BLACKOUT.set(player, 40);
		ServerPlayer reborn = helper.getLevel().getServer().getPlayerList().respawn(player, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
		try {
			helper.assertValueEqual(Blackout.memory(reborn), memory, "the memory after death");
			helper.assertFalse(Blackout.isOut(reborn), "a blackout doesn't outlive death");
		} finally {
			done(helper, reborn);
		}
		helper.succeed();
	}
	// ------------------------------------------------------------------ review: where nobody may wake up

	/**
	 * The Nether and the End have no open sky: the outdoor endings never happen there. In the Nether the surface
	 * heightmap is the bedrock roof, which would pass every other rule.
	 */
	private static void noOpenSkyInNetherOrEnd(GameTestHelper helper) {
		ServerLevel overworld = helper.getLevel();
		ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
		helper.assertTrue(BlackoutSpots.hasSky(overworld), "the overworld has a sky");
		helper.assertTrue(nether != null && !BlackoutSpots.hasSky(nether), "the Nether has a roof");
		// A roof like the Nether's bedrock one (the test server's Nether is flat): nothing above it, a heightmap on top
		nether.getChunk(0, 0);
		BlockPos roofBlock = new BlockPos(8, 127, 8);
		BlockState was = nether.getBlockState(roofBlock);
		nether.setBlockAndUpdate(roofBlock, Blocks.BEDROCK.defaultBlockState());
		int roof = nether.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 8, 8);
		helper.assertValueEqual(roof, 128, "the heightmap stands on the roof");
		helper.assertFalse(BlackoutSpots.underOpenSky(nether, new Vec3(8.5, roof, 8.5)), "the roof top is no open sky");
		nether.setBlockAndUpdate(roofBlock, was);
		RandomSource random = RandomSource.create(41L);
		BlockPos at = new BlockPos(8, 64, 8);
		helper.assertTrue(BlackoutSpots.inTheOpen(nether, at, random) == null, "in the open: never in the Nether");
		helper.assertTrue(BlackoutSpots.onTheRoof(nether, new BlockPos(8, roof, 8), 16, random) == null, "the roof: never the Nether's");
		helper.assertTrue(BlackoutSpots.inTheHay(nether, at, 16, random) == null, "the hay: never in the Nether");
		helper.assertTrue(BlackoutSpots.inABoat(nether, at, 16, random) == null, "a boat: never in the Nether");
		helper.assertTrue(BlackoutSpots.farmyard(nether, Vec3.atCenterOf(at), 16, random) == null, "the farmyard: never in the Nether");
		ServerLevel end = overworld.getServer().getLevel(Level.END);
		if (end != null) {
			helper.assertFalse(BlackoutSpots.hasSky(end), "the End has no sky either");
			helper.assertTrue(BlackoutSpots.inTheOpen(end, at, random) == null, "in the open: never in the End");
		}
		for (BlackoutEnding ending : BlackoutEnding.values()) {
			boolean outdoors = ending == BlackoutEnding.IN_THE_OPEN || ending == BlackoutEnding.FARMYARD || ending == BlackoutEnding.IN_THE_HAY
				|| ending == BlackoutEnding.ON_THE_ROOF || ending == BlackoutEnding.IN_A_BOAT;
			helper.assertValueEqual(ending.needsOpenSky(), outdoors, ending + " needs the open sky");
		}
		helper.succeed();
	}

	/**
	 * Nobody wakes up inside someone's build: a closed stone hut with a bale and a cow, and a glass dome over another
	 * bale, are off limits; the same bale and cow out in the open are fine.
	 */
	private static void neverInsideSealedBuilds(GameTestHelper helper) {
		floor(helper, 32);
		ServerLevel level = helper.getLevel();
		BlockPos from = helper.absolutePos(MIDDLE);
		RandomSource random = RandomSource.create(43L);
		// A closed hut: walls and a roof all round, a bale and a cow inside
		box(helper, new BlockPos(6, 1, 6), new BlockPos(12, 5, 12), Blocks.STONE_BRICKS);
		helper.setBlock(new BlockPos(9, 1, 9), Blocks.HAY_BLOCK);
		Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(8, 1, 10));
		cow.setNoAi(true);
		// A glass dome over another bale
		box(helper, new BlockPos(18, 1, 18), new BlockPos(22, 4, 22), Blocks.GLASS);
		helper.setBlock(new BlockPos(20, 1, 20), Blocks.HAY_BLOCK);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(BlackoutSpots.inTheHay(level, from, NEAR, random) == null, "no bale in a hut or under glass");
			helper.assertTrue(BlackoutSpots.farmyard(level, Vec3.atCenterOf(from), NEAR, random) == null, "no cow in a closed hut");
			// Out in the open they count
			helper.setBlock(new BlockPos(26, 1, 26), Blocks.HAY_BLOCK);
			BlackoutSpots.Wake hay = BlackoutSpots.inTheHay(level, from, NEAR, random);
			helper.assertTrue(hay != null && BlackoutSpots.underOpenSky(level, hay.spot()), "the bale out in the open");
			cow.snapTo(helper.absoluteVec(new Vec3(16.5, 1.0, 24.5)));
			BlackoutSpots.Wake yard = BlackoutSpots.farmyard(level, Vec3.atCenterOf(from), NEAR, random);
			helper.assertTrue(yard != null && BlackoutSpots.underOpenSky(level, yard.spot()), "the cow out in the open");
			helper.succeed();
		});
	}

	/** Walls, floor and roof of a hollow box between the corners (relative positions). */
	private static void box(GameTestHelper helper, BlockPos min, BlockPos max, Block block) {
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			boolean shell = pos.getX() == min.getX() || pos.getX() == max.getX() || pos.getY() == min.getY() || pos.getY() == max.getY()
				|| pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
			if (shell && pos.getY() > min.getY()) {
				helper.setBlock(pos, block);
			}
		}
	}

	/**
	 * The boat follows the safety rules: not where a drowned swims, not where something already floats; and if the boat
	 * can't be put down after all, the player wakes up right where they fell, never standing on the water.
	 */
	private static void boatSafeOrRightHere(GameTestHelper helper) {
		floor(helper, 32);
		ServerLevel level = helper.getLevel();
		BlockPos from = helper.absolutePos(MIDDLE);
		RandomSource random = RandomSource.create(47L);
		// A 2x2 pond with a rim: one place for a boat
		for (int x = 19; x <= 22; x++) {
			for (int z = 19; z <= 22; z++) {
				boolean rim = x == 19 || x == 22 || z == 19 || z == 22;
				helper.setBlock(new BlockPos(x, 1, z), rim ? Blocks.STONE : Blocks.WATER);
			}
		}
		BlackoutSpots.Wake wake = BlackoutSpots.inABoat(level, from, NEAR, random);
		helper.assertTrue(wake != null, "the pond");
		var drowned = helper.spawn(EntityTypes.DROWNED, new BlockPos(14, 1, 14));
		drowned.setNoAi(true);
		helper.assertTrue(BlackoutSpots.inABoat(level, from, NEAR, random) == null, "a drowned nearby");
		drowned.discard();
		Boat other = EntityTypes.OAK_BOAT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		other.snapTo(wake.spot().x, wake.spot().y, wake.spot().z, 0.0F, 0.0F);
		level.addFreshEntity(other);
		helper.assertTrue(BlackoutSpots.inABoat(level, from, NEAR, random) == null, "a boat already floats there");
		other.discard();
		helper.assertTrue(BlackoutSpots.inABoat(level, from, NEAR, random) != null, "free again");
		// Found, then the spot is taken before the wake-up: right here instead
		ServerPlayer player = player(helper);
		Vec3 before = player.position();
		helper.setBlock(new BlockPos(20, 2, 20), Blocks.OAK_PLANKS);
		blackOutTo(player, wake, random);
		helper.assertTrue(player.position().distanceTo(before) < 1.0E-6, "not moved");
		helper.assertFalse(player.isPassenger(), "not in a boat");
		helper.assertValueEqual(Blackout.pose(player), BlackoutEnding.Pose.GROUND, "lying where they fell");
		helper.assertValueEqual(Blackout.memory(player).last(), "right_here", "it counts as right here");
		helper.assertTrue(level.getEntitiesOfClass(Boat.class, new AABB(BlockPos.containing(wake.spot())).inflate(4)).isEmpty(), "no boat left behind");
		TipsyRegistry.BLACKOUT.remove(player);
		done(helper, player);
		helper.succeed();
	}

	/**
	 * A blackout's bed is no night's sleep: when the others skip the night meanwhile, Sleep It Off isn't earned and Tipsy
	 * stays; and the blacked-out sleeper isn't counted among the players sleeping.
	 */
	private static void bedIsNotANightsSleep(GameTestHelper helper) {
		floor(helper, 32);
		withPlayer(helper, (h, player) -> {
			ServerLevel level = h.getLevel();
			BlockPos head = bed(h, new BlockPos(20, 1, 20), Direction.EAST);
			player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), head, 0.0F, 0.0F), false), false);
			blackOutTo(player, BlackoutSpots.home(player), RandomSource.create(53L));
			helper.assertTrue(player.isSleeping(), "in bed");
			net.minecraft.server.players.SleepStatus status = new net.minecraft.server.players.SleepStatus();
			status.update(List.of(player));
			helper.assertValueEqual(status.amountSleeping(), 0, "not counted as sleeping");
			// The night is skipped by the others: vanilla wakes every sleeper like this
			player.stopSleepInBed(false, false);
			helper.assertFalse(earned(h, player, "sleep_it_off"), "no Sleep It Off from a blackout");
			helper.assertValueEqual(jexxme.vineandbarrel.tipsy.Tipsy.getLevel(player), 1, "still Tipsy I");
			TipsyRegistry.BLACKOUT.remove(player);
		});
		helper.succeed();
	}

	/**
	 * A roof is never a cliff edge: a tower whose ground is mostly a high plateau (so the middle of the ground around it
	 * is close) but drops deep on one side doesn't count; walking off it would be a long fall.
	 */
	private static void roofNeverACliff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// A plateau 6 high over the whole area, a tower 6 higher in its middle: a roof, a fall of 6 from any side
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = 0; y <= 6; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		for (int x = 7; x <= 9; x++) {
			for (int z = 7; z <= 9; z++) {
				for (int y = 7; y <= 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS);
				}
			}
		}
		BlockPos tower = helper.absolutePos(new BlockPos(8, 13, 8));
		helper.assertTrue(BlackoutSpots.onTheRoof(level, tower, 1, RandomSource.create(59L)) != null, "the tower on the plateau");
		// Now the plateau breaks off behind the tower: most of the ground around it is still high (its middle too), but
		// walking off that side drops 12
		for (int x = 0; x < 16; x++) {
			for (int z = 11; z < 16; z++) {
				for (int y = 1; y <= 6; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		BlackoutSpots.Wake wake = BlackoutSpots.onTheRoof(level, tower, 1, RandomSource.create(59L));
		helper.assertTrue(wake == null, "the tower over the cliff: " + wake);
		helper.succeed();
	}

	/**
	 * Where the entities aren't loaded (a chunk just read from disk, or none at all) nobody can tell whether a monster
	 * waits, so it isn't safe; and looking for home doesn't load chunks.
	 */
	private static void unloadedEntitiesUnsafe(GameTestHelper helper) {
		floor(helper, 32);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BlackoutSpots.noMonsters(level, new AABB(helper.absolutePos(MIDDLE))), "the test area is known to be clear");
		BlockPos far = helper.absolutePos(MIDDLE).offset(4000, 0, 4000);
		helper.assertFalse(BlackoutSpots.noMonsters(level, new AABB(far)), "nobody knows what is out there");
		withPlayer(helper, (h, player) -> {
			BlockPos nearButUnloaded = null;
			for (int dx = 64; dx <= 120 && nearButUnloaded == null; dx += 16) {
				BlockPos pos = h.absolutePos(MIDDLE).offset(dx, 0, dx);
				if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
					nearButUnloaded = pos;
				}
			}
			if (nearButUnloaded != null) {
				player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), nearButUnloaded, 0.0F, 0.0F), false), false);
				helper.assertTrue(BlackoutSpots.home(player) == null, "no home in an unloaded chunk");
				helper.assertTrue(level.getChunkSource().getChunkNow(nearButUnloaded.getX() >> 4, nearButUnloaded.getZ() >> 4) == null, "and it stays unloaded");
			}
		});
		helper.succeed();
	}
}
