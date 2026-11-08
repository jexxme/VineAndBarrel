package jexxme.vineandbarrel.fabric.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's Config button opens {@link VBConfigScreen}. Mod Menu is optional: without it this class is never loaded. */
public class VBModMenu implements ModMenuApi
{
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return VBConfigScreen::new;
	}
}
