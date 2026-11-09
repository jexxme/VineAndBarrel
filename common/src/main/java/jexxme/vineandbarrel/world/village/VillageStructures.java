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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Villages (DESIGN 3 and 8), done exactly like Farmer's Delight's {@code VillageStructures}: on every server start the
 * taverns join the village house pools and the village farm processor lists (a living village's and an abandoned one's)
 * get one more rule processor that turns some of their wheat into our crops. Both are code, not data, because a data pack can only replace a vanilla pool or
 * processor list as a whole, which would fight every other mod that adds to them.
 */
public final class VillageStructures
{
	/**
	 * One tavern layout: its template ({@code vineandbarrel:village/<type>/houses/<name>}) and its weight in the village
	 * type's house pool.
	 */
	public record TavernLayout(VillageType type, String name, int weight) {
		public Identifier id() {
			return VineAndBarrel.id("village/" + type.id() + "/houses/" + name);
		}

		/**
		 * Its barrel cellar: a template of its own, which the tavern's dressing processor lays under the floor as the tavern
		 * is placed (see {@link TavernDressingProcessor#finalizeProcessing}).
		 */
		public Identifier cellarId() {
			return VineAndBarrel.id("village/" + type.id() + "/tavern_cellars/" + name);
		}
	}

	/** The cellar pieces' height: the cellar floor, three layers of room, the vault under the tavern floor. */
	public static final int CELLAR_HEIGHT = 5;

	/**
	 * Every tavern layout, two or three per village type, weighted for a tavern in about one village in three. Vanilla
	 * houses weigh 1 to 5 (most 2) out of about 80 per pool, but a house only gets a plot it fits, and a tavern is one of
	 * the biggest; how often each layout fits depends on its size and the village type (desert plots are small, snowy and
	 * taiga ones roomy), and on real terrain far fewer plots fit than on flat ground. The weights come from counting the
	 * villages with a tavern over a few hundred real villages; the world_taverns_one_village_in_three GameTest guards them
	 * on flat ground, where taverns fit more often. The
	 * 0.9.0 taverns keep their own templates under their old names (see {@link VillageType#legacyTavern}).
	 */
	public static final List<TavernLayout> TAVERNS = List.of(
		new TavernLayout(VillageType.PLAINS, "plains_tavern_1", 60),
		new TavernLayout(VillageType.PLAINS, "plains_tavern_2", 30),
		new TavernLayout(VillageType.DESERT, "desert_tavern_1", 16),
		new TavernLayout(VillageType.DESERT, "desert_tavern_2", 36),
		new TavernLayout(VillageType.SAVANNA, "savanna_tavern_1", 45),
		new TavernLayout(VillageType.SAVANNA, "savanna_tavern_2", 145),
		new TavernLayout(VillageType.SNOWY, "snowy_tavern_1", 5),
		new TavernLayout(VillageType.SNOWY, "snowy_tavern_2", 46),
		new TavernLayout(VillageType.TAIGA, "taiga_tavern_1", 15),
		new TavernLayout(VillageType.TAIGA, "taiga_tavern_2", 60));

	/** This village type's tavern layouts, in order. */
	public static List<TavernLayout> taverns(VillageType type) {
		return TAVERNS.stream().filter(layout -> layout.type() == type).toList();
	}

	/** The weight of all of a village type's taverns together in its house pool. */
	public static final Map<VillageType, Integer> TAVERN_WEIGHTS = java.util.Arrays.stream(VillageType.values())
		.collect(java.util.stream.Collectors.toUnmodifiableMap(type -> type, type -> taverns(type).stream().mapToInt(TavernLayout::weight).sum()));

	/** Every village type has its taverns, built in its own style. */
	public static final List<VillageType> TAVERN_TYPES = List.of(VillageType.values());

