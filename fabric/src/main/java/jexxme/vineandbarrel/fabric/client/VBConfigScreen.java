package jexxme.vineandbarrel.fabric.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.fabric.FabricConfig;
import jexxme.vineandbarrel.platform.ConfigValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
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

	/** As wide as a full-width option row. */
	private static final int HEADER_WIDTH = 310;

	private final boolean worldSettings;
	private Rows list;

	public VBConfigScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(PREFIX + "title"));
		Minecraft minecraft = Minecraft.getInstance();
		this.worldSettings = minecraft.getConnection() == null
			|| minecraft.hasSingleplayerServer() && !minecraft.getSingleplayerServer().isPublished();
	}

	/** The list and the Done button, laid out as vanilla's own options screens are on this Minecraft. */
	@Override
	protected void init() {
		this.list = new Rows(this.minecraft, this.width, this.height);
		section(ConfigValue.Side.CLIENT);
		if (worldSettings) {
			section(ConfigValue.Side.SERVER);
		}
		this.addWidget(this.list);
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.minecraft.setScreen(this.lastScreen))
			.bounds(this.width / 2 - 100, this.height - 27, 200, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// What vanilla's basicListRender does for its own options list
		this.renderBackground(graphics);
		this.list.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 16777215);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	/** One side's settings, a header over each of its sections ({@code <modid>.configuration.header.<section>.<side>}). */
	private void section(ConfigValue.Side side) {
		for (String section : ConfigValue.sections(side)) {
			// A header row: the section's name across the row, centred (this Minecraft's options list has no header rows of its own)
			Component header = Component.translatable(PREFIX + "header." + section + "." + side.name().toLowerCase(Locale.ROOT));
			this.list.add(new StringWidget(this.width / 2 - HEADER_WIDTH / 2, 0, HEADER_WIDTH, Button.DEFAULT_HEIGHT, header, this.font));
			for (ConfigValue<?> value : ConfigValue.all()) {
				if (value.side() == side && value.section().equals(section)) {
					this.list.add(option(value).createButton(this.options, this.width / 2 - HEADER_WIDTH / 2, 0, HEADER_WIDTH));
				}
			}
		}
	}

	/**
	 * Vanilla's options list with one full-width widget per row, sized and placed like it (its own class only takes
	 * options on this Minecraft, and a header is not one).
	 */
	private static final class Rows extends ContainerObjectSelectionList<Row>
	{
		Rows(Minecraft minecraft, int width, int height) {
			super(minecraft, width, height, 32, height - 32, 25);
			this.centerListVertically = false;
		}

		void add(AbstractWidget widget) {
			this.addEntry(new Row(widget));
		}

		@Override
		public int getRowWidth() {
			return 400;
		}

		@Override
		protected int getScrollbarPosition() {
			return super.getScrollbarPosition() + 32;
		}
	}

	private static final class Row extends ContainerObjectSelectionList.Entry<Row>
	{
		private final List<AbstractWidget> widgets;

		Row(AbstractWidget widget) {
			this.widgets = List.of(widget);
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
			for (AbstractWidget widget : widgets) {
				widget.setY(top);
				widget.render(graphics, mouseX, mouseY, partialTick);
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return widgets;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return widgets;
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
					Mth.clamp(Math.round(number / (float) COARSE_STEP), bottom, top), steps -> setting.set(steps * COARSE_STEP));
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
