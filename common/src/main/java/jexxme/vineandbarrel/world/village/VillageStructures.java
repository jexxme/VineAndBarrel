package jexxme.vineandbarrel.world.village;

import com.mojang.datafixers.util.Pair;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.mixin.SinglePoolElementAccessor;
import jexxme.vineandbarrel.mixin.StructureProcessorListAccessor;
import jexxme.vineandbarrel.mixin.StructureTemplatePoolAccessor;
import jexxme.vineandbarrel.registry.VBBlocks;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Villages (DESIGN 3 and 8), done exactly like Farmer's Delight's {@code VillageStructures}: on every server start the
 * taverns join the village house pools (and those of other mods' villages, {@link #OTHER_VILLAGES}) and the village farm
 * processor lists (a living village's and an abandoned one's) get one more rule processor that turns some of their wheat
 * into our crops. Both are code, not data, because a data pack can only replace a vanilla pool or
 * processor list as a whole, which would fight every other mod that adds to them. The pools' and lists' own fields are
 * private: they are reached through mixin accessors, the same on both loaders.
 */
public final class VillageStructures
{
	/**
	 * One tavern layout: its template ({@code vineandbarrel:village/<type>/houses/<name>}) and its weight in the village
	 * type's house pool.
	 */
	public record TavernLayout(VillageType type, String name, int weight) {
		public ResourceLocation id() {
			return VineAndBarrel.id("village/" + type.id() + "/houses/" + name);
		}

		/**
		 * Its barrel cellar: a template of its own, which the tavern's dressing processor lays under the floor as the tavern
		 * is placed (see {@link TavernDressingProcessor#finalizeProcessing}).
		 */
		public ResourceLocation cellarId() {
			return VineAndBarrel.id("village/" + type.id() + "/tavern_cellars/" + name);
		}
	}

	/**
	 * The cellar pieces' height: the cellar floor, three layers of room and the vault under the tavern floor, and under
	 * them five more for the lower rooms of 1.2 (the old vault, the cistern and its pool, the cold store), solid
	 * ground where a layout has none.
	 */
	public static final int CELLAR_HEIGHT = 10;

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

	/**
	 * Another mod's village whose houses get our taverns too: its house pool, the village type whose taverns suit it, how
	 * much the taverns weigh there all together, in percent of the pool's own houses' weight (so it follows the pool as
	 * that mod changes it; the split between the layouts is the vanilla one, {@link #TAVERNS}), and in what percent of
	 * its villages a tavern may go up at all. That last one is what sets how often they have one: a tavern only takes a
	 * plot it fits, and in a village whose plots most of its own houses don't fill, it takes one of them whatever its
	 * weight, so the weight alone can't make them rarer there.
	 */
	public record OtherVillage(ResourceLocation housesPool, VillageType style, int percent, int villagePercent) {
		/** The weight of one tavern layout in this pool, whose own houses weigh {@code houses} together. */
		public int weight(TavernLayout layout, int houses) {
			return Math.max(1, Math.round(houses * percent / 100.0F * layout.weight() / TAVERN_WEIGHTS.get(style)));
		}
	}

	/**
	 * The villages of Towns & Towers (by Kaisyn; pools under {@code kaisyn:village/<village>/houses}), each with the
	 * tavern of the vanilla village closest to it in climate and build. Towns & Towers keeps the vanilla villages (and our
	 * taverns in them) in plains, desert, savanna, snowy plains and taiga and puts its own villages into the other
	 * biomes, meadows (where vanilla built plains villages) among them. Applied only to the pools a server has, so only
	 * with Towns & Towers (mod or data pack) installed. None go to its villages without a house pool of their own
	 * (beach, grove, snowy slopes, savanna plateau, sunflower plains), the mushroom fields' mushroom houses, the swamp's
	 * houses on stilts (no plot is deep enough for a tavern), nor its villages for other mods' biomes whose houses join
	 * their streets in other ways (nilotic, mediterranean), whose plots no tavern fits (tudor) or that are no village of
	 * houses (piglin, wandering trader camp). The shares of villages that may have one are tuned so about one village
	 * in three has a tavern, as in vanilla villages, counted over a few thousand villages of real worlds (with and
	 * without Terralith); those of the villages for other mods' biomes follow the nearest kind.
	 */
	public static final List<OtherVillage> OTHER_VILLAGES = List.of(
		townsAndTowers("meadow_swiss", VillageType.PLAINS, 103, 38),
		townsAndTowers("birch_forest_romanian", VillageType.PLAINS, 103, 75),
		townsAndTowers("flower_forest_japanese", VillageType.PLAINS, 103, 70),
		townsAndTowers("forest_ruins", VillageType.PLAINS, 103, 83),
		townsAndTowers("old_growth_taiga_polish", VillageType.TAIGA, 99, 36),
		townsAndTowers("snowy_taiga_viking", VillageType.SNOWY, 111, 83),
		townsAndTowers("badlands_pueblo", VillageType.DESERT, 72, 35),
		townsAndTowers("wooded_badlands_tipi", VillageType.DESERT, 72, 30),
		townsAndTowers("jungle_tribal", VillageType.SAVANNA, 235, 100),
		townsAndTowers("sparse_jungle_polynesian", VillageType.SAVANNA, 235, 38),
		// only in other mods' biomes (Terralith, Biomes O' Plenty, ...)
		townsAndTowers("exclusives/classic", VillageType.PLAINS, 103, 75),
		townsAndTowers("exclusives/rustic", VillageType.PLAINS, 103, 75),
		townsAndTowers("exclusives/swedish", VillageType.TAIGA, 99, 36),
		townsAndTowers("exclusives/iberian", VillageType.DESERT, 72, 35));

	private static OtherVillage townsAndTowers(String village, VillageType style, int percent, int villagePercent) {
		return new OtherVillage(new ResourceLocation("kaisyn", "village/" + village + "/houses"), style, percent, villagePercent);
	}

	/** The taverns added to other mods' pools, each with its village (read as villages are laid out, on worldgen threads). */
	private static final Map<StructurePoolElement, OtherVillage> OTHER_TAVERNS = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Whether a village of this kind laid out from this start piece may have a tavern: the same answer for every plot of
	 * the village, from where it starts, in {@link OtherVillage#villagePercent} of them.
	 */
	public static boolean mayHaveTavern(OtherVillage village, net.minecraft.world.level.levelgen.structure.StructurePiece start) {
		var box = start.getBoundingBox();
		long h = box.minX() * 0x9E3779B97F4A7C15L ^ box.minZ() * 0xC2B2AE3D27D4EB4FL ^ box.minY() * 0x165667B19E3779F9L;
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return Math.floorMod(h, 100) < village.villagePercent();
	}

	/** The processor list each tavern is placed with: the one most vanilla houses of that type use (then the dressing). */
	private static final Map<VillageType, ResourceLocation> TAVERN_PROCESSORS = Map.of(
		VillageType.PLAINS, new ResourceLocation("mossify_10_percent"),
		VillageType.DESERT, new ResourceLocation("empty"),
		VillageType.SAVANNA, new ResourceLocation("empty"),
		VillageType.SNOWY, new ResourceLocation("empty"),
		VillageType.TAIGA, new ResourceLocation("mossify_10_percent"));

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
		Registry<StructureTemplatePool> pools = access.registryOrThrow(Registries.TEMPLATE_POOL);
		Registry<StructureProcessorList> processorLists = access.registryOrThrow(Registries.PROCESSOR_LIST);
		for (TavernLayout layout : TAVERNS) {
			addBuildingToPool(pools, processorLists, layout.type().housesPool(), layout.id(), TAVERN_PROCESSORS.get(layout.type()), layout.weight(),
				dressing(layout.type()).withCellar(layout.cellarId(), TAVERN_FLOORS.get(layout.type()).defaultBlockState()));
		}
		for (OtherVillage village : OTHER_VILLAGES) {
			// only there with the mod (or its data pack) installed
			StructureTemplatePool pool = pools.get(village.housesPool());
			if (pool != null) {
				addTaverns(pool, processorLists, village);
			}
		}
		for (VillageType type : VillageType.values()) {
			// abandoned (zombie) villages too: their farms are placed with the zombie list, which carries the same farm rules
			for (ResourceLocation list : type.farmProcessorLists()) {
				addProcessorToList(processorLists, list, farmCropProcessor(FARM_CROPS.get(type)));
			}
		}
	}

	/**
	 * Another mod's village pool gets the taverns of its {@link OtherVillage#style}, weighted by its own houses' weight
	 * (taverns already in it not counted, so a second server start adds nothing).
	 */
	public static void addTaverns(StructureTemplatePool pool, Registry<StructureProcessorList> processorLists, OtherVillage village) {
		int houses = ((StructureTemplatePoolAccessor) pool).vineandbarrel$rawTemplates().stream().filter(entry -> !isTavern(entry.getFirst())).mapToInt(Pair::getSecond).sum();
		for (TavernLayout layout : taverns(village.style())) {
			addBuildingToPool(pool, processorLists, layout.id(), TAVERN_PROCESSORS.get(layout.type()), village.weight(layout, houses),
				dressing(layout.type()).withCellar(layout.cellarId(), TAVERN_FLOORS.get(layout.type()).defaultBlockState()));
		}
		((StructureTemplatePoolAccessor) pool).vineandbarrel$rawTemplates().stream().filter(entry -> isTavern(entry.getFirst())).forEach(entry -> OTHER_TAVERNS.put(entry.getFirst(), village));
	}

	/** {@return the other mod's village whose pool this tavern element was added to, or null} */
	public static @org.jspecify.annotations.Nullable OtherVillage otherVillageOf(StructurePoolElement element) {
		return OTHER_TAVERNS.get(element);
	}

	/**
	 * FD's {@code addBuildingToPool}, with the processor list of the pool's own houses instead of {@code empty}, and our
	 * own processors after it ({@code extra}, if any).
	 */
	public static void addBuildingToPool(Registry<StructureTemplatePool> pools, Registry<StructureProcessorList> processorLists, ResourceLocation poolId,
		ResourceLocation template, ResourceLocation processors, int weight, StructureProcessor... extra) {
		StructureTemplatePool pool = pools.get(poolId);
		if (pool == null) {
			VineAndBarrel.LOGGER.warn("Village pool {} is missing, so {} is not added", poolId, template);
			return;
		}
		addBuildingToPool(pool, processorLists, template, processors, weight, extra);
	}

	/** {@link #addBuildingToPool(Registry, Registry, ResourceLocation, ResourceLocation, ResourceLocation, int, StructureProcessor...)} into this pool. */
	public static void addBuildingToPool(StructureTemplatePool pool, Registry<StructureProcessorList> processorLists, ResourceLocation template, ResourceLocation processors,
		int weight, StructureProcessor... extra) {
		StructureTemplatePoolAccessor poolAccess = (StructureTemplatePoolAccessor) pool;
		if (poolAccess.vineandbarrel$rawTemplates().stream().anyMatch(entry -> isTemplate(entry.getFirst(), template))) {
			return;
		}
		Holder<StructureProcessorList> processorHolder = processorLists.getHolderOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, processors));
		if (extra.length > 0) {
			List<StructureProcessor> all = new ArrayList<>(processorHolder.value().list());
			all.addAll(List.of(extra));
			processorHolder = Holder.direct(new StructureProcessorList(all));
		}
		// never waterlogged by the water it is placed into (a tavern at the water's edge would flood its cellar stairs):
		// on this Minecraft the tavern's dressing processor sees to that (TavernDressingProcessor#finalizeProcessing)
		SinglePoolElement piece = SinglePoolElement.single(template.toString(), processorHolder).apply(StructureTemplatePool.Projection.RIGID);
		for (int i = 0; i < weight; i++) {
			poolAccess.vineandbarrel$templates().add(piece);
		}
		List<Pair<StructurePoolElement, Integer>> entries = new ArrayList<>(poolAccess.vineandbarrel$rawTemplates());
		entries.add(new Pair<>(piece, weight));
		poolAccess.vineandbarrel$setRawTemplates(entries);
	}

	public static boolean isTemplate(StructurePoolElement element, ResourceLocation template) {
		return element instanceof SinglePoolElement single && template.equals(templateOf(single));
	}

	/** The template a single pool element places, by name (null for one built around a template itself). */
	private static @Nullable ResourceLocation templateOf(SinglePoolElement single) {
		return ((SinglePoolElementAccessor) single).vineandbarrel$template().left().orElse(null);
	}

	/** Whether this pool element is one of this village type's taverns, a 0.9.0 one included. */
	public static boolean isTavern(StructurePoolElement element, VillageType type) {
		return element instanceof SinglePoolElement single
			&& (type.taverns().contains(templateOf(single)) || type.legacyTavern().equals(templateOf(single)));
	}

	/** Whether this pool element is one of our taverns, of any village type, a 0.9.0 one included. */
	public static boolean isTavern(StructurePoolElement element) {
		if (element instanceof SinglePoolElement) {
			for (VillageType type : VillageType.values()) {
				if (isTavern(element, type)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The houses a village plot chooses from, without the taverns once the village has one: one tavern per village,
	 * however many of its plots one would fit. In another mod's village, also without them if this village may have
	 * none ({@link #mayHaveTavern}). {@code pieces} is what the village has laid out so far, its start piece first.
	 */
	public static List<StructurePoolElement> oneTavernPerVillage(List<StructurePoolElement> houses, List<?> pieces) {
		StructurePoolElement tavern = houses.stream().filter(VillageStructures::isTavern).findFirst().orElse(null);
		if (tavern == null) {
			return houses;
		}
		OtherVillage other = OTHER_TAVERNS.get(tavern);
		boolean none = other != null && !pieces.isEmpty() && pieces.get(0) instanceof net.minecraft.world.level.levelgen.structure.StructurePiece start
			&& !mayHaveTavern(other, start);
		for (Object piece : pieces) {
			none |= piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece placed && isTavern(placed.getElement());
		}
		if (none) {
			List<StructurePoolElement> rest = new ArrayList<>(houses);
			rest.removeIf(VillageStructures::isTavern);
			return rest;
		}
		return houses;
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

	private static void addProcessorToList(Registry<StructureProcessorList> processorLists, ResourceLocation listId, StructureProcessor processor) {
		processorLists.getOptional(listId).ifPresent(list -> {
			if (list.list().stream().anyMatch(VillageStructures::isOurFarmProcessor)) {
				return;
			}
			// First, before vanilla's farm rules and any other mod's (FD appends its own): our share of the wheat then
			// doesn't depend on what the others took or on the mod load order
			List<StructureProcessor> processors = new ArrayList<>(list.list());
			processors.add(0, processor);
			((StructureProcessorListAccessor) list).vineandbarrel$setList(processors);
		});
	}

	/** Whether this is one of our farm crop processors (so a second server start in the same JVM adds none twice). */
	public static boolean isOurFarmProcessor(StructureProcessor processor) {
		return processor instanceof FarmCropProcessor;
	}
}
