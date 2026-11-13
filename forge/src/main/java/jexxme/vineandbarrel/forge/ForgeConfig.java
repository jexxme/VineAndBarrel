package jexxme.vineandbarrel.forge;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.platform.ConfigValue;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The common settings ({@link ConfigValue}) as Forge config files, exactly as on NeoForge: the server file is
 * {@link ModConfig.Type#SYNCED} (per world, sent to every client so tooltips can follow it), the client file is each
 * player's own. Same file names, keys, comments and ranges.
 * <p>
 * A renamed client setting ({@link ConfigValue#formerly}) still saved under its old key takes that value once: the old
 * key is read from the file before Forge loads it (Forge drops keys it doesn't know when it corrects the file), and the
 * converted value is set and saved once the file is loaded (Forge 47 loads the client file right after the mods are
 * constructed).
 */
public final class ForgeConfig
{
	private ForgeConfig() {
	}

	/** Each setting's Forge value, for setting a renamed one's carried-over value. */
	private static final Map<ConfigValue<?>, ForgeConfigSpec.ConfigValue<?>> FORGE = new HashMap<>();

	static void register() {
		TipsyConfig.load();
		String clientFile = VineAndBarrel.MODID + "-tipsy-client.toml";
		Map<ConfigValue<?>, String> carried = carriedOver(FMLPaths.CONFIGDIR.get().resolve(clientFile));
		ForgeConfigSpec client = build(ConfigValue.Side.CLIENT, TipsyConfig.CLIENT_COMMENT);
		if (!carried.isEmpty()) {
			FMLJavaModLoadingContext.get().getModEventBus().addListener((ModConfigEvent.Loading event) -> {
				if (event.getConfig().getSpec() == client) {
					carryOver(carried, FORGE, client);
					VineAndBarrel.LOGGER.info("Vine & Barrel: carried {} renamed setting(s) over into {}", carried.size(), clientFile);
				}
			});
		}
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, build(ConfigValue.Side.SERVER, TipsyConfig.SERVER_COMMENT), VineAndBarrel.MODID + "-tipsy-server.toml");
		ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, client, clientFile);
	}

	/** {@code key = value} in the Tipsy section of a TOML file, values unquoted (numbers and booleans). */
	private static final Pattern LINE = Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s*=\\s*\"?([^\"#]*?)\"?\\s*(#.*)?$");

	/** The client settings a file saved under their old keys only, with the values they carry over (empty if none). */
	public static Map<ConfigValue<?>, String> carriedOver(Path file) {
		Map<ConfigValue<?>, String> carried = new HashMap<>();
		if (!Files.isRegularFile(file)) {
			return carried;
		}
		Map<String, String> saved = new HashMap<>();
		try {
			boolean inSection = false;
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				String trimmed = line.trim();
				if (trimmed.startsWith("[")) {
					inSection = trimmed.equals("[" + TipsyConfig.SECTION + "]");
					continue;
				}
				Matcher match = LINE.matcher(line);
				if (inSection && match.matches()) {
					saved.put(match.group(1), match.group(2).trim());
				}
			}
		} catch (IOException e) {
			VineAndBarrel.LOGGER.warn("Could not read {} for renamed settings", file, e);
			return carried;
		}
		for (ConfigValue<?> value : ConfigValue.all()) {
			if (value.side() == ConfigValue.Side.CLIENT && value.formerName() != null && !saved.containsKey(value.name())) {
				String text = value.savedText(saved::get);
				if (text != null) {
					carried.put(value, text);
				}
			}
		}
		return carried;
	}

	/**
	 * Once the client file is loaded: sets each carried-over setting to its value in {@code spec} (whose values are
	 * {@code values}) and saves the file.
	 */
	public static void carryOver(Map<ConfigValue<?>, String> carried, Map<ConfigValue<?>, ForgeConfigSpec.ConfigValue<?>> values, ForgeConfigSpec spec) {
		carried.forEach((value, text) -> setText(values.get(value), value, text));
		spec.save();
	}

	/** One side's settings as a spec of its own, not bound to the game's settings (for tests), and each setting's value in it. */
	public record Detached(ForgeConfigSpec spec, Map<ConfigValue<?>, ForgeConfigSpec.ConfigValue<?>> values)
	{
	}

	/** {@return one side's settings as a spec of its own, not bound to the game's settings} */
	public static Detached detached(ConfigValue.Side side, String sectionComment) {
		Map<ConfigValue<?>, ForgeConfigSpec.ConfigValue<?>> values = new HashMap<>();
		return new Detached(build(side, sectionComment, values, false), values);
	}

	@SuppressWarnings("unchecked")
	private static void setText(ForgeConfigSpec.ConfigValue<?> forge, ConfigValue<?> value, String text) {
		Object defaultValue = value.getDefault();
		try {
			if (defaultValue instanceof Boolean) {
				((ForgeConfigSpec.ConfigValue<Boolean>) forge).set(Boolean.parseBoolean(text));
			} else if (defaultValue instanceof Integer) {
				((ForgeConfigSpec.ConfigValue<Integer>) forge).set(Integer.parseInt(text));
			} else if (defaultValue instanceof Double) {
				((ForgeConfigSpec.ConfigValue<Double>) forge).set(Double.parseDouble(text));
			}
		} catch (NumberFormatException e) {
			VineAndBarrel.LOGGER.warn("Could not carry {} over to {}", text, value);
		}
	}

	private static ForgeConfigSpec build(ConfigValue.Side side, String sectionComment) {
		return build(side, sectionComment, FORGE, true);
	}

	/** {@return one side's spec}, each setting's value put in {@code values}, and bound to the game's setting if {@code live} */
	private static ForgeConfigSpec build(ConfigValue.Side side, String sectionComment, Map<ConfigValue<?>, ForgeConfigSpec.ConfigValue<?>> values,
		boolean live) {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		List<ConfigValue<?>> settings = ConfigValue.all().stream().filter(value -> value.side() == side).toList();
		builder.comment(sectionComment).push(TipsyConfig.SECTION);
		ForgeConfigSpec[] spec = new ForgeConfigSpec[1];
		for (ConfigValue<?> value : settings) {
			if (!value.section().equals(TipsyConfig.SECTION)) {
				throw new IllegalStateException("Only the Tipsy section is supported so far: " + value);
			}
			define(builder, value, spec, values, live);
		}
		builder.pop();
		spec[0] = builder.build();
		return spec[0];
	}

	@SuppressWarnings("unchecked")
	private static void define(ForgeConfigSpec.Builder builder, ConfigValue<?> value, ForgeConfigSpec[] spec,
		Map<ConfigValue<?>, ForgeConfigSpec.ConfigValue<?>> values, boolean live) {
		builder.comment(value.comment());
		Object defaultValue = value.getDefault();
		if (defaultValue instanceof Boolean flag) {
			bind((ConfigValue<Boolean>) value, builder.define(value.name(), flag.booleanValue()), spec, values, live);
		} else if (defaultValue instanceof Integer number) {
			bind((ConfigValue<Integer>) value, builder.defineInRange(value.name(), number.intValue(), (Integer) value.min(), (Integer) value.max()), spec,
				values, live);
		} else if (defaultValue instanceof Double number) {
			bind((ConfigValue<Double>) value, builder.defineInRange(value.name(), number.doubleValue(), (Double) value.min(), (Double) value.max()), spec,
				values, live);
		} else {
			throw new IllegalStateException("Unsupported setting type: " + value);
		}
	}

	/** Reads the file's value once it is loaded, the default before (main menu, early startup). */
	private static <T> void bind(ConfigValue<T> value, ForgeConfigSpec.ConfigValue<T> forge, ForgeConfigSpec[] spec,
		Map<ConfigValue<?>, ForgeConfigSpec.ConfigValue<?>> values, boolean live) {
		values.put(value, forge);
		if (live) {
			value.bind(() -> spec[0] != null && spec[0].isLoaded() ? forge.get() : forge.getDefault(), forge::set);
		}
	}
}
