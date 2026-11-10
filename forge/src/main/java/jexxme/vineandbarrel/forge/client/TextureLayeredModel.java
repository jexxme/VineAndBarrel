package jexxme.vineandbarrel.forge.client;

import jexxme.vineandbarrel.client.VBRenderLayers;
import jexxme.vineandbarrel.kettle.KettleChimneyBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A block with glass (the Brew Kettle's and the Aging Barrel's sight glass, placed drinks) in the world: each face is
 * drawn in the layer of its own texture, as 26.x draws every block ({@link VBRenderLayers#layerOf}). With the whole block
 * in the translucent layer, a pane of glass sorted before the wood or copper behind it left a hole in the block.
 * <p>
 * Also here, as these models are wrapped anyway: the rim of the chimney's roof sheet, which was drawn black
 * ({@link #relit}).
 */
final class TextureLayeredModel extends BakedModelWrapper<BakedModel>
{
	private static final Direction[] SIDES = Direction.values();

	/** How far a rim face's first corner is moved in (blocks): far too little to see. */
	private static final float NUDGE = 1.0E-4F;

	/** The layers this state's faces are in. */
	private final ChunkRenderTypeSet layers;
	/** The faces drawn in a copy with its light put right (the roof sheet's rim), by the face they stand in for. Only read after the constructor. */
	private final Map<BakedQuad, BakedQuad> relit = new IdentityHashMap<>();

	private TextureLayeredModel(BakedModel model, BlockState state) {
		super(model);
		// every texture is looked at now, while the models are made: drawing only looks the layers up
		EnumSet<VBRenderLayers.Layer> used = EnumSet.noneOf(VBRenderLayers.Layer.class);
		RandomSource random = RandomSource.create(42L);
		for (int i = 0; i <= SIDES.length; i++) {
			for (BakedQuad quad : model.getQuads(state, i < SIDES.length ? SIDES[i] : null, random, ModelData.EMPTY, null)) {
				used.add(VBRenderLayers.layerOf(quad.getSprite()));
				if (isSheetRim(state, quad)) {
					relit.put(quad, relit(quad));
				}
			}
		}
		List<RenderType> types = new ArrayList<>(3);
		for (VBRenderLayers.Layer layer : used) {
			types.add(switch (layer) {
				case SOLID -> RenderType.solid();
				case CUTOUT -> RenderType.cutout();
				case TRANSLUCENT -> RenderType.translucent();
			});
		}
		this.layers = ChunkRenderTypeSet.of(types);
	}

	/** Wraps the baked model of every state of the blocks with glass (Forge: {@code ModelEvent.ModifyBakingResult}). */
	static void wrap(ModelEvent.ModifyBakingResult event) {
		VBRenderLayers.forgetTextures();
		for (Block block : VBRenderLayers.perTextureBlocks()) {
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state), (location, model) -> new TextureLayeredModel(model, state));
			}
		}
	}

	@Override
	public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
		return layers;
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random, ModelData data, @Nullable RenderType renderType) {
		List<BakedQuad> all = originalModel.getQuads(state, side, random, data, null);
		VBRenderLayers.@Nullable Layer wanted = layerOf(renderType);
		if (all.isEmpty() || wanted == null && relit.isEmpty()) {
			return all;
		}
		List<BakedQuad> quads = new ArrayList<>(all.size());
		for (BakedQuad quad : all) {
			if (wanted == null || VBRenderLayers.layerOf(quad.getSprite()) == wanted) {
				quads.add(relit.getOrDefault(quad, quad));
			}
		}
		return quads;
	}

	/**
	 * A side face of the copper sheet a chimney piece lays on the roof it goes through: upright, and wholly above the
	 * piece's own block (the sheet lies on the roof, one pixel thick and a pixel wider than the block all round).
	 */
	private static boolean isSheetRim(BlockState state, BakedQuad quad) {
		if (!(state.getBlock() instanceof KettleChimneyBlock) || !state.getValue(KettleChimneyBlock.FLASHING) || quad.getDirection().getAxis().isVertical()) {
			return false;
		}
		int[] vertices = quad.getVertices();
		int stride = vertices.length / 4;
		for (int corner = 0; corner < 4; corner++) {
			if (Float.intBitsToFloat(vertices[corner * stride + 1]) < 1.0F - NUDGE) {
				return false;
			}
		}
		return true;
	}

	/**
	 * This Minecraft lights a flat face that lies on or beyond its block's edge from the next block that way: for the
	 * sheet's rim that is the roof block beside the chimney, dark inside, and the rim was drawn black (26.x and Fabric's
	 * renderer light it from the chimney's own block). The copy is no longer quite flat (one corner a hair further
	 * in), so it takes its light from the chimney's own block too.
	 */
	private static BakedQuad relit(BakedQuad quad) {
		int[] vertices = quad.getVertices().clone();
		Direction facing = quad.getDirection();
		int along = facing.getAxis() == Direction.Axis.X ? 0 : 2;
		vertices[along] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[along]) - facing.getAxisDirection().getStep() * NUDGE);
		return new BakedQuad(vertices, quad.getTintIndex(), facing, quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
	}

	/** The layer a render type draws, or null for one that draws the block in one go (an item, a "Watch it" scene). */
	private static VBRenderLayers.@Nullable Layer layerOf(@Nullable RenderType type) {
		if (type == RenderType.solid()) {
			return VBRenderLayers.Layer.SOLID;
		}
		if (type == RenderType.cutout() || type == RenderType.cutoutMipped()) {
			return VBRenderLayers.Layer.CUTOUT;
		}
		if (type == RenderType.translucent()) {
			return VBRenderLayers.Layer.TRANSLUCENT;
		}
		return null;
	}
}