	/** The processor list each tavern is placed with: the one most vanilla houses of that type use (then the dressing). */
	private static final Map<VillageType, Identifier> TAVERN_PROCESSORS = Map.of(
		VillageType.PLAINS, Identifier.withDefaultNamespace("mossify_10_percent"),
		VillageType.DESERT, Identifier.withDefaultNamespace("empty"),
		VillageType.SAVANNA, Identifier.withDefaultNamespace("empty"),
		VillageType.SNOWY, Identifier.withDefaultNamespace("empty"),
		VillageType.TAIGA, Identifier.withDefaultNamespace("mossify_10_percent"));

	/** Each village type's tavern floor, which closes the stairwell where the ground is too shallow for a cellar. */
	private static final Map<VillageType, Block> TAVERN_FLOORS = Map.of(
		VillageType.PLAINS, Blocks.OAK_PLANKS,
		VillageType.DESERT, Blocks.SMOOTH_SANDSTONE,
		VillageType.SAVANNA, Blocks.ACACIA_PLANKS,
		VillageType.SNOWY, Blocks.SPRUCE_PLANKS,
		VillageType.TAIGA, Blocks.SPRUCE_PLANKS);

	/** How each village type dresses its taverns (see {@link TavernDressingProcessor}): its flowers, pots, banners and candles. */
	public static TavernDressingProcessor dressing(VillageType type) {
		return switch (type) {
			case PLAINS -> new TavernDressingProcessor(
				List.of(Blocks.POPPY, Blocks.DANDELION, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.PINK_TULIP),
				List.of(Blocks.POTTED_POPPY, Blocks.POTTED_DANDELION, Blocks.POTTED_AZURE_BLUET, Blocks.POTTED_OXEYE_DAISY, Blocks.POTTED_CORNFLOWER),
				List.of(DyeColor.RED, DyeColor.YELLOW, DyeColor.GREEN, DyeColor.BLUE, DyeColor.ORANGE),
				List.of(DyeColor.WHITE, DyeColor.YELLOW, DyeColor.RED, DyeColor.ORANGE));
			case DESERT -> new TavernDressingProcessor(
				List.of(),
				List.of(Blocks.POTTED_CACTUS, Blocks.POTTED_DEAD_BUSH),
				List.of(DyeColor.ORANGE, DyeColor.RED, DyeColor.CYAN, DyeColor.LIGHT_BLUE, DyeColor.YELLOW),
				List.of(DyeColor.WHITE, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.CYAN));
			case SAVANNA -> new TavernDressingProcessor(
				List.of(Blocks.DANDELION, Blocks.POPPY, Blocks.ORANGE_TULIP, Blocks.OXEYE_DAISY, Blocks.RED_TULIP),
				List.of(Blocks.POTTED_DANDELION, Blocks.POTTED_ORANGE_TULIP, Blocks.POTTED_ACACIA_SAPLING, Blocks.POTTED_RED_TULIP),
				List.of(DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.RED, DyeColor.BROWN, DyeColor.LIME),
				List.of(DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.RED, DyeColor.BROWN));
			case SNOWY -> new TavernDressingProcessor(
				List.of(Blocks.LILY_OF_THE_VALLEY, Blocks.CORNFLOWER, Blocks.WHITE_TULIP, Blocks.AZURE_BLUET),
				List.of(Blocks.POTTED_SPRUCE_SAPLING, Blocks.POTTED_LILY_OF_THE_VALLEY, Blocks.POTTED_FERN, Blocks.POTTED_BLUE_ORCHID),
				List.of(DyeColor.BLUE, DyeColor.LIGHT_BLUE, DyeColor.WHITE, DyeColor.RED, DyeColor.CYAN),
				List.of(DyeColor.WHITE, DyeColor.RED, DyeColor.LIGHT_BLUE, DyeColor.BLUE));
			case TAIGA -> new TavernDressingProcessor(
				List.of(Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.POPPY, Blocks.ALLIUM),
				List.of(Blocks.POTTED_FERN, Blocks.POTTED_SPRUCE_SAPLING, Blocks.POTTED_RED_MUSHROOM, Blocks.POTTED_BROWN_MUSHROOM),
				List.of(DyeColor.GREEN, DyeColor.BROWN, DyeColor.RED, DyeColor.PURPLE, DyeColor.BLACK),
				List.of(DyeColor.WHITE, DyeColor.RED, DyeColor.BROWN, DyeColor.GREEN));
		};
	}

