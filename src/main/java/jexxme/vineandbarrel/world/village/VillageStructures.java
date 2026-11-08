package jexxme.vineandbarrel.world.village;

import com.mojang.datafixers.util.Pair;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.registry.VBBlocks;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Villages (DESIGN 3 and 8), done exactly like Farmer's Delight's {@code VillageStructures}: on every server start the
 * taverns join the village house pools and the village farm processor lists get one more rule processor that turns
 * some of their wheat into our crops. Both are code, not data, because a data pack can only replace a vanilla pool or
 * processor list as a whole, which would fight every other mod that adds to them.
 */
public final class VillageStructures
{
	/**
	 * Tavern weight in its house pool. Vanilla houses weigh 1 to 5 (most 2) out of about 80 per pool, and a village has
	 * around 10 to 20 house slots, so 3 puts a tavern in roughly every second village of every type; sometimes two.
	 */
	public static final int TAVERN_WEIGHT = 3;

	/** Every village type has its tavern, built in its own style. */
	public static final List<VillageType> TAVERN_TYPES = List.of(VillageType.values());

	/** The processor list each tavern is placed with: the one most vanilla houses of that type use. */
	private static final Map<VillageType, Identifier> TAVERN_PROCESSORS = Map.of(
		VillageType.PLAINS, Identifier.withDefaultNamespace("mossify_10_percent"),
		VillageType.DESERT, Identifier.withDefaultNamespace("empty"),
		VillageType.SAVANNA, Identifier.withDefaultNamespace("empty"),
		VillageType.SNOWY, Identifier.withDefaultNamespace("empty"),
		VillageType.TAIGA, Identifier.withDefaultNamespace("mossify_10_percent"));

	/** A crop the farms of a village type grow: {@code chance} of each remaining wheat block becomes {@code crop}. */
	public record FarmCrop(Supplier<? extends Block> crop, float chance) {
	}

	/**
	 * Which of our crops village farms grow, by climate like the wild crops (DESIGN 3): grapes where it is warm, hops
	 * where it is temperate or cool, barley nearly everywhere. Our processor runs first, so each chance is of all the
	 * farm's wheat (each rule of what is still wheat); vanilla's carrots, potatoes and beetroots and FD's crops then
	 * share what is left, whichever mod loads first. About a fifth of a farm becomes ours.
	 */
	public static final Map<VillageType, List<FarmCrop>> FARM_CROPS = Map.of(
		VillageType.PLAINS, List.of(new FarmCrop(VBBlocks.BARLEY, 0.1F), new FarmCrop(VBBlocks.GRAPE_BUSH, 0.05F), new FarmCrop(VBBlocks.HOP_BUSH, 0.05F)),
		VillageType.DESERT, List.of(new FarmCrop(VBBlocks.GRAPE_BUSH, 0.08F), new FarmCrop(VBBlocks.BARLEY, 0.05F)),
		VillageType.SAVANNA, List.of(new FarmCrop(VBBlocks.BARLEY, 0.1F), new FarmCrop(VBBlocks.GRAPE_BUSH, 0.08F)),
		VillageType.SNOWY, List.of(new FarmCrop(VBBlocks.BARLEY, 0.1F), new FarmCrop(VBBlocks.HOP_BUSH, 0.06F)),
		VillageType.TAIGA, List.of(new FarmCrop(VBBlocks.BARLEY, 0.1F), new FarmCrop(VBBlocks.HOP_BUSH, 0.08F)));

	private VillageStructures() {
	}

	private static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
		DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, VineAndBarrel.MODID);

	static final Supplier<StructureProcessorType<FarmCropProcessor>> FARM_CROP_PROCESSOR = PROCESSORS.register("farm_crops", () -> () -> FarmCropProcessor.CODEC);

	public static void register(IEventBus modEventBus) {
		PROCESSORS.register(modEventBus);
		NeoForge.EVENT_BUS.addListener(VillageStructures::onServerAboutToStart);
	}

	private static void onServerAboutToStart(ServerAboutToStartEvent event) {
		apply(event.getServer().registryAccess());
	}

	/** Adds the taverns and the farm crops to this server's registries. Safe to call again: nothing is added twice. */
	public static void apply(RegistryAccess access) {
		Registry<StructureTemplatePool> pools = access.lookupOrThrow(Registries.TEMPLATE_POOL);
		Registry<StructureProcessorList> processorLists = access.lookupOrThrow(Registries.PROCESSOR_LIST);
		for (VillageType type : TAVERN_TYPES) {
			addBuildingToPool(pools, processorLists, type.housesPool(), type.tavern(), TAVERN_PROCESSORS.get(type), TAVERN_WEIGHT);
		}
		for (VillageType type : VillageType.values()) {
			addProcessorToList(processorLists, type.farmProcessors(), farmCropProcessor(FARM_CROPS.get(type)));
		}
	}

	/** FD's {@code addBuildingToPool}, with the processor list of the pool's own houses instead of {@code empty}. */
	public static void addBuildingToPool(Registry<StructureTemplatePool> pools, Registry<StructureProcessorList> processorLists, Identifier poolId,
		Identifier template, Identifier processors, int weight) {
		StructureTemplatePool pool = pools.getValue(poolId);
		if (pool == null) {
			VineAndBarrel.LOGGER.warn("Village pool {} is missing, so {} is not added", poolId, template);
			return;
		}
		if (pool.rawTemplates.stream().anyMatch(entry -> isTemplate(entry.getFirst(), template))) {
			return;
		}
		Holder<StructureProcessorList> processorHolder = processorLists.getOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, processors));
		SinglePoolElement piece = SinglePoolElement.single(template.toString(), processorHolder).apply(StructureTemplatePool.Projection.RIGID);
		for (int i = 0; i < weight; i++) {
			pool.templates.add(piece);
		}
		List<Pair<StructurePoolElement, Integer>> entries = new ArrayList<>(pool.rawTemplates);
		entries.add(new Pair<>(piece, weight));
		pool.rawTemplates = entries;
	}

	public static boolean isTemplate(StructurePoolElement element, Identifier template) {
		return element instanceof SinglePoolElement single && single.getTemplateLocation().equals(template);
	}

	/** Our farm processor for these crops (see {@link FarmCropProcessor} for why it is not a vanilla rule processor). */
	public static StructureProcessor farmCropProcessor(List<FarmCrop> crops) {
		return new FarmCropProcessor(crops.stream().map(crop -> new FarmCropProcessor.Rule(crop.crop().get().defaultBlockState(), crop.chance())).toList());
	}

	private static void addProcessorToList(Registry<StructureProcessorList> processorLists, Identifier listId, StructureProcessor processor) {
		processorLists.getOptional(listId).ifPresent(list -> {
			if (list.list().stream().anyMatch(VillageStructures::isOurFarmProcessor)) {
				return;
			}
			// First, before vanilla's farm rules and any other mod's (FD appends its own): our share of the wheat then
			// doesn't depend on what the others took or on the mod load order
			List<StructureProcessor> processors = new ArrayList<>(list.list());
			processors.add(0, processor);
			list.list = processors;
		});
	}

	/** Whether this is one of our farm crop processors (so a second server start in the same JVM adds none twice). */
	public static boolean isOurFarmProcessor(StructureProcessor processor) {
		return processor instanceof FarmCropProcessor;
	}
}
