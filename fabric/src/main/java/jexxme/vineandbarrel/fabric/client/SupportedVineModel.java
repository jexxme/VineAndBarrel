package jexxme.vineandbarrel.fabric.client;

import jexxme.vineandbarrel.block.VineOnSupportBlock;
import jexxme.vineandbarrel.block.VineSupports;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * A vine on a fence, wall, chain or bars ({@link VineOnSupportBlock}): its leaves (the block state's own model), plus the
 * block it holds drawn with that block's own model, read from the vine's block entity. So an oak fence stays an oak fence
 * under the leaves, with its arms as they are. The Fabric twin of NeoForge's {@code SupportedVineModel}.
 */
final class SupportedVineModel implements BlockStateModel
{
	private final BlockStateModel leaves;

	private SupportedVineModel(BlockStateModel leaves) {
		this.leaves = leaves;
	}

	/** Wraps the baked model of every vine-on-support state, once at client start. */
	static void register() {
		ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model, bake) ->
				bake.state().getBlock() instanceof VineOnSupportBlock ? new SupportedVineModel(model) : model));
	}

	private static @Nullable BlockStateModel supportModel(@Nullable BlockState support) {
		return support == null ? null : Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(support);
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
		leaves.emitQuads(emitter, level, pos, state, random, cullTest);
		BlockState support = VineSupports.supportAt(level, pos);
		BlockStateModel model = supportModel(support);
		if (model != null) {
			model.emitQuads(emitter, level, pos, support, random, cullTest);
		}
	}

	@Override
	public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		BlockState support = VineSupports.supportAt(level, pos);
		BlockStateModel model = supportModel(support);
		int flags = leaves.materialFlags(level, pos, state, random);
		return model == null ? flags : flags | model.materialFlags(level, pos, support, random);
	}

	/** Drawn from the block entity each time: no geometry to share between positions. */
	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return null;
	}

	@Override
	public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		return leaves.particleMaterial(level, pos, state);
	}

	// Without a level (an item, a falling block): the leaves alone

	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
		leaves.collectParts(random, output);
	}

	@Override
	public Material.Baked particleMaterial() {
		return leaves.particleMaterial();
	}

	@Override
	public int materialFlags() {
		return leaves.materialFlags();
	}
}
