package jexxme.vineandbarrel.neoforge;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.platform.ConfigValue;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * The common settings ({@link ConfigValue}) as NeoForge config files, exactly as before the split: the server file is
 * {@link ModConfig.Type#SYNCED} (per world, sent to every client so tooltips can follow it), the client file is each
 * player's own. Same file names, keys, comments and ranges.
 */
final class NeoForgeConfig
{
	private NeoForgeConfig() {
	}

	static void register(ModContainer container) {
		TipsyConfig.load();
		container.registerConfig(ModConfig.Type.SERVER, build(ConfigValue.Side.SERVER, TipsyConfig.SERVER_COMMENT), VineAndBarrel.MODID + "-tipsy-server.toml");
		container.registerConfig(ModConfig.Type.CLIENT, build(ConfigValue.Side.CLIENT, TipsyConfig.CLIENT_COMMENT), VineAndBarrel.MODID + "-tipsy-client.toml");
	}

	private static ModConfigSpec build(ConfigValue.Side side, String sectionComment) {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		List<ConfigValue<?>> values = ConfigValue.all().stream().filter(value -> value.side() == side).toList();
		builder.comment(sectionComment).push(TipsyConfig.SECTION);
		ModConfigSpec[] spec = new ModConfigSpec[1];
		for (ConfigValue<?> value : values) {
			if (!value.section().equals(TipsyConfig.SECTION)) {
				throw new IllegalStateException("Only the Tipsy section is supported so far: " + value);
			}
			define(builder, value, spec);
		}
		builder.pop();
		spec[0] = builder.build();
		return spec[0];
	}

	@SuppressWarnings("unchecked")
	private static void define(ModConfigSpec.Builder builder, ConfigValue<?> value, ModConfigSpec[] spec) {
		builder.comment(value.comment());
		Object defaultValue = value.getDefault();
		if (defaultValue instanceof Boolean flag) {
			bind((ConfigValue<Boolean>) value, builder.define(value.name(), flag.booleanValue()), spec);
		} else if (defaultValue instanceof Integer number) {
			bind((ConfigValue<Integer>) value, builder.defineInRange(value.name(), number.intValue(), (Integer) value.min(), (Integer) value.max()), spec);
		} else if (defaultValue instanceof Double number) {
			bind((ConfigValue<Double>) value, builder.defineInRange(value.name(), number.doubleValue(), (Double) value.min(), (Double) value.max()), spec);
		} else {
			throw new IllegalStateException("Unsupported setting type: " + value);
		}
	}

	/** Reads the file's value once it is loaded, the default before (main menu, early startup). */
	private static <T> void bind(ConfigValue<T> value, ModConfigSpec.ConfigValue<T> neo, ModConfigSpec[] spec) {
		value.bind(() -> spec[0] != null && spec[0].isLoaded() ? neo.get() : neo.getDefault(), neo::set);
	}
}