	/** A crop the farms of a village type grow: {@code chance} of each remaining wheat block becomes {@code crop}. */
	public record FarmCrop(Supplier<? extends Block> crop, float chance) {
	}

	/**
	 * Which of our crops village farms grow, by climate like the wild crops (DESIGN 3): red grapes where it is warm, white
	 * grapes where it is milder (plains, taiga), hops where it is temperate or cool, barley nearly everywhere. Our processor runs first, so each chance is of all the
	 * farm's wheat (each rule of what is still wheat); vanilla's carrots, potatoes and beetroots and FD's crops then
	 * share what is left, whichever mod loads first. About a fifth of a farm becomes ours.
	 */
	public static final Map<VillageType, List<FarmCrop>> FARM_CROPS = Map.of(
		VillageType.PLAINS, List.of(new FarmCrop(VBBlocks.BARLEY, 0.1F), new FarmCrop(VBBlocks.GRAPE_BUSH, 0.05F), new FarmCrop(VBBlocks.HOP_BUSH, 0.05F),
			new FarmCrop(VBBlocks.WHITE_GRAPE_BUSH, 0.03F)),
		VillageType.DESERT, List.of(new FarmCrop(VBBlocks.GRAPE_BUSH, 0.08F), new FarmCrop(VBBlocks.BARLEY, 0.05F)),
		VillageType.SAVANNA, List.of(new FarmCrop(VBBlocks.BARLEY, 0.1F), new FarmCrop(VBBlocks.GRAPE_BUSH, 0.08F)),
		VillageType.SNOWY, List.of(new FarmCrop(VBBlocks.BARLEY, 0.1F), new FarmCrop(VBBlocks.HOP_BUSH, 0.06F)),
		VillageType.TAIGA, List.of(new FarmCrop(VBBlocks.BARLEY, 0.1F), new FarmCrop(VBBlocks.HOP_BUSH, 0.08F), new FarmCrop(VBBlocks.WHITE_GRAPE_BUSH, 0.04F)));

	private VillageStructures() {
	}

