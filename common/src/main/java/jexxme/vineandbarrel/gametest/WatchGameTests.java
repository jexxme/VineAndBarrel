package jexxme.vineandbarrel.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.kettle.KettleRegistry;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The "Watch it" scenes are complete and point at real things: every scene file reads, its lang keys are in en_us, its
 * recipes are real aging recipes on this server, its sounds and blocks exist, its figures are on the stage before they
 * act, and the scenes it offers at its end exist. The files are read from the jar (a server has no client resources).
 */
final class WatchGameTests
{
	/** Every scene the mod ships, by file name; "Also watch" may only name these. */
	static final List<String> SCENES = List.of("aging_barrel", "cheers", "brew_kettle", "trellis", "farmers_delight");

	private WatchGameTests() {
	}

	static void registerAll() {
		VBGameTests.test("watch_scenes_are_complete", WatchGameTests::scenesAreComplete);
	}

	private static void scenesAreComplete(GameTestHelper helper) {
		JsonObject lang = read(helper, "/assets/vineandbarrel/lang/en_us.json");
		List<String> problems = new ArrayList<>();
		for (String name : SCENES) {
			JsonObject json = read(helper, "/assets/vineandbarrel/watch/" + name + ".json");
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
			}
			for (WatchScene.Entry entry : scene.opens().blocks()) {
				entry.now().ifPresent(now -> key(lang, now, name, problems));
				if (!BuiltInRegistries.BLOCK.containsKey(entry.block())) {
					problems.add(name + ": no block " + entry.block());
				}
				if (entry.step() > scene.steps().size()) {
					problems.add(name + ": " + entry.block() + " opens at step " + entry.step() + " of " + scene.steps().size());
				}
			}
			for (Identifier also : scene.also()) {
				if (!also.getNamespace().equals(VineAndBarrel.MODID) || !SCENES.contains(also.getPath())) {
					problems.add(name + ": offers " + also + ", which isn't one of the scenes");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), "scene problems: " + problems);
		helper.succeed();
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
