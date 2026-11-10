package jexxme.vineandbarrel.neoforge.client;

import jexxme.vineandbarrel.block.VineOnSupportBlock;
import jexxme.vineandbarrel.block.VineSupports;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A vine on a fence, wall, chain or bars ({@link VineOnSupportBlock}): its leaves (the block state's own model), plus the
 * block it holds drawn with that block's own model, read from the vine's block entity. So an oak fence stays an oak fence
 * under the leaves, with its arms as they are.
 */
final class SupportedVineModel extends BakedModelWrapper<BakedModel>
{
	/** The block the vine holds and what its own model wants to know about this spot, read when the chunk is drawn. */
	private record Support(BlockState state, BakedModel model, ModelData data)
	{
	}

	private static final ModelProperty<Support> SUPPORT = new ModelProperty<>();

	private SupportedVineModel(BakedModel leaves) {
		super(leaves);
	}

	/** Wraps the baked model of every vine-on-support state (NeoForge: {@code ModelEvent.ModifyBakingResult}). */
	static void wrap(ModelEvent.ModifyBakingResult event) {
		for (Block block : BuiltInRegistries.BLOCK) {
			if (block instanceof VineOnSupportBlock) {
				for (BlockState state : block.getStateDefinition().getPossibleStates()) {
					event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state), (location, model) -> new SupportedVineModel(model));
				}
			}
		}
	}

	@Override
	public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
		ModelData leaves = originalModel.getModelData(level, pos, state, modelData);
		BlockState support = VineSupports.supportAt(level, pos);
		if (support == null) {
			return leaves;
		}
		BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(support);
		return leaves.derive().with(SUPPORT, new Support(support, model, model.getModelData(level, pos, support, ModelData.EMPTY))).build();
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random, ModelData data, @Nullable RenderType renderType) {
		Support support = data.get(SUPPORT);
		if (support == null) {
			return originalModel.getQuads(state, side, random, data, renderType);
		}
		// Each model only in its own layers (a fence is solid, the leaves are cutout): the chunk asks once per layer of both
		List<BakedQuad> quads = new ArrayList<>();
		if (renderType == null || state == null || originalModel.getRenderTypes(state, random, data).contains(renderType)) {
			quads.addAll(originalModel.getQuads(state, side, random, data, renderType));
		}
		if (renderType == null || support.model().getRenderTypes(support.state(), random, support.data()).contains(renderType)) {
			quads.addAll(support.model().getQuads(support.state(), side, random, support.data(), renderType));
		}
		return quads;
	}

	@Override
	public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
		Support support = data.get(SUPPORT);
		ChunkRenderTypeSet leaves = originalModel.getRenderTypes(state, random, data);
		return support == null ? leaves : ChunkRenderTypeSet.union(leaves, support.model().getRenderTypes(support.state(), random, support.data()));
	}
}
