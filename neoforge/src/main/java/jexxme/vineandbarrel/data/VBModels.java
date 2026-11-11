package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.block.ClimbingBushBlock;
import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.block.TrellisBlock;
import jexxme.vineandbarrel.block.VineOnSupportBlock;
import jexxme.vineandbarrel.block.VineOnTrellisBlock;
import jexxme.vineandbarrel.cauldron.BaseCauldronBlock;
import jexxme.vineandbarrel.cauldron.VBCauldrons;
import jexxme.vineandbarrel.kettle.BrewKettleBlock;
import jexxme.vineandbarrel.kettle.BrewKettlePartBlock;
import jexxme.vineandbarrel.kettle.KettlePart;
import jexxme.vineandbarrel.kettle.KettleChimneyBlock;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.kettle.KettleSupport;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static net.minecraft.client.data.models.BlockModelGenerators.plainVariant;

/**
 * Block states, block models and item models. Plants use vanilla's templates (cross for the bushes and wild crops,
 * crop for barley); a vine on a rope uses FD's crop-with-rope model (a rope through a cross of vine planes). The Trellis
 * is a hand-written model (a lattice panel), turned for its axis; a vine on a trellis is that model with a leafy plane in
 * front of and behind the lattice ({@code template_vine_on_trellis}), and the bush under it the same planes without the
 * lattice, so the plant reads as one.
 */
public class VBModels extends ModelProvider
{
	private static final TextureSlot ROPE_SIDE = TextureSlot.create("rope_side");
	private static final TextureSlot ROPE_TOP = TextureSlot.create("rope_top");
	private static final TextureSlot KETTLE_BREW = TextureSlot.create("brew");
	private static final TextureSlot KETTLE_TRAY_TOP = TextureSlot.create("tray_top");
	private static final TextureSlot KETTLE_TRAY_SIDE = TextureSlot.create("tray_side");
	private static final TextureSlot KETTLE_HANDLE = TextureSlot.create("handle");
	private static final ModelTemplate CROP_WITH_ROPE = new ModelTemplate(Optional.of(VineAndBarrel.id("block/template_crop_with_rope")),
			Optional.empty(), TextureSlot.CROP, ROPE_SIDE, ROPE_TOP);
	private static final TextureSlot TRELLIS = TextureSlot.create("trellis");
	private static final TextureSlot VINE = TextureSlot.create("vine");
	private static final ModelTemplate VINE_ON_TRELLIS = new ModelTemplate(Optional.of(VineAndBarrel.id("block/template_vine_on_trellis")),
			Optional.empty(), TRELLIS, VINE);
	private static final ModelTemplate BUSH_ON_TRELLIS = new ModelTemplate(Optional.of(VineAndBarrel.id("block/template_bush_on_trellis")),
			Optional.empty(), VINE);
	private static final ModelTemplate BUSH_ON_ROPE = new ModelTemplate(Optional.of(VineAndBarrel.id("block/template_bush_on_rope")),
			Optional.empty(), TextureSlot.CROP);
	private static final ModelTemplate VINE_ON_THIN_SUPPORT = new ModelTemplate(Optional.of(VineAndBarrel.id("block/template_vine_on_thin_support")),
			Optional.empty(), VINE);
	private static final ModelTemplate VINE_ON_WIDE_SUPPORT = new ModelTemplate(Optional.of(VineAndBarrel.id("block/template_vine_on_wide_support")),
			Optional.empty(), VINE);
	private static final ModelTemplate VINE_WOUND = new ModelTemplate(Optional.of(VineAndBarrel.id("block/template_vine_wound")),
			Optional.empty(), TextureSlot.CROP);
	/** Vanilla's cauldron with its content one level (3 px) below vanilla's lowest, our cauldrons' first serving. */
	private static final ModelTemplate CAULDRON_LEVEL0 = new ModelTemplate(Optional.of(VineAndBarrel.id("block/template_cauldron_level0")),
			Optional.empty(), TextureSlot.CONTENT, TextureSlot.INSIDE, TextureSlot.PARTICLE, TextureSlot.TOP, TextureSlot.BOTTOM, TextureSlot.SIDE);

	public VBModels(PackOutput output) {
		super(output, VineAndBarrel.MODID);
	}

