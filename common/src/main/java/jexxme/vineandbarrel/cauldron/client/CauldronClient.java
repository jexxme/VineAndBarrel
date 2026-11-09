package jexxme.vineandbarrel.cauldron.client;

import jexxme.vineandbarrel.cauldron.BaseCauldronBlock;
import jexxme.vineandbarrel.cauldron.VBCauldrons;
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** The client side of our cauldrons: the liquid in its base's colour, as the water cauldron's is tinted by the biome's water. */
public final class CauldronClient
{
	private CauldronClient() {
	}

	public static void register(ClientRegistrar registrar) {
		for (BaseCauldronBlock cauldron : VBCauldrons.all()) {
			registrar.blockTints(List.of(new BaseTint(cauldron)), cauldron);
		}
	}

	/** The base's colour, looked up on first use: tints are registered before items have their components. */
	private static final class BaseTint implements BlockTintSource
	{
		private final BaseCauldronBlock cauldron;
		private int color;

		private BaseTint(BaseCauldronBlock cauldron) {
			this.cauldron = cauldron;
		}

		@Override
		public int color(BlockState state) {
			if (color == 0) {
				color = 0xFF000000 | VBCauldrons.color(cauldron);
			}
			return color;
		}
	}
}
