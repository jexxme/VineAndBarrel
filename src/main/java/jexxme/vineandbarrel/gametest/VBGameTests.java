package jexxme.vineandbarrel.gametest;

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
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Headless GameTests: run with {@code ./gradlew runGameTestServer} (no window). Each milestone adds tests that check
 * real behaviour (brewing, fermenting, tapping, item conservation), not just that the game starts.
 */
public final class VBGameTests
{
	static final Identifier SMALL = VineAndBarrel.id("gametest/empty_5x5");
	static final Identifier LARGE = VineAndBarrel.id("gametest/empty_32x6x32");
	private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, VineAndBarrel.MODID);
	private static final List<Entry> TESTS = new ArrayList<>();

	private record Entry(ResourceKey<Consumer<GameTestHelper>> function, Identifier structure, int maxTicks, boolean skyAccess) {
	}

	static {
		test("skeleton_mod_loaded", VBGameTests::modLoaded);
		test("skeleton_world_edit", VBGameTests::worldEdit);
		test("polish_lang_covers_everything", VBGameTests::langCoversEverything);
		test("polish_creative_tab_has_every_item_once", VBGameTests::creativeTabHasEveryItemOnce);
		VBCropTests.registerAll();
	}

	private VBGameTests() {
	}

	static void test(String name, Consumer<GameTestHelper> function) {
		test(name, SMALL, 200, function);
	}

	static void test(String name, Identifier structure, int maxTicks, Consumer<GameTestHelper> function) {
		test(name, structure, maxTicks, false, function);
	}

	/** {@code skyAccess}: no barrier ceiling over the test, e.g. for worldgen placement that follows the heightmap. */
	static void test(String name, Identifier structure, int maxTicks, boolean skyAccess, Consumer<GameTestHelper> function) {
		FUNCTIONS.register(name, () -> function);
		TESTS.add(new Entry(ResourceKey.create(Registries.TEST_FUNCTION, VineAndBarrel.id(name)), structure, maxTicks, skyAccess));
	}

	public static void register(IEventBus modEventBus) {
		FUNCTIONS.register(modEventBus);
		modEventBus.addListener(VBGameTests::registerTests);
	}

	private static void registerTests(RegisterGameTestsEvent event) {
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(VineAndBarrel.id("default"));
		for (Entry test : TESTS) {
			event.registerTest(test.function().identifier(), new FunctionGameTestInstance(test.function(), new TestData<>(environment,
					Level.OVERWORLD, test.structure(), test.maxTicks(), 0, true, Rotation.NONE, false, 1, 1, test.skyAccess(), 0)));
		}
	}

	// ------------------------------------------------------------------ M0: the harness itself works

	/** The mod is loaded under its id, so registries and data under "vineandbarrel" are live. */
	private static void modLoaded(GameTestHelper helper) {
		helper.assertTrue(ModList.get().isLoaded(VineAndBarrel.MODID), "Vine & Barrel is not loaded");
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
		BuiltInRegistries.BLOCK.listElements().filter(h -> h.key().identifier().getNamespace().equals(VineAndBarrel.MODID))
			.forEach(h -> need(lang, h.value().getDescriptionId(), missing));
		BuiltInRegistries.ITEM.listElements().filter(h -> h.key().identifier().getNamespace().equals(VineAndBarrel.MODID))
			.forEach(h -> need(lang, h.value().getDescriptionId(), missing));
		BuiltInRegistries.MOB_EFFECT.listElements().filter(h -> h.key().identifier().getNamespace().equals(VineAndBarrel.MODID))
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

	/** The creative tab lists every item of ours exactly once (FD's order is set in {@link VBCreativeTabs#tabOrder()}). */
	private static void creativeTabHasEveryItemOnce(GameTestHelper helper) {
		List<Item> tab = VBCreativeTabs.tabOrder();
		helper.assertValueEqual(new HashSet<>(tab).size(), tab.size(), "items listed twice in the tab");
		List<Item> ours = BuiltInRegistries.ITEM.stream().filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(VineAndBarrel.MODID)).toList();
		List<Item> notInTab = ours.stream().filter(item -> !tab.contains(item)).toList();
		helper.assertTrue(notInTab.isEmpty(), "items missing from the tab: " + notInTab);
		helper.assertValueEqual(tab.getFirst(), KettleRegistry.kettleItem(), "the tab starts with the workstations, like FD's");
		helper.succeed();
	}
}
