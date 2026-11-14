package jexxme.vineandbarrel.tipsy;

import jexxme.vineandbarrel.platform.ConfigValue;

import static jexxme.vineandbarrel.platform.ConfigValue.Side.CLIENT;
import static jexxme.vineandbarrel.platform.ConfigValue.Side.SERVER;

/**
 * Tipsy settings. What each level does is {@link TipsyLevels}.
 * <ul>
 *   <li><b>Server</b> (per world, sent to every client so the effect tooltip can follow it): non-alcoholic mode, max
 *       level, decay speed, and one switch each for slurred chat, the legs (stumbling and tripping), nodding off, the
 *       hangover, the blackout and Rowdy (with two more to turn off just its punches or just the cut-off at the bar).</li>
 *   <li><b>Client</b>: how much being tipsy may move your own view ("drunk screen", 0 = never, for motion sickness;
 *       vanilla's Distortion Effects slider scales it too), double vision, whether tipsy players sway, and the
 *       heartbeat's volume. The client value always wins for what this client sees.</li>
 * </ul>
 * The loader stores them (NeoForge: {@code vineandbarrel-tipsy-server.toml} and {@code -client.toml}). Read values
 * through the accessors here: they fall back to the defaults while a config is not loaded yet (main menu, early
 * startup), so callers never have to care.
 */
public final class TipsyConfig
{
	/** The section all Tipsy settings are in. */
	public static final String SECTION = "tipsy";
	public static final String SERVER_COMMENT = "Tipsy: the effect alcoholic drinks stack, one level per drink (I-III the fun part, IV the turn, V really drunk, then a blackout)";
	public static final String CLIENT_COMMENT = "Tipsy visuals on this client";

	static {
		ConfigValue.describeSection(SERVER, SECTION, SERVER_COMMENT);
		ConfigValue.describeSection(CLIENT, SECTION, CLIENT_COMMENT);
	}

	public static final ConfigValue<Boolean> ENABLED = ConfigValue.define(SERVER, SECTION, "enabled",
			"false = non-alcoholic mode: drinks never make you tipsy. Their buffs stay and their names don't change.", true);
	public static final ConfigValue<Integer> MAX_LEVEL = ConfigValue.defineInRange(SERVER, SECTION, "maxLevel",
			"Highest Tipsy level drinks can stack to", Tipsy.ABSOLUTE_MAX_LEVEL, 1, Tipsy.ABSOLUTE_MAX_LEVEL);
	public static final ConfigValue<Integer> SECONDS_PER_LEVEL = ConfigValue.defineInRange(SERVER, SECTION, "secondsPerLevel",
			"Decay speed: how many seconds each Tipsy level lasts before it drops by one", 90, 1, 3600);
	public static final ConfigValue<Boolean> SLURRED_CHAT = ConfigValue.define(SERVER, SECTION, "slurredChat",
			"Tipsy players' chat messages come out slurred (links, mentions and commands stay as typed)", true);
	public static final ConfigValue<Boolean> STUMBLING = ConfigValue.define(SERVER, SECTION, "stumbling",
			"Drunk legs: tipsy players steer late, weave as they walk, stumble and trip (never over an edge, never hurts; sneaking walks straight)", true);
	public static final ConfigValue<Boolean> NODDING_OFF = ConfigValue.define(SERVER, SECTION, "noddingOff",
			"Level 4+: standing still too long, you nod off on your feet and jerk awake", true);
	public static final ConfigValue<Boolean> HANGOVER = ConfigValue.define(SERVER, SECTION, "hangover",
			"After a blackout, or a night's sleep at level 4+, you wake up hungover", true);
	public static final ConfigValue<Boolean> BLACKOUT = ConfigValue.define(SERVER, SECTION, "blackout",
			"One drink more at the top level: a short blackout, and you wake up somewhere", true);
	public static final ConfigValue<Boolean> ROWDY = ConfigValue.define(SERVER, SECTION, "rowdy",
			"Level 4+: Rowdy at all, the wild punches and being cut off at the bar. false turns both off (as it always did); rowdyPunches and cutOff turn off just one", true);
	public static final ConfigValue<Boolean> ROWDY_PUNCHES = ConfigValue.define(SERVER, SECTION, "rowdyPunches",
			"Level 4+ (with rowdy on): now and then a wild punch at whoever is in front (never pets, iron golems or armor stands; players only with PvP)", true);
	public static final ConfigValue<Boolean> CUT_OFF = ConfigValue.define(SERVER, SECTION, "cutOff",
			"Level 4+ (with rowdy on): villagers and wandering traders won't trade with you", true);

