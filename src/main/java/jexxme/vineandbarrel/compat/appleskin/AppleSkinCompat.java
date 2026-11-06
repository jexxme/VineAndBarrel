package jexxme.vineandbarrel.compat.appleskin;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import squeek.appleskin.api.event.FoodValuesEvent;

/**
 * AppleSkin integration: its tooltip and hunger bar preview show what a drink really feeds. AppleSkin already reads
 * the food of our juices and meals by itself; wheat beer feeds through its Saturation buff instead ({@link DrinkFood}),
 * so we hand AppleSkin those values as the drink's own. Plain wines, ales and ciders feed nothing and stay without a
 * preview. AppleSkin is optional: only {@link jexxme.vineandbarrel.compat.ModCompat} loads this class, and only when
 * AppleSkin is installed.
 */
public final class AppleSkinCompat
{
	public static final String MODID = "appleskin";

	private AppleSkinCompat() {
	}

	public static void register() {
		// First, so mods that change food values (AppleSkin's modified values) build on the drink's real food
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, AppleSkinCompat::onFoodValues);
	}

	private static void onFoodValues(FoodValuesEvent event) {
		DrinkFood.of(event.itemStack).ifPresent(food -> {
			event.defaultFoodProperties = food;
			event.modifiedFoodProperties = food;
		});
	}
}
