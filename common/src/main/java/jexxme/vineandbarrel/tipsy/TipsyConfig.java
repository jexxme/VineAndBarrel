package jexxme.vineandbarrel.tipsy;

import jexxme.vineandbarrel.platform.ConfigValue;

import static jexxme.vineandbarrel.platform.ConfigValue.Side.CLIENT;
import static jexxme.vineandbarrel.platform.ConfigValue.Side.SERVER;

/**
 * Tipsy settings. What each level does is {@link TipsyLevels}.
 * <ul>
 *   <li><b>Server</b> (per world, sent to every client so the effect tooltip can follow it): non-alcoholic mode, max
 *       level, decay speed, and one switch each for slurred chat, stumbling and tripping, the blackout and Rowdy (with
 *       two more to turn off just its punches or just the cut-off at the bar).</li>
 *   <li><b>Client</b>: how much Tipsy moves the screen (0 = never, for motion sickness; vanilla's Distortion Effects
 *       and FOV Effects sliders scale it too), whether tipsy players sway, and the vision effects. The client value
 *       always wins for what this client sees.</li>
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

	public static final ConfigValue<Boolean> ENABLED = ConfigValue.define(SERVER, SECTION, "enabled",
			"false = non-alcoholic mode: drinks never make you tipsy. Their buffs stay and their names don't change.", true);
	public static final ConfigValue<Integer> MAX_LEVEL = ConfigValue.defineInRange(SERVER, SECTION, "maxLevel",
			"Highest Tipsy level drinks can stack to", Tipsy.ABSOLUTE_MAX_LEVEL, 1, Tipsy.ABSOLUTE_MAX_LEVEL);
	public static final ConfigValue<Integer> SECONDS_PER_LEVEL = ConfigValue.defineInRange(SERVER, SECTION, "secondsPerLevel",
			"Decay speed: how many seconds each Tipsy level lasts before it drops by one", 90, 1, 3600);
	public static final ConfigValue<Boolean> SLURRED_CHAT = ConfigValue.define(SERVER, SECTION, "slurredChat",
			"Level 3+: chat messages come out slurred (stretched letters, the odd *hic*)", true);
	public static final ConfigValue<Boolean> STUMBLING = ConfigValue.define(SERVER, SECTION, "stumbling",
			"Level 5: unsteady legs while walking: now and then a stumble to the side, and once in a while a trip onto your face (never hurts), plus a slow drift to the side", true);
	public static final ConfigValue<Boolean> BLACKOUT = ConfigValue.define(SERVER, SECTION, "blackout",
			"One drink more at the top level: a short blackout, and you wake up somewhere", true);
	public static final ConfigValue<Boolean> ROWDY = ConfigValue.define(SERVER, SECTION, "rowdy",
			"Level 4+: Rowdy at all, the wild punches and being cut off at the bar. false turns both off (as it always did); rowdyPunches and cutOff turn off just one", true);
	public static final ConfigValue<Boolean> ROWDY_PUNCHES = ConfigValue.define(SERVER, SECTION, "rowdyPunches",
			"Level 4+ (with rowdy on): now and then a wild punch at whoever is in front (never pets, iron golems or armor stands; players only with PvP)", true);
	public static final ConfigValue<Boolean> CUT_OFF = ConfigValue.define(SERVER, SECTION, "cutOff",
			"Level 4+ (with rowdy on): villagers and wandering traders won't trade with you", true);

	public static final ConfigValue<Double> WOBBLE_INTENSITY = ConfigValue.defineInRange(CLIENT, SECTION, "wobbleIntensity",
			"How much Tipsy moves your screen: the sway, hiccups, stumbles and the Nausea spells. 0 keeps it still (for motion sickness), 1 is the default, 2 is double. Vanilla's Distortion Effects and FOV Effects sliders scale it too.", 1.0, 0.0, 2.0);
	public static final ConfigValue<Boolean> OTHER_PLAYERS_SWAY = ConfigValue.define(CLIENT, SECTION, "otherPlayersSway",
			"Show tipsy players (yourself in third person included) swaying, hiccuping and stumbling", true);
	public static final ConfigValue<Boolean> VISION_EFFECTS = ConfigValue.define(CLIENT, SECTION, "visionEffects",
			"Darker screen edges and slow, heavy-eyed blinks", true);
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

	public static double wobbleIntensity() {
		return WOBBLE_INTENSITY.get();
	}

	public static boolean visionEffects() {
		return VISION_EFFECTS.get();
	}

	public static double heartbeatVolume() {
		return HEARTBEAT_VOLUME.get();
	}

	public static boolean otherPlayersSway() {
		return OTHER_PLAYERS_SWAY.get();
	}
}
