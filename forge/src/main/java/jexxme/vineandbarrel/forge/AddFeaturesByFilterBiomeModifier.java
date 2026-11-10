package jexxme.vineandbarrel.forge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeGenerationSettingsBuilder;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Adds placed features to biomes picked by tag and base temperature: FD's {@code add_features_by_filter} biome modifier
 * (Farmer's Delight by vectorwing, MIT, see NOTICE_farmersdelight.txt), registered under our own id so it works with or
 * without FD installed. Forge only (the files live in {@code data/<namespace>/forge/biome_modifier}); Fabric reads the
 * same files with {@code WildCropFilter}.
 */
public record AddFeaturesByFilterBiomeModifier(
		HolderSet<Biome> allowedBiomes,
		Optional<HolderSet<Biome>> deniedBiomes,
		Optional<Float> minimumTemperature,
		Optional<Float> maximumTemperature,
		HolderSet<PlacedFeature> features,
		GenerationStep.Decoration step
) implements BiomeModifier
{
	public static final DeferredRegister<Codec<? extends BiomeModifier>> SERIALIZERS =
			DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, VineAndBarrel.MODID);

	public static final Supplier<Codec<AddFeaturesByFilterBiomeModifier>> CODEC = SERIALIZERS.register("add_features_by_filter", () ->
			RecordCodecBuilder.create(builder -> builder.group(
					Biome.LIST_CODEC.fieldOf("allowed_biomes").forGetter(AddFeaturesByFilterBiomeModifier::allowedBiomes),
					Biome.LIST_CODEC.optionalFieldOf("denied_biomes").forGetter(AddFeaturesByFilterBiomeModifier::deniedBiomes),
					Codec.FLOAT.optionalFieldOf("min_temperature").forGetter(AddFeaturesByFilterBiomeModifier::minimumTemperature),
					Codec.FLOAT.optionalFieldOf("max_temperature").forGetter(AddFeaturesByFilterBiomeModifier::maximumTemperature),
					PlacedFeature.LIST_CODEC.fieldOf("features").forGetter(AddFeaturesByFilterBiomeModifier::features),
					GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(AddFeaturesByFilterBiomeModifier::step)
			).apply(builder, AddFeaturesByFilterBiomeModifier::new)));

	public static void register(IEventBus modEventBus) {
		SERIALIZERS.register(modEventBus);
	}

	/** The same filter the modifier applies, so tests can check which biomes get our wild crops. */
	public boolean accepts(Holder<Biome> biome) {
		if (!allowedBiomes.contains(biome) || deniedBiomes.map(denied -> denied.contains(biome)).orElse(false)) {
			return false;
		}
		float temperature = biome.value().getBaseTemperature();
		return minimumTemperature.map(min -> temperature >= min).orElse(true)
				&& maximumTemperature.map(max -> temperature <= max).orElse(true);
	}

	@Override
	public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
		if (phase == Phase.ADD && accepts(biome)) {
			BiomeGenerationSettingsBuilder generationSettings = builder.getGenerationSettings();
			features.forEach(holder -> generationSettings.addFeature(step, holder));
		}
	}

	@Override
	public Codec<? extends BiomeModifier> codec() {
		return CODEC.get();
	}
}
