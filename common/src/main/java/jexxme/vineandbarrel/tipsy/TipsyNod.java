package jexxme.vineandbarrel.tipsy;

import jexxme.vineandbarrel.tipsy.TipsyLevels.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Nodding off on your feet (Tipsy overhaul, step 3): at IV and V, stand still a while and your lids sink, your head drops,
 * and with a jerk (and a snort) you're awake again; stand on and it comes back. Moving, turning or using
 * anything wakes you. The eyes only close when you earned it: Tipsy has no random blinks.
 * <p>
 * The server decides ({@link #tick}): it counts how long the player stands still and, once that reaches the level's
 * {@link TipsyLevels.Nod#stillTicks}, sets {@link TipsyRegistry#NOD} to the game time the nodding started (synced to the
 * player and everyone near; -1 when awake). Standing still means: not further than a little from where the stillness began
 * (so a slow creep adds up), not looking further than a few degrees from where you looked then (so a slow turn adds up),
 * and no action at all (a swing of the arm, mining, attacking, placing, using, a key, a menu: anything the server marks
 * as the player's own action, vanilla's idle timer). Rowdy's wild swings are the drink's, not the player's: they don't
 * wake anyone. Every client draws the same nod from it ({@link #nod}): the own lids and
 * the head going down, and for everyone else the chin sinking and the body sagging. The server plays the snort at each
 * jerk, so everyone hears it at the same moment. Switched off with the server's {@code noddingOff} setting.
 */
public final class TipsyNod
{
	/** Of one nod's cycle: the share spent sinking, then the jerk awake, then a pause. */
	public static final float SINK_SHARE = 0.78F, JERK_SHARE = 0.05F;
	/** Fully sunk, the own view looks this far down (degrees) and the lids are this far shut. */
	public static final float HEAD_DROP = 24.0F, LIDS = 0.82F;
	/** The jerk awake throws the head this far back up past level (degrees). */
	public static final float JERK = 4.0F;
	/** For others: the chin sinks this far (degrees of head pitch) and the body sags this far forward. */
	public static final float CHIN = 55.0F, SAG = 12.0F;
	/** Further than this (blocks) from where the stillness began, or looking further than this (degrees), is moving. */
	public static final double STILL_MOVE = 0.25;
	public static final float STILL_TURN = 6.0F;

	/**
	 * Where the player stood and looked when the stillness began, the server's last action time for them then, and for
	 * how many ticks they have stood still since (server only).
	 */
	public record Stillness(double x, double y, double z, float yaw, float pitch, long action, int ticks)
	{
	}

	private TipsyNod() {
	}

	/** {@return the game time this player's nodding started, or -1 when awake} */
	public static long start(Player player) {
		Long start = TipsyRegistry.NOD.getExisting(player);
		return start == null ? -1L : start;
	}

	/** Once a tick on the server, for a tipsy player: counts the standing still, starts the nodding, snorts at each jerk. */
	static void tick(ServerPlayer player, Level at) {
		TipsyLevels.Nod nod = at.nod();
		boolean can = TipsyConfig.noddingOff() && nod.any() && !Blackout.isOut(player) && !TipsyLegs.isDown(player) && !player.isSpectator()
			&& !player.isSleeping() && !player.isSwimming() && !player.isFallFlying() && !player.getAbilities().flying;
		Stillness was = TipsyRegistry.STILL.getExisting(player);
		boolean still = was != null && can && isStill(was, player.position().x, player.position().y, player.position().z, player.getYRot(),
			player.getXRot(), player.getLastActionTime(), player.isUsingItem());
		// still: counting on from where it began; moved (or anything done): it begins again here
		TipsyRegistry.STILL.set(player, still ? new Stillness(was.x(), was.y(), was.z(), was.yaw(), was.pitch(), was.action(), was.ticks() + 1)
			: new Stillness(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), player.getLastActionTime(), 0));
		int ticks = still ? was.ticks() + 1 : 0;
		long time = player.level().getGameTime();
		long start = start(player);
		if (ticks >= nod.stillTicks() && can) {
			if (start < 0) {
				TipsyRegistry.NOD.set(player, time);
			} else if (Math.floorMod(time - start, (long) nod.cycleTicks()) == jerkTick(nod.cycleTicks())) {
				TipsySounds.snort(player);
			}
		} else if (start >= 0) {
			wake(player);
		}
	}

	/**
	 * {@return whether a player is still standing still since {@code since}: no further than {@link #STILL_MOVE} from
	 * where it began, looking no further than {@link #STILL_TURN} from how they looked then, no action since}
	 *
	 * @param action the server's last action time for the player now
	 * @param busy   using an item right now
	 */
	public static boolean isStill(Stillness since, double x, double y, double z, float yaw, float pitch, long action, boolean busy) {
		double dx = x - since.x(), dy = y - since.y(), dz = z - since.z();
		return !busy && action == since.action() && dx * dx + dy * dy + dz * dz < STILL_MOVE * STILL_MOVE
			&& Math.abs(Mth.wrapDegrees(yaw - since.yaw())) < STILL_TURN && Math.abs(pitch - since.pitch()) < STILL_TURN;
	}

	/** Awake (moved, sobered up, out cold): the nodding stops for everyone. */
	static void wake(Player player) {
		if (TipsyRegistry.NOD.has(player)) {
			TipsyRegistry.NOD.remove(player);
		}
	}

	/** {@return the tick of a nod's cycle at which the jerk awake comes} */
	public static int jerkTick(int cycle) {
		return Math.round(cycle * SINK_SHARE);
	}

	/**
	 * {@return how far into a nod: 0 awake, rising to 1 as the head goes all the way down; negative during the jerk awake
	 * (down to -1 at its top)}
	 *
	 * @param elapsed ticks since the nodding started (may be fractional)
	 * @param cycle   one nod's length in ticks
	 */
	public static float nod(float elapsed, int cycle) {
		if (elapsed < 0.0F || cycle <= 0) {
			return 0.0F;
		}
		float p = (elapsed % cycle) / cycle;
		if (p < SINK_SHARE) {
			// slow at first (fighting it), then it gives
			float t = p / SINK_SHARE;
			return t * t * (3.0F - 2.0F * t) * (0.6F + 0.4F * t);
		}
		if (p < SINK_SHARE + JERK_SHARE) {
			return Mth.lerp((p - SINK_SHARE) / JERK_SHARE, 1.0F, -1.0F);
		}
		float t = (p - SINK_SHARE - JERK_SHARE) / (1.0F - SINK_SHARE - JERK_SHARE);
		float back = 1.0F - t * 4.0F;
		return back > 0.0F ? -back : 0.0F;
	}

	/** {@return this player's nod right now (see {@link #nod(float, int)}), from the synced start, on any client} */
	public static float nod(Player player, float partial) {
		// Going under: the lids get heavy and the head sinks, further and further, no jerk awake this time
		float under = UNDER * GoingUnder.under(player, partial);
		long start = start(player);
		if (start < 0) {
			return under;
		}
		// (another player's effects aren't sent to this client: their synced level is)
		Integer synced = TipsyRegistry.SYNCED_LEVEL.getExisting(player);
		int level = synced != null ? synced : Tipsy.getLevel(player);
		TipsyLevels.Nod nod = TipsyLevels.at(level).nod();
		float nodding = nod(player.level().getGameTime() - start + partial, nod.cycleTicks());
		return under > 0.0F ? Math.max(under, nodding) : nodding;
	}

	/** Gone under all the way, the nod is this far down (the blackout's own lids take it from there). */
	public static final float UNDER = 0.85F;
}
