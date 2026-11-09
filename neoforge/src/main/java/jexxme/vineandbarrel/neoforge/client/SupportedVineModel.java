package jexxme.vineandbarrel.neoforge.client;

import jexxme.vineandbarrel.block.VineOnSupportBlock;
import jexxme.vineandbarrel.block.VineSupports;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A vine on a fence, wall, chain or bars ({@link VineOnSupportBlock}): its leaves (the block state's own model), plus the
 * block it holds drawn with that block's own model, read from the vine's block entity. So an oak fence stays an oak fence
 * under the leaves, with its arms as they are.
 */
final class SupportedVineModel extends DelegateBlockStateModel
{
	private SupportedVineModel(BlockStateModel leaves) {
		super(leaves);
	}

	/** Wraps the baked model of every vine-on-support state (NeoForge: {@code ModelEvent.ModifyBakingResult}). */
	static void wrap(ModelEvent.ModifyBakingResult event) {
		for (Map.Entry<BlockState, BlockStateModel> entry : event.getBakingResult().blockStateModels().entrySet()) {
			if (entry.getKey().getBlock() instanceof VineOnSupportBlock) {
				entry.setValue(new SupportedVineModel(entry.getValue()));
			}
		}
	}

	private static @Nullable BlockStateModel supportModel(@Nullable BlockState support) {
		return support == null ? null : Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(support);
	}

	@Override
	public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
		delegate.collectParts(level, pos, state, random, parts);
		BlockState support = VineSupports.supportAt(level, pos);
		BlockStateModel model = supportModel(support);
		if (model != null) {
			model.collectParts(level, pos, support, random, parts);
		}
	}

	@Override
	public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		BlockState support = VineSupports.supportAt(level, pos);
		BlockStateModel model = supportModel(support);
		int leaves = delegate.materialFlags(level, pos, state);
		return model == null ? leaves : leaves | model.materialFlags(level, pos, support);
	}

	/** Drawn from the block entity each time: no geometry to share between positions. */
	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return null;
	}
}
