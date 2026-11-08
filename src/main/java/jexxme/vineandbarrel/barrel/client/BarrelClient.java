package jexxme.vineandbarrel.barrel.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.AgingBarrelItem;
import jexxme.vineandbarrel.barrel.AgingRecipes;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.drink.DrinkColors;
import jexxme.vineandbarrel.drink.PlacedDrinksBlock;
import jexxme.vineandbarrel.drink.PlacedDrinksBlockEntity;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Client registration for the Aging Barrel and the drinks: particles, the barrel item's tooltip, placed drinks' colours. */
@EventBusSubscriber(modid = VineAndBarrel.MODID, value = Dist.CLIENT)
public final class BarrelClient
{
	private BarrelClient() {
	}

	@SubscribeEvent
	public static void registerParticles(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(BarrelRegistry.FERMENT_BUBBLE.get(), BarrelParticles.FermentBubble.Provider::new);
		event.registerSpriteSet(BarrelRegistry.TAP_DRIP.get(), BarrelParticles.TapDrip.Provider::new);
		event.registerSpriteSet(BarrelRegistry.CHEERS.get(), BarrelParticles.CheersSparkle.Provider::new);
	}

	@SubscribeEvent
	public static void registerTooltips(RegisterClientTooltipComponentFactoriesEvent event) {
		event.register(AgingBarrelItem.BarrelTooltipData.class, BarrelTooltip::new);
	}

	/** The client knows the aging recipes, so it claims exactly the barrel clicks the server takes. */
	@SubscribeEvent
	public static void onRecipesReceived(RecipesReceivedEvent event) {
		if (event.getRecipeTypes().contains(BarrelRegistry.AGING.get())) {
			AgingRecipes.setClientRecipes(event.getRecipeMap().byType(BarrelRegistry.AGING.get()));
		}
	}

	@SubscribeEvent
	public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		AgingRecipes.setClientRecipes(List.of());
	}

	/** D1: each spot's liquid (tint index = spot) in the colour of the drink standing there. */
	@SubscribeEvent
	public static void registerBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
		List<BlockTintSource> spots = new ArrayList<>();
		for (int i = 0; i < PlacedDrinksBlock.SPOTS; i++) {
			spots.add(new SpotTint(i));
		}
		event.register(spots, BarrelRegistry.PLACED_DRINKS.get());
		// The fill gauge's liquid in the colour of what the barrel holds
		event.register(List.of(new GaugeTint()), BarrelRegistry.AGING_BARREL.get());
	}

	/** The shimmer on aged drinks set down on a table. */
	@SubscribeEvent
	public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(BarrelRegistry.PLACED_DRINKS_BE.get(), context -> new PlacedDrinksRenderer());
	}

	private static final class GaugeTint implements BlockTintSource
	{
		@Override
		public int color(BlockState state) {
			return 0xFF000000 | DrinkColors.FALLBACK;
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			if (level.getBlockEntity(pos) instanceof AgingBarrelBlockEntity barrel && barrel.servings() > 0) {
				return 0xFF000000 | DrinkColors.of(barrel.content());
			}
			return color(state);
		}

		@Override
		public Set<Property<?>> relevantProperties() {
			return Set.of(AgingBarrelBlock.FILL, AgingBarrelBlock.BUNG);
		}
	}

	private record SpotTint(int spot) implements BlockTintSource
	{
		@Override
		public int color(BlockState state) {
			return 0xFF000000 | DrinkColors.FALLBACK;
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			if (level.getBlockEntity(pos) instanceof PlacedDrinksBlockEntity drinks) {
				ItemStack drink = drinks.getItem(spot);
				if (!drink.isEmpty()) {
					return 0xFF000000 | DrinkColors.of(drink);
				}
			}
			return color(state);
		}

		@Override
		public Set<Property<?>> relevantProperties() {
			return Set.of(PlacedDrinksBlock.SPOT.get(spot));
		}
	}
}
