package jexxme.vineandbarrel.fabric.client;

import jexxme.vineandbarrel.block.VineOnSupportBlock;
import jexxme.vineandbarrel.block.VineSupports;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/**
 * A vine on a fence, wall, chain or bars ({@link VineOnSupportBlock}): its leaves (the block state's own model), plus the
 * block it holds drawn with that block's own model, read from the vine's block entity. So an oak fence stays an oak fence
 * under the leaves, with its arms as they are. The Fabric twin of NeoForge's {@code SupportedVineModel}.
 */
final class SupportedVineModel extends ForwardingBakedModel
{
	private SupportedVineModel(BakedModel leaves) {
		this.wrapped = leaves;
	}

	/** Wraps the baked model of every vine-on-support state, once at client start. */
	static void register() {
		ModelLoadingPlugin.register(context -> {
			Map<ModelResourceLocation, BlockState> states = TrellisFrameModel.stateModels(block -> block instanceof VineOnSupportBlock);
			context.modifyModelAfterBake().register((model, bake) ->
				// A block state's own model is baked under its model id; the models it is made of under their plain ids
				model != null && bake.id() instanceof ModelResourceLocation id && states.containsKey(id) ? new SupportedVineModel(model) : model);
		});
	}

	private static @Nullable BakedModel supportModel(@Nullable BlockState support) {
		return support == null ? null : Minecraft.getInstance().getBlockRenderer().getBlockModel(support);
	}

	/** Drawn from the block entity each time. */
	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
		wrapped.emitBlockQuads(level, state, pos, random, context);
		BlockState support = VineSupports.supportAt(level, pos);
		BakedModel model = supportModel(support);
		if (model != null) {
			model.emitBlockQuads(level, support, pos, random, context);
		}
	}

	// As an item: the leaves alone (ForwardingBakedModel hands everything else to the leaves' model)
}
