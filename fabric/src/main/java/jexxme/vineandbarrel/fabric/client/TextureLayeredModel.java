package jexxme.vineandbarrel.fabric.client;

import jexxme.vineandbarrel.client.VBRenderLayers;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A block with glass (the Brew Kettle's and the Aging Barrel's sight glass, placed drinks) in the world: each face is
 * drawn in the layer of its own texture, as 26.x draws every block ({@link VBRenderLayers#layerOf}). With the whole block
 * in the translucent layer, a pane of glass sorted before the wood or copper behind it left a hole in the block. The
 * Fabric twin of NeoForge's {@code TextureLayeredModel}.
 * <p>
 * Without a renderer of Fabric's rendering API (Sodium without Indium) the world is drawn from the plain quads, the whole
 * block in its one layer: there the glass is handed out last ({@link #getQuads}).
 */
final class TextureLayeredModel extends ForwardingBakedModel
{
	private static final Direction[] SIDES = Direction.values();

	/** How a face of each layer is drawn: as vanilla draws it, but in that layer whatever the block's own is. Empty without a renderer. */
	private final Map<VBRenderLayers.Layer, RenderMaterial> materials = new EnumMap<>(VBRenderLayers.Layer.class);

	private TextureLayeredModel(BakedModel model, BlockState state, @Nullable Renderer renderer) {
		this.wrapped = model;
		for (VBRenderLayers.Layer layer : VBRenderLayers.Layer.values()) {
			if (renderer != null) {
				materials.put(layer, renderer.materialFinder()
					.ambientOcclusion(model.useAmbientOcclusion() ? TriState.DEFAULT : TriState.FALSE)
					.blendMode(switch (layer) {
						case SOLID -> BlendMode.SOLID;
						case CUTOUT -> BlendMode.CUTOUT;
						case TRANSLUCENT -> BlendMode.TRANSLUCENT;
					}).find());
			}
		}
		// every texture is looked at now, while the models are made: drawing only looks the layers up
		RandomSource random = RandomSource.create(42L);
		for (int i = 0; i <= SIDES.length; i++) {
			for (BakedQuad quad : model.getQuads(state, i < SIDES.length ? SIDES[i] : null, random)) {
				VBRenderLayers.layerOf(quad.getSprite());
			}
		}
	}

	/** Wraps the baked model of every state of the blocks with glass, once at client start. */
	static void register() {
		ModelLoadingPlugin.register(context -> {
			VBRenderLayers.forgetTextures();
			Set<Block> blocks = Set.copyOf(VBRenderLayers.perTextureBlocks());
			Map<ModelResourceLocation, BlockState> states = TrellisFrameModel.stateModels(blocks::contains);
			context.modifyModelAfterBake().register((model, bake) -> {
				@Nullable BlockState state = bake.id() instanceof ModelResourceLocation id ? states.get(id) : null;
				return model == null || state == null ? model : new TextureLayeredModel(model, state, RendererAccess.INSTANCE.getRenderer());
			});
		});
	}

	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
		QuadEmitter emitter = context.getEmitter();
		for (int i = 0; i <= SIDES.length; i++) {
			@Nullable Direction side = i < SIDES.length ? SIDES[i] : null;
			if (!context.hasTransform() && context.isFaceCulled(side)) {
				continue;
			}
			for (BakedQuad quad : wrapped.getQuads(state, side, random.get())) {
				emitter.fromVanilla(quad, materials.get(VBRenderLayers.layerOf(quad.getSprite())), side);
				emitter.emit();
			}
		}
	}

	/**
	 * Without a renderer: the block's quads with the glass last, after every other face. Sodium without Indium draws the
	 * block's faces in the order they come, unsorted and in the translucent layer, so a pane drawn first hid the wood,
	 * copper or drink behind it. The glass of a side comes with the faces of no side (it is no longer left out where the
	 * side touches a full block, where the block in front hides it anyway).
	 */
	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
		List<BakedQuad> quads = wrapped.getQuads(state, side, random);
		if (!materials.isEmpty() || state == null) {
			return quads;
		}
		if (side != null) {
			return quads.stream().anyMatch(TextureLayeredModel::glass) ? quads.stream().filter(quad -> !glass(quad)).toList() : quads;
		}
		List<BakedQuad> ordered = new ArrayList<>();
		List<BakedQuad> glass = new ArrayList<>();
		for (int i = 0; i <= SIDES.length; i++) {
			for (BakedQuad quad : i < SIDES.length ? wrapped.getQuads(state, SIDES[i], random) : quads) {
				if (glass(quad)) {
					glass.add(quad);
				} else if (i == SIDES.length) {
					ordered.add(quad);
				}
			}
		}
		ordered.addAll(glass);
		return ordered;
	}

	private static boolean glass(BakedQuad quad) {
		return VBRenderLayers.layerOf(quad.getSprite()) == VBRenderLayers.Layer.TRANSLUCENT;
	}

	// As an item: the model as it is (ForwardingBakedModel hands everything else to the model)
}
