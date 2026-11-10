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
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import org.jspecify.annotations.Nullable;

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
		registrar.blockTints(new SpotTint(), BarrelRegistry.PLACED_DRINKS.get());
		// The fill gauge's liquid (tint index 0) in the colour of what the barrel holds
		registrar.blockTints(new GaugeTint(), BarrelRegistry.AGING_BARREL.get());
		// The shimmer on aged drinks set down on a table
		registrar.blockEntityRenderer(BarrelRegistry.PLACED_DRINKS_BE.get(), context -> new PlacedDrinksRenderer());
	}

	// The client knows the aging recipes (1.21.1: vanilla sends every recipe and AgingRecipes reads them from the
	// client's recipe manager), so it claims exactly the barrel clicks the server takes

	/** When the player leaves a world or server. */
	public static void onLoggingOut() {
		AgingRecipes.clearClientRecipes();
	}

	/** No tint (1.21.1's BlockColor answers for every tint index; 26.x has one source per index and none beyond). */
	private static final int NO_TINT = -1;

	private static final class GaugeTint implements BlockColor
	{
		@Override
		public int getColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
			if (tintIndex != 0) {
				return NO_TINT;
			}
			if (level != null && pos != null && level.getBlockEntity(pos) instanceof AgingBarrelBlockEntity barrel && barrel.servings() > 0) {
				return 0xFF000000 | DrinkColors.of(barrel.content());
			}
			return 0xFF000000 | DrinkColors.FALLBACK;
		}
	}

	/** The tint index is the spot. */
	private static final class SpotTint implements BlockColor
	{
		@Override
		public int getColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int spot) {
			if (spot < 0 || spot >= PlacedDrinksBlock.SPOTS) {
				return NO_TINT;
			}
			if (level != null && pos != null && level.getBlockEntity(pos) instanceof PlacedDrinksBlockEntity drinks) {
				ItemStack drink = drinks.getItem(spot);
				if (!drink.isEmpty()) {
					return 0xFF000000 | DrinkColors.of(drink);
				}
			}
			return 0xFF000000 | DrinkColors.FALLBACK;
		}
	}
}
