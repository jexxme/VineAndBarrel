package jexxme.vineandbarrel.neoforge.client;

import jexxme.vineandbarrel.block.TrellisFrame;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A trellis, a vine on one, or a bush under one, with the row's frame added ({@link TrellisFrame}): end posts, the top
 * rail and its caps, worked out from the neighbours each time the block's chunk is drawn.
 */
final class TrellisFrameModel extends BakedModelWrapper<BakedModel>
{
	/** The frame pieces at one spot, already the baked models of its row's direction. */
	private static final ModelProperty<List<BakedModel>> FRAME = new ModelProperty<>();
	/** The baked pieces of the last model reload (read by the chunk builders' threads). */
	private static volatile Map<TrellisFrame.Part, BakedModel> alongX = Map.of();
	private static volatile Map<TrellisFrame.Part, BakedModel> alongZ = Map.of();

	private TrellisFrameModel(BakedModel model) {
		super(model);
	}

	/** The frame pieces are models no block state names: loaded (with their parents) as standalone models. */
	static void registerParts(ModelEvent.RegisterAdditional event) {
		for (TrellisFrame.Part part : TrellisFrame.Part.values()) {
			event.register(ModelResourceLocation.standalone(part.model()));
		}
	}

	/**
	 * Wraps the baked model of every trellis, trellis vine and bush state, and keeps the frame pieces: baked for an
	 * east-west row, and baked again turned a quarter for a north-south one (as a block state's {@code "y": 90} does).
	 */
	static void wrap(ModelEvent.ModifyBakingResult event) {
		Map<TrellisFrame.Part, BakedModel> x = new EnumMap<>(TrellisFrame.Part.class);
		Map<TrellisFrame.Part, BakedModel> z = new EnumMap<>(TrellisFrame.Part.class);
		ModelBakery bakery = event.getModelBakery();
		for (TrellisFrame.Part part : TrellisFrame.Part.values()) {
			ModelResourceLocation location = ModelResourceLocation.standalone(part.model());
			BakedModel baked = event.getModels().get(location);
			if (baked != null) {
				x.put(part, baked);
			}
			ModelBaker baker = bakery.new ModelBakerImpl((model, material) -> event.getTextureGetter().apply(material), location);
			BakedModel turned = baker.bake(part.model(), BlockModelRotation.X0_Y90, event.getTextureGetter());
			if (turned != null) {
				z.put(part, turned);
			}
		}
		alongX = x;
		alongZ = z;
		for (Block block : BuiltInRegistries.BLOCK) {
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				if (TrellisFrame.draws(state)) {
					event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state), (location, model) -> new TrellisFrameModel(model));
				}
			}
		}
	}

	@Override
	public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
		ModelData own = originalModel.getModelData(level, pos, state, modelData);
		Direction.Axis axis = TrellisFrame.latticeAxis(state);
		if (axis == null) {
			return own;
		}
		Map<TrellisFrame.Part, BakedModel> models = axis == Direction.Axis.X ? alongX : alongZ;
		List<BakedModel> frame = new ArrayList<>(5);
		for (TrellisFrame.Part part : TrellisFrame.parts(level, pos, state)) {
			BakedModel model = models.get(part);
			if (model != null) {
				frame.add(model);
			}
		}
		return frame.isEmpty() ? own : own.derive().with(FRAME, frame).build();
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random, ModelData data, @Nullable RenderType renderType) {
		List<BakedQuad> own = originalModel.getQuads(state, side, random, data, renderType);
		List<BakedModel> frame = data.get(FRAME);
		if (frame == null) {
			return own;
		}
		// The frame is drawn in the block's own layer (cutout, like the lattice)
		List<BakedQuad> quads = new ArrayList<>(own);
		for (BakedModel model : frame) {
			quads.addAll(model.getQuads(state, side, random, ModelData.EMPTY, renderType));
		}
		return quads;
	}
}
