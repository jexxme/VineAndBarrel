package jexxme.vineandbarrel.tipsy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * The drink one too many doesn't knock you out on the spot (Jexxme, playtest 2: "im Real Life wäre das ja auch nicht so,
 * dass man das Bier trinkt und dann direkt abkackt"): it catches up with you {@link #SOONEST} to {@link #LATEST} later,
 * whatever you are doing then, and the last {@link #TICKS} you feel yourself going under: the lids get heavy and the
 * head sinks, the world goes woolly and far away, the legs give and the body sways hard, and your friends see it
 * coming. Then the blackout as ever ({@link Blackout}).
 * <p>
 * The server keeps the game time it hits at ({@link TipsyRegistry#GOING_UNDER}, synced so every client draws the going
 * under from the same moment, smoothly, from its own game time). It only hits on your feet (or in a seat): gliding,
 * swimming, climbing or falling, it waits until you're steady. Asleep in a bed it waits too: a night's sleep sobers you
 * up and so gets you out of it; up again before that, the going under plays first. Sobering up first (milk, a night's
 * sleep) gets you out of it; another drink meanwhile never puts it off.
 */
public final class GoingUnder
{
	/** The soonest and the latest the blackout hits after the drink one too many (ticks). */
	public static final int SOONEST = 10 * 20, LATEST = 40 * 20;
	/** How long the going under lasts before it hits (ticks). */
	public static final int TICKS = 5 * 20;

	private GoingUnder() {
	}

	/** {@return the game time the blackout hits this player at, or -1 if it isn't coming} */
	public static long hitAt(Player player) {
		Long at = TipsyRegistry.GOING_UNDER.getExisting(player);
		return at == null ? -1L : at;
	}

	/** The drink one too many: the blackout is coming, some time from now (never later than one already coming). */
	public static void drankOneTooMany(ServerPlayer player, RandomSource random) {
		long at = player.level().getGameTime() + SOONEST + random.nextInt(LATEST - SOONEST + 1);
		long coming = hitAt(player);
		comeAt(player, coming >= 0L ? Math.min(coming, at) : at);
	}

	/** The blackout comes at this game time. */
	public static void comeAt(ServerPlayer player, long gameTime) {
		TipsyRegistry.GOING_UNDER.set(player, gameTime);
	}

	/** Once a tick on the server, while it is coming: the blackout when its time has come; sobered up, it's off. */
	static void tick(ServerPlayer player) {
		long at = hitAt(player);
		if (at < 0L) {
			return;
		}
		if (Blackout.isOut(player) || Tipsy.getLevel(player) <= 0 || !TipsyConfig.blackout() || player.isSpectator()) {
			TipsyRegistry.GOING_UNDER.remove(player);
			return;
		}
		long now = player.level().getGameTime();
		if (player.isSleeping()) {
			// asleep it waits (the night's sleep sobers you up, and that ends it); up again before that, the last
			// seconds still lie ahead: never in bed, never the moment you get up
			if (at < now + TICKS) {
				comeAt(player, now + TICKS + ASLEEP_SLACK);
			}
			return;
		}
		if (player.tickCount < AWAY && now > at) {
			// back after time away (logged out while it was coming): it comes again, the going under first
			comeAt(player, now + TICKS);
			return;
		}
		if (now >= at && steady(player)) {
			TipsyRegistry.GOING_UNDER.remove(player);
			Blackout.start(player);
		}
		// not on your feet on solid ground (gliding, swimming, climbing, falling): it waits until you are
	}

	/** A player who just joined (ticks since): time away from the world doesn't count. */
	private static final int AWAY = 40;
	/** Asleep, the blackout is put back this much further than its last seconds at a time (not once a tick). */
	private static final int ASLEEP_SLACK = 20;

	/** {@return whether the blackout may hit now: standing on solid ground, not gliding, swimming, climbing or asleep} */
	static boolean steady(ServerPlayer player) {
		return (player.onGround() || player.isPassenger()) && !player.isFallFlying() && !player.isInWater() && !player.isInLava()
			&& !player.onClimbable() && !player.isSwimming() && !player.isSleeping();
	}

	/** {@return how far gone under this player is now: 0 until the last {@link #TICKS}, then up to 1 as it hits} */
	public static float under(Player player, float partial) {
		long at = hitAt(player);
		if (at < 0L) {
			return 0.0F;
		}
		return under(at - (player.level().getGameTime() + partial));
	}

	/** {@return how far gone under with this many ticks left before the blackout hits} */
	public static float under(float ticksLeft) {
		float t = Mth.clamp(1.0F - ticksLeft / TICKS, 0.0F, 1.0F);
		// slow at first, then it takes you
		return t * t;
	}
}
