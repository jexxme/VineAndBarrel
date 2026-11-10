package jexxme.vineandbarrel.fabric.compat;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.compat.appleskin.DrinkFood;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import squeek.appleskin.api.event.FoodValuesEvent;
import squeek.appleskin.api.food.FoodValues;

/**
 * AppleSkin on Fabric (see {@link jexxme.vineandbarrel.compat.appleskin.AppleSkinCompat}): its food event gets a
 * drink's real food. Only loaded when AppleSkin is installed. Our handler runs in its own phase ordered before the
 * default one, so every other mod's handler sees the drink's real food and builds on it, whatever order the mods
 * started in (NeoForge: the event's priority).
 */
public final class AppleSkinFabric
{
	private AppleSkinFabric() {
	}

	/** Our phase of AppleSkin's food event: before the default phase every other handler uses. */
	private static final ResourceLocation DRINK_FOOD = VineAndBarrel.id("drink_food");

	public static void register() {
		FoodValuesEvent.EVENT.addPhaseOrdering(DRINK_FOOD, Event.DEFAULT_PHASE);
		FoodValuesEvent.EVENT.register(DRINK_FOOD, event -> DrinkFood.of(event.itemStack).ifPresent(food -> {
			// This AppleSkin's food is hunger and the saturation modifier
			FoodValues values = new FoodValues(food.getNutrition(), food.getSaturationModifier());
			event.defaultFoodValues = values;
			event.modifiedFoodValues = values;
		}));
	}
}
