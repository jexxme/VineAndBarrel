package jexxme.vineandbarrel.drink;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Players drank together (D2 "Cheers!"): the hook for the "Cheers!" advancement (M7), a plain listener list on every
 * loader. On NeoForge each one is also posted on {@code NeoForge.EVENT_BUS} as {@code jexxme.vineandbarrel.drink.CheersEvent},
 * as before the loaders split, so bus listeners keep working.
 *
 * @param players everyone who drank together, two or more
 */
public record DrankTogether(ServerLevel level, List<Player> players)
{
	private static final List<Consumer<DrankTogether>> LISTENERS = new CopyOnWriteArrayList<>();

	public static void addListener(Consumer<DrankTogether> listener) {
		LISTENERS.add(listener);
	}

	public static void removeListener(Consumer<DrankTogether> listener) {
		LISTENERS.remove(listener);
	}

	/** Tells every listener. */
	public static void post(DrankTogether cheers) {
		LISTENERS.forEach(listener -> listener.accept(cheers));
	}
}
