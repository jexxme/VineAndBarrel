package jexxme.vineandbarrel.world.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.drink.PlacedDrink;
import jexxme.vineandbarrel.drink.PlacedDrinksBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Dresses a tavern as a village places it, so no two taverns are the same: every cask gets one of the batches its
 * template offers (another of the village's drinks; aged casks stay aged, so a tavern always has a few), half the young
 * ones drunk dry and the rest holding one or two servings, as do the aged ones; some drinks leave the tables and the rest
 * swap places, the flowers and potted plants come from the village's own, and the tavern picks one colour for its banners
 * and one for its candles, which burn down to one to four.
 * <p>
 * Each block draws from its own position, each whole-tavern choice from the tavern's origin, so the same tavern always
 * comes out the same, and two taverns hardly ever do. Everything a GameTest checks stays true: casks keep their fill
 * gauge, every table keeps at least one drink and shows what it holds.
 */
public final class TavernDressingProcessor extends StructureProcessor
{
	/** The batches a cask's template data offers: a list of {Batch, PausedAge}. */
	public static final String CHOICES = "TavernChoices";

	public static final Codec<TavernDressingProcessor> CODEC = RecordCodecBuilder.create(i -> i.group(
		BuiltInRegistries.BLOCK.byNameCodec().listOf().fieldOf("flowers").forGetter(p -> p.flowers),
		BuiltInRegistries.BLOCK.byNameCodec().listOf().fieldOf("potted").forGetter(p -> p.potted),
		DyeColor.CODEC.listOf().fieldOf("banner_colors").forGetter(p -> p.bannerColors),
		DyeColor.CODEC.listOf().fieldOf("candle_colors").forGetter(p -> p.candleColors),
		ResourceLocation.CODEC.optionalFieldOf("cellar").forGetter(p -> p.cellar),
		BlockState.CODEC.optionalFieldOf("floor").forGetter(p -> p.floor)
	).apply(i, TavernDressingProcessor::new));

	private static final long TAVERN_SALT = 0x5441_5645_524E_5631L; // "TAVERNV1"
	private static final long BLOCK_SALT = 0x4452_4553_5349_4E47L; // "DRESSING"

	private final List<Block> flowers;
	private final List<Block> potted;
	private final List<DyeColor> bannerColors;
	private final List<DyeColor> candleColors;
	private final java.util.Optional<ResourceLocation> cellar;
	private final java.util.Optional<BlockState> floor;

	public TavernDressingProcessor(List<Block> flowers, List<Block> potted, List<DyeColor> bannerColors, List<DyeColor> candleColors,
		java.util.Optional<ResourceLocation> cellar, java.util.Optional<BlockState> floor) {
		this.flowers = List.copyOf(flowers);
		this.potted = List.copyOf(potted);
		this.bannerColors = List.copyOf(bannerColors);
		this.candleColors = List.copyOf(candleColors);
		this.cellar = cellar;
		this.floor = floor;
	}

	public TavernDressingProcessor(List<Block> flowers, List<Block> potted, List<DyeColor> bannerColors, List<DyeColor> candleColors) {
		this(flowers, potted, bannerColors, candleColors, java.util.Optional.empty(), java.util.Optional.empty());
	}

	/**
	 * The same dressing for this tavern layout, which lays its barrel cellar (a template of its own) under its floor,
	 * or closes the stairwell with {@code floor} where the ground is too shallow for it.
	 */
	public TavernDressingProcessor withCellar(ResourceLocation template, BlockState floor) {
		return new TavernDressingProcessor(flowers, potted, bannerColors, candleColors, java.util.Optional.of(template), java.util.Optional.of(floor));
	}

	/**
	 * Lays the cellar under the tavern's floor as the tavern is placed, turned with it, its casks dressed like the
	 * tavern's. A village places a house inside its street's bounding box, which only grows upward, so neither the house
	 * nor a piece hung under it could reach below the street; the placement itself is clipped to the chunk's whole
	 * height, so this can. The cellar's template is solid ground round its room, so it is sealed in any terrain, and the
	 * village's terrain adaptation already treats the floor as the ground.
	 * <p>
	 * It also keeps the tavern dry. This Minecraft has no liquid settings on a pool element (1.21.1:
	 * {@code LiquidSettings.IGNORE_WATERLOGGING}), only the placement's own "keep liquids", which a pool element never
	 * turns off. The placement reads it block by block after the processors have run, so it is turned off here: a tavern
	 * at the water's edge would otherwise flood its cellar stairs.
	 */
	@Override
	public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(net.minecraft.world.level.ServerLevelAccessor level, BlockPos position, BlockPos referencePos,
		List<StructureTemplate.StructureBlockInfo> original, List<StructureTemplate.StructureBlockInfo> processed, StructurePlaceSettings settings) {
		settings.setKeepLiquids(false);
		if (cellar.isPresent()) {
			BlockPos at = position.below(VillageStructures.CELLAR_HEIGHT);
			if (at.getY() < level.getMinBuildHeight() + 1) {
				// too near the bottom of the world (a superflat world): no cellar, and the stairwell in the floor closed
				return closeStairwell(original, processed);
			}
			var template = level.getLevel().getServer().getStructureManager().get(cellar.get());
			if (template.isPresent()) {
				StructurePlaceSettings below = new StructurePlaceSettings().setMirror(settings.getMirror()).setRotation(settings.getRotation())
					.setRotationPivot(settings.getRotationPivot()).setBoundingBox(settings.getBoundingBox()).setRandom(RandomSource.create(Mth.getSeed(position)))
					// never waterlogged by an aquifer it is dug into: the room is air and its walls keep the water out
					.setKeepLiquids(false)
					// as a pool element places its template: shapes known, so torches by a chunk border stay
					.setKnownShape(true)
					.addProcessor(new TavernDressingProcessor(List.of(), List.of(), List.of(), List.of()));
				template.get().placeInWorld(level, at, at, below, RandomSource.create(Mth.getSeed(at)),
					net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
			}
		}
		return processed;
	}

	@Override
	public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition, BlockPos referencePos,
		StructureTemplate.StructureBlockInfo originalBlockInfo, StructureTemplate.StructureBlockInfo info, StructurePlaceSettings settings) {
		BlockState state = info.state();
		RandomSource here = RandomSource.create(Mth.getSeed(info.pos()) ^ BLOCK_SALT);
		if (state.is(BarrelRegistry.AGING_BARREL.get())) {
			return cask(info, here);
		}
		if (state.is(BarrelRegistry.PLACED_DRINKS.get())) {
			return drinks(info, here);
		}
		if (state.is(BlockTags.SMALL_FLOWERS) && !flowers.isEmpty() && flowers.contains(state.getBlock())) {
			return new StructureTemplate.StructureBlockInfo(info.pos(), flowers.get(here.nextInt(flowers.size())).defaultBlockState(), info.nbt());
		}
		if (state.getBlock() instanceof FlowerPotBlock pot && pot.getContent() != Blocks.AIR && potted.contains(state.getBlock())) {
			return new StructureTemplate.StructureBlockInfo(info.pos(), potted.get(here.nextInt(potted.size())).defaultBlockState(), info.nbt());
		}
		RandomSource tavern = RandomSource.create(Mth.getSeed(targetPosition) ^ TAVERN_SALT);
		if (state.getBlock() instanceof WallBannerBlock && !bannerColors.isEmpty()) {
			DyeColor color = bannerColors.get(tavern.nextInt(bannerColors.size()));
			BlockState banner = byColor(color, "wall_banner").defaultBlockState().setValue(WallBannerBlock.FACING, state.getValue(WallBannerBlock.FACING));
			return new StructureTemplate.StructureBlockInfo(info.pos(), banner, info.nbt());
		}
		if (state.getBlock() instanceof CandleBlock && !candleColors.isEmpty()) {
			tavern.nextInt(16); // not the banners' draw
			DyeColor color = candleColors.get(tavern.nextInt(candleColors.size()));
			BlockState candle = byColor(color, "candle").defaultBlockState().setValue(CandleBlock.CANDLES, 1 + here.nextInt(4))
				.setValue(CandleBlock.LIT, true).setValue(CandleBlock.WATERLOGGED, state.getValue(CandleBlock.WATERLOGGED));
			return new StructureTemplate.StructureBlockInfo(info.pos(), candle, null);
		}
		return info;
	}

	/**
	 * The tavern's floor layer without its stairwell: the top step and the openings over the steps below become floor.
	 * The floor is the template's lowest layer, and in it only the stairwell is air or a stair.
	 */
	private List<StructureTemplate.StructureBlockInfo> closeStairwell(List<StructureTemplate.StructureBlockInfo> original,
		List<StructureTemplate.StructureBlockInfo> processed) {
		if (floor.isEmpty() || original.size() != processed.size()) {
			return processed;
		}
		List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>(processed);
		for (int i = 0; i < out.size(); i++) {
			BlockState state = out.get(i).state();
			if (original.get(i).pos().getY() == 0 && (state.isAir() || state.getBlock() instanceof net.minecraft.world.level.block.StairBlock)) {
				out.set(i, new StructureTemplate.StructureBlockInfo(out.get(i).pos(), floor.get(), null));
			}
		}
		return out;
	}

	/** One of the cask's batches, its fill gauge to match. */
	private static StructureTemplate.StructureBlockInfo cask(StructureTemplate.StructureBlockInfo info, RandomSource random) {
		CompoundTag nbt = info.nbt();
		if (nbt == null) {
			return info;
		}
		ListTag choices = nbt.getList(CHOICES, Tag.TAG_COMPOUND);
		if (choices.isEmpty()) {
			return info;
		}
		CompoundTag pick = choices.getCompound(random.nextInt(choices.size()));
		CompoundTag out = nbt.copy();
		out.remove(CHOICES);
		Tag batch = pick.get("Batch");
		if (batch == null) {
			return new StructureTemplate.StructureBlockInfo(info.pos(), info.state(), out);
		}
		// a tavern that has been serving: aged casks (wax bung) hold one or two servings; of the young ones half are drunk
		// dry and the rest hold one or two, so a tavern holds some fifteen to twenty servings
		boolean aged = info.state().getValue(AgingBarrelBlock.BUNG) == jexxme.vineandbarrel.barrel.BungState.WAX;
		if (!aged && random.nextFloat() < 0.5F) {
			out.remove("Batch");
			out.remove("PausedAge");
			BlockState empty = info.state().setValue(AgingBarrelBlock.FILL, 0)
				.setValue(AgingBarrelBlock.BUNG, BarrelRegistry.AGING_BARREL.get().defaultBlockState().getValue(AgingBarrelBlock.BUNG));
			return new StructureTemplate.StructureBlockInfo(info.pos(), empty, out);
		}
		int fill = 1 + random.nextInt(2);
		Tag poured = batch.copy();
		if (poured instanceof CompoundTag batchTag) {
			batchTag.putInt("servings", fill);
		}
		out.put("Batch", poured);
		out.putLong("PausedAge", longOr(pick, "PausedAge", longOr(nbt, "PausedAge", -1L)));
		return new StructureTemplate.StructureBlockInfo(info.pos(), info.state().setValue(AgingBarrelBlock.FILL, fill), out);
	}

	private static long longOr(CompoundTag tag, String key, long fallback) {
		return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback;
	}

	/** Some drinks gone, one maybe poured again, the rest swapped about; at least one stays, and the block shows them. */
	private static StructureTemplate.StructureBlockInfo drinks(StructureTemplate.StructureBlockInfo info, RandomSource random) {
		CompoundTag nbt = info.nbt();
		if (nbt == null) {
			return info;
		}
		String[] ids = new String[PlacedDrinksBlock.SPOTS];
		for (Tag t : nbt.getList("Items", Tag.TAG_COMPOUND)) {
			if (t instanceof CompoundTag item) {
				int slot = item.contains("Slot", Tag.TAG_ANY_NUMERIC) ? item.getByte("Slot") : -1;
				if (slot >= 0 && slot < ids.length) {
					ids[slot] = item.contains("id", Tag.TAG_STRING) ? item.getString("id") : null;
				}
			}
		}
		List<String> held = new ArrayList<>();
		for (String id : ids) {
			if (id != null) {
				held.add(id);
			}
		}
		if (held.isEmpty()) {
			return info;
		}
		for (int i = 0; i < ids.length; i++) {
			long left = java.util.Arrays.stream(ids).filter(java.util.Objects::nonNull).count();
			if (ids[i] != null && left > 1 && random.nextFloat() < 0.3F) {
				ids[i] = null;
			} else if (ids[i] == null && random.nextFloat() < 0.2F) {
				ids[i] = held.get(random.nextInt(held.size()));
			}
		}
		if (random.nextBoolean()) { // mirrored across the block
			String a = ids[0], b = ids[2];
			ids[0] = ids[1];
			ids[1] = a;
			ids[2] = ids[3];
			ids[3] = b;
		}
		CompoundTag out = nbt.copy();
		ListTag items = new ListTag();
		BlockState state = info.state();
		for (int i = 0; i < ids.length; i++) {
			PlacedDrink shown = PlacedDrink.NONE;
			if (ids[i] != null) {
				CompoundTag item = new CompoundTag();
				item.putByte("Slot", (byte) i);
				item.putString("id", ids[i]);
				item.putByte("Count", (byte) 1); // an item stack as this Minecraft saves it
				items.add(item);
				var item2 = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(ids[i]));
				if (item2.isPresent()) {
					PlacedDrink of = PlacedDrink.of(new ItemStack(item2.get()));
					shown = of == null ? PlacedDrink.NONE : of;
				}
			}
			state = state.setValue(PlacedDrinksBlock.SPOT.get(i), shown);
		}
		out.put("Items", items);
		return new StructureTemplate.StructureBlockInfo(info.pos(), state, out);
	}

	/** This Minecraft has no colour sets of blocks: the vanilla block named {@code <colour>_<kind>}, e.g. red_wall_banner. */
	private static net.minecraft.world.level.block.Block byColor(net.minecraft.world.item.DyeColor color, String kind) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new ResourceLocation(color.getName() + "_" + kind));
	}

	@Override
	protected StructureProcessorType<?> getType() {
		return VillageStructures.TAVERN_DRESSING_PROCESSOR.get();
	}
}
