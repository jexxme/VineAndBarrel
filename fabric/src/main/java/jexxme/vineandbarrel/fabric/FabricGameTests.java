package jexxme.vineandbarrel.fabric;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.gametest.ModCompatGameTests;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.gametest.framework.GameTestHelper;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The GameTests that need Fabric's own API, by the names the common test classes ask for ({@code loaderTest}). Fabric
 * has no place event a protection mod could cancel, so {@code drinks_cancelled_place_keeps_no_duplicate} has no Fabric
 * version.
 */
final class FabricGameTests
{
	private static final Map<String, Consumer<GameTestHelper>> TESTS = Map.of(
		"fd_optional_dependency", FabricGameTests::optionalDependency,
		"compat_optional_dependencies", FabricGameTests::compatOptionalDependencies,
		"tipsy_server_config_per_world", FabricGameTests::serverConfigPerWorld);

	private FabricGameTests() {
	}

	static @Nullable Consumer<GameTestHelper> get(String name) {
		return TESTS.get(name);
	}

	/**
	 * fabric.mod.json suggests each integration's mod that has a Fabric build (Nutritional Balance has none, so it isn't
	 * listed), and an installed one is in range.
	 */
	private static void compatOptionalDependencies(GameTestHelper helper) {
		for (String mod : ModCompatGameTests.OPTIONAL) {
			java.util.Optional<ModDependency> dependency = FabricLoader.getInstance().getModContainer(VineAndBarrel.MODID).orElseThrow().getMetadata()
				.getDependencies().stream().filter(d -> d.getModId().equals(mod)).findFirst();
			if (mod.equals(ModCompatGameTests.NUTRITIONAL_BALANCE)) {
				helper.assertTrue(dependency.isEmpty(), "no Fabric build of Nutritional Balance to suggest");
				continue;
			}
			helper.assertTrue(dependency.isPresent(), "fabric.mod.json suggests " + mod);
			helper.assertValueEqual(dependency.get().getKind(), ModDependency.Kind.SUGGESTS, mod + " dependency kind");
			FabricLoader.getInstance().getModContainer(mod).ifPresent(installed -> helper.assertTrue(
				dependency.get().matches(installed.getMetadata().getVersion()), "the installed " + mod + " is in range"));
		}
		helper.succeed();
	}

	/**
	 * fabric.mod.json suggests Farmer's Delight (Refabricated) and breaks on builds older than the first one for this
	 * Minecraft (as NeoForge's version range); the installed one is suggested and not broken.
	 */
	private static void optionalDependency(GameTestHelper helper) {
		java.util.Collection<ModDependency> dependencies = FabricLoader.getInstance().getModContainer(VineAndBarrel.MODID).orElseThrow().getMetadata().getDependencies();
		ModDependency suggests = dependencies.stream().filter(d -> d.getModId().equals(FDCompat.MODID) && d.getKind() == ModDependency.Kind.SUGGESTS).findFirst()
			.orElseThrow(() -> new IllegalStateException("fabric.mod.json doesn't suggest farmersdelight"));
		ModDependency breaks = dependencies.stream().filter(d -> d.getModId().equals(FDCompat.MODID) && d.getKind() == ModDependency.Kind.BREAKS).findFirst()
			.orElseThrow(() -> new IllegalStateException("fabric.mod.json has no lower bound (breaks) for farmersdelight"));
		FabricLoader.getInstance().getModContainer(FDCompat.MODID).ifPresent(fd -> {
			helper.assertTrue(suggests.matches(fd.getMetadata().getVersion()), "the installed FD " + fd.getMetadata().getVersion() + " is suggested");
			helper.assertFalse(breaks.matches(fd.getMetadata().getVersion()), "the installed FD " + fd.getMetadata().getVersion() + " is not in " + breaks.getVersionRequirements());
		});
		helper.succeed();
	}

	/**
	 * The server settings per world, as NeoForge's SYNCED config: a world's own serverconfig file wins over the global
	 * one, and reading again (what /reload does) after it is gone gives the global one back. All within one tick, so no
	 * other test sees the changed value.
	 */
	private static void serverConfigPerWorld(GameTestHelper helper) {
		net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
		java.nio.file.Path worldFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("serverconfig")
			.resolve(FabricConfig.fileName(jexxme.vineandbarrel.platform.ConfigValue.Side.SERVER));
		int global = jexxme.vineandbarrel.tipsy.TipsyConfig.SECONDS_PER_LEVEL.get();
		int own = global == 123 ? 124 : 123;
		try {
			java.nio.file.Files.createDirectories(worldFile.getParent());
			java.nio.file.Files.writeString(worldFile, "tipsy.secondsPerLevel=" + own + System.lineSeparator());
			helper.assertValueEqual(FabricConfig.serverFile(server), worldFile, "the world's own file is the one read");
			FabricConfig.loadServer(server);
			helper.assertValueEqual(jexxme.vineandbarrel.tipsy.TipsyConfig.SECONDS_PER_LEVEL.get(), own, "secondsPerLevel from the world's file");
			java.nio.file.Files.delete(worldFile);
			FabricConfig.loadServer(server);
			helper.assertValueEqual(jexxme.vineandbarrel.tipsy.TipsyConfig.SECONDS_PER_LEVEL.get(), global, "secondsPerLevel from the global file again");
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		} finally {
			try {
				java.nio.file.Files.deleteIfExists(worldFile);
			} catch (java.io.IOException ignored) {
				// left for the next run to overwrite
			}
			FabricConfig.loadServer(server);
		}
		helper.succeed();
	}
}
