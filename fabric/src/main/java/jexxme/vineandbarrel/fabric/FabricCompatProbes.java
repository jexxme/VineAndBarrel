package jexxme.vineandbarrel.fabric;

import jexxme.vineandbarrel.gametest.CompatProbes;
import jexxme.vineandbarrel.gametest.ModCompatGameTests;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * {@link CompatProbes} on Fabric, against the Fabric builds of the mods (compile-only, see fabric/build.gradle).
 * Nutritional Balance and Spice of Life Onion have no Fabric build for this Minecraft: they are never loaded here, so
 * their probes are never called (a probe is only called when its mod is loaded) and name none of their classes.
 */
public final class FabricCompatProbes implements CompatProbes
{
	@Override
	public List<String> compatRunMods() {
		// Spice of Life Onion has no Fabric build for this Minecraft (see fabric/build.gradle)
		return List.of(ModCompatGameTests.TOUGH_AS_NAILS, "glitchcore", ModCompatGameTests.HOMEOSTATIC, ModCompatGameTests.THIRST_WAS_TAKEN);
	}

	@Override
	public void prepareMockConnection(Connection connection) {
		// Nothing to do: Fabric's networking checks a player's channels itself before a mod sends to them
	}

	@Override
	public FoodProperties[] appleSkinFoodValues(Player player, ItemStack stack) {
		return AppleSkin.foodValues(player, stack);
	}

	@Override
	public boolean toughAsNailsThirstOn() {
		return Tan.thirstOn();
	}

	@Override
	public void toughAsNailsSetThirst(Player player, int thirst, float hydration) {
		Tan.setThirst(player, thirst, hydration);
	}

	@Override
	public float[] toughAsNailsThirst(Player player) {
		return Tan.thirst(player);
	}

	@Override
	public float @Nullable [] homeostaticValues(ItemStack stack) {
		return Homeostatic.values(stack);
	}

	@Override
	public int[] thirstWasTakenValues(ItemStack stack) {
		return Twt.values(stack);
	}

	@Override
	public boolean thirstWasTakenRestoresThirst(ItemStack stack) {
		return Twt.restoresThirst(stack);
	}

	@Override
	public Set<String> nutritionalBalanceGroups(Item item, ServerLevel level) {
		throw new UnsupportedOperationException("Nutritional Balance has no Fabric build");
	}

	@Override
	public double spiceOfLifeDiversity(Player player, ItemStack stack) {
		throw new UnsupportedOperationException("Spice of Life Onion has no Fabric build for this Minecraft");
	}

	/** Fires AppleSkin's food event the way its tooltip and HUD do. */
	private static final class AppleSkin
	{
		static FoodProperties[] foodValues(Player player, ItemStack stack) {
			// The item's own food as this AppleSkin starts from it: hunger and saturation modifier, 0 and 0 for no food
			FoodProperties food = stack.getItem().getFoodProperties();
			squeek.appleskin.api.food.FoodValues own = food == null ? new squeek.appleskin.api.food.FoodValues(0, 0.0F)
				: new squeek.appleskin.api.food.FoodValues(food.getNutrition(), food.getSaturationModifier());
			squeek.appleskin.api.event.FoodValuesEvent event = new squeek.appleskin.api.event.FoodValuesEvent(player, stack, own, own);
			squeek.appleskin.api.event.FoodValuesEvent.EVENT.invoker().interact(event);
			return new FoodProperties[] {food(event.defaultFoodValues), food(event.modifiedFoodValues)};
		}

		private static FoodProperties food(squeek.appleskin.api.food.FoodValues values) {
			return new FoodProperties.Builder().nutrition(values.hunger).saturationMod(values.saturationModifier).build();
		}
	}

	private static final class Tan
	{
		static boolean thirstOn() {
			return toughasnails.api.thirst.ThirstHelper.isThirstEnabled();
		}

		static void setThirst(Player player, int thirst, float hydration) {
			toughasnails.api.thirst.IThirst data = toughasnails.api.thirst.ThirstHelper.getThirst(player);
			data.setThirst(thirst);
			data.setHydration(hydration);
		}

		static float[] thirst(Player player) {
			toughasnails.api.thirst.IThirst data = toughasnails.api.thirst.ThirstHelper.getThirst(player);
			return new float[] {data.getThirst(), data.getHydration()};
		}
	}

	private static final class Homeostatic
	{
		static float @Nullable [] values(ItemStack stack) {
			homeostatic.common.item.DrinkableItem drinkable = homeostatic.common.item.DrinkableItemManager.get(stack);
			return drinkable == null ? null : new float[] {drinkable.amount(), drinkable.saturation(), drinkable.chance()};
		}
	}

	private static final class Twt
	{
		static int[] values(ItemStack stack) {
			return com.thirstwastaken2.api.ThirstApi.thirstValues(stack);
		}

		static boolean restoresThirst(ItemStack stack) {
			return com.thirstwastaken2.api.ThirstApi.restoresThirst(stack);
		}
	}

}
