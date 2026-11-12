package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.registry.VBCreativeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestAssertPosException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import jexxme.vineandbarrel.platform.GameTestRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Headless GameTests: run with {@code ./gradlew runGameTestServer} (no window). Each milestone adds tests that check
 * real behaviour (brewing, fermenting, tapping, item conservation), not just that the game starts.
 *
 * <p>The harness on this Minecraft (1.20.1): a test is a vanilla {@link TestFunction} (26.x: entries of the test
 * registries). Every test class adds its tests through {@link #add}, from the consumer it gives
 * {@code Platform.addGameTests}:
 * <ul>
 * <li><b>Names</b> are the plain 26.x names ({@code kettle_boils}); the report writes them as
 * {@code vineandbarrel:<name>}, the id they had on 26.x (see {@link TestReports}).</li>
 * <li><b>Batches</b> stand in for the 26.x test environments: the batch of a test is the path of its old environment
 * id ({@code "default"}, {@code "tipsy"}, {@code "tipsy_legs"}, ...; {@link #add} turns it into
 * {@code vineandbarrel:<path>}). Vanilla runs the batches one after the other and the tests of one batch together,
 * as 26.x did with environments. All our 26.x environments were empty (no weather, time or game rule), so no batch
 * has setup code. A test that needs such a world state sets it at its own start (and undoes it when it ends):
 * vanilla's before/after-batch consumers in {@code GameTestRegistry} are private maps that only its
 * {@code @BeforeBatch} / {@code @AfterBatch} annotation scan fills, so they are not used here.</li>
 * <li><b>{@code -Dvineandbarrel.tests=<pattern>}</b>: only tests whose name matches are added, a pattern with
 * {@code *} as on 26.x's {@code --tests} selector or a prefix (the compat run sets {@code compat_}); {@code -Ptests}
 * sets it for the GameTest runs.</li>
 * </ul>
 *
 * <p>Helpers for what 26.x's {@code GameTestHelper} had and this Minecraft's lacks (use these in every test class, so
 * all read the same): {@link #blockEntity}, {@link #assertBlockState}, {@link #assertionException}; and for what
 * 1.21.1's had and 1.20.1's lacks, each with the helper as its first argument and then the vanilla arguments:
 * {@link #assertValueEqual}, {@link #makeMockPlayer}, {@link #getEntities}, {@link #getTestRotation},
 * {@link #getBounds}.
 */
public final class VBGameTests
{
	static final ResourceLocation SMALL = VineAndBarrel.id("gametest/empty_5x5");
	static final ResourceLocation LARGE = VineAndBarrel.id("gametest/empty_32x6x32");
	/** Only tests whose name matches this (a pattern with {@code *}, else a prefix) are added; not set or empty: all of them. */
	public static final String TESTS_PROPERTY = "vineandbarrel.tests";
	private static final List<Entry> TESTS = new ArrayList<>();
	/** The names of every test added through {@link #add} (the report gives these their {@code vineandbarrel:} id). */
	private static final Set<String> OURS = ConcurrentHashMap.newKeySet();

	private record Entry(String name, ResourceLocation structure, int maxTicks, boolean skyAccess, Consumer<GameTestHelper> function) {
	}

	static {
		test("skeleton_mod_loaded", VBGameTests::modLoaded);
		test("skeleton_world_edit", VBGameTests::worldEdit);
		test("polish_lang_covers_everything", VBGameTests::langCoversEverything);
		test("polish_creative_tab_has_every_item_once", VBGameTests::creativeTabHasEveryItemOnce);
		VBCropTests.registerAll();
		VBTrellisTests.registerAll();
		WatchGameTests.registerAll();
		VBSupportTests.registerAll();
	}

	private VBGameTests() {
	}

	static void test(String name, Consumer<GameTestHelper> function) {
		test(name, SMALL, 200, function);
	}

	static void test(String name, ResourceLocation structure, int maxTicks, Consumer<GameTestHelper> function) {
		test(name, structure, maxTicks, false, function);
	}

	/**
	 * {@code skyAccess}: on newer Minecraft, no barrier ceiling over the test (e.g. for worldgen placement that follows
	 * the heightmap). 1.20.1 puts no ceiling over any test, so the flag is kept for the callers and ignored.
	 */
	static void test(String name, ResourceLocation structure, int maxTicks, boolean skyAccess, Consumer<GameTestHelper> function) {
		TESTS.add(new Entry(name, structure, maxTicks, skyAccess, function));
	}

	public static void register() {
		jexxme.vineandbarrel.platform.Services.PLATFORM.addGameTests(VBGameTests::registerTests);
	}

	private static void registerTests(GameTestRegistrar event) {
		for (Entry test : TESTS) {
			add(event, "default", test.name(), test.structure(), test.maxTicks(), test.skyAccess(), test.function());
		}
	}

	// ------------------------------------------------------------------ the harness, for every test class

	/** {@link #add(GameTestRegistrar, String, String, ResourceLocation, int, boolean, Consumer)} without sky access (the same thing on 1.20.1). */
	static void add(GameTestRegistrar event, String batch, String name, ResourceLocation structure, int maxTicks, Consumer<GameTestHelper> function) {
		add(event, batch, name, structure, maxTicks, false, function);
	}

	/**
	 * Adds one test, as 26.x's {@code new FunctionGameTestInstance(function, new TestData<>(environment, structure,
	 * maxTicks, 0, true, Rotation.NONE, false, 1, 1, skyAccess, 0))} did: required, not rotated, no setup ticks, one
	 * attempt. Left out if {@code -Dvineandbarrel.tests} asks for other tests.
	 *
	 * @param batch the path of the test's 26.x environment id ({@code "kettle"} for {@code vineandbarrel:kettle});
	 *              tests of one batch run together, batches one after the other
	 * @param name the test's 26.x name, without namespace ({@code "kettle_boils"})
	 * @param structure a template under {@code data/<namespace>/structures/}, e.g. {@link #SMALL}
	 * @param skyAccess ignored on 1.20.1: its {@link TestFunction} has no such flag, because no test gets a barrier
	 *                  ceiling here (every test is open to the sky); kept so the test classes call this as on 1.21.1
	 */
	static void add(GameTestRegistrar event, String batch, String name, ResourceLocation structure, int maxTicks, boolean skyAccess,
			Consumer<GameTestHelper> function) {
		if (!selected(name)) {
			return;
		}
		OURS.add(name);
		event.registerTest(new TestFunction(VineAndBarrel.MODID + ":" + batch, name, structure.toString(), Rotation.NONE, maxTicks, 0L, true,
				1, 1, helper -> runWhenChunksReady(helper, function)));
	}

	/**
	 * Starts the test once the chunks its structure stands in tick entities and have their entities loaded. 1.21.1's
	 * {@code GameTestInfo#tick} waits like this before every test ("chunksLoaded"); 1.20.1 starts a test in the tick its
	 * structure was placed, when a chunk the test has just loaded may still be reading its entities (and code that asks
	 * "is a monster near" can't know yet). Usually nothing has to be waited for and the test starts at once; otherwise it
	 * is asked again every tick (these ticks count toward the test's time, which 1.21.1's wait did not).
	 */
	private static void runWhenChunksReady(GameTestHelper helper, Consumer<GameTestHelper> function) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		net.minecraft.world.phys.AABB bounds = getBounds(helper);
		boolean ready = net.minecraft.world.level.ChunkPos.rangeClosed(new net.minecraft.world.level.ChunkPos(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ)),
				new net.minecraft.world.level.ChunkPos(BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ)))
			.allMatch(chunk -> level.isPositionEntityTicking(chunk.getWorldPosition()) && level.areEntitiesLoaded(chunk.toLong()));
		if (ready) {
			function.accept(helper);
		} else {
			helper.runAfterDelay(1, () -> runWhenChunksReady(helper, function));
		}
	}

	/**
	 * Whether {@code -Dvineandbarrel.tests} lets this test run (no property: every test). With a {@code *} it is a
	 * pattern like 26.x's {@code --tests} selector ({@code kettle_*}, {@code *_juice}; {@code ./gradlew
	 * runGameTestServer -Ptests='kettle_*'} passes it), without one a prefix (the compat run's {@code compat_}).
	 */
	static boolean selected(String name) {
		String pattern = System.getProperty(TESTS_PROPERTY);
		if (pattern == null || pattern.isEmpty()) {
			return true;
		}
		if (pattern.indexOf('*') < 0) {
			return name.startsWith(pattern);
		}
		// every * stands for any characters, everything else for itself
		String regex = java.util.Arrays.stream(pattern.split("\\*", -1)).map(java.util.regex.Pattern::quote)
			.collect(java.util.stream.Collectors.joining(".*"));
		return name.matches(regex);
	}

	/** Whether a test of this name was added through {@link #add} (so it is ours, not another mod's). */
	static boolean isOurs(String testName) {
		return OURS.contains(testName);
	}

	// ------------------------------------------------------------------ what 26.x's GameTestHelper had

	/**
	 * 26.x's {@code helper.getBlockEntity(pos, type)}: the block entity at the test-relative position, failing the test
	 * if there is none or it is of another class (1.20.1's {@code getBlockEntity(pos)} returns a plain block entity).
	 */
	static <T extends BlockEntity> T blockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
		BlockEntity entity = helper.getBlockEntity(pos);
		if (!type.isInstance(entity)) {
			throw new GameTestAssertPosException("Expected a " + type.getSimpleName() + ", got " + (entity == null ? "no block entity" : entity.getClass().getSimpleName()),
				helper.absolutePos(pos), pos, helper.getTick());
		}
		return type.cast(entity);
	}

	/** 26.x's {@code helper.assertBlockState(pos, expected)}: exactly this state at the test-relative position. */
	static void assertBlockState(GameTestHelper helper, BlockPos pos, BlockState expected) {
		helper.assertBlockState(pos, state -> state == expected, () -> "Expected " + expected + ", got " + helper.getBlockState(pos));
	}

	/**
	 * Vanilla's {@code helper.makeMockServerPlayerInLevel()} with a connection the loader has prepared
	 * ({@code Platform#prepareMockConnection}): mods send their packets to a player the moment it joins. As on 1.21.1
	 * the connection sits on an embedded channel (1.20.1's own mock has no channel at all, which loader networking code
	 * that asks the channel about the other side does not survive).
	 */
	static net.minecraft.server.level.ServerPlayer mockServerPlayerInLevel(GameTestHelper helper) {
		net.minecraft.server.level.ServerPlayer player = new MockServerPlayer(helper, net.minecraft.world.level.GameType.CREATIVE);
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		jexxme.vineandbarrel.platform.Services.PLATFORM.prepareMockConnection(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player);
		return player;
	}

	/** 26.x's {@code helper.assertionException(message)}: the exception that fails a test, to {@code throw}. */
	static GameTestAssertException assertionException(GameTestHelper helper, String message) {
		return new GameTestAssertException(message);
	}

	// ------------------------------------------------------------------ what 1.21.1's GameTestHelper had

	/** 1.21.1's {@code assertValueEqual(helper, actual, expected, name)}: fails with "Expected name to be ..., but was ...". */
	static <N> void assertValueEqual(GameTestHelper helper, N actual, N expected, String name) {
		if (!actual.equals(expected)) {
			throw new GameTestAssertException("Expected " + name + " to be " + expected + ", but was " + actual);
		}
	}

	/**
	 * 1.21.1's {@code helper.makeMockPlayer(gameType)}: a player that is in no player list, in this game mode as far
	 * as {@code isCreative()} / {@code isSpectator()} say (1.20.1's own {@code makeMockPlayer()} is always creative).
	 */
	static net.minecraft.world.entity.player.Player makeMockPlayer(GameTestHelper helper, net.minecraft.world.level.GameType gameType) {
		return new MockPlayer(helper, gameType);
	}

	// The mock players and the menu are named classes, not anonymous ones: an anonymous class takes over the names of
	// the parameters of the constructor it calls, and in the Minecraft jar a build server compiles against (not
	// recompiled from source there) several parameters of one constructor share a name, which javac refuses.

	/** A server player whose game mode is the given one as far as {@code isCreative()} / {@code isSpectator()} say. */
	static final class MockServerPlayer extends net.minecraft.server.level.ServerPlayer
	{
		private final net.minecraft.world.level.GameType gameType;

		MockServerPlayer(GameTestHelper helper, net.minecraft.world.level.GameType gameType) {
			super(helper.getLevel().getServer(), helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"));
			this.gameType = gameType;
		}

		@Override
		public boolean isSpectator() {
			return gameType == net.minecraft.world.level.GameType.SPECTATOR;
		}

		@Override
		public boolean isCreative() {
			return gameType.isCreative();
		}
	}

	private static final class MockPlayer extends net.minecraft.world.entity.player.Player
	{
		private final net.minecraft.world.level.GameType gameType;

		MockPlayer(GameTestHelper helper, net.minecraft.world.level.GameType gameType) {
			super(helper.getLevel(), BlockPos.ZERO, 0.0F, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"));
			this.gameType = gameType;
		}

		@Override
		public boolean isSpectator() {
			return gameType == net.minecraft.world.level.GameType.SPECTATOR;
		}

		@Override
		public boolean isCreative() {
			return gameType.isCreative();
		}

		@Override
		public boolean isLocalPlayer() {
			return true;
		}
	}

	/** A menu nobody opens: 1.20.1's crafting grid belongs to a menu. */
	static final class NoMenu extends net.minecraft.world.inventory.AbstractContainerMenu
	{
		NoMenu() {
			super(null, -1);
		}

		@Override
		public net.minecraft.world.item.ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
			return net.minecraft.world.item.ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(net.minecraft.world.entity.player.Player player) {
			return false;
		}
	}

	/** 1.21.1's {@code helper.getEntities(type)}: every living entity of the type inside the test's structure. */
	static <T extends net.minecraft.world.entity.Entity> List<T> getEntities(GameTestHelper helper, net.minecraft.world.entity.EntityType<T> type) {
		return helper.getLevel().getEntities(type, getBounds(helper), net.minecraft.world.entity.Entity::isAlive);
	}

	/** 1.21.1's {@code helper.getTestRotation()}: how the test's structure is turned (ours never are: {@link #add}). */
	static Rotation getTestRotation(GameTestHelper helper) {
		return structureBlock(helper).getRotation();
	}

	/** 1.21.1's {@code helper.getBounds()} (private on 1.20.1): the test's structure, in world coordinates. */
	static net.minecraft.world.phys.AABB getBounds(GameTestHelper helper) {
		return net.minecraft.gametest.framework.StructureUtils.getStructureBounds(structureBlock(helper));
	}

	/** The test's structure block: it stands at the test's origin, which is what the helper's test info asks too. */
	private static net.minecraft.world.level.block.entity.StructureBlockEntity structureBlock(GameTestHelper helper) {
		if (helper.getLevel().getBlockEntity(helper.absolutePos(BlockPos.ZERO)) instanceof net.minecraft.world.level.block.entity.StructureBlockEntity structure) {
			return structure;
		}
		throw new GameTestAssertException("The test's structure block is gone");
	}

	// ------------------------------------------------------------------ M0: the harness itself works

	/** The mod is loaded under its id, so registries and data under "vineandbarrel" are live. */
	private static void modLoaded(GameTestHelper helper) {
		helper.assertTrue(jexxme.vineandbarrel.platform.Services.PLATFORM.isModLoaded(VineAndBarrel.MODID), "Vine & Barrel is not loaded");
		helper.succeed();
	}

	/** Our own test structure loads and the world can be edited inside it (the base every later test builds on). */
	private static void worldEdit(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, Blocks.BARREL);
		helper.assertBlockPresent(Blocks.BARREL, pos);
		helper.setBlock(pos, Blocks.AIR);
		helper.assertBlockPresent(Blocks.AIR, pos);
		helper.succeed();
	}

	// ------------------------------------------------------------------ M7 polish

	/**
	 * Every block, item, effect and advancement of ours has an English name (and every advancement a description), so
	 * nothing shows a raw translation key. The file is read from the jar, since a server has no client language.
	 */
	private static void langCoversEverything(GameTestHelper helper) {
		JsonObject lang;
		try (InputStream in = VineAndBarrel.class.getResourceAsStream("/assets/vineandbarrel/lang/en_us.json")) {
			helper.assertTrue(in != null, "en_us.json is in the jar");
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		List<String> missing = new ArrayList<>();
		BuiltInRegistries.BLOCK.holders().filter(h -> h.key().location().getNamespace().equals(VineAndBarrel.MODID))
			.forEach(h -> need(lang, h.value().getDescriptionId(), missing));
		BuiltInRegistries.ITEM.holders().filter(h -> h.key().location().getNamespace().equals(VineAndBarrel.MODID))
			.forEach(h -> need(lang, h.value().getDescriptionId(), missing));
		BuiltInRegistries.MOB_EFFECT.holders().filter(h -> h.key().location().getNamespace().equals(VineAndBarrel.MODID))
			.forEach(h -> need(lang, h.value().getDescriptionId(), missing));
		for (String name : jexxme.vineandbarrel.advancement.VBAdvancementGenerator.ALL) {
			need(lang, "advancements.vineandbarrel." + name + ".title", missing);
			need(lang, "advancements.vineandbarrel." + name + ".description", missing);
		}
		need(lang, "itemGroup.vineandbarrel", missing);
		helper.assertTrue(missing.isEmpty(), "missing English names: " + missing);
		helper.succeed();
	}

	private static void need(JsonObject lang, String key, List<String> missing) {
		if (!lang.has(key)) {
			missing.add(key);
		}
	}

	/**
	 * The creative tab lists every item of ours exactly once (FD's order is set in {@link VBCreativeTabs#tabOrder()}),
	 * except the FD meals, the feast, beer bread and melon wine when Farmer's Delight is missing.
	 */
	private static void creativeTabHasEveryItemOnce(GameTestHelper helper) {
		List<Item> tab = VBCreativeTabs.tabOrder();
		assertValueEqual(helper, new HashSet<>(tab).size(), tab.size(), "items listed twice in the tab");
		List<Item> hidden = VBCreativeTabs.hidden();
		// without FD: 6 meals (2 + 4 incl. the coq au vin bowl), the feast, beer bread, melon wine young and aged
		assertValueEqual(helper, hidden.size(), FDCompat.isLoaded() ? 0 : 10, "items hidden from the tab");
		if (!FDCompat.isLoaded()) {
			helper.assertTrue(hidden.containsAll(FDCompat.items()), "every FD meal, the feast and beer bread are hidden: " + hidden);
		}
		helper.assertTrue(hidden.stream().noneMatch(tab::contains), "hidden items in the tab: " + hidden);
		List<Item> ours = BuiltInRegistries.ITEM.stream().filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(VineAndBarrel.MODID)).toList();
		List<Item> notInTab = ours.stream().filter(item -> !tab.contains(item) && !hidden.contains(item)).toList();
		helper.assertTrue(notInTab.isEmpty(), "items missing from the tab: " + notInTab);
		assertValueEqual(helper, tab.get(0), KettleRegistry.kettleItem(), "the tab starts with the workstations, like FD's");
		helper.succeed();
	}
}