	@Override
	protected Stream<? extends Holder<Block>> getKnownBlocks() {
		return Stream.concat(Stream.concat(VBDataGenerators.CROP_BLOCKS.stream(),
				Stream.of(KettleRegistry.KETTLES, KettleRegistry.WAXED_KETTLES, KettleRegistry.PARTS, KettleRegistry.WAXED_PARTS, KettleRegistry.CHIMNEYS,
						KettleRegistry.WAXED_CHIMNEYS).flatMap(map -> map.values().stream())),
				VBDataGenerators.CAULDRON_BLOCKS.stream())
			.map(block -> block.asHolder());
	}

	@Override
	protected Stream<? extends Holder<Item>> getKnownItems() {
		return VBDataGenerators.CROP_ITEMS.stream().map(item -> item.asHolder());
	}

	@Override
	protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
		// Bushes: one cross model per age, like the sweet berry bush; under a trellis or a rope the base of that vine
		bush(blockModels, VBBlocks.GRAPE_BUSH.get());
		bush(blockModels, VBBlocks.WHITE_GRAPE_BUSH.get());
		bush(blockModels, VBBlocks.HOP_BUSH.get());
		// the produce that plants them: flat icons
		itemModels.generateFlatItem(VBItems.RED_GRAPES.get(), ModelTemplates.FLAT_ITEM);
		itemModels.generateFlatItem(VBItems.WHITE_GRAPES.get(), ModelTemplates.FLAT_ITEM);
		itemModels.generateFlatItem(VBItems.HOPS.get(), ModelTemplates.FLAT_ITEM);
		vineOnRope(blockModels, VBBlocks.GRAPE_VINE_ON_ROPE.get(), "grape_vine_coiled_rope");
		// White grapes climb on the same woody vine as red ones, so they share its coil around the rope
		vineOnRope(blockModels, VBBlocks.WHITE_GRAPE_VINE_ON_ROPE.get(), "grape_vine_coiled_rope");
		vineOnRope(blockModels, VBBlocks.HOP_VINE_ON_ROPE.get(), "hop_vine_coiled_rope");

