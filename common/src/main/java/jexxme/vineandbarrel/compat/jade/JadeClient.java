package jexxme.vineandbarrel.compat.jade;

import jexxme.vineandbarrel.tipsy.TipsyRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * The client-only part of the Jade plugin, in its own class: Jade loads {@link VBJadePlugin} on dedicated servers too,
 * where client classes such as {@link Minecraft} must never be touched (Fabric refuses to load them there).
 */
final class JadeClient
{
	private JadeClient() {
	}

	/** Whether the local player is out cold (the screen is black, so Jade shows nothing either). */
	static boolean blackedOut() {
		Player player = Minecraft.getInstance().player;
		Integer left = player == null ? null : TipsyRegistry.BLACKOUT.getExisting(player);
		return left != null && left > 0;
	}
}
