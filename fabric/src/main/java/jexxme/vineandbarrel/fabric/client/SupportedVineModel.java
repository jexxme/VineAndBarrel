package jexxme.vineandbarrel.fabric.client;

import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.block.SupportLeaves;
import jexxme.vineandbarrel.block.VineOnSupportBlock;
import jexxme.vineandbarrel.block.VineSupports;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A vine on a fence, wall, chain or bars ({@link VineOnSupportBlock}): the block it holds, drawn with that block's own
 * model (read from the vine's block entity), and the vine's leaves as pieces that follow that block's shape
 * ({@link SupportLeaves}). The Fabric twin of NeoForge's {@code SupportedVineModel}.
 */
final class SupportedVineModel extends ForwardingBakedModel
{
	/** A piece's quarter turns, as a block state's {@code "y"} turns a model. */
	private static final BlockModelRotation[] TURNS = {BlockModelRotation.X0_Y0, BlockModelRotation.X0_Y90, BlockModelRotation.X0_Y180,
			BlockModelRotation.X0_Y270};

	/** The leaf pieces of this state's vine, age and root, by piece and turn. */
	private final Map<SupportLeaves.Placed, BakedModel> pieces;

	/** {@code particle}: the block state's own model, no leaves, its particle. */
	private SupportedVineModel(BakedModel particle, Map<SupportLeaves.Placed, BakedModel> pieces) {
		this.wrapped = particle;
		this.pieces = pieces;
	}

	/** Every leaf piece (per vine block, age and root) and the wrapper, once at client start. */
	static void register() {
		ModelLoadingPlugin.register(context -> {
			// Loaded with the other models; each wrapped block model bakes its own in each quarter turn (once per reload: the
			// baker keeps them)
			List<ResourceLocation> models = new ArrayList<>();
			for (Block block : BuiltInRegistries.BLOCK) {
				if (!(block instanceof VineOnSupportBlock)) {
					continue;
				}
				for (int age = 0; age <= ClimbingCropBlock.MAX_AGE; age++) {
					for (boolean base : new boolean[] {false, true}) {
						for (SupportLeaves.Piece piece : SupportLeaves.Piece.values()) {
							models.add(SupportLeaves.model(block, piece, base, age));
						}
					}
				}
			}
			context.addModels(models);
			Map<ModelResourceLocation, BlockState> states = TrellisFrameModel.stateModels(block -> block instanceof VineOnSupportBlock);
			context.modifyModelAfterBake().register((model, bake) -> {
				// A block state's own model is baked under its model id; the models it is made of (and the leaf pieces) under
				// their plain ids
				@Nullable BlockState state = bake.id() instanceof ModelResourceLocation id ? states.get(id) : null;
				return model == null || state == null ? model : new SupportedVineModel(model, bakePieces(bake.baker(), state));
			});
		});
	}

	private static Map<SupportLeaves.Placed, BakedModel> bakePieces(ModelBaker baker, BlockState state) {
		boolean base = state.getValue(VineOnSupportBlock.BASE);
		int age = state.getValue(ClimbingCropBlock.AGE);
		Map<SupportLeaves.Placed, BakedModel> pieces = new HashMap<>();
		for (SupportLeaves.Piece piece : SupportLeaves.Piece.values()) {
			ResourceLocation model = SupportLeaves.model(state.getBlock(), piece, base, age);
			for (int turn = 0; turn < piece.turns(); turn++) {
				BakedModel baked = baker.bake(model, TURNS[turn]);
				if (baked != null) {
					pieces.put(new SupportLeaves.Placed(piece, turn), baked);
				}
			}
		}
		return pieces;
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
		BlockState support = VineSupports.supportAt(level, pos);
		if (support == null) {
			return;
		}
		for (SupportLeaves.Placed placed : SupportLeaves.of(support)) {
			BakedModel piece = pieces.get(placed);
			if (piece != null) {
				piece.emitBlockQuads(level, state, pos, random, context);
			}
		}
		BakedModel model = supportModel(support);
		if (model != null) {
			model.emitBlockQuads(level, support, pos, random, context);
		}
	}

	// Without a level (an item, a falling block): nothing but the particle (ForwardingBakedModel hands everything else to it)
}
