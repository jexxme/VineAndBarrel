package jexxme.vineandbarrel.fabric;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.platform.ConfigValue;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.world.WorldgenConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The common settings ({@link ConfigValue}) on Fabric: two plain files in the config folder,
 * {@code vineandbarrel-tipsy-server.properties} and {@code vineandbarrel-tipsy-client.properties}, with the same keys,
 * comments and ranges as NeoForge's TOML files. A missing or broken value falls back to the default and the file is
 * written back complete. As NeoForge's per-world (SYNCED) server config: a world can have its own server file,
 * {@code <world>/serverconfig/vineandbarrel-tipsy-server.properties}, which then wins over the global one while that
 * world runs; {@code /reload} reads the server file again and sends it to every connected player. A client on a server
 * uses that server's values while it is connected ({@link ServerConfigPayload}).
 */
public final class FabricConfig
{
	/** Each setting's current value (what {@link ConfigValue#get()} reads), and the value its own file gave it. */
	private static final Map<ConfigValue<?>, Object[]> CURRENT = new ConcurrentHashMap<>();
	private static final Map<ConfigValue<?>, Object> FROM_FILE = new ConcurrentHashMap<>();

	private FabricConfig() {
	}

	/** A setting's key in the files and in {@link ServerConfigPayload}: {@code section.name}. */
	static String key(ConfigValue<?> value) {
		return value.section() + "." + value.name();
	}

	/** On a client joining a server: that server's settings, until {@link #useOwnValues()}. Unknown keys are skipped. */
	public static void useServerValues(Map<String, String> values) {
		for (ConfigValue<?> value : ConfigValue.all()) {
			String text = values.get(key(value));
			Object[] current = CURRENT.get(value);
			if (value.side() == ConfigValue.Side.SERVER && text != null && current != null) {
				current[0] = parse(value, text);
			}
		}
	}

	/** On a client leaving a server: its own files' settings again. */
	public static void useOwnValues() {
		FROM_FILE.forEach((value, own) -> CURRENT.get(value)[0] = own);
	}

	static void load() {
		TipsyConfig.load();
		WorldgenConfig.load();
		for (ConfigValue.Side side : ConfigValue.Side.values()) {
			load(side, globalFile(side), true);
		}
	}

	static String fileName(ConfigValue.Side side) {
		return VineAndBarrel.MODID + "-tipsy-" + side.name().toLowerCase(java.util.Locale.ROOT) + ".properties";
	}

	static Path globalFile(ConfigValue.Side side) {
		return FabricLoader.getInstance().getConfigDir().resolve(fileName(side));
	}

	/** The server settings file for this server's world: its own one in {@code serverconfig} if it has one, else the global. */
	public static Path serverFile(MinecraftServer server) {
		Path world = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(fileName(ConfigValue.Side.SERVER));
		return Files.isRegularFile(world) ? world : globalFile(ConfigValue.Side.SERVER);
	}

	/** As a server starts or reloads: the server settings from its world's file, or the global one. */
	public static void loadServer(MinecraftServer server) {
		Path file = serverFile(server);
		load(ConfigValue.Side.SERVER, file, false);
		VineAndBarrel.LOGGER.info("Vine & Barrel server settings from {}", file);
	}

	/** As a server stops: the global server settings again (a singleplayer client may open another world next). */
	public static void unloadServer() {
		load(ConfigValue.Side.SERVER, globalFile(ConfigValue.Side.SERVER), true);
	}

	/**
	 * After the settings screen: writes one side's current values to its file. The client settings go to the global
	 * client file. The server settings go to the running singleplayer world's file ({@link #serverFile}), or the global
	 * one from the main menu ({@code server} null); only the global file's values become this game's own (what leaving
	 * a server goes back to).
	 */
	public static void save(ConfigValue.Side side, @Nullable MinecraftServer server) {
		Path file = side == ConfigValue.Side.SERVER && server != null ? serverFile(server) : globalFile(side);
		if (file.equals(globalFile(side))) {
			for (ConfigValue<?> value : ConfigValue.all()) {
				if (value.side() == side && CURRENT.containsKey(value)) {
					FROM_FILE.put(value, CURRENT.get(value)[0]);
				}
			}
		}
		write(side, file);
	}

	/**
	 * Reads one side's file into the settings and writes it back complete. {@code own}: the file is this game's own
	 * global one, whose values a client goes back to after leaving a server.
	 */
	private static void load(ConfigValue.Side side, Path file, boolean own) {
		Properties saved = new Properties();
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				saved.load(reader);
			} catch (IOException e) {
				VineAndBarrel.LOGGER.warn("Could not read {}, using the defaults", file, e);
			}
		}
		for (ConfigValue<?> value : ConfigValue.all()) {
			if (value.side() != side) {
				continue;
			}
			// A renamed setting still saved under its old key takes that value once; the file is written with the new key
			set(value, value.savedText(name -> saved.getProperty(value.section() + "." + name)), own);
		}
		write(side, file);
	}

	/** The file complete, section by section (each with its comment), with every setting's comment, range and current value. */
	private static void write(ConfigValue.Side side, Path file) {
		StringBuilder out = new StringBuilder();
		for (String section : ConfigValue.sections(side)) {
			out.append(out.isEmpty() ? "" : "\n").append("# Vine & Barrel: ").append(ConfigValue.sectionComment(side, section)).append('\n');
			for (ConfigValue<?> value : ConfigValue.all()) {
				if (value.side() != side || !value.section().equals(section)) {
					continue;
				}
				out.append('\n').append("# ").append(value.comment()).append('\n');
				if (value.min() != null) {
					out.append("# Range: ").append(value.min()).append(" ~ ").append(value.max()).append('\n');
				}
				out.append(key(value)).append('=').append(value.get()).append('\n');
			}
		}
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				writer.write(out.toString());
			}
		} catch (IOException e) {
			VineAndBarrel.LOGGER.warn("Could not write {}", file, e);
		}
	}

	/** The setting's value from its file: bound on the first read, changed in place after that. */
	@SuppressWarnings("unchecked")
	private static <T> void set(ConfigValue<T> value, String text, boolean own) {
		T parsed = parse(value, text);
		Object[] current = CURRENT.get(value);
		if (current == null) {
			Object[] holder = {parsed};
			CURRENT.put(value, holder);
			value.bind(() -> (T) holder[0], newValue -> holder[0] = newValue);
		} else {
			current[0] = parsed;
		}
		if (own) {
			FROM_FILE.put(value, parsed);
		}
	}

	/** The saved value, if it is one this setting can hold (in range); otherwise the default. */
	@SuppressWarnings("unchecked")
	private static <T> T parse(ConfigValue<T> value, String text) {
		if (text == null) {
			return value.getDefault();
		}
		try {
			Object defaultValue = value.getDefault();
			Object parsed;
			if (defaultValue instanceof Boolean) {
				if (!text.trim().equalsIgnoreCase("true") && !text.trim().equalsIgnoreCase("false")) {
					return value.getDefault();
				}
				parsed = Boolean.parseBoolean(text.trim());
			} else if (defaultValue instanceof Integer) {
				int number = Integer.parseInt(text.trim());
				parsed = number < (Integer) value.min() || number > (Integer) value.max() ? defaultValue : number;
			} else if (defaultValue instanceof Double) {
				double number = Double.parseDouble(text.trim());
				parsed = number < (Double) value.min() || number > (Double) value.max() ? defaultValue : number;
			} else {
				return value.getDefault();
			}
			return (T) parsed;
		} catch (NumberFormatException e) {
			return value.getDefault();
		}
	}
}