		// The Trellis: its hand-written model, the panel turned to its axis; the item is a flat icon like the ladder's
		Identifier trellis = VineAndBarrel.id("block/trellis");
		blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(VBBlocks.TRELLIS.get()).with(PropertyDispatch.initial(TrellisBlock.AXIS)
				.select(Direction.Axis.X, plainVariant(trellis))
				.select(Direction.Axis.Z, plainVariant(trellis).with(BlockModelGenerators.Y_ROT_90))));
		itemModels.generateFlatItem(VBItems.TRELLIS.get(), ModelTemplates.FLAT_ITEM);
		vineOnTrellis(blockModels, VBBlocks.GRAPE_VINE_ON_TRELLIS.get());
		vineOnTrellis(blockModels, VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS.get());
		vineOnTrellis(blockModels, VBBlocks.HOP_VINE_ON_TRELLIS.get());
		// Vines on fences, walls, chains and bars: only the leaves (the block they hold is drawn by the loader's model wrapper)
		vineOnSupport(blockModels, VBBlocks.GRAPE_VINE_ON_SUPPORT.get(), "grape");
		vineOnSupport(blockModels, VBBlocks.WHITE_GRAPE_VINE_ON_SUPPORT.get(), "white_grape");
		vineOnSupport(blockModels, VBBlocks.HOP_VINE_ON_SUPPORT.get(), "hop");

		// Barley like wheat (also generates the barley_seeds item model)
		blockModels.createCropBlock(VBBlocks.BARLEY.get(), CropBlock.AGE, 0, 1, 2, 3, 4, 5, 6, 7);
		itemModels.generateFlatItem(VBItems.BARLEY.get(), ModelTemplates.FLAT_ITEM);

		// Wild crops: a cross, the item shows the plant (FD)
		blockModels.createCrossBlockWithDefaultItem(VBBlocks.WILD_GRAPES.get(), BlockModelGenerators.PlantType.NOT_TINTED);
		blockModels.createCrossBlockWithDefaultItem(VBBlocks.WILD_WHITE_GRAPES.get(), BlockModelGenerators.PlantType.NOT_TINTED);
		blockModels.createCrossBlockWithDefaultItem(VBBlocks.WILD_HOPS.get(), BlockModelGenerators.PlantType.NOT_TINTED);
		blockModels.createCrossBlockWithDefaultItem(VBBlocks.WILD_BARLEY.get(), BlockModelGenerators.PlantType.NOT_TINTED);

		// Crates: FD's cube_bottom_top crate; our crate sides are plain boards, so they also close the bottom
		crate(blockModels, VBBlocks.GRAPE_CRATE.get());
		crate(blockModels, VBBlocks.WHITE_GRAPE_CRATE.get());
		crate(blockModels, VBBlocks.HOPS_CRATE.get());
		// The bale: a hay bale (axis pillar with a horizontal variant)
		blockModels.createRotatedPillarWithHorizontalVariant(VBBlocks.BARLEY_BALE.get(), TexturedModel.COLUMN, TexturedModel.COLUMN_HORIZONTAL);

		// The Brew Kettle: every weathering stage, its waxed twin looks the same; big kettles are made of its parts
		for (WeatherState stage : WeatherState.values()) {
			kettle(blockModels, stage, KettleRegistry.KETTLES.get(stage).get(), KettleRegistry.WAXED_KETTLES.get(stage).get());
			kettleParts(blockModels, stage, KettleRegistry.PARTS.get(stage).get(), KettleRegistry.WAXED_PARTS.get(stage).get());
			kettleChimney(blockModels, stage, KettleRegistry.CHIMNEYS.get(stage).get(), KettleRegistry.WAXED_CHIMNEYS.get(stage).get());
		}

		cauldrons(blockModels);
	}

	/**
	 * Our bases in a cauldron: vanilla's cauldron models with the kettle's brew liquid as the content, tinted per base
	 * ({@code CauldronClient}). Four levels share four models: 6 px (our own template), then vanilla's 9, 12 and 15.
	 */
	private static void cauldrons(BlockModelGenerators blockModels) {
		TextureMapping textures = TextureMapping.cauldron(new Material(VineAndBarrel.id("block/brew_liquid")));
		List<ModelTemplate> templates = List.of(CAULDRON_LEVEL0, ModelTemplates.CAULDRON_LEVEL1, ModelTemplates.CAULDRON_LEVEL2, ModelTemplates.CAULDRON_FULL);
		List<Identifier> models = new ArrayList<>();
		for (int level = BaseCauldronBlock.MIN_LEVEL; level <= BaseCauldronBlock.MAX_LEVEL; level++) {
			models.add(templates.get(level - 1).create(VineAndBarrel.id("block/base_cauldron_level" + level), textures, blockModels.modelOutput));
		}
		for (BaseCauldronBlock cauldron : VBCauldrons.all()) {
			blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(cauldron)
					.with(PropertyDispatch.initial(BaseCauldronBlock.LEVEL).generate(level -> plainVariant(models.get(level - 1)))));
		}
	}

	private static final TextureSlot K_PLATE = TextureSlot.create("plate");
	private static final TextureSlot K_CUT = TextureSlot.create("cut");
	private static final TextureSlot K_BAND = TextureSlot.create("band");
	private static final TextureSlot K_TRIM = TextureSlot.create("trim");
	private static final TextureSlot K_PIPE = TextureSlot.create("pipe");
	private static final TextureSlot K_DARK = TextureSlot.create("dark");
	private static final TextureSlot K_IRON = TextureSlot.create("iron");
	private static final TextureSlot K_TAP = TextureSlot.create("tap");
	private static final TextureSlot K_GLASS = TextureSlot.create("glass");
	private static final TextureSlot[] KETTLE_SLOTS = {K_PLATE, K_CUT, K_BAND, K_TRIM, K_PIPE, K_DARK, K_IRON, K_TAP, K_GLASS, KETTLE_BREW,
			KETTLE_TRAY_TOP, KETTLE_TRAY_SIDE, KETTLE_HANDLE};

	/**
	 * A kettle's textures for one copper stage (WP4 redesign): its body and dome are vanilla's copper block and cut copper of
	 * that stage, its bands, trim and chimney our own art in the stage's colours; the tap and sight glass the barrel's. {@code brew} is what an open
	 * kettle shows inside (tinted with the brew's colour).
	 */
	private static TextureMapping kettleTextures(WeatherState stage, Material brew) {
		String prefix = stage == WeatherState.UNAFFECTED ? "" : stage.getSerializedName() + "_";
		return new TextureMapping()
				.put(K_PLATE, new Material(Identifier.withDefaultNamespace("block/" + (stage == WeatherState.UNAFFECTED ? "copper_block" : prefix + "copper"))))
				.put(K_CUT, new Material(Identifier.withDefaultNamespace("block/" + prefix + "cut_copper")))
				.put(K_BAND, new Material(VineAndBarrel.id("block/" + prefix + "brew_kettle_band")))
				.put(K_TRIM, new Material(VineAndBarrel.id("block/" + prefix + "brew_kettle_trim")))
				.put(K_PIPE, new Material(VineAndBarrel.id("block/" + prefix + "brew_kettle_pipe")))
				.put(K_DARK, new Material(VineAndBarrel.id("block/brew_kettle_dark")))
				.put(K_IRON, new Material(Identifier.withDefaultNamespace("block/iron_block")))
				.put(K_TAP, new Material(VineAndBarrel.id("block/aging_barrel_tap")))
				.put(K_GLASS, new Material(VineAndBarrel.id("block/placed_drink_glass")))
				.put(KETTLE_BREW, brew)
				.put(KETTLE_TRAY_TOP, new Material(VineAndBarrel.id("block/brew_kettle_tray_top")))
				.put(KETTLE_TRAY_SIDE, new Material(VineAndBarrel.id("block/brew_kettle_tray_side")))
				.put(KETTLE_HANDLE, new Material(VineAndBarrel.id("block/brew_kettle_handle")));
	}

	private static final Material WATER = new Material(Identifier.withDefaultNamespace("block/water_still"));
	private static final Material BREW_LIQUID = new Material(VineAndBarrel.id("block/brew_liquid"));

	/**
	 * A single kettle's block states: support (none, tray, handle) x lid x fill level x extended chimney, turned by facing.
	 * The geometry lives in the generated {@code template_brew_kettle*} models; each stage only swaps textures. Closed, open
	 * while the screen is open (water inside), or with a waiting brew at one of four levels (the lid stays up); with its
	 * chimney extended the lid stays shut and the chimney ends at the block's top for the pieces above.
	 */
	private static void kettle(BlockModelGenerators blockModels, WeatherState stage, Block kettle, Block waxed) {
		Map<String, Identifier> models = new HashMap<>();
		for (KettleSupport support : KettleSupport.values()) {
			String supportSuffix = support == KettleSupport.NONE ? "" : "_" + support.getSerializedName();
			for (boolean open : List.of(false, true)) {
				for (int fill = 0; fill <= BrewKettleBlock.MAX_FILL; fill++) {
					for (boolean extended : List.of(false, true)) {
						if (extended && (support == KettleSupport.HANDLE || open)) {
							continue;
						}
						String suffix = supportSuffix + singleSuffix(open, fill, extended);
						ModelTemplate template = new ModelTemplate(Optional.of(VineAndBarrel.id("block/template_brew_kettle" + suffix)), Optional.of(suffix), KETTLE_SLOTS);
						// an open kettle with no brew shows water inside
						models.put(suffix, template.create(kettle, kettleTextures(stage, open && fill == 0 ? WATER : BREW_LIQUID), blockModels.modelOutput));
					}
				}
			}
		}
		for (Block block : List.of(kettle, waxed)) {
			blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
					.with(PropertyDispatch.initial(BrewKettleBlock.SUPPORT, BrewKettleBlock.OPEN, BrewKettleBlock.FILL, BrewKettleBlock.EXTENDED).generate((support, open, fill, extended) -> {
						String supportSuffix = support == KettleSupport.NONE ? "" : "_" + support.getSerializedName();
						boolean ext = extended && support != KettleSupport.HANDLE;
						return plainVariant(models.get(supportSuffix + singleSuffix(open && !ext, fill, ext)));
					}))
					.with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
		}
	}

	/**
	 * The blocks of the Big and the Great Kettle: one generated template per block ({@code template_brew_kettle_part_<shape>_<xyz>},
	 * built facing north), with tray variants for the bottom layer, the sight glass's levels for its block and the extended
	 * chimney for the chimney block; turned by facing like a single kettle.
	 */
	private static void kettleParts(BlockModelGenerators blockModels, WeatherState stage, Block part, Block waxed) {
		Map<String, Identifier> models = new HashMap<>();
		for (KettlePart p : KettlePart.all()) {
			for (String suffix : partSuffixes(p)) {
				ModelTemplate template = new ModelTemplate(Optional.of(VineAndBarrel.id("block/template_brew_kettle_part" + suffix)), Optional.of(suffix), KETTLE_SLOTS);
				models.put(suffix, template.create(part, kettleTextures(stage, BREW_LIQUID), blockModels.modelOutput));
			}
		}
		for (Block block : List.of(part, waxed)) {
			blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
					.with(PropertyDispatch.initial(BrewKettlePartBlock.PART, BrewKettlePartBlock.TRAY, BrewKettlePartBlock.LEVEL, BrewKettlePartBlock.EXTENDED)
							.generate((id, tray, level, extended) -> plainVariant(models.get(partSuffix(KettlePart.byId(id), tray, level, extended)))))
					.with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
		}
	}

	/** "_open_fill2", "_fill3_extended", as the model generator names a single kettle's variants. */
	private static String singleSuffix(boolean open, int fill, boolean extended) {
		return (open ? "_open" : "") + (fill > 0 ? "_fill" + fill : "") + (extended ? "_extended" : "");
	}

	/** "_2x2x2_100_tray_level3", as the model generator names a part's variants. */
	private static String partSuffix(KettlePart p, boolean tray, int level, boolean extended) {
		return "_" + p.shape().id() + "_" + p.x() + p.y() + p.z() + (tray && p.isBottom() ? "_tray" : "") + (p.isGauge() ? "_level" + level : "")
				+ (extended && p.isChimney() ? "_extended" : "");
	}

	private static List<String> partSuffixes(KettlePart p) {
		List<String> list = new ArrayList<>();
		for (boolean tray : p.isBottom() ? List.of(false, true) : List.of(false)) {
			for (int level = 0; level <= (p.isGauge() ? BrewKettlePartBlock.MAX_LEVEL : 0); level++) {
				for (boolean extended : p.isChimney() ? List.of(false, true) : List.of(false)) {
					list.add(partSuffix(p, tray, level, extended));
				}
			}
		}
		return list;
	}

	/**
	 * A chimney's extension pieces: per kettle size a full piece and the top piece with the cap (each also with the old
	 * flashing plate), plus the pipe drawn down through the roof block under a piece ({@code through}) and up into the
	 * lower block of a two-block roof ({@code rising}), turned by facing. A multipart, so the pipe parts add to any piece.
	 */
	private static void kettleChimney(BlockModelGenerators blockModels, WeatherState stage, Block chimney, Block waxed) {
		Map<String, Identifier> models = new HashMap<>();
		for (int size = 1; size <= 3; size++) {
			List<String> suffixes = new ArrayList<>(List.of("_" + size + "_through", "_" + size + "_rising"));
			for (String top : List.of("", "_top")) {
				for (String flashing : List.of("", "_flashing")) {
					suffixes.add("_" + size + top + flashing);
				}
			}
			for (String suffix : suffixes) {
				ModelTemplate template = new ModelTemplate(Optional.of(VineAndBarrel.id("block/template_brew_kettle_chimney" + suffix)), Optional.of(suffix), KETTLE_SLOTS);
				models.put(suffix, template.create(chimney, kettleTextures(stage, BREW_LIQUID), blockModels.modelOutput));
			}
		}
		Map<Direction, VariantMutator> turns = Map.of(Direction.NORTH, BlockModelGenerators.NOP, Direction.EAST, BlockModelGenerators.Y_ROT_90,
				Direction.SOUTH, BlockModelGenerators.Y_ROT_180, Direction.WEST, BlockModelGenerators.Y_ROT_270);
		for (Block block : List.of(chimney, waxed)) {
			MultiPartGenerator parts = MultiPartGenerator.multiPart(block);
			for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
				VariantMutator turn = turns.get(facing);
				for (int size = 1; size <= 3; size++) {
					for (boolean top : List.of(false, true)) {
						for (boolean flashing : List.of(false, true)) {
							parts.with(BlockModelGenerators.condition().term(KettleChimneyBlock.FACING, facing).term(KettleChimneyBlock.SIZE, size)
									.term(KettleChimneyBlock.TOP, top).term(KettleChimneyBlock.FLASHING, flashing),
									plainVariant(models.get("_" + size + (top ? "_top" : "") + (flashing ? "_flashing" : ""))).with(turn));
						}
					}
					parts.with(BlockModelGenerators.condition().term(KettleChimneyBlock.FACING, facing).term(KettleChimneyBlock.SIZE, size)
							.term(KettleChimneyBlock.THROUGH, 1, 2), plainVariant(models.get("_" + size + "_through")).with(turn));
					parts.with(BlockModelGenerators.condition().term(KettleChimneyBlock.FACING, facing).term(KettleChimneyBlock.SIZE, size)
							.term(KettleChimneyBlock.RISING, true), plainVariant(models.get("_" + size + "_rising")).with(turn));
				}
			}
			blockModels.blockStateOutput.accept(parts);
		}
	}

	private static void crate(BlockModelGenerators blockModels, Block block) {
		Material side = TextureMapping.getBlockTexture(block, "_side");
		TextureMapping textures = new TextureMapping()
				.put(TextureSlot.SIDE, side)
				.put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"))
				.put(TextureSlot.BOTTOM, side);
		blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block,
				plainVariant(ModelTemplates.CUBE_BOTTOM_TOP.create(block, textures, blockModels.modelOutput))));
	}

	/**
	 * A bush's models per age: the round bush (vanilla's cross) on its own, and the base of its vine under a trellis (the
	 * vine's two planes, turned for the trellis's axis) or under a rope (the vine on a rope's cross), each with its own
	 * texture that carries the vine's leaves and stem down to a trunk.
	 */
	private static void bush(BlockModelGenerators blockModels, Block block) {
		Material trellis = new Material(VineAndBarrel.id("block/trellis"));
		Map<Integer, Identifier> round = new HashMap<>();
		Map<Integer, Identifier> onTrellis = new HashMap<>();
		Map<Integer, Identifier> inTrellis = new HashMap<>();
		Map<Integer, Identifier> onRope = new HashMap<>();
		for (int age = 0; age <= ClimbingCropBlock.MAX_AGE; age++) {
			String stage = "_stage" + age;
			round.put(age, ModelTemplates.CROSS.createWithSuffix(block, stage, TextureMapping.cross(TextureMapping.getBlockTexture(block, stage)),
					blockModels.modelOutput));
			onTrellis.put(age, BUSH_ON_TRELLIS.createWithSuffix(block, "_on_trellis" + stage,
					new TextureMapping().put(VINE, TextureMapping.getBlockTexture(block, "_on_trellis" + stage)), blockModels.modelOutput));
			// a trellis in the bush's own block: the same base of the vine, with the lattice through it
			inTrellis.put(age, VINE_ON_TRELLIS.createWithSuffix(block, "_in_trellis" + stage,
					new TextureMapping().put(TRELLIS, trellis).put(VINE, TextureMapping.getBlockTexture(block, "_on_trellis" + stage)), blockModels.modelOutput));
			onRope.put(age, BUSH_ON_ROPE.createWithSuffix(block, "_on_rope" + stage,
					new TextureMapping().put(TextureSlot.CROP, TextureMapping.getBlockTexture(block, "_on_rope" + stage)), blockModels.modelOutput));
		}
		blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
				.with(PropertyDispatch.initial(ClimbingCropBlock.AGE, ClimbingBushBlock.SUPPORT, ClimbingBushBlock.TRELLIS)
				.generate((age, support, inside) -> switch (inside) {
					case X -> plainVariant(inTrellis.get(age));
					case Z -> plainVariant(inTrellis.get(age)).with(BlockModelGenerators.Y_ROT_90);
					case NONE -> switch (support) {
						case NONE -> plainVariant(round.get(age));
						case TRELLIS_X, FACES_X -> plainVariant(onTrellis.get(age));
						case TRELLIS_Z, FACES_Z -> plainVariant(onTrellis.get(age)).with(BlockModelGenerators.Y_ROT_90);
						case ROPE -> plainVariant(onRope.get(age));
					};
				})));
	}

	/** One model per age (the lattice and its leafy planes), turned for the trellis's axis. */
	private static void vineOnTrellis(BlockModelGenerators blockModels, Block block) {
		Material trellis = new Material(VineAndBarrel.id("block/trellis"));
		Map<Integer, Identifier> models = new HashMap<>();
		for (int age = 0; age <= ClimbingCropBlock.MAX_AGE; age++) {
			String suffix = "_stage" + age;
			TextureMapping textures = new TextureMapping()
					.put(TRELLIS, trellis)
					.put(VINE, TextureMapping.getBlockTexture(block, suffix));
			models.put(age, VINE_ON_TRELLIS.createWithSuffix(block, suffix, textures, blockModels.modelOutput));
		}
		blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(PropertyDispatch.initial(ClimbingCropBlock.AGE, VineOnTrellisBlock.AXIS)
				.generate((age, axis) -> axis == Direction.Axis.X ? plainVariant(models.get(age))
						: plainVariant(models.get(age)).with(BlockModelGenerators.Y_ROT_90))));
	}

	/**
	 * A vine on a fence, wall, chain or bars: only its leaves, per age, as a vine or as the plant's root (planted into the
	 * support): spread across both faces of a fence or bars (thin) or a wall (wide), turned for the row, with the trellis
	 * vine's art (the trellis bush's art at the root), or wound round a chain or a lone post with the rope vine's art (the
	 * rope bush's at the root). The block it holds is drawn by the loaders' model wrappers.
	 */
	private static void vineOnSupport(BlockModelGenerators blockModels, Block block, String crop) {
		Map<String, Identifier> models = new HashMap<>();
		for (int age = 0; age <= ClimbingCropBlock.MAX_AGE; age++) {
			String stage = "_stage" + age;
			for (boolean base : new boolean[] {false, true}) {
				String root = base ? "_base" : "";
				Material faces = new Material(VineAndBarrel.id("block/" + crop + (base ? "_bush_on_trellis" : "_vine_on_trellis") + stage));
				Material wound = new Material(VineAndBarrel.id("block/" + crop + (base ? "_bush_on_rope" : "_vine_on_rope") + stage));
				models.put("thin" + root + age, VINE_ON_THIN_SUPPORT.createWithSuffix(block, "_thin" + root + stage,
						new TextureMapping().put(VINE, faces), blockModels.modelOutput));
				models.put("wide" + root + age, VINE_ON_WIDE_SUPPORT.createWithSuffix(block, "_wide" + root + stage,
						new TextureMapping().put(VINE, faces), blockModels.modelOutput));
				models.put("wound" + root + age, (base ? BUSH_ON_ROPE : VINE_WOUND).createWithSuffix(block, "_wound" + root + stage,
						new TextureMapping().put(TextureSlot.CROP, wound), blockModels.modelOutput));
			}
		}
		blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
				.with(PropertyDispatch.initial(ClimbingCropBlock.AGE, VineOnSupportBlock.BASE, VineOnSupportBlock.LOOK).generate((age, base, look) -> {
					String root = base ? "_base" : "";
					return switch (look) {
						case CROSS -> plainVariant(models.get("wound" + root + age));
						case THIN_X -> plainVariant(models.get("thin" + root + age));
						case THIN_Z -> plainVariant(models.get("thin" + root + age)).with(BlockModelGenerators.Y_ROT_90);
						case WIDE_X -> plainVariant(models.get("wide" + root + age));
						case WIDE_Z -> plainVariant(models.get("wide" + root + age)).with(BlockModelGenerators.Y_ROT_90);
					};
				})));
	}

	private static void vineOnRope(BlockModelGenerators blockModels, Block block, String coiledRope) {
		Material ropeSide = new Material(VineAndBarrel.id("block/" + coiledRope));
		Material ropeTop = new Material(VineAndBarrel.id("block/rope_top"));
		blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(PropertyDispatch.initial(ClimbingCropBlock.AGE).generate(age -> {
			String suffix = "_stage" + age;
			TextureMapping textures = new TextureMapping()
					.put(TextureSlot.CROP, TextureMapping.getBlockTexture(block, suffix))
					.put(ROPE_SIDE, ropeSide)
					.put(ROPE_TOP, ropeTop);
			return plainVariant(CROP_WITH_ROPE.createWithSuffix(block, suffix, textures, blockModels.modelOutput));
		})));
	}
}
