package jexxme.vineandbarrel.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientChatEvent;

import java.util.function.UnaryOperator;

/**
 * Drunk chat on NeoForge: the own chat message, right before it is signed and sent ({@link ClientChatEvent}), becomes
 * what {@code chat} makes of it (the client's {@code TipsyClient#ownChat}). Apart from the client class so a test can
 * post a real event to it on the server.
 */
public final class NeoForgeChat
{
	private NeoForgeChat() {
	}

	public static void register(IEventBus bus, UnaryOperator<String> chat) {
		bus.addListener(ClientChatEvent.class, event -> event.setMessage(chat.apply(event.getMessage())));
	}
}
