package jexxme.vineandbarrel.neoforge.client;

import com.mojang.math.OctahedralGroup;
import jexxme.vineandbarrel.block.TrellisFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A trellis, a vine on one, or a bush under one, with the row's frame added ({@link TrellisFrame}): end posts, the top
 * rail and its caps, worked out from the neighbours each time the block is drawn.
 */
final class TrellisFrameModel extends DelegateBlockStateModel
{
	private static final Map<TrellisFrame.Part, StandaloneModelKey<BlockStateModel>> ALONG_X = new EnumMap<>(TrellisFrame.Part.class);
	private static final Map<TrellisFrame.Part, StandaloneModelKey<BlockStateModel>> ALONG_Z = new EnumMap<>(TrellisFrame.Part.class);

	static {
		for (TrellisFrame.Part part : TrellisFrame.Part.values()) {
			ALONG_X.put(part, new StandaloneModelKey<>(() -> part.model() + " (east-west)"));
			ALONG_Z.put(part, new StandaloneModelKey<>(() -> part.model() + " (north-south)"));
		}
	}

	private TrellisFrameModel(BlockStateModel model) {
		super(model);
	}

	/** The frame pieces, baked for an east-west row and turned a quarter for a north-south one. */
	static void registerParts(ModelEvent.RegisterStandalone event) {
		for (TrellisFrame.Part part : TrellisFrame.Part.values()) {
			event.register(ALONG_X.get(part), SimpleUnbakedStandaloneModel.blockStateModel(part.model()));
			event.register(ALONG_Z.get(part), SimpleUnbakedStandaloneModel.blockStateModel(part.model(), BlockModelRotation.get(OctahedralGroup.BLOCK_ROT_Y_90)));
		}
	}

	/** Wraps the baked model of every trellis, trellis vine and bush state. */
	static void wrap(ModelEvent.ModifyBakingResult event) {
		for (Map.Entry<BlockState, BlockStateModel> entry : event.getBakingResult().blockStateModels().entrySet()) {
			if (TrellisFrame.draws(entry.getKey())) {
				entry.setValue(new TrellisFrameModel(entry.getValue()));
			}
		}
	}

	@Override
	public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
		delegate.collectParts(level, pos, state, random, parts);
		Direction.Axis axis = TrellisFrame.latticeAxis(state);
		if (axis == null) {
			return;
		}
		Map<TrellisFrame.Part, StandaloneModelKey<BlockStateModel>> keys = axis == Direction.Axis.X ? ALONG_X : ALONG_Z;
		for (TrellisFrame.Part part : TrellisFrame.parts(level, pos, state)) {
			BlockStateModel model = Minecraft.getInstance().getModelManager().getStandaloneModel(keys.get(part));
			if (model != null) {
				model.collectParts(level, pos, state, random, parts);
			}
		}
	}

	/** Drawn from the neighbours each time: no geometry to share between positions. */
	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return null;
	}
}
