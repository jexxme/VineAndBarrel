package jexxme.vineandbarrel.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Where a wild crop patch generates: biomes in a list or tag (minus a deny list or tag) within a base temperature range.
 * The files are Forge biome modifiers of our type ({@code data/<namespace>/forge/biome_modifier},
 * {@code vineandbarrel:add_features_by_filter}, after FD's {@code add_features_by_filter}, Farmer's Delight by vectorwing,
 * MIT). Fabric reads the same files with this class, with the same shapes Forge's codec takes (a {@code #tag}, one id
 * or a list of ids, for the biomes and the features), and adds the features through Fabric's biome API, so both loaders
 * place wild crops in exactly the same biomes.
 */
public record WildCropFilter(
		Keys<Biome> allowedBiomes,
		Optional<Keys<Biome>> deniedBiomes,
		Optional<Float> minimumTemperature,
		Optional<Float> maximumTemperature,
		List<ResourceKey<PlacedFeature>> features,
		GenerationStep.Decoration step
)
{
	/** Our own files, by name (each crop has a common patch and a rarer extra one). */
	public static final List<String> NAMES = List.of("wild_grapes", "wild_grapes_extra", "wild_hops", "wild_hops_extra", "wild_barley", "wild_barley_extra",
		"wild_white_grapes", "wild_white_grapes_extra");
	/** Where biome modifier files live in a datapack, in any namespace. */
	public static final String FOLDER = "forge/biome_modifier";
	public static final String TYPE = VineAndBarrel.MODID + ":add_features_by_filter";

	/** The biome modifier's fields, read like NeoForge's {@code AddFeaturesByFilterBiomeModifier} reads them. */
	public static final Codec<WildCropFilter> CODEC = RecordCodecBuilder.create(builder -> builder.group(
			Keys.codec(Registries.BIOME).fieldOf("allowed_biomes").forGetter(WildCropFilter::allowedBiomes),
			Keys.codec(Registries.BIOME).optionalFieldOf("denied_biomes").forGetter(WildCropFilter::deniedBiomes),
			Codec.FLOAT.optionalFieldOf("min_temperature").forGetter(WildCropFilter::minimumTemperature),
			Codec.FLOAT.optionalFieldOf("max_temperature").forGetter(WildCropFilter::maximumTemperature),
			compactListCodec(ResourceKey.codec(Registries.PLACED_FEATURE)).fieldOf("features").forGetter(WildCropFilter::features),
			GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(WildCropFilter::step)
	).apply(builder, WildCropFilter::new));

	/** One element or a list of them, written as the single element when there is just one (26.x: {@code ExtraCodecs.compactListCodec}). */
	private static <E> Codec<List<E>> compactListCodec(Codec<E> element) {
		return Codec.either(element.listOf(), element).xmap(
				either -> either.map(list -> list, List::of),
				list -> list.size() == 1 ? Either.right(list.get(0)) : Either.left(list));
	}

	/**
	 * A registry list the way vanilla's holder-set codec writes one: a {@code #tag}, a single id, or a list of ids.
	 * Matched against a biome's key and tags, so it needs no registry access to read.
	 */
	public record Keys<T>(Optional<TagKey<T>> tag, List<ResourceKey<T>> ids)
	{
		public static <T> Codec<Keys<T>> codec(ResourceKey<? extends Registry<T>> registry) {
			Codec<TagKey<T>> tag = TagKey.hashedCodec(registry);
			Codec<List<ResourceKey<T>>> ids = compactListCodec(ResourceKey.codec(registry));
			return Codec.either(tag, ids).xmap(
					either -> either.map(t -> new Keys<>(Optional.of(t), List.of()), list -> new Keys<>(Optional.empty(), list)),
					keys -> keys.tag().<Either<TagKey<T>, List<ResourceKey<T>>>>map(Either::left).orElseGet(() -> Either.right(keys.ids())));
		}

		public boolean contains(Holder<T> holder) {
			return contains(holder::is, holder.unwrapKey().orElse(null));
		}

		/** Whether an entry with this key, and tags as {@code hasTag} says, is in the list. */
		public boolean contains(Predicate<TagKey<T>> hasTag, ResourceKey<T> key) {
			return tag.map(hasTag::test).orElse(false) || ids.contains(key);
		}
	}

	/** The file's id: {@code vineandbarrel:<name>} in {@code data/vineandbarrel/forge/biome_modifier}. */
	public static ResourceLocation file(String name) {
		return VineAndBarrel.id(FOLDER + "/" + name + ".json");
	}

	/** Reads one of our files from the mod's own resources. */
	public static WildCropFilter load(String name) {
		String path = "/data/" + VineAndBarrel.MODID + "/" + FOLDER + "/" + name + ".json";
		try (InputStream in = WildCropFilter.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Missing " + path);
			}
			JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			return fromJson(json, path).orElseThrow(() -> new IllegalStateException(path + " adds nothing"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The biome modifier type a file declares, or "" without one. */
	public static String typeOf(JsonElement json) {
		return json.isJsonObject() && json.getAsJsonObject().has("type") ? json.getAsJsonObject().get("type").getAsString() : "";
	}

	/**
	 * One of our files as a datapack may have replaced it: ours, or Forge's {@code forge:none} (no wild crops from
	 * it), which is empty. Any other type is one we can't read off Forge, also empty (with a warning).
	 */
	public static Optional<WildCropFilter> fromJson(JsonElement json, String source) {
		String type = typeOf(json);
		if (type.equals("forge:none")) {
			return Optional.empty();
		}
		if (!type.equals(TYPE)) {
			VineAndBarrel.LOGGER.warn("{}: biome modifier type {} only works on Forge; no wild crops from it", source, type);
			return Optional.empty();
		}
		return Optional.of(CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, message -> {
			throw new IllegalStateException(source + ": " + message);
		}));
	}

	public boolean accepts(Holder<Biome> biome) {
		return accepts(biome::is, biome.unwrapKey().orElse(null), biome.value().getBaseTemperature());
	}

	/** Whether a biome with this key, tags and base temperature gets the features. */
	public boolean accepts(Predicate<TagKey<Biome>> hasTag, ResourceKey<Biome> key, float temperature) {
		if (!allowedBiomes.contains(hasTag, key) || deniedBiomes.map(denied -> denied.contains(hasTag, key)).orElse(false)) {
			return false;
		}
		return minimumTemperature.map(min -> temperature >= min).orElse(true)
				&& maximumTemperature.map(max -> temperature <= max).orElse(true);
	}
}
