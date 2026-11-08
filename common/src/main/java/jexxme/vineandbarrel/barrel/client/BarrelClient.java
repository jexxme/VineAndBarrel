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
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import net.minecraft.world.item.crafting.RecipeMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Client registration for the Aging Barrel and the drinks: particles, the barrel item's tooltip, placed drinks' colours. */
public final class BarrelClient
{
	private BarrelClient() {
	}

	public static void register(ClientRegistrar registrar) {
		registrar.particle(BarrelRegistry.FERMENT_BUBBLE.get(), BarrelParticles.FermentBubble.Provider::new);
		registrar.particle(BarrelRegistry.TAP_DRIP.get(), BarrelParticles.TapDrip.Provider::new);
		registrar.particle(BarrelRegistry.CHEERS.get(), BarrelParticles.CheersSparkle.Provider::new);
		registrar.tooltip(AgingBarrelItem.BarrelTooltipData.class, BarrelTooltip::new);
		// D1: each spot's liquid (tint index = spot) in the colour of the drink standing there
		List<BlockTintSource> spots = new ArrayList<>();
		for (int i = 0; i < PlacedDrinksBlock.SPOTS; i++) {
			spots.add(new SpotTint(i));
		}
		registrar.blockTints(spots, BarrelRegistry.PLACED_DRINKS.get());
		// The fill gauge's liquid in the colour of what the barrel holds
		registrar.blockTints(List.of(new GaugeTint()), BarrelRegistry.AGING_BARREL.get());
		// The shimmer on aged drinks set down on a table
		registrar.blockEntityRenderer(BarrelRegistry.PLACED_DRINKS_BE.get(), context -> new PlacedDrinksRenderer());
	}

	/** The client knows the aging recipes, so it claims exactly the barrel clicks the server takes. */
	public static void onRecipesReceived(RecipeMap recipes) {
		AgingRecipes.setClientRecipes(recipes.byType(BarrelRegistry.AGING.get()));
	}

	/** When the player leaves a world or server. */
	public static void onLoggingOut() {
		AgingRecipes.setClientRecipes(List.of());
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
