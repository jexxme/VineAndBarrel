package jexxme.vineandbarrel.fabric.client;

import jexxme.vineandbarrel.block.TrellisFrame;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A trellis, a vine on one, or a bush under one, with the row's frame added ({@link TrellisFrame}): end posts, the top
 * rail and its caps, worked out from the neighbours each time the block is drawn. The Fabric twin of NeoForge's
 * {@code TrellisFrameModel}.
 */
final class TrellisFrameModel extends ForwardingBakedModel
{
	private final Map<TrellisFrame.Part, BakedModel> alongX;
	private final Map<TrellisFrame.Part, BakedModel> alongZ;

	private TrellisFrameModel(BakedModel model, Map<TrellisFrame.Part, BakedModel> alongX, Map<TrellisFrame.Part, BakedModel> alongZ) {
		this.wrapped = model;
		this.alongX = alongX;
		this.alongZ = alongZ;
	}

	/** The frame pieces (baked for an east-west row and turned for a north-south one), and the wrapper, once at client start. */
	static void register() {
		ModelLoadingPlugin.register(context -> {
			// Loaded with the other models; each wrapped block model bakes them (once per reload: the baker keeps them)
			context.addModels(Arrays.stream(TrellisFrame.Part.values()).map(TrellisFrame.Part::model).toList());
			Map<ModelResourceLocation, BlockState> states = stateModels(block -> TrellisFrame.draws(block.defaultBlockState()));
			context.modifyModelAfterBake().register((model, bake) -> {
				if (model == null || bake.topLevelId() == null || !states.containsKey(bake.topLevelId())) {
					return model;
				}
				return new TrellisFrameModel(model, bakeParts(bake.baker(), BlockModelRotation.X0_Y0), bakeParts(bake.baker(), BlockModelRotation.X0_Y90));
			});
		});
	}

	private static Map<TrellisFrame.Part, BakedModel> bakeParts(ModelBaker baker, BlockModelRotation rotation) {
		Map<TrellisFrame.Part, BakedModel> parts = new EnumMap<>(TrellisFrame.Part.class);
		for (TrellisFrame.Part part : TrellisFrame.Part.values()) {
			BakedModel baked = baker.bake(part.model(), rotation);
			if (baked != null) {
				parts.put(part, baked);
			}
		}
		return parts;
	}

	/**
	 * The block state behind each block state model's id, for the blocks {@code wrapped} picks (on this Minecraft a baked
	 * model is told its id, not its state; a wrapper is chosen by the block, as NeoForge's twin does it).
	 */
	static Map<ModelResourceLocation, BlockState> stateModels(Predicate<Block> wrapped) {
		Map<ModelResourceLocation, BlockState> states = new HashMap<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (wrapped.test(block)) {
				for (BlockState state : block.getStateDefinition().getPossibleStates()) {
					states.put(BlockModelShaper.stateToModelLocation(state), state);
				}
			}
		}
		return states;
	}

	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
		wrapped.emitBlockQuads(level, state, pos, random, context);
		Direction.Axis axis = TrellisFrame.latticeAxis(state);
		if (axis == null) {
			return;
		}
		Map<TrellisFrame.Part, BakedModel> models = axis == Direction.Axis.X ? alongX : alongZ;
		for (TrellisFrame.Part part : TrellisFrame.parts(level, pos, state)) {
			@Nullable BakedModel frame = models.get(part);
			if (frame != null) {
				frame.emitBlockQuads(level, state, pos, random, context);
			}
		}
	}

	// As an item: no frame (ForwardingBakedModel hands everything else to the model)
}