	public static final ConfigValue<Integer> DRUNK_SCREEN = ConfigValue.defineInRange(CLIENT, SECTION, "drunkScreen",
			"How much being tipsy moves your own view, in percent: a hiccup's flick, a stumble's dip, a trip. 0 keeps it still (for motion sickness), 100 is the default. Tipsy never moves your view on its own. Vanilla's Distortion Effects slider scales it too.", 100, 0, 100)
			// Was wobbleIntensity, 0 to 2 with 1 the default: 0 stays off, anything from 1 up is the full 100 %
			.formerly("wobbleIntensity", old -> {
				try {
					double wobble = Double.parseDouble(old);
					return wobble >= 0.0 && wobble <= 2.0 ? Long.toString(Math.round(Math.min(wobble, 1.0) * 100.0)) : null;
				} catch (NumberFormatException e) {
					return null;
				}
			});
	public static final ConfigValue<Boolean> OTHER_PLAYERS_SWAY = ConfigValue.define(CLIENT, SECTION, "otherPlayersSway",
			"Show tipsy players (yourself in third person included) swaying, hiccuping and stumbling", true);
	public static final ConfigValue<Boolean> DOUBLE_VISION = ConfigValue.define(CLIENT, SECTION, "doubleVision",
			"Drunk eyes: soft screen edges, double vision, blurred signs and the morning after's glare", true)
			// Was visionEffects (darker edges and blinks): whoever switched those off doesn't want these either
			.formerly("visionEffects", old -> old.equalsIgnoreCase("true") || old.equalsIgnoreCase("false") ? old.toLowerCase(java.util.Locale.ROOT) : null);
	public static final ConfigValue<Boolean> WOOLLY_SOUND = ConfigValue.define(CLIENT, SECTION, "woollySound",
			"Being tipsy muffles the sounds around you (your own heartbeat, music and menus stay clear)", true);
	public static final ConfigValue<Double> HEARTBEAT_VOLUME = ConfigValue.defineInRange(CLIENT, SECTION, "heartbeatVolume",
			"At Tipsy V you hear your own heart. 0 turns it off, 1 is the default.", 1.0, 0.0, 1.0);

	private TipsyConfig() {
	}

	/** Makes sure every setting above is declared (the loaders build their config files from {@link ConfigValue#all()}). */
	public static void load() {
	}

	public static boolean enabled() {
		return ENABLED.get();
	}

	public static int maxLevel() {
		return MAX_LEVEL.get();
	}

	public static int ticksPerLevel() {
		return SECONDS_PER_LEVEL.get() * 20;
	}

	public static boolean slurredChat() {
		return SLURRED_CHAT.get();
	}

	public static boolean noddingOff() {
		return NODDING_OFF.get();
	}

	public static boolean hangover() {
		return HANGOVER.get();
	}

	public static boolean blackout() {
		return BLACKOUT.get();
	}

	/** {@return whether Rowdy's wild punches are on: {@code rowdy} (the old switch for all of Rowdy) and {@code rowdyPunches}} */
	public static boolean rowdy() {
		return ROWDY.get() && ROWDY_PUNCHES.get();
	}

	/** {@return whether villagers and traders cut you off: {@code rowdy} (the old switch for all of Rowdy) and {@code cutOff}} */
	public static boolean cutOff() {
		return ROWDY.get() && CUT_OFF.get();
	}

	public static boolean stumbling() {
		return STUMBLING.get();
	}

	/** {@return how much Tipsy may move this player's own view, 0 (never) to 1 (fully)} */
	public static double drunkScreen() {
		return DRUNK_SCREEN.get() / 100.0;
	}

	public static boolean doubleVision() {
		return DOUBLE_VISION.get();
	}

	public static boolean woollySound() {
		return WOOLLY_SOUND.get();
	}

	public static double heartbeatVolume() {
		return HEARTBEAT_VOLUME.get();
	}

	public static boolean otherPlayersSway() {
		return OTHER_PLAYERS_SWAY.get();
	}
}
