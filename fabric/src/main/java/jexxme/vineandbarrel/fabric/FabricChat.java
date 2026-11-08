package jexxme.vineandbarrel.fabric;

import jexxme.vineandbarrel.tipsy.TipsyEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Slurred chat on Fabric: what a typed chat message becomes before the server's decorators run (see the packet mixin). */
public final class FabricChat
{
	private FabricChat() {
	}

	public static Component slur(ServerPlayer player, Component typed) {
		Component slurred = TipsyEvents.onChat(player, typed.getString());
		return slurred != null ? slurred : typed;
	}
}
