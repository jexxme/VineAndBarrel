package jexxme.vineandbarrel.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.barrel.AgingRecipes;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.kettle.BrewingRecipe;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.watch.WatchParts;
import jexxme.vineandbarrel.watch.WatchPath;
import jexxme.vineandbarrel.watch.WatchScene;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
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
		VBGameTests.test("watch_aging_scene_plays_each_base", WatchGameTests::agingScenePlaysEachBase);
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
				for (String caption : step.captions()) {
					key(lang, caption, name, problems);
					// a line's %s are what the step's "with" names, as many of them
					int args = lang.has(caption) ? countArgs(lang.get(caption).getAsString()) : step.with().size();
					if (args != step.with().size()) {
						problems.add(name + ": " + caption + " has " + args + " %s, the step names " + step.with().size());
					}
				}
				for (Either<Holder<Item>, WatchScene.RecipePart> with : step.with()) {
					part(helper, with, name, problems, true);
				}
				for (WatchScene.CaptionVariant variant : step.variants()) {
					item(helper, variant.of(), name, problems);
					for (String selector : variant.items()) {
						ResourceLocation id = ResourceLocation.tryParse(selector.startsWith("#") ? selector.substring(1) : selector);
						if (id == null || !selector.startsWith("#") && !BuiltInRegistries.ITEM.containsKey(id)) {
							problems.add(name + ": a caption variant names " + selector + ", which isn't an item");
						}
					}
				}
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
					StateDefinition<Block, BlockState> states = BuiltInRegistries.BLOCK.get(entry.block()).getStateDefinition();
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
			scene.recipe().ifPresent(recipe -> recipe(helper, recipe, name, problems));
			for (ResourceLocation also : scene.also()) {
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
		Set<ResourceLocation> stations = new HashSet<>();
		for (WatchPath.Station station : path.stations()) {
			key(lang, station.verb(), "path", problems);
			if (!isScene(station.scene(), names)) {
				problems.add("path: station " + station.scene() + " is not one of the scenes");
			}
			station.advancement().filter(id -> helper.getLevel().getServer().getAdvancements().getAdvancement(id) == null)
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

	private static boolean isScene(ResourceLocation id, List<String> names) {
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
		// Java 17: no pattern switch over the sealed actions, so one instanceof after the other, in the same order
		if (action instanceof WatchScene.Barrel barrel) {
			recipe(helper, barrel.recipe(), name, problems);
		} else if (action instanceof WatchScene.Kettle kettle) {
			brewing(helper, kettle.recipe(), name, problems);
		} else if (action instanceof WatchScene.Particles particles) {
			particles.colorOf().ifPresent(item -> item(helper, item, name, problems));
		} else if (action instanceof WatchScene.Timelapse lapse) {
			lapse.daysOf().ifPresent(part -> part(helper, Either.right(part), name, problems, true));
		} else if (action instanceof WatchScene.Sound sound) {
			if (!BuiltInRegistries.SOUND_EVENT.containsKey(sound.sound())) {
				problems.add(name + ": no sound event " + sound.sound());
			}
		} else if (action instanceof WatchScene.Figure figure) {
			figures.add(figure.id());
		} else if (action instanceof WatchScene.FigureAction other) {
			if (!figures.contains(other.id())) {
				problems.add(name + ": " + other.type().getSerializedName() + " for " + other.id() + " before it is on the stage");
			}
			if (other instanceof WatchScene.Hold hold) {
				item(helper, hold.item(), name, problems);
			}
		}
	}

	private static void item(GameTestHelper helper, Either<Holder<Item>, WatchScene.RecipePart> item, String name, List<String> problems) {
		part(helper, item, name, problems, false);
	}

	/** An item or a recipe's part; {@code times}: also an aging recipe's fermenting or aging time (a caption's or a time-lapse's). */
	private static void part(GameTestHelper helper, Either<Holder<Item>, WatchScene.RecipePart> item, String name, List<String> problems, boolean times) {
		item.ifRight(part -> {
			switch (part.part()) {
				case "fermenting_time", "aging_time" -> {
					if (times) {
						recipe(helper, part.recipe(), name, problems);
					} else {
						problems.add(name + ": " + part.part() + " is a time, not an item");
					}
				}
				case "base", "young", "aged", "vessel" -> recipe(helper, part.recipe(), name, problems);
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

	private static void recipe(GameTestHelper helper, ResourceLocation id, String name, List<String> problems) {
		if (!isType(helper, id, BarrelRegistry.AGING.get())) {
			problems.add(name + ": no aging recipe " + id);
		}
	}

	private static void brewing(GameTestHelper helper, ResourceLocation id, String name, List<String> problems) {
		if (!isType(helper, id, KettleRegistry.BREWING.get())) {
			problems.add(name + ": no brewing recipe " + id);
		}
	}

	private static boolean isType(GameTestHelper helper, ResourceLocation id, RecipeType<?> type) {
		Optional<? extends Recipe<?>> recipe = helper.getLevel().getRecipeManager().byKey(id);
		return recipe.isPresent() && recipe.get().getType() == type;
	}

	private static int countArgs(String line) {
		int count = 0;
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("%(\\d+\\$)?s").matcher(line);
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	/**
	 * The barrel scene played for each aging base, as a player asks for it (the base's bucket or bottle, a barrel of it,
	 * JEI's page): it plays that recipe, its "What you need" names that base's bucket (a bottle where there is none) and
	 * the vessel its drinks are tapped into, the brewer holds them, and the lines say the right thing: "Pour in the wort."
	 * for a wort, a mug for a drink served in one. A mug or a wine glass asked for plays a recipe served in it.
	 */
	private static void agingScenePlaysEachBase(GameTestHelper helper) {
		JsonObject lang = read(helper, "/assets/vineandbarrel/lang/en_us.json");
		WatchScene scene = WatchScene.CODEC.parse(JsonOps.INSTANCE, read(helper, FOLDER + "aging_barrel.json")).getOrThrow(false, VineAndBarrel.LOGGER::error);
		helper.assertTrue(scene.recipe().isPresent(), "the barrel scene names the recipe it is written for");
		List<String> problems = new ArrayList<>();
		WatchParts.Recipes recipes = serverRecipes(helper);
		List<AgingRecipe> all = AgingRecipes.all(helper.getLevel());
		helper.assertTrue(all.size() >= 7, "only " + all.size() + " aging recipes");
		Item mug = BuiltInRegistries.ITEM.get(VineAndBarrel.id("mug"));
		Item glass = BuiltInRegistries.ITEM.get(VineAndBarrel.id("wine_glass"));
		Set<Item> bases = new HashSet<>();
		for (AgingRecipe recipe : all) {
			ResourceLocation id = recipe.getId();
			if (!bases.add(recipe.base().value())) {
				// a second recipe from the same base (the test data pack's duplicates): the barrel uses the lowest id, so does the scene
				continue;
			}
			List<net.minecraft.world.item.ItemStack> asks = new ArrayList<>();
			asks.add(new net.minecraft.world.item.ItemStack(recipe.base()));
			recipe.bucket().ifPresent(bucket -> asks.add(new net.minecraft.world.item.ItemStack(bucket)));
			for (net.minecraft.world.item.ItemStack ask : asks) {
				Optional<ResourceLocation> played = WatchParts.agingRecipeFor(ask, all, scene.recipe());
				if (!played.equals(Optional.of(id))) {
					problems.add(ask.getItem() + " plays " + played + " instead of " + id);
				}
			}
			if (!WatchParts.agingRecipeOfBase(new net.minecraft.world.item.ItemStack(recipe.base()), all).equals(Optional.of(id))) {
				problems.add("a barrel of " + recipe.base().value() + " doesn't play " + id);
			}
			WatchParts parts = WatchParts.of(scene, recipes, Optional.of(id));
			Item pour = recipe.bucket().map(Holder::value).orElse(recipe.base().value());
			Item vessel = WatchParts.vesselOf(recipe).getItem();
			Item other = vessel == mug ? glass : mug;
			List<Item> needs = scene.needs().stream().map(parts::item).map(net.minecraft.world.item.ItemStack::getItem).toList();
			if (!needs.contains(pour) || !needs.contains(vessel) || needs.contains(other)) {
				problems.add(id + ": What you need is " + needs + " (its " + pour + " and " + vessel + ", never " + other + ")");
			}
			List<Item> held = new ArrayList<>();
			for (WatchScene.Step step : scene.steps()) {
				for (WatchScene.Action action : step.actions()) {
					if (action instanceof WatchScene.Hold hold) {
						held.add(parts.item(hold.item()).getItem());
					}
				}
			}
			if (!held.contains(pour) || !held.contains(vessel) || held.contains(other) || !held.contains(recipe.young().getItem())) {
				problems.add(id + ": the brewer holds " + held + " (its " + pour + ", " + vessel + " and young drink)");
			}
			// the lines: the base's kind where it is poured, the vessel where it is tapped, the recipe's own days
			String base = BuiltInRegistries.ITEM.getKey(recipe.base().value()).getPath();
			String expectedPour = base.endsWith("_wort_bottle") ? ".wort" : base.equals("honey_must_bottle") ? ".must" : "";
			String pourKey = parts.captionKey(scene.steps().get(0));
			if (!pourKey.equals(scene.steps().get(0).caption() + expectedPour)) {
				problems.add(id + ": pouring " + base + " says " + pourKey);
			}
			boolean mugLine = false;
			for (WatchScene.Step step : scene.steps()) {
				String key = parts.captionKey(step);
				mugLine |= key.endsWith(".mug");
				String english = lang.has(key) ? lang.get(key).getAsString() : "";
				if (vessel == mug && english.contains("glass") || vessel == glass && english.contains("mug")) {
					problems.add(id + ": " + key + " (" + english + ") names the other vessel");
				}
				parts.caption(step).getString();
			}
			if (mugLine != (vessel == mug)) {
				problems.add(id + ": served in " + vessel + ", " + (mugLine ? "a line says mug" : "no line says mug"));
			}
		}
		// a vessel asked for: a recipe whose drinks are served in it (the wine glass: the red wine the scene is written for)
		for (Item vessel : List.of(mug, glass)) {
			Optional<ResourceLocation> played = WatchParts.agingRecipeFor(new net.minecraft.world.item.ItemStack(vessel), all, scene.recipe());
			Optional<AgingRecipe> recipe = played.map(recipes::aging);
			if (recipe.isEmpty() || WatchParts.vesselOf(recipe.get()).getItem() != vessel) {
				problems.add("asked for " + vessel + ", the scene plays " + played);
			}
		}
		if (!WatchParts.agingRecipeFor(new net.minecraft.world.item.ItemStack(glass), all, scene.recipe()).equals(scene.recipe())) {
			problems.add("a wine glass doesn't play the red wine the scene is written for");
		}
		helper.assertTrue(problems.isEmpty(), "barrel scene variants: " + problems);
		helper.succeed();
	}

	/** The server's recipes, for what a scene names. */
	private static WatchParts.Recipes serverRecipes(GameTestHelper helper) {
		return new WatchParts.Recipes()
		{
			@Override
			public AgingRecipe aging(ResourceLocation id) {
				return helper.getLevel().getRecipeManager().byKey(id)
					.filter(AgingRecipe.class::isInstance).map(AgingRecipe.class::cast).orElse(null);
			}

			@Override
			public BrewingRecipe brewing(ResourceLocation id) {
				return helper.getLevel().getRecipeManager().byKey(id)
					.filter(BrewingRecipe.class::isInstance).map(BrewingRecipe.class::cast).orElse(null);
			}
		};
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
