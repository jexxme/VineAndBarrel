package jexxme.vineandbarrel.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.watch.WatchPath;
import jexxme.vineandbarrel.watch.WatchScene;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The "Watch it" scenes and the Brewer's path are complete and point at real things: every scene file in the mod's
 * {@code watch} folder reads, its lang keys are in en_us, its recipes are real on this server, its sounds, blocks, block
 * states and items exist, its figures are on the stage before they act or a caption points at them, and the scenes it
 * offers at its end exist. The path's stations and branches name real scenes, advancements and lang keys. The files
 * are read from the mod's own resources (a server has no client resources), so a new scene is checked without a list
 * to keep up to date.
 */
final class WatchGameTests
{
	private static final String FOLDER = "/assets/vineandbarrel/watch/";

	private WatchGameTests() {
	}

	static void registerAll() {
		VBGameTests.test("watch_scenes_are_complete", WatchGameTests::scenesAreComplete);
	}

	private static void scenesAreComplete(GameTestHelper helper) {
		JsonObject lang = read(helper, "/assets/vineandbarrel/lang/en_us.json");
		List<String> problems = new ArrayList<>();
		List<String> names = sceneFiles(helper);
		helper.assertTrue(names.size() >= 5, "only " + names.size() + " scenes in " + FOLDER + ": " + names);
		for (String name : names) {
			JsonObject json = read(helper, FOLDER + name + ".json");
			if (!WatchScene.requirementsMet(json)) {
				// a scene of a mod that isn't in this run (the FD scene without Farmer's Delight): checked in the FD run
				continue;
			}
			Optional<WatchScene> parsed = WatchScene.CODEC.parse(JsonOps.INSTANCE, json).resultOrPartial(e -> problems.add(name + ": " + e));
			if (parsed.isEmpty()) {
				continue;
			}
			WatchScene scene = parsed.get();
			key(lang, scene.title(), name, problems);
			Set<String> figures = new HashSet<>();
			for (WatchScene.Step step : scene.steps()) {
				key(lang, step.caption(), name, problems);
				for (WatchScene.Action action : step.actions()) {
					check(helper, name, action, figures, problems);
				}
				step.anchor().flatMap(WatchScene.Anchor::figure).filter(figure -> !figures.contains(figure))
					.ifPresent(figure -> problems.add(name + ": a caption points at " + figure + ", who is not on the stage"));
			}
			for (Either<Holder<Item>, WatchScene.RecipePart> need : scene.needs()) {
				item(helper, need, name, problems);
			}
			for (WatchScene.Entry entry : scene.opens().blocks()) {
				entry.now().ifPresent(now -> key(lang, now, name, problems));
				if (!BuiltInRegistries.BLOCK.containsKey(entry.block())) {
					problems.add(name + ": no block " + entry.block());
				} else {
					// a block state the scene waits for must be one the block can have (a renamed state would never match)
					StateDefinition<Block, BlockState> states = BuiltInRegistries.BLOCK.getValue(entry.block()).getStateDefinition();
					entry.when().forEach((property, value) -> {
						Property<?> found = states.getProperty(property);
						if (found == null || found.getValue(value).isEmpty()) {
							problems.add(name + ": " + entry.block() + " has no state " + property + "=" + value);
						}
					});
				}
				if (entry.step() > scene.steps().size()) {
					problems.add(name + ": " + entry.block() + " opens at step " + entry.step() + " of " + scene.steps().size());
				}
			}
			for (Identifier also : scene.also()) {
				if (!isScene(also, names)) {
					problems.add(name + ": offers " + also + ", which isn't one of the scenes");
				}
			}
		}
		checkPath(helper, lang, names, problems);
		helper.assertTrue(problems.isEmpty(), "scene problems: " + problems);
		helper.succeed();
	}

	/** The Brewer's path: its stations and branches name scenes we have, advancements this server has, and English lines. */
	private static void checkPath(GameTestHelper helper, JsonObject lang, List<String> names, List<String> problems) {
		Optional<WatchPath> parsed = WatchPath.CODEC.parse(JsonOps.INSTANCE, read(helper, FOLDER + WatchPath.FILE))
			.resultOrPartial(e -> problems.add("path: " + e));
		if (parsed.isEmpty()) {
			return;
		}
		WatchPath path = parsed.get();
		if (path.stations().isEmpty()) {
			problems.add("path: no stations");
		}
		Set<Identifier> stations = new HashSet<>();
		for (WatchPath.Station station : path.stations()) {
			key(lang, station.verb(), "path", problems);
			if (!isScene(station.scene(), names)) {
				problems.add("path: station " + station.scene() + " is not one of the scenes");
			}
			station.advancement().filter(id -> helper.getLevel().getServer().getAdvancements().get(id) == null)
				.ifPresent(id -> problems.add("path: no advancement " + id));
			stations.add(station.scene());
		}
		for (WatchPath.Branch branch : path.branches()) {
			branch.note().ifPresent(note -> key(lang, note, "path", problems));
			if (!stations.contains(branch.after())) {
				problems.add("path: branch " + branch.scene() + " hangs under " + branch.after() + ", which is not a station");
			}
			if (!isScene(branch.scene(), names)) {
				problems.add("path: branch " + branch.scene() + " is not one of the scenes");
			}
		}
	}

