package jexxme.vineandbarrel.block;

import jexxme.vineandbarrel.registry.VBBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How our blocks burn, in vanilla's own fire table ({@code FireBlock.setFlammable}, as {@code FireBlock.bootStrap}
 * fills it), which both loaders read: the plants like the sweet berry bush (60 / 100, as FD's plants do), the Trellis
 * like a wooden fence (5 / 20), the barley bale like a hay bale (60 / 20). A vine on a fence, wall, chain or bars burns away
 * like the other plants and leaves the block it held.
 */
public final class VBFlammability
{
	private VBFlammability() {
	}

	/** Once, after the blocks are registered. */
	public static void register() {
		for (Block plant : new Block[] {VBBlocks.GRAPE_BUSH.get(), VBBlocks.WHITE_GRAPE_BUSH.get(), VBBlocks.HOP_BUSH.get(),
				VBBlocks.GRAPE_VINE_ON_ROPE.get(), VBBlocks.WHITE_GRAPE_VINE_ON_ROPE.get(), VBBlocks.HOP_VINE_ON_ROPE.get(),
				VBBlocks.GRAPE_VINE_ON_TRELLIS.get(), VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS.get(), VBBlocks.HOP_VINE_ON_TRELLIS.get(),
				VBBlocks.GRAPE_VINE_ON_SUPPORT.get(), VBBlocks.WHITE_GRAPE_VINE_ON_SUPPORT.get(), VBBlocks.HOP_VINE_ON_SUPPORT.get(),
				VBBlocks.WILD_GRAPES.get(), VBBlocks.WILD_WHITE_GRAPES.get(), VBBlocks.WILD_HOPS.get(), VBBlocks.WILD_BARLEY.get()}) {
			fire().setFlammable(plant, 60, 100);
		}
		fire().setFlammable(VBBlocks.TRELLIS.get(), 5, 20);
		fire().setFlammable(VBBlocks.BARLEY_BALE.get(), 60, 20);
	}

	/** How readily a block catches fire from a neighbour (NeoForge: {@code getFireSpreadSpeed}). */
	public static int igniteOdds(BlockState state) {
		return fire().getIgniteOdds(state);
	}

	/** How readily a burning block burns away (NeoForge: {@code getFlammability}). */
	public static int burnOdds(BlockState state) {
		return fire().getBurnOdds(state);
	}

	private static FireBlock fire() {
		return (FireBlock) Blocks.FIRE;
	}
}
