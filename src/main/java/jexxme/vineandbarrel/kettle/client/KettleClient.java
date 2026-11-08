package jexxme.vineandbarrel.kettle.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.BrewKettleBlock;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.BrewKettleItem;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookSearchCategoriesEvent;

import java.util.List;
import java.util.Set;

/** Client registration for the Brew Kettle: screen, recipe book search tab, steam particle, item tooltip, brew colour. */
@EventBusSubscriber(modid = VineAndBarrel.MODID, value = Dist.CLIENT)
public final class KettleClient
{
	/** Shown in an open kettle with nothing in it: the dark copper of the kettle's inside. */
	private static final int EMPTY_INSIDE = 0x5E2C1B;

	private KettleClient() {
	}

	@SubscribeEvent
	public static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(KettleRegistry.BREW_KETTLE_MENU.get(), BrewKettleScreen::new);
	}

	@SubscribeEvent
	public static void registerRecipeBookCategories(RegisterRecipeBookSearchCategoriesEvent event) {
		event.register(KettleRegistry.BREWING_SEARCH.get(), KettleRegistry.BREWING_JUICES.get(), KettleRegistry.BREWING_WORTS.get(), KettleRegistry.BREWING_MISC.get());
	}

	@SubscribeEvent
	public static void registerParticles(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(KettleRegistry.STEAM.get(), SteamParticle.Provider::new);
	}

	@SubscribeEvent
	public static void registerTooltips(RegisterClientTooltipComponentFactoriesEvent event) {
		event.register(BrewKettleItem.KettleTooltipData.class, KettleTooltip::new);
	}

	/** K2: the open kettle shows its brew in the brew's colour, water while it only holds ingredients. */
	@SubscribeEvent
	public static void registerBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
		event.register(List.of(new BrewTint()), KettleRegistry.allKettleBlocks().toArray(Block[]::new));
	}

	private static final class BrewTint implements BlockTintSource
	{
		@Override
		public int color(BlockState state) {
			return 0xFF000000 | BaseItems.WATER_COLOR;
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			if (level.getBlockEntity(pos) instanceof BrewKettleBlockEntity kettle) {
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

		@Override
		public Set<Property<?>> relevantProperties() {
			return Set.of(BrewKettleBlock.OPEN, BrewKettleBlock.FILL);
		}
	}
}