	private static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
		DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, VineAndBarrel.MODID);

	static final Supplier<StructureProcessorType<FarmCropProcessor>> FARM_CROP_PROCESSOR = PROCESSORS.register("farm_crops", () -> () -> FarmCropProcessor.CODEC);
	static final Supplier<StructureProcessorType<TavernDressingProcessor>> TAVERN_DRESSING_PROCESSOR = PROCESSORS.register("tavern_dressing", () -> () -> TavernDressingProcessor.CODEC);

	public static void register() {
		PROCESSORS.register();
	}

	/** Before a server starts (each loader calls this from its own event). */
	public static void onServerAboutToStart(MinecraftServer server) {
		apply(server.registryAccess());
	}

	/** Adds the taverns and the farm crops to this server's registries. Safe to call again: nothing is added twice. */
	public static void apply(RegistryAccess access) {
		Registry<StructureTemplatePool> pools = access.lookupOrThrow(Registries.TEMPLATE_POOL);
		Registry<StructureProcessorList> processorLists = access.lookupOrThrow(Registries.PROCESSOR_LIST);
		for (TavernLayout layout : TAVERNS) {
			addBuildingToPool(pools, processorLists, layout.type().housesPool(), layout.id(), TAVERN_PROCESSORS.get(layout.type()), layout.weight(),
				dressing(layout.type()).withCellar(layout.cellarId(), TAVERN_FLOORS.get(layout.type()).defaultBlockState()));
		}
		for (VillageType type : VillageType.values()) {
			// abandoned (zombie) villages too: their farms are placed with the zombie list, which carries the same farm rules
			for (Identifier list : type.farmProcessorLists()) {
				addProcessorToList(processorLists, list, farmCropProcessor(FARM_CROPS.get(type)));
			}
		}
	}

	/**
	 * FD's {@code addBuildingToPool}, with the processor list of the pool's own houses instead of {@code empty}, and our
	 * own processors after it ({@code extra}, if any).
	 */
	public static void addBuildingToPool(Registry<StructureTemplatePool> pools, Registry<StructureProcessorList> processorLists, Identifier poolId,
		Identifier template, Identifier processors, int weight, StructureProcessor... extra) {
		StructureTemplatePool pool = pools.getValue(poolId);
		if (pool == null) {
			VineAndBarrel.LOGGER.warn("Village pool {} is missing, so {} is not added", poolId, template);
			return;
		}
		if (pool.rawTemplates.stream().anyMatch(entry -> isTemplate(entry.getFirst(), template))) {
			return;
		}
		Holder<StructureProcessorList> processorHolder = processorLists.getOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, processors));
		if (extra.length > 0) {
			List<StructureProcessor> all = new ArrayList<>(processorHolder.value().list());
			all.addAll(List.of(extra));
			processorHolder = Holder.direct(new StructureProcessorList(all));
		}
		// never waterlogged by the water it is placed into (a tavern at the water's edge would flood its cellar stairs)
		SinglePoolElement piece = SinglePoolElement.single(template.toString(), processorHolder,
			net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.IGNORE_WATERLOGGING).apply(StructureTemplatePool.Projection.RIGID);
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

	/** Whether this pool element is one of this village type's taverns, a 0.9.0 one included. */
	public static boolean isTavern(StructurePoolElement element, VillageType type) {
		return element instanceof SinglePoolElement single
			&& (type.taverns().contains(single.getTemplateLocation()) || type.legacyTavern().equals(single.getTemplateLocation()));
	}

	/**
	 * A tavern piece's box with its cellar: the piece's own box stops at the tavern floor, the cellar its processor lays
	 * under it reaches {@link #CELLAR_HEIGHT} further down.
	 */
	public static net.minecraft.world.level.levelgen.structure.BoundingBox withCellar(net.minecraft.world.level.levelgen.structure.BoundingBox box) {
		return new net.minecraft.world.level.levelgen.structure.BoundingBox(box.minX(), box.minY() - CELLAR_HEIGHT, box.minZ(), box.maxX(), box.maxY(), box.maxZ());
	}

	/**
	 * The generated village with a piece at this block (invalid if there is none), a tavern's cellar counted as part of
	 * its tavern: the village's own lookup only knows the pieces' boxes, and the cellar lies under the tavern's.
	 */
	public static net.minecraft.world.level.levelgen.structure.StructureStart villageAt(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		var manager = level.structureManager();
		var village = manager.getStructureWithPieceAt(pos, net.minecraft.tags.StructureTags.VILLAGE);
		if (village.isValid()) {
			return village;
		}
		for (int up = 1; up <= CELLAR_HEIGHT; up++) {
			var above = manager.getStructureWithPieceAt(pos.above(up), net.minecraft.tags.StructureTags.VILLAGE);
			if (above.isValid() && tavernPieceAt(above, pos) != null) {
				return above;
			}
		}
		return net.minecraft.world.level.levelgen.structure.StructureStart.INVALID_START;
	}

	/** Whether this block lies in one of our taverns in a generated village, its cellar included. */
	public static boolean inTavern(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		var village = villageAt(level, pos);
		return village.isValid() && tavernPieceAt(village, pos) != null;
	}

	/** The village's tavern piece whose box, its cellar included, holds this block, or null. */
	private static net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece tavernPieceAt(net.minecraft.world.level.levelgen.structure.StructureStart village,
		net.minecraft.core.BlockPos pos) {
		for (var piece : village.getPieces()) {
			if (piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool && isAnyTavern(pool.getElement())
				&& withCellar(piece.getBoundingBox()).isInside(pos)) {
				return pool;
			}
		}
		return null;
	}

	/** Whether this pool element is any village type's tavern, a 0.9.0 one included. */
	public static boolean isAnyTavern(StructurePoolElement element) {
		return java.util.Arrays.stream(VillageType.values()).anyMatch(type -> isTavern(element, type));
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
