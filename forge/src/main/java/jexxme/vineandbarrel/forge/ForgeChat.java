package jexxme.vineandbarrel.forge;

import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.function.UnaryOperator;

/**
 * Drunk chat on Forge: the own chat message, right before it is signed and sent ({@link ClientChatEvent}), becomes
 * what {@code chat} makes of it (the client's {@code TipsyClient#ownChat}). Apart from the client class so a test can
 * post a real event to it on the server.
 */
public final class ForgeChat
{
	private ForgeChat() {
	}

	public static void register(IEventBus bus, UnaryOperator<String> chat) {
		bus.addListener(EventPriority.NORMAL, false, ClientChatEvent.class, event -> event.setMessage(chat.apply(event.getMessage())));
	}
}
