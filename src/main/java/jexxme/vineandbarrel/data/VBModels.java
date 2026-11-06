package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.kettle.BrewKettleBlock;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.kettle.KettleSupport;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.resources.model.sprite.Material;
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
 * crop for barley); a vine on a rope uses FD's crop-with-rope model (a rope through a cross of vine planes).
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

	public VBModels(PackOutput output) {
		super(output, VineAndBarrel.MODID);
	}

	@Override
	protected Stream<? extends Holder<Block>> getKnownBlocks() {
		return Stream.concat(VBDataGenerators.CROP_BLOCKS.stream(),
				Stream.concat(KettleRegistry.KETTLES.values().stream(), KettleRegistry.WAXED_KETTLES.values().stream()));
	}

	@Override
	protected Stream<? extends Holder<Item>> getKnownItems() {
		return VBDataGenerators.CROP_ITEMS.stream();
	}

	@Override
	protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
		// Bushes: one cross model per age, like the sweet berry bush; also generates the flat red_grapes / hops item models
		blockModels.createCrossBlock(VBBlocks.GRAPE_BUSH.get(), BlockModelGenerators.PlantType.NOT_TINTED, ClimbingCropBlock.AGE, 0, 1, 2, 3);
		blockModels.createCrossBlock(VBBlocks.HOP_BUSH.get(), BlockModelGenerators.PlantType.NOT_TINTED, ClimbingCropBlock.AGE, 0, 1, 2, 3);
		vineOnRope(blockModels, VBBlocks.GRAPE_VINE_ON_ROPE.get(), "grape_vine_coiled_rope");
		vineOnRope(blockModels, VBBlocks.HOP_VINE_ON_ROPE.get(), "hop_vine_coiled_rope");

		// Barley like wheat (also generates the barley_seeds item model)
		blockModels.createCropBlock(VBBlocks.BARLEY.get(), CropBlock.AGE, 0, 1, 2, 3, 4, 5, 6, 7);
		itemModels.generateFlatItem(VBItems.BARLEY.get(), ModelTemplates.FLAT_ITEM);

		// Wild crops: a cross, the item shows the plant (FD)
		blockModels.createCrossBlockWithDefaultItem(VBBlocks.WILD_GRAPES.get(), BlockModelGenerators.PlantType.NOT_TINTED);
		blockModels.createCrossBlockWithDefaultItem(VBBlocks.WILD_HOPS.get(), BlockModelGenerators.PlantType.NOT_TINTED);
		blockModels.createCrossBlockWithDefaultItem(VBBlocks.WILD_BARLEY.get(), BlockModelGenerators.PlantType.NOT_TINTED);

		// Crates: FD's cube_bottom_top crate; our crate sides are plain boards, so they also close the bottom
		crate(blockModels, VBBlocks.GRAPE_CRATE.get());
		crate(blockModels, VBBlocks.HOPS_CRATE.get());
		// The bale: a hay bale (axis pillar with a horizontal variant)
		blockModels.createRotatedPillarWithHorizontalVariant(VBBlocks.BARLEY_BALE.get(), TexturedModel.COLUMN, TexturedModel.COLUMN_HORIZONTAL);

		// The Brew Kettle: every weathering stage, its waxed twin looks the same
		for (WeatherState stage : WeatherState.values()) {
			kettle(blockModels, KettleRegistry.KETTLES.get(stage).get(), KettleRegistry.WAXED_KETTLES.get(stage).get());
		}
	}

	/**
	 * A kettle's block states: support (none, tray, handle) x lid x fill level, turned by facing. The geometry lives in
	 * the hand-written {@code template_brew_kettle*} models; each stage only swaps the copper textures. Closed, open
	 * while the screen is open (the inside shows), or with a waiting brew at one of four levels (the lid stays up).
	 */
	private static void kettle(BlockModelGenerators blockModels, Block kettle, Block waxed) {
		Map<String, Identifier> models = new HashMap<>();
		for (KettleSupport support : KettleSupport.values()) {
			String supportSuffix = support == KettleSupport.NONE ? "" : "_" + support.getSerializedName();
			List<String> states = new ArrayList<>(List.of("", "_open"));
			for (int fill = 1; fill <= BrewKettleBlock.MAX_FILL; fill++) {
				states.add("_fill" + fill);
			}
			for (String state : states) {
				String suffix = supportSuffix + state;
				TextureMapping textures = new TextureMapping()
						.put(TextureSlot.SIDE, TextureMapping.getBlockTexture(kettle, "_side"))
						.put(TextureSlot.TOP, TextureMapping.getBlockTexture(kettle, "_top"))
						.put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(kettle, "_bottom"));
				List<TextureSlot> slots = new ArrayList<>(List.of(TextureSlot.SIDE, TextureSlot.TOP, TextureSlot.BOTTOM));
				if (!state.isEmpty()) {
					textures.put(TextureSlot.INSIDE, TextureMapping.getBlockTexture(kettle, "_bottom"));
					textures.put(KETTLE_BREW, state.equals("_open") ? new Material(Identifier.withDefaultNamespace("block/water_still"))
							: new Material(VineAndBarrel.id("block/brew_liquid")));
					slots.add(TextureSlot.INSIDE);
					slots.add(KETTLE_BREW);
				}
				if (support == KettleSupport.TRAY) {
					textures.put(KETTLE_TRAY_TOP, new Material(VineAndBarrel.id("block/brew_kettle_tray_top")));
					textures.put(KETTLE_TRAY_SIDE, new Material(VineAndBarrel.id("block/brew_kettle_tray_side")));
					slots.add(KETTLE_TRAY_TOP);
					slots.add(KETTLE_TRAY_SIDE);
				} else if (support == KettleSupport.HANDLE) {
					textures.put(KETTLE_HANDLE, new Material(VineAndBarrel.id("block/brew_kettle_handle")));
					slots.add(KETTLE_HANDLE);
				}
				ModelTemplate template = new ModelTemplate(Optional.of(VineAndBarrel.id("block/template_brew_kettle" + suffix)),
						Optional.of(suffix), slots.toArray(TextureSlot[]::new));
				models.put(suffix, template.create(kettle, textures, blockModels.modelOutput));
			}
		}
		for (Block block : List.of(kettle, waxed)) {
			blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
					.with(PropertyDispatch.initial(BrewKettleBlock.SUPPORT, BrewKettleBlock.OPEN, BrewKettleBlock.FILL).generate((support, open, fill) -> {
						String suffix = (support == KettleSupport.NONE ? "" : "_" + support.getSerializedName())
								+ (fill > 0 ? "_fill" + fill : open ? "_open" : "");
						return plainVariant(models.get(suffix));
					}))
					.with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
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
