package jexxme.vineandbarrel.client;

import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import com.mojang.blaze3d.platform.NativeImage;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.mixin.SpriteContentsAccessor;
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import jexxme.vineandbarrel.registry.VBBlocks;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which of our blocks are drawn with holes (cutout) or see-through pixels (translucent). 26.x reads that off each
 * texture; on this Minecraft every block has one layer, solid unless said otherwise. The lists follow the textures:
 * the plants, the trellis and the feast have fully transparent pixels; the Brew Kettle (its sight glass), the Aging
 * Barrel and placed drinks use the one texture with half-transparent pixels, the glass.
 * <p>
 * A whole block in the translucent layer is drawn wrong in the world, though: its faces are sorted among themselves by
 * distance, and a pane of glass drawn before the wood or copper behind it leaves a hole there. So in the world the
 * blocks with glass ({@link #perTextureBlocks}) are drawn as 26.x draws everything, each face in the layer of its own
 * texture ({@link #layerOf}): the loaders wrap their models for that (TextureLayeredModel). The translucent layer set
 * here is what everything else uses that draws a block in one go (items, moving blocks, "Watch it" scenes).
 */
public final class VBRenderLayers
{
	private VBRenderLayers() {
	}

	public static void register(ClientRegistrar registrar) {
		registrar.blockRenderLayer(RenderType.cutout(),
			VBBlocks.GRAPE_BUSH.get(), VBBlocks.WHITE_GRAPE_BUSH.get(), VBBlocks.HOP_BUSH.get(),
			VBBlocks.GRAPE_VINE_ON_ROPE.get(), VBBlocks.WHITE_GRAPE_VINE_ON_ROPE.get(), VBBlocks.HOP_VINE_ON_ROPE.get(),
			VBBlocks.GRAPE_VINE_ON_TRELLIS.get(), VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS.get(), VBBlocks.HOP_VINE_ON_TRELLIS.get(),
			VBBlocks.GRAPE_VINE_ON_SUPPORT.get(), VBBlocks.WHITE_GRAPE_VINE_ON_SUPPORT.get(), VBBlocks.HOP_VINE_ON_SUPPORT.get(),
			VBBlocks.TRELLIS.get(), VBBlocks.BARLEY.get(), VBBlocks.WILD_GRAPES.get(), VBBlocks.WILD_WHITE_GRAPES.get(),
			VBBlocks.WILD_HOPS.get(), VBBlocks.WILD_BARLEY.get(), FDCompat.COQ_AU_VIN_BLOCK.get());
		registrar.blockRenderLayer(RenderType.translucent(), perTextureBlocks().toArray(Block[]::new));
	}

	/** The blocks with glass: in the world each of their faces is drawn in the layer of its own texture. */
	public static List<Block> perTextureBlocks() {
		List<Block> glass = new ArrayList<>();
		glass.addAll(KettleRegistry.allKettleBlocks());
		glass.addAll(KettleRegistry.allPartBlocks());
		glass.addAll(KettleRegistry.allChimneyBlocks());
		glass.add(BarrelRegistry.AGING_BARREL.get());
		glass.add(BarrelRegistry.PLACED_DRINKS.get());
		return glass;
	}

	/** The layer of one face, read off its texture. */
	public enum Layer
	{
		SOLID, CUTOUT, TRANSLUCENT
	}

	/** The textures looked at since the last model reload (a resource pack may bring others). */
	private static final Map<ResourceLocation, Layer> TEXTURE_LAYERS = new ConcurrentHashMap<>();

	/** Before models are wrapped anew: the textures are read again. */
	public static void forgetTextures() {
		TEXTURE_LAYERS.clear();
	}

	/**
	 * The layer 26.x picks for a face with this texture: translucent when it has half-transparent pixels, cutout when it
	 * has fully transparent ones, solid otherwise. The loaders ask for every texture of a model when they wrap it, so
	 * drawing only looks the answer up.
	 */
	public static Layer layerOf(TextureAtlasSprite sprite) {
		Layer known = TEXTURE_LAYERS.get(sprite.contents().name());
		if (known != null) {
			return known;
		}
		Layer layer = Layer.SOLID;
		try {
			NativeImage image = ((SpriteContentsAccessor) sprite.contents()).vineandbarrel$originalImage();
			pixels:
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int alpha = image.getPixelRGBA(x, y) >>> 24;
					if (alpha == 0) {
						layer = Layer.CUTOUT;
					} else if (alpha < 255) {
						layer = Layer.TRANSLUCENT;
						break pixels;
					}
				}
			}
		} catch (IllegalStateException closed) {
			// a texture of the atlas before a reload, asked for by a chunk still being drawn with the old models
			return Layer.SOLID;
		}
		TEXTURE_LAYERS.put(sprite.contents().name(), layer);
		return layer;
	}
}
