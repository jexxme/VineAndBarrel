package jexxme.vineandbarrel.compat;

import jexxme.vineandbarrel.compat.appleskin.AppleSkinCompat;
import jexxme.vineandbarrel.platform.Services;

/**
 * The optional integrations that need code at runtime, each switched on only when its mod is installed, so none of
 * their classes load without it. JEI and Jade find their plugins themselves; Farmer's Delight is wired in
 * {@link jexxme.vineandbarrel.compat.farmersdelight.FDCompat}. The thirst and nutrition mods need no code at all, only
 * data ({@link CompatData}).
 */
public final class ModCompat
{
	private ModCompat() {
	}

	public static void register() {
		if (Services.PLATFORM.isModLoaded(AppleSkinCompat.MODID)) {
			Services.PLATFORM.registerAppleSkin();
		}
	}
}
