package jexxme.vineandbarrel.tipsy;

import jexxme.vineandbarrel.VineAndBarrel;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Tipsy settings (DESIGN.md §6).
 * <ul>
 *   <li><b>Server</b> ({@link ModConfig.Type#SERVER}: per world, sent to every client so tooltips can follow it):
 *       non-alcoholic mode, max level, decay speed, slurred chat, stumbling.</li>
 *   <li><b>Client</b>: camera wobble intensity (0 = off, for motion sickness) and whether other players sway.
 *       The client value always wins for camera effects.</li>
 * </ul>
 * Read values through the accessors here: they fall back to the defaults while a config is not loaded yet
 * (main menu, early startup), so callers never have to care.
 */
public final class TipsyConfig
{
	public static final ModConfigSpec SERVER_SPEC;
	public static final ModConfigSpec CLIENT_SPEC;

	public static final ModConfigSpec.BooleanValue ENABLED;
	public static final ModConfigSpec.IntValue MAX_LEVEL;
	public static final ModConfigSpec.IntValue SECONDS_PER_LEVEL;
	public static final ModConfigSpec.BooleanValue SLURRED_CHAT;
	public static final ModConfigSpec.BooleanValue STUMBLING;
	public static final ModConfigSpec.BooleanValue BLACKOUT;
	public static final ModConfigSpec.BooleanValue ROWDY;

	public static final ModConfigSpec.DoubleValue WOBBLE_INTENSITY;
	public static final ModConfigSpec.BooleanValue OTHER_PLAYERS_SWAY;
	public static final ModConfigSpec.BooleanValue VISION_EFFECTS;

	static {
		ModConfigSpec.Builder server = new ModConfigSpec.Builder();
		server.comment("Tipsy: the effect alcoholic drinks stack (DESIGN: level 1-2 wobble, 3 hiccups, 4 slurred chat, 5 Slowness, Mining Fatigue and stumbling)").push("tipsy");
		ENABLED = server.comment("false = non-alcoholic mode: drinks never make you tipsy. Their buffs stay and their names don't change.")
				.define("enabled", true);
		MAX_LEVEL = server.comment("Highest Tipsy level drinks can stack to")
				.defineInRange("maxLevel", Tipsy.ABSOLUTE_MAX_LEVEL, 1, Tipsy.ABSOLUTE_MAX_LEVEL);
		SECONDS_PER_LEVEL = server.comment("Decay speed: how many seconds each Tipsy level lasts before it drops by one")
				.defineInRange("secondsPerLevel", 90, 1, 3600);
		SLURRED_CHAT = server.comment("Level 4+: chat messages come out slurred (stretched letters, the odd *hic*)")
				.define("slurredChat", true);
		STUMBLING = server.comment("Level 5: a rare small sideways nudge while walking")
				.define("stumbling", true);
		BLACKOUT = server.comment("One drink more at the top level: a short blackout, and you wake up somewhere else (usually your bed)")
				.define("blackout", true);
		ROWDY = server.comment("Level 4+: now and then a wild punch at whoever is in front (never pets; players only with PvP), and villagers won't trade")
				.define("rowdy", true);
		server.pop();
		SERVER_SPEC = server.build();

		ModConfigSpec.Builder client = new ModConfigSpec.Builder();
		client.comment("Tipsy visuals on this client").push("tipsy");
		WOBBLE_INTENSITY = client.comment("Camera wobble strength while tipsy. 0 turns it off (for motion sickness), 1 is the default, 2 is double. This always wins over the server.")
				.defineInRange("wobbleIntensity", 1.0, 0.0, 2.0);
		OTHER_PLAYERS_SWAY = client.comment("Show tipsy players (yourself in third person included) swaying a little")
				.define("otherPlayersSway", true);
		VISION_EFFECTS = client.comment("Darker screen edges from level 1 and slow, heavy-eyed blinks from level 3")
				.define("visionEffects", true);
		client.pop();
		CLIENT_SPEC = client.build();
	}

	private TipsyConfig() {
	}

	public static void register(ModContainer container) {
		container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, VineAndBarrel.MODID + "-tipsy-server.toml");
		container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, VineAndBarrel.MODID + "-tipsy-client.toml");
	}

	public static boolean enabled() {
		return value(SERVER_SPEC, ENABLED);
	}

	public static int maxLevel() {
		return value(SERVER_SPEC, MAX_LEVEL);
	}

	public static int ticksPerLevel() {
		return value(SERVER_SPEC, SECONDS_PER_LEVEL) * 20;
	}

	public static boolean slurredChat() {
		return value(SERVER_SPEC, SLURRED_CHAT);
	}

	public static boolean blackout() {
		return value(SERVER_SPEC, BLACKOUT);
	}

	public static boolean rowdy() {
		return value(SERVER_SPEC, ROWDY);
	}

	public static boolean stumbling() {
		return value(SERVER_SPEC, STUMBLING);
	}

	public static double wobbleIntensity() {
		return value(CLIENT_SPEC, WOBBLE_INTENSITY);
	}

	public static boolean visionEffects() {
		return value(CLIENT_SPEC, VISION_EFFECTS);
	}

	public static boolean otherPlayersSway() {
		return value(CLIENT_SPEC, OTHER_PLAYERS_SWAY);
	}

	private static <T> T value(ModConfigSpec spec, ModConfigSpec.ConfigValue<T> value) {
		return spec.isLoaded() ? value.get() : value.getDefault();
	}
}
