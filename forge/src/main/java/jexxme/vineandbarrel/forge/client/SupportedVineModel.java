package jexxme.vineandbarrel.forge.client;

import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.block.SupportLeaves;
import jexxme.vineandbarrel.block.VineOnSupportBlock;
import jexxme.vineandbarrel.block.VineSupports;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * A vine on a fence, wall, chain or bars ({@link VineOnSupportBlock}): the block it holds, drawn with that block's own
 * model (read from the vine's block entity), and the vine's leaves as pieces that follow that block's shape
 * ({@link SupportLeaves}). So an oak fence stays an oak fence, with its arms as they are, and the leaves run along them.
 */
final class SupportedVineModel extends BakedModelWrapper<BakedModel>
{
	/** A piece's quarter turns, as a block state's {@code "y"} turns a model. */
	private static final BlockModelRotation[] TURNS = {BlockModelRotation.X0_Y0, BlockModelRotation.X0_Y90, BlockModelRotation.X0_Y180,
			BlockModelRotation.X0_Y270};

	/**
	 * The block the vine holds, what its own model wants to know about this spot, and the leaf pieces on it, read when the
	 * chunk is drawn.
	 */
	private record Support(BlockState state, BakedModel model, ModelData data, List<BakedModel> leaves)
	{
	}

	private static final ModelProperty<Support> SUPPORT = new ModelProperty<>();
	/** The baked leaf pieces of the last model reload, by model and turn (read by the chunk builders' threads). */
	private static volatile Map<String, BakedModel> pieces = Map.of();

	private SupportedVineModel(BakedModel model) {
		super(model);
	}

	/**
	 * Every piece of every vine on a support, per age and root: models no block state names, loaded (with their parents)
	 * as additional models, which this Minecraft bakes and keeps under the model's own id.
	 */
	static void registerPieces(ModelEvent.RegisterAdditional event) {
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!(block instanceof VineOnSupportBlock)) {
				continue;
			}
			for (int age = 0; age <= ClimbingCropBlock.MAX_AGE; age++) {
				for (boolean base : new boolean[] {false, true}) {
					for (SupportLeaves.Piece piece : SupportLeaves.Piece.values()) {
						event.register(SupportLeaves.model(block, piece, base, age));
					}
				}
			}
		}
	}

	/**
	 * Wraps the baked model of every vine-on-support state (Forge: {@code ModelEvent.ModifyBakingResult}), and keeps the
	 * leaf pieces in each of their quarter turns: the unturned one as baked, the others baked again turned (as the
	 * trellis frame's are, with the sprites {@code ModelBakeryMixin} keeps).
	 */
	static void wrap(ModelEvent.ModifyBakingResult event) {
		Map<String, BakedModel> baked = new HashMap<>();
		ModelBakery bakery = event.getModelBakery();
		BiFunction<ResourceLocation, Material, TextureAtlasSprite> sprites = ForgeClient.bakingSprites(bakery);
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!(block instanceof VineOnSupportBlock)) {
				continue;
			}
			for (int age = 0; age <= ClimbingCropBlock.MAX_AGE; age++) {
				for (boolean base : new boolean[] {false, true}) {
					for (SupportLeaves.Piece piece : SupportLeaves.Piece.values()) {
						ResourceLocation location = SupportLeaves.model(block, piece, base, age);
						BakedModel plain = event.getModels().get(location);
						if (plain != null) {
							baked.put(location + "#0", plain);
						}
						if (sprites == null) {
							continue;
						}
						ModelBaker baker = new TrellisFrameModel.PieceBaker(bakery, material -> sprites.apply(location, material));
						for (int turn = 1; turn < piece.turns(); turn++) {
							BakedModel turned = baker.bake(location, TURNS[turn]);
							if (turned != null) {
								baked.put(location + "#" + turn, turned);
							}
						}
					}
				}
			}
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state), (location, model) -> new SupportedVineModel(model));
			}
		}
		pieces = baked;
	}

	@Override
	public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
		ModelData own = originalModel.getModelData(level, pos, state, modelData);
		BlockState support = VineSupports.supportAt(level, pos);
		if (support == null) {
			return own;
		}
		boolean base = state.getValue(VineOnSupportBlock.BASE);
		int age = state.getValue(ClimbingCropBlock.AGE);
		Map<String, BakedModel> baked = pieces;
		List<BakedModel> leaves = new ArrayList<>(6);
		for (SupportLeaves.Placed placed : SupportLeaves.of(support)) {
			BakedModel piece = baked.get(SupportLeaves.model(state.getBlock(), placed.piece(), base, age) + "#" + placed.turn());
			if (piece != null) {
				leaves.add(piece);
			}
		}
		BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(support);
		return own.derive().with(SUPPORT, new Support(support, model, model.getModelData(level, pos, support, ModelData.EMPTY), leaves)).build();
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
			for (BakedModel leaves : support.leaves()) {
				quads.addAll(leaves.getQuads(state, side, random, ModelData.EMPTY, renderType));
			}
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
