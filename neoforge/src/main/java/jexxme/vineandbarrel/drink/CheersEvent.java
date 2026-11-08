package jexxme.vineandbarrel.drink;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

import java.util.List;

/**
 * Posted on {@code NeoForge.EVENT_BUS} whenever players drink together (D2 "Cheers!"), for every {@link DrankTogether}.
 * Award things to every player in {@link #players()}.
 */
public class CheersEvent extends Event
{
	private final ServerLevel level;
	private final List<Player> players;

	public CheersEvent(ServerLevel level, List<Player> players) {
		this.level = level;
		this.players = players;
	}

	public ServerLevel level() {
		return level;
	}

	/** Everyone who drank together, two or more. */
	public List<Player> players() {
		return players;
	}
}
