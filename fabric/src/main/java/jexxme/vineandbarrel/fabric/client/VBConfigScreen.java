package jexxme.vineandbarrel.fabric.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.fabric.FabricConfig;
import jexxme.vineandbarrel.platform.ConfigValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The settings screen on Fabric (Mod Menu's Config button): the same settings, names and tooltips as NeoForge's
 * configuration screen, as vanilla options rows. Changes apply right away and are saved when the screen closes. The
 * world settings show only while they are this game's own (main menu, singleplayer not opened to LAN), as on NeoForge:
 * on a server, or with other players on this LAN world, they are what everyone plays by and stay as they are.
 */
public class VBConfigScreen extends OptionsSubScreen
{
	/** The same keys NeoForge's configuration screen reads ({@code <modid>.configuration.<name>}). */
	private static final String PREFIX = VineAndBarrel.MODID + ".configuration.";
	/** Steps per 1.0 on a slider for a decimal setting. */
	private static final int STEPS = 10;
	/** A whole-number setting with a range wider than this gets a coarser slider (see {@link #COARSE_STEP}). */
	private static final int COARSE_RANGE = 600;
	/** Its slider moves in steps of this, up to {@link #COARSE_RANGE} (or the current value); the file takes any value. */
	private static final int COARSE_STEP = 10;

	private final boolean worldSettings;

	public VBConfigScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(PREFIX + "title"));
		Minecraft minecraft = Minecraft.getInstance();
		this.worldSettings = minecraft.getConnection() == null
			|| minecraft.hasSingleplayerServer() && !minecraft.getSingleplayerServer().isPublished();
	}

	@Override
	protected void addOptions() {
		section(ConfigValue.Side.CLIENT);
		if (worldSettings) {
			section(ConfigValue.Side.SERVER);
		}
	}

	/** One side's settings, a header over each of its sections ({@code <modid>.configuration.header.<section>.<side>}). */
	private void section(ConfigValue.Side side) {
		for (String section : ConfigValue.sections(side)) {
			this.list.addHeader(Component.translatable(PREFIX + "header." + section + "." + side.name().toLowerCase(Locale.ROOT)));
			for (ConfigValue<?> value : ConfigValue.all()) {
				if (value.side() == side && value.section().equals(section)) {
					this.list.addBig(option(value));
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static OptionInstance<?> option(ConfigValue<?> value) {
		String key = PREFIX + value.name();
		Object current = value.get();
		if (current instanceof Boolean flag) {
			ConfigValue<Boolean> setting = (ConfigValue<Boolean>) value;
			return OptionInstance.createBoolean(key, OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip")), flag, setting::set);
		}
		if (current instanceof Integer number) {
			ConfigValue<Integer> setting = (ConfigValue<Integer>) value;
			if (setting.max() - setting.min() > COARSE_RANGE) {
				// Seconds per level goes up to an hour: in steps of 10 up to 10 minutes, so each step is a few pixels
				int top = Math.max(COARSE_RANGE, number) / COARSE_STEP + (Math.max(COARSE_RANGE, number) % COARSE_STEP == 0 ? 0 : 1);
				int bottom = Math.max(1, (setting.min() + COARSE_STEP - 1) / COARSE_STEP);
				return new OptionInstance<>(key, OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip")),
					(caption, steps) -> Options.genericValueLabel(caption, steps * COARSE_STEP), new OptionInstance.IntRange(bottom, top),
					Math.clamp(Math.round(number / (float) COARSE_STEP), bottom, top), steps -> setting.set(steps * COARSE_STEP));
			}
			return new OptionInstance<>(key, OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip")),
				(caption, shown) -> Options.genericValueLabel(caption, shown), new OptionInstance.IntRange(setting.min(), setting.max()), number, setting::set);
		}
		if (current instanceof Double number) {
			// A slider in tenths: 0.0, 0.1, ... (the wobble's 0 to 2)
			ConfigValue<Double> setting = (ConfigValue<Double>) value;
			int min = (int) Math.round(setting.min() * STEPS);
			int max = (int) Math.round(setting.max() * STEPS);
			return new OptionInstance<>(key, OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip")),
				(caption, steps) -> Options.genericValueLabel(caption, Component.literal(String.format(Locale.ROOT, "%.1f", steps / (double) STEPS))),
				new OptionInstance.IntRange(min, max), (int) Math.round(number * STEPS), steps -> setting.set(steps / (double) STEPS));
		}
		throw new IllegalStateException("Unsupported setting type: " + value);
	}

	@Override
	public void removed() {
		super.removed();
		FabricConfig.save(ConfigValue.Side.CLIENT, null);
		if (worldSettings) {
			FabricConfig.save(ConfigValue.Side.SERVER, Minecraft.getInstance().getSingleplayerServer());
		}
	}
}
