package jexxme.vineandbarrel.forge.compat;

import jexxme.vineandbarrel.compat.appleskin.DrinkFood;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import squeek.appleskin.api.event.FoodValuesEvent;
import squeek.appleskin.api.food.FoodValues;

/**
 * AppleSkin on Forge (see {@link jexxme.vineandbarrel.compat.appleskin.AppleSkinCompat}): its food event gets a
 * drink's real food. Only loaded when AppleSkin is installed.
 */
public final class AppleSkinForge
{
	private AppleSkinForge() {
	}

	public static void register() {
		// First, so mods that change food values (AppleSkin's modified values) build on the drink's real food
		MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, AppleSkinForge::onFoodValues);
	}

	/** AppleSkin for this Minecraft counts a food as hunger and saturation modifier (saturation = hunger x modifier x 2). */
	private static void onFoodValues(FoodValuesEvent event) {
		DrinkFood.of(event.itemStack).ifPresent(food -> {
			FoodValues values = new FoodValues(food.getNutrition(), food.getSaturationModifier());
			event.defaultFoodValues = values;
			event.modifiedFoodValues = values;
		});
	}
}
