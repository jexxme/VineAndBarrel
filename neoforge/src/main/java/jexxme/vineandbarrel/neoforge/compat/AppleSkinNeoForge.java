package jexxme.vineandbarrel.neoforge.compat;

import jexxme.vineandbarrel.compat.appleskin.DrinkFood;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import squeek.appleskin.api.event.FoodValuesEvent;

/**
 * AppleSkin on NeoForge (see {@link jexxme.vineandbarrel.compat.appleskin.AppleSkinCompat}): its food event gets a
 * drink's real food. Only loaded when AppleSkin is installed.
 */
public final class AppleSkinNeoForge
{
	private AppleSkinNeoForge() {
	}

	public static void register() {
		// First, so mods that change food values (AppleSkin's modified values) build on the drink's real food
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, AppleSkinNeoForge::onFoodValues);
	}

	private static void onFoodValues(FoodValuesEvent event) {
		DrinkFood.of(event.itemStack).ifPresent(food -> {
			event.defaultFoodProperties = food;
			event.modifiedFoodProperties = food;
		});
	}
}
