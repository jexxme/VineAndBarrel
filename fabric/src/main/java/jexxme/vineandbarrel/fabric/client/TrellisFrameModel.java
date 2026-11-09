package jexxme.vineandbarrel.fabric.client;

import com.mojang.math.OctahedralGroup;
import jexxme.vineandbarrel.block.TrellisFrame;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A trellis, a vine on one, or a bush under one, with the row's frame added ({@link TrellisFrame}): end posts, the top
 * rail and its caps, worked out from the neighbours each time the block is drawn. The Fabric twin of NeoForge's
 * {@code TrellisFrameModel}.
 */
final class TrellisFrameModel implements BlockStateModel
{
	private static final Map<TrellisFrame.Part, ExtraModelKey<BlockStateModel>> ALONG_X = new EnumMap<>(TrellisFrame.Part.class);
	private static final Map<TrellisFrame.Part, ExtraModelKey<BlockStateModel>> ALONG_Z = new EnumMap<>(TrellisFrame.Part.class);

	static {
		for (TrellisFrame.Part part : TrellisFrame.Part.values()) {
			ALONG_X.put(part, ExtraModelKey.create(() -> part.model() + " (east-west)"));
			ALONG_Z.put(part, ExtraModelKey.create(() -> part.model() + " (north-south)"));
		}
	}

	private final BlockStateModel model;

	private TrellisFrameModel(BlockStateModel model) {
		this.model = model;
	}

	/** The frame pieces (baked for an east-west row and turned for a north-south one), and the wrapper, once at client start. */
	static void register() {
		ModelLoadingPlugin.register(context -> {
			for (TrellisFrame.Part part : TrellisFrame.Part.values()) {
				context.addModel(ALONG_X.get(part), SimpleUnbakedExtraModel.blockStateModel(part.model()));
				context.addModel(ALONG_Z.get(part), SimpleUnbakedExtraModel.blockStateModel(part.model(), BlockModelRotation.get(OctahedralGroup.BLOCK_ROT_Y_90)));
			}
			context.modifyBlockModelAfterBake().register((model, bake) -> TrellisFrame.draws(bake.state()) ? new TrellisFrameModel(model) : model);
		});
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
		model.emitQuads(emitter, level, pos, state, random, cullTest);
		Direction.Axis axis = TrellisFrame.latticeAxis(state);
		if (axis == null) {
			return;
		}
		Map<TrellisFrame.Part, ExtraModelKey<BlockStateModel>> keys = axis == Direction.Axis.X ? ALONG_X : ALONG_Z;
		for (TrellisFrame.Part part : TrellisFrame.parts(level, pos, state)) {
			BlockStateModel frame = Minecraft.getInstance().getModelManager().getModel(keys.get(part));
			if (frame != null) {
				frame.emitQuads(emitter, level, pos, state, random, cullTest);
			}
		}
	}

	/** Drawn from the neighbours each time: no geometry to share between positions. */
	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return null;
	}

	@Override
	public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return model.materialFlags(level, pos, state, random);
	}

	@Override
	public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		return model.particleMaterial(level, pos, state);
	}

	// Without a level (an item, a falling block): no frame

	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
		model.collectParts(random, output);
	}

	@Override
	public Material.Baked particleMaterial() {
		return model.particleMaterial();
	}

	@Override
	public int materialFlags() {
		return model.materialFlags();
	}
}
