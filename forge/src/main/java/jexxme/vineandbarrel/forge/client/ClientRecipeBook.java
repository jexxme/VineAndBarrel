package jexxme.vineandbarrel.forge.client;

import jexxme.vineandbarrel.forge.ForgeRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** The client's end of {@link ForgeRecipeBook}'s settings packet (its own class: a dedicated server never loads it). */
public final class ClientRecipeBook
{
	private ClientRecipeBook() {
	}

	public static void receive(ForgeRecipeBook.Settings settings) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			settings.applyTo(player.getRecipeBook());
		}
	}
}
