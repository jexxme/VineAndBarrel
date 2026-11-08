package jexxme.vineandbarrel.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.world.WildCropFilter;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wild crops on Fabric, from the biome modifier files NeoForge reads ({@link WildCropFilter}), as the world's datapacks
 * have them: every file of our type in any namespace's {@code neoforge/biome_modifier} folder, so a datapack can
 * replace one of ours (other biomes or temperatures, or {@code neoforge:none} to turn it off) or add its own, as on
 * NeoForge. The files are read from the resources the world's registries were just loaded from
 * ({@code ResourceManagerRegistryLoadTaskMixin}), when Fabric applies biome modifications as the server starts; our own
 * copies are the fallback. Like NeoForge's biome modifiers they apply once per server start, not on /reload.
 */
public final class FabricWildCrops
{
	private static volatile @Nullable ResourceManager worldgenResources;
	private static volatile @Nullable List<WildCropFilter> filters;

	private FabricWildCrops() {
	}

	static void register() {
		BiomeModifications.create(VineAndBarrel.id("wild_crops")).add(ModificationPhase.ADDITIONS,
			context -> filters().stream().anyMatch(filter -> filter.accepts(context::hasTag, context.getBiomeKey(), context.getBiome().getBaseTemperature())),
			(context, modification) -> {
				for (WildCropFilter filter : filters()) {
					if (filter.accepts(context::hasTag, context.getBiomeKey(), context.getBiome().getBaseTemperature())) {
						filter.features().forEach(feature -> modification.getGenerationSettings().addFeature(filter.step(), feature));
					}
				}
			});
	}

	/** The resources a server's worldgen registries are being loaded from (the data packs of the world about to start). */
	public static void loadingWorldgen(ResourceManager resources) {
		worldgenResources = resources;
		filters = null;
	}

	static List<WildCropFilter> filters() {
		List<WildCropFilter> known = filters;
		if (known == null) {
			known = read(worldgenResources);
			filters = known;
		}
		return known;
	}

	private static List<WildCropFilter> read(@Nullable ResourceManager resources) {
		List<WildCropFilter> found = new ArrayList<>();
		if (resources == null) {
			WildCropFilter.NAMES.forEach(name -> found.add(WildCropFilter.load(name)));
			return found;
		}
		for (Map.Entry<Identifier, Resource> file : resources.listResources(WildCropFilter.FOLDER, path -> path.getPath().endsWith(".json")).entrySet()) {
			String source = file.getKey() + " from " + file.getValue().sourcePackId();
			try (Reader reader = file.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				String type = WildCropFilter.typeOf(json);
				// Other mods' NeoForge biome modifiers aren't ours to read; one of ours turned into another type is reported
				boolean ours = file.getKey().getNamespace().equals(VineAndBarrel.MODID);
				if (type.equals(WildCropFilter.TYPE) || ours) {
					WildCropFilter.fromJson(json, source).ifPresent(found::add);
				}
			} catch (IOException | RuntimeException e) {
				VineAndBarrel.LOGGER.error("Could not read {}; no wild crops from it", source, e);
			}
		}
		return found;
	}
}
