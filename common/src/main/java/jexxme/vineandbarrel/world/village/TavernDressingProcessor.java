package jexxme.vineandbarrel.world.village;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.drink.PlacedDrink;
import jexxme.vineandbarrel.drink.PlacedDrinksBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
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
	/** A young cask the template keeps full: never drunk dry by the dressing (1.2: fewer casks, as much drink). */
	public static final String FULL = "TavernFull";

	public static final MapCodec<TavernDressingProcessor> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BuiltInRegistries.BLOCK.byNameCodec().listOf().fieldOf("flowers").forGetter(p -> p.flowers),
		BuiltInRegistries.BLOCK.byNameCodec().listOf().fieldOf("potted").forGetter(p -> p.potted),
		DyeColor.CODEC.listOf().fieldOf("banner_colors").forGetter(p -> p.bannerColors),
		DyeColor.CODEC.listOf().fieldOf("candle_colors").forGetter(p -> p.candleColors),
		Identifier.CODEC.optionalFieldOf("cellar").forGetter(p -> p.cellar),
		BlockState.CODEC.optionalFieldOf("floor").forGetter(p -> p.floor)
	).apply(i, TavernDressingProcessor::new));

	private static final long TAVERN_SALT = 0x5441_5645_524E_5631L; // "TAVERNV1"
	private static final long BLOCK_SALT = 0x4452_4553_5349_4E47L; // "DRESSING"

	private final List<Block> flowers;
	private final List<Block> potted;
	private final List<DyeColor> bannerColors;
	private final List<DyeColor> candleColors;
	private final java.util.Optional<Identifier> cellar;
	private final java.util.Optional<BlockState> floor;

	public TavernDressingProcessor(List<Block> flowers, List<Block> potted, List<DyeColor> bannerColors, List<DyeColor> candleColors,
		java.util.Optional<Identifier> cellar, java.util.Optional<BlockState> floor) {
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
	public TavernDressingProcessor withCellar(Identifier template, BlockState floor) {
		return new TavernDressingProcessor(flowers, potted, bannerColors, candleColors, java.util.Optional.of(template), java.util.Optional.of(floor));
	}

	/**
	 * Lays the cellar under the tavern's floor as the tavern is placed, turned with it, its casks dressed like the
	 * tavern's. A village places a house inside its street's bounding box, which only grows upward, so neither the house
	 * nor a piece hung under it could reach below the street; the placement itself is clipped to the chunk's whole
	 * height, so this can. The cellar's template is solid ground round its room, so it is sealed in any terrain, and the
	 * village's terrain adaptation already treats the floor as the ground.
	 */
	@Override
	public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(net.minecraft.world.level.ServerLevelAccessor level, BlockPos position, BlockPos referencePos,
		List<StructureTemplate.StructureBlockInfo> original, List<StructureTemplate.StructureBlockInfo> processed, StructurePlaceSettings settings) {
		if (cellar.isPresent()) {
			BlockPos at = position.below(VillageStructures.CELLAR_HEIGHT);
			if (at.getY() < level.getMinY() + 1) {
				// too near the bottom of the world (a superflat world): no cellar, and the stairwell in the floor closed
				return closeStairwell(original, processed);
			}
			var template = level.getLevel().getServer().getStructureManager().get(cellar.get());
			if (template.isPresent()) {
				StructurePlaceSettings below = new StructurePlaceSettings().setMirror(settings.getMirror()).setRotation(settings.getRotation())
					.setRotationPivot(settings.getRotationPivot()).setBoundingBox(settings.getBoundingBox()).setRandom(RandomSource.create(Mth.getSeed(position)))
					// never waterlogged by an aquifer it is dug into: the room is air and its walls keep the water out
					.setLiquidSettings(net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.IGNORE_WATERLOGGING)
					// as a pool element places its template: shapes known, so torches by a chunk border stay
					.setKnownShape(true)
					.addProcessor(new TavernDressingProcessor(List.of(), List.of(), List.of(), List.of()));
				template.get().placeInWorld(level, at, at, below, RandomSource.create(Mth.getSeed(at)),
					net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
			}
			spawnKeeper(level, processed, settings);
		}
		return processed;
	}

	/**
	 * The tavern's keeper (1.2), standing behind the bar beside his kettle, like a vanilla house brings its villagers:
	 * on the floor next to the kettle's fire, on the side with room, in the chunk being placed only (a tavern is placed
	 * chunk by chunk, so he comes exactly once).
	 */
	private static void spawnKeeper(net.minecraft.world.level.ServerLevelAccessor level, List<StructureTemplate.StructureBlockInfo> processed,
		StructurePlaceSettings settings) {
		java.util.Map<BlockPos, BlockState> blocks = new java.util.HashMap<>();
		BlockPos kettle = null;
		var kettles = TavernKeeper.kettleStates();
		for (StructureTemplate.StructureBlockInfo info : processed) {
			blocks.put(info.pos(), info.state());
			if (kettles.contains(info.state())) {
				kettle = info.pos();
			}
		}
		if (kettle == null) {
			return;
		}
		BlockPos fire = kettle.below();
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			BlockPos feet = fire.relative(side);
			BlockState at = blocks.get(feet), head = blocks.get(feet.above()), floor = blocks.get(feet.below());
			if (at == null || head == null || floor == null || !at.isAir() || !head.isAir() || !floor.isSolid()) {
				continue;
			}
			if (settings.getBoundingBox() != null && !settings.getBoundingBox().isInside(feet)) {
				return;
			}
			var keeper = new net.minecraft.world.entity.npc.villager.Villager(net.minecraft.world.entity.EntityType.VILLAGER, level.getLevel());
			keeper.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, side.getOpposite().toYRot(), 0.0F);
			keeper.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE, null);
			keeper.setVillagerData(keeper.getVillagerData().withProfession(level.registryAccess(), TavernKeeper.PROFESSION));
			// a keeper's mind: a villager's brain is made for the job he has when he is made (none), and with a jobless
			// villager's brain he drops every job site he takes the moment he takes it and its ticket stays taken, so
			// nobody can ever take that kettle again (a village's keeper got a new brain when his chunk loaded, one placed
			// by a command never did)
			keeper.refreshBrain(level.getLevel());
			// his kettle, which he takes as his job site on his first tick (it is no job site before the tavern is built)
			TavernKeeper.TAVERN_KETTLE.set(keeper, kettle);
			// a little experience: the tavern's own keeper keeps his trade should he ever lose his kettle (a villager
			// with none and no job site goes back to having no job)
			keeper.setVillagerXp(1);
			keeper.setPersistenceRequired();
			level.addFreshEntityWithPassengers(keeper);
			return;
		}
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
		if (state.getBlock() instanceof FlowerPotBlock pot && pot.getPotted() != Blocks.AIR && potted.contains(state.getBlock())) {
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

	/** One of the cask's batches, its fill gauge to match; marked as the tavern's own cask, which its keeper restocks. */
	private static StructureTemplate.StructureBlockInfo cask(StructureTemplate.StructureBlockInfo info, RandomSource random) {
		CompoundTag nbt = info.nbt() == null ? new CompoundTag() : info.nbt();
		CompoundTag out = nbt.copy();
		out.putBoolean(AgingBarrelBlockEntity.TAVERN_CASK, true);
		ListTag choices = nbt.getListOrEmpty(CHOICES);
		if (choices.isEmpty()) {
			return new StructureTemplate.StructureBlockInfo(info.pos(), info.state(), out);
		}
		CompoundTag pick = choices.getCompoundOrEmpty(random.nextInt(choices.size()));
		out.remove(CHOICES);
		Tag batch = pick.get("Batch");
		if (batch == null) {
			return new StructureTemplate.StructureBlockInfo(info.pos(), info.state(), out);
		}
		// a tavern that has been serving: aged casks (wax bung) hold one or two servings; of the young ones half are drunk
		// dry and the rest hold one or two (those the template keeps full always hold one or two), so a tavern holds some
		// fifteen to twenty servings
		boolean aged = info.state().getValue(AgingBarrelBlock.BUNG) == jexxme.vineandbarrel.barrel.BungState.WAX;
		boolean full = out.getBooleanOr(FULL, false);
		out.remove(FULL);
		if (!aged && !full && random.nextFloat() < 0.5F) {
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
		out.putLong("PausedAge", pick.getLongOr("PausedAge", nbt.getLongOr("PausedAge", -1L)));
		return new StructureTemplate.StructureBlockInfo(info.pos(), info.state().setValue(AgingBarrelBlock.FILL, fill), out);
	}

	/** Some drinks gone, one maybe poured again, the rest swapped about; at least one stays, and the block shows them. */
	private static StructureTemplate.StructureBlockInfo drinks(StructureTemplate.StructureBlockInfo info, RandomSource random) {
		CompoundTag nbt = info.nbt();
		if (nbt == null) {
			return info;
		}
		String[] ids = new String[PlacedDrinksBlock.SPOTS];
		for (Tag t : nbt.getListOrEmpty("Items")) {
			if (t instanceof CompoundTag item) {
				int slot = item.getByteOr("Slot", (byte) -1);
				if (slot >= 0 && slot < ids.length) {
					ids[slot] = item.getStringOr("id", null);
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
				item.putInt("count", 1);
				items.add(item);
				var item2 = BuiltInRegistries.ITEM.getOptional(Identifier.parse(ids[i]));
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

	/** 26.1.2 has no colour sets of blocks: the vanilla block named {@code <colour>_<kind>}, e.g. red_wall_banner. */
	private static net.minecraft.world.level.block.Block byColor(net.minecraft.world.item.DyeColor color, String kind) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(color.getName() + "_" + kind));
	}

	@Override
	protected StructureProcessorType<?> getType() {
		return VillageStructures.TAVERN_DRESSING_PROCESSOR.get();
	}
}
