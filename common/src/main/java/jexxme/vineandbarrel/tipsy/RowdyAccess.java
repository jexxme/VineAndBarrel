package jexxme.vineandbarrel.tipsy;

import net.minecraft.server.level.ServerPlayer;

/** For tests: one Rowdy tick for a player at their own level. */
public final class RowdyAccess
{
	private RowdyAccess() {
	}

	public static boolean tick(ServerPlayer player) {
		return Rowdy.tick(player, TipsyLevels.at(Tipsy.getLevel(player)));
	}

	/** Spoiling for a fight right now. */
	public static void itch(ServerPlayer player) {
		TipsyRegistry.ROWDY_ITCH.set(player, Rowdy.ITCH);
	}

}