	private static boolean isScene(Identifier id, List<String> names) {
		return id.getNamespace().equals(VineAndBarrel.MODID) && names.contains(id.getPath());
	}

	/** {@return the scene files in the mod's watch folder, by name (the path file is not a scene)} */
	private static List<String> sceneFiles(GameTestHelper helper) {
		try {
			// found by a file that is always there (a loader may not hand out a folder's URL), then its folder
			URL known = VineAndBarrel.class.getResource(FOLDER + WatchPath.FILE);
			helper.assertTrue(known != null, FOLDER + WatchPath.FILE + " is in the mod");
			URI uri = known.toURI();
			FileSystem opened = null;
			Path pathFile;
			if (uri.getScheme().equals("jar")) {
				try {
					pathFile = FileSystems.getFileSystem(uri).provider().getPath(uri);
				} catch (FileSystemNotFoundException e) {
					opened = FileSystems.newFileSystem(uri, Map.of());
					pathFile = opened.provider().getPath(uri);
				}
			} else {
				pathFile = Path.of(uri);
			}
			Path dir = pathFile.getParent();
			try (Stream<Path> files = Files.list(dir)) {
				return files.map(file -> file.getFileName().toString())
					.filter(file -> file.endsWith(".json") && !file.equals(WatchPath.FILE))
					.map(file -> file.substring(0, file.length() - ".json".length()))
					.sorted()
					.toList();
			} finally {
				if (opened != null) {
					opened.close();
				}
			}
		} catch (IOException | URISyntaxException e) {
			throw new IllegalStateException("can't list " + FOLDER, e);
		}
	}

	private static void check(GameTestHelper helper, String name, WatchScene.Action action, Set<String> figures, List<String> problems) {
		switch (action) {
			case WatchScene.Barrel barrel -> recipe(helper, barrel.recipe(), name, problems);
			case WatchScene.Kettle kettle -> brewing(helper, kettle.recipe(), name, problems);
			case WatchScene.Particles particles -> particles.colorOf().ifPresent(item -> item(helper, item, name, problems));
			case WatchScene.Sound sound -> {
				if (!BuiltInRegistries.SOUND_EVENT.containsKey(sound.sound())) {
					problems.add(name + ": no sound event " + sound.sound());
				}
			}
			case WatchScene.Figure figure -> figures.add(figure.id());
			case WatchScene.FigureAction other -> {
				if (!figures.contains(other.id())) {
					problems.add(name + ": " + other.type().getSerializedName() + " for " + other.id() + " before it is on the stage");
				}
				if (other instanceof WatchScene.Hold hold) {
					item(helper, hold.item(), name, problems);
				}
			}
			default -> {
			}
		}
	}

	private static void item(GameTestHelper helper, Either<Holder<Item>, WatchScene.RecipePart> item, String name, List<String> problems) {
		item.ifRight(part -> {
			switch (part.part()) {
				case "base", "young", "aged" -> recipe(helper, part.recipe(), name, problems);
				case "result", "ingredient" -> brewing(helper, part.recipe(), name, problems);
				case "bucket" -> {
					if (!isType(helper, part.recipe(), BarrelRegistry.AGING.get()) && !isType(helper, part.recipe(), KettleRegistry.BREWING.get())) {
						problems.add(name + ": no aging or brewing recipe " + part.recipe());
					}
				}
				default -> problems.add(name + ": no recipe part " + part.part());
			}
		});
	}

	private static void recipe(GameTestHelper helper, Identifier id, String name, List<String> problems) {
		if (!isType(helper, id, BarrelRegistry.AGING.get())) {
			problems.add(name + ": no aging recipe " + id);
		}
	}

	private static void brewing(GameTestHelper helper, Identifier id, String name, List<String> problems) {
		if (!isType(helper, id, KettleRegistry.BREWING.get())) {
			problems.add(name + ": no brewing recipe " + id);
		}
	}

	private static boolean isType(GameTestHelper helper, Identifier id, RecipeType<?> type) {
		Optional<RecipeHolder<?>> recipe = helper.getLevel().recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, id));
		return recipe.isPresent() && recipe.get().value().getType() == type;
	}

	private static void key(JsonObject lang, String key, String name, List<String> problems) {
		if (!lang.has(key)) {
			problems.add(name + ": no English line for " + key);
		}
	}

	private static JsonObject read(GameTestHelper helper, String path) {
		try (InputStream in = VineAndBarrel.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, path + " is in the jar");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
