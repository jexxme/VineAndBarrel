package jexxme.vineandbarrel.fabric.client;

import com.mojang.math.OctahedralGroup;
import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.block.SupportLeaves;
import jexxme.vineandbarrel.block.VineOnSupportBlock;
import jexxme.vineandbarrel.block.VineSupports;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A vine on a fence, wall, chain or bars ({@link VineOnSupportBlock}): the block it holds, drawn with that block's own
 * model (read from the vine's block entity), and the vine's leaves as pieces that follow that block's shape
 * ({@link SupportLeaves}). The Fabric twin of NeoForge's {@code SupportedVineModel}.
 */
final class SupportedVineModel implements BlockStateModel
{
	private static final OctahedralGroup[] TURNS = {OctahedralGroup.IDENTITY, OctahedralGroup.BLOCK_ROT_Y_90, OctahedralGroup.BLOCK_ROT_Y_180,
			OctahedralGroup.BLOCK_ROT_Y_270};
	private static final Map<String, ExtraModelKey<BlockStateModel>> PIECES = new HashMap<>();

	/** The block state's own model: no leaves, its particle. */
	private final BlockStateModel leaves;

	private SupportedVineModel(BlockStateModel leaves) {
		this.leaves = leaves;
	}

	/** Every leaf piece (per vine block, age and root, in each of its quarter turns) and the wrapper, once at client start. */
	static void register() {
		ModelLoadingPlugin.register(context -> {
			for (Block block : BuiltInRegistries.BLOCK) {
				if (!(block instanceof VineOnSupportBlock)) {
					continue;
				}
				for (int age = 0; age <= ClimbingCropBlock.MAX_AGE; age++) {
					for (boolean base : new boolean[] {false, true}) {
						for (SupportLeaves.Piece piece : SupportLeaves.Piece.values()) {
							Identifier model = SupportLeaves.model(block, piece, base, age);
							for (int turn = 0; turn < piece.turns(); turn++) {
								String name = model + "#" + turn;
								ExtraModelKey<BlockStateModel> key = ExtraModelKey.create(() -> name);
								PIECES.put(name, key);
								context.addModel(key, SimpleUnbakedExtraModel.blockStateModel(model, BlockModelRotation.get(TURNS[turn])));
							}
						}
					}
				}
			}
			context.modifyBlockModelAfterBake().register((model, bake) ->
					bake.state().getBlock() instanceof VineOnSupportBlock ? new SupportedVineModel(model) : model);
		});
	}

	private static @Nullable BlockStateModel supportModel(@Nullable BlockState support) {
		return support == null ? null : Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(support);
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
		BlockState support = VineSupports.supportAt(level, pos);
		if (support == null) {
			return;
		}
		boolean base = state.getValue(VineOnSupportBlock.BASE);
		int age = state.getValue(ClimbingCropBlock.AGE);
		for (SupportLeaves.Placed placed : SupportLeaves.of(support)) {
			ExtraModelKey<BlockStateModel> key = PIECES.get(SupportLeaves.model(state.getBlock(), placed.piece(), base, age) + "#" + placed.turn());
			BlockStateModel piece = key == null ? null : Minecraft.getInstance().getModelManager().getModel(key);
			if (piece != null) {
				piece.emitQuads(emitter, level, pos, state, random, cullTest);
			}
		}
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

	// Without a level (an item, a falling block): nothing but the particle

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
