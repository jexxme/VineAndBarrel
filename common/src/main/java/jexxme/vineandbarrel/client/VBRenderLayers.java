package jexxme.vineandbarrel.client;

import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import jexxme.vineandbarrel.registry.VBBlocks;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Which of our blocks are drawn with holes (cutout) or see-through pixels (translucent). 26.x reads that off each
 * texture; on this Minecraft every block has one layer, solid unless said otherwise. The lists follow the textures:
 * the plants, the trellis and the feast have fully transparent pixels; the Brew Kettle (its sight glass), the Aging
 * Barrel and placed drinks use the one texture with half-transparent pixels, the glass.
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
		List<Block> glass = new ArrayList<>();
		glass.addAll(KettleRegistry.allKettleBlocks());
		glass.addAll(KettleRegistry.allPartBlocks());
		glass.addAll(KettleRegistry.allChimneyBlocks());
		glass.add(BarrelRegistry.AGING_BARREL.get());
		glass.add(BarrelRegistry.PLACED_DRINKS.get());
		registrar.blockRenderLayer(RenderType.translucent(), glass.toArray(Block[]::new));
	}
}
