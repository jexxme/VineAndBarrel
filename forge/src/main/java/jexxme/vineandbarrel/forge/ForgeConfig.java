package jexxme.vineandbarrel.forge;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.platform.ConfigValue;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

/**
 * The common settings ({@link ConfigValue}) as Forge config files, exactly as on NeoForge: the server file is
 * {@link ModConfig.Type#SYNCED} (per world, sent to every client so tooltips can follow it), the client file is each
 * player's own. Same file names, keys, comments and ranges.
 */
final class ForgeConfig
{
	private ForgeConfig() {
	}

	static void register() {
		TipsyConfig.load();
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, build(ConfigValue.Side.SERVER, TipsyConfig.SERVER_COMMENT), VineAndBarrel.MODID + "-tipsy-server.toml");
		ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, build(ConfigValue.Side.CLIENT, TipsyConfig.CLIENT_COMMENT), VineAndBarrel.MODID + "-tipsy-client.toml");
	}

	private static ForgeConfigSpec build(ConfigValue.Side side, String sectionComment) {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		List<ConfigValue<?>> values = ConfigValue.all().stream().filter(value -> value.side() == side).toList();
		builder.comment(sectionComment).push(TipsyConfig.SECTION);
		ForgeConfigSpec[] spec = new ForgeConfigSpec[1];
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
	private static void define(ForgeConfigSpec.Builder builder, ConfigValue<?> value, ForgeConfigSpec[] spec) {
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
	private static <T> void bind(ConfigValue<T> value, ForgeConfigSpec.ConfigValue<T> forge, ForgeConfigSpec[] spec) {
		value.bind(() -> spec[0] != null && spec[0].isLoaded() ? forge.get() : forge.getDefault(), forge::set);
	}
}
