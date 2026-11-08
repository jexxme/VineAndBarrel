package jexxme.vineandbarrel.kettle;

import jexxme.vineandbarrel.VineAndBarrel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Sends the brewing recipes to clients (NeoForge recipe sync), the way FD sends its cooking recipes, so recipe viewers
 * such as JEI can show them. The kettle's own recipe book doesn't need this: it works from the server's displays.
 */
@EventBusSubscriber(modid = VineAndBarrel.MODID)
public final class BrewingRecipeSync
{
	private BrewingRecipeSync() {
	}

	@SubscribeEvent
	public static void onDatapackSync(OnDatapackSyncEvent event) {
		event.sendRecipes(KettleRegistry.BREWING.get());
	}
}
