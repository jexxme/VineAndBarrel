package jexxme.vineandbarrel.kettle.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.BrewKettleItem;
import jexxme.vineandbarrel.kettle.KettleMultiblock;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import org.jspecify.annotations.Nullable;

/** Client registration for the Brew Kettle: screen, recipe book tabs, steam particle, item tooltip, brew colour. */
public final class KettleClient
{
	/** Shown in an open kettle with nothing in it: the dark copper of the kettle's inside. */
	private static final int EMPTY_INSIDE = 0x5E2C1B;

	private KettleClient() {
	}

	public static void register(ClientRegistrar registrar) {
		registrar.menuScreen(KettleRegistry.BREW_KETTLE_MENU.get(), BrewKettleScreen::new);
		registrar.recipeBook(Services.PLATFORM.brewingRecipeBookType(), BrewingRecipeBookCategories.tabs(), BrewingRecipeBookCategories.search(),
			BrewingRecipeBookCategories.searched(), KettleRegistry.BREWING.get(), BrewingRecipeBookCategories::of);
		registrar.particle(KettleRegistry.STEAM.get(), SteamParticle.Provider::new);
		registrar.tooltip(BrewKettleItem.KettleTooltipData.class, KettleTooltip::new);
		// K2: the open kettle shows its brew in the brew's colour, water while it only holds ingredients
		registrar.blockTints(new BrewTint(), KettleRegistry.allKettleBlocks().toArray(Block[]::new));
		// a big kettle's hatch shows its brew the same way (the brew is in the controller's block entity)
		registrar.blockTints(new BrewTint(), KettleRegistry.allPartBlocks().toArray(Block[]::new));
	}

	/** The brew's colour on tint index 0 (under a kettle's lid, in a big kettle's sight glass); water where there is no world to ask. */
	private record BrewTint() implements BlockColor
	{
		@Override
		public int getColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
			if (tintIndex != 0) {
				return -1;
			}
			if (level == null || pos == null) {
				return 0xFF000000 | BaseItems.WATER_COLOR;
			}
			if (KettleMultiblock.controller(level, pos) instanceof BrewKettleBlockEntity kettle) {
				int brew = BaseItems.liquidColor(kettle.getBrew());
				if (brew != -1) {
					return 0xFF000000 | brew;
				}
				if (!kettle.getBrew().isEmpty() || kettle.hasIngredients()) {
					return 0xFF000000 | BaseItems.WATER_COLOR;
				}
			}
			return 0xFF000000 | EMPTY_INSIDE;
		}
	}
}
