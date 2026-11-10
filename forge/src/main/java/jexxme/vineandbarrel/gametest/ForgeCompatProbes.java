package jexxme.vineandbarrel.gametest;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** {@link CompatProbes} on Forge, against the mods' Forge builds for this Minecraft (compile-only, see forge/build.gradle). */
public final class ForgeCompatProbes implements CompatProbes
{
	@Override
	public List<String> compatRunMods() {
		// 1.20.1: every compat mod but Nutritional Balance has a build, with the libraries two of them need (the
		// compat run's classpath, see forge/build.gradle)
		return List.of(ModCompatGameTests.TOUGH_AS_NAILS, "glitchcore", ModCompatGameTests.HOMEOSTATIC, ModCompatGameTests.THIRST_WAS_TAKEN,
			ModCompatGameTests.SPICE_OF_LIFE, "creativecore");
	}

	@Override
	public void prepareMockConnection(Connection connection) {
		// As a real Forge client's connection (see ForgePlatform)
		jexxme.vineandbarrel.platform.Services.PLATFORM.prepareMockConnection(connection);
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

	/** Nutritional Balance has no build for this Minecraft: never installed, never asked; no groups. */
	@Override
	public Set<String> nutritionalBalanceGroups(Item item, ServerLevel level) {
		return Set.of();
	}

	@Override
	public double spiceOfLifeDiversity(Player player, ItemStack stack) {
		return Sol.diversity(player, stack);
	}

	/** Posts AppleSkin's food event the way its tooltip and HUD do. */
	private static final class AppleSkin
	{
		static FoodProperties[] foodValues(Player player, ItemStack stack) {
			FoodProperties food = stack.getFoodProperties(player);
			squeek.appleskin.api.food.FoodValues own = food == null ? new squeek.appleskin.api.food.FoodValues(0, 0.0F)
				: new squeek.appleskin.api.food.FoodValues(food.getNutrition(), food.getSaturationModifier());
			squeek.appleskin.api.event.FoodValuesEvent event = new squeek.appleskin.api.event.FoodValuesEvent(player, stack, own, own);
			MinecraftForge.EVENT_BUS.post(event);
			return new FoodProperties[] {food(event.defaultFoodValues), food(event.modifiedFoodValues)};
		}

		/** AppleSkin's hunger and saturation modifier, as this Minecraft's food. */
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

	private static final class Sol
	{
		static double diversity(Player player, ItemStack stack) {
			return team.creative.solonion.common.SOLOnion.CONFIG.getDiversity(player, stack);
		}
	}
}
