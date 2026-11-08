package jexxme.vineandbarrel.tipsy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * What Tipsy does at the current level, in player words, for the effect's tooltip in the inventory (playtest 1: the effect was a
 * black box). Built from the same constants the effects use, so it can't drift from them; kept free of client classes
 * so it is tested headless. Good things are blue and bad ones red, like vanilla's potion tooltips.
 */
public final class TipsyInfo
{
	private static final String KEY = "effect.vineandbarrel.tipsy.info.";
	private static final int PER_LINE = 3;

	private TipsyInfo() {
	}

	/**
	 * {@return what this level does right now} Only the present: what the next drink brings and how to sober up are
	 * for the player to find out (Jexxme, playtest 1).
	 */
	public static List<Component> lines(int level) {
		List<Component> lines = new ArrayList<>();
		if (level <= 0) {
			return lines;
		}
		if (level <= TipsyEffect.COURAGE_KNOCKBACK_RESISTANCE.length) {
			lines.add(Component.translatable(KEY + "courage").withStyle(ChatFormatting.BLUE));
		}
		// The drawbacks as short words, a few per line, so the tooltip stays small
		List<Component> bad = effectsAt(level);
		for (int i = 0; i < bad.size(); i += PER_LINE) {
			MutableComponent line = Component.empty().withStyle(ChatFormatting.RED);
			for (int j = i; j < Math.min(i + PER_LINE, bad.size()); j++) {
				line.append(j == i ? Component.empty() : Component.literal(", ")).append(bad.get(j));
			}
			lines.add(line);
		}
		return lines;
	}

	/** The drawbacks active at {@code level}, as short words. */
	static List<Component> effectsAt(int level) {
		List<Component> lines = new ArrayList<>();
		lines.add(bad("sway"));
		lines.add(bad("tunnel_vision"));
		if (level >= Tipsy.HICCUP_LEVEL) {
			lines.add(bad("hiccups"));
		}
		if (level >= Tipsy.NAUSEA_LEVEL) {
			lines.add(bad("dizzy"));
			lines.add(bad("heavy_eyes"));
		}
		if (level >= Tipsy.SLUR_LEVEL && TipsyConfig.slurredChat()) {
			lines.add(bad("slurred"));
		}
		if (level >= Tipsy.WEAKNESS_LEVEL) {
			lines.add(bad("weakness"));
		}
		if (level >= Tipsy.NO_BUFF_LEVEL) {
			lines.add(bad("no_buffs"));
		}
		if (level >= Rowdy.ROWDY_LEVEL && TipsyConfig.rowdy()) {
			lines.add(bad("rowdy"));
			lines.add(bad("cut_off"));
		}
		if (level >= Tipsy.HEAVY_LEVEL) {
			lines.add(bad("heavy"));
		}
		if (level >= Tipsy.STUMBLE_LEVEL && TipsyConfig.stumbling()) {
			lines.add(bad("stumbling"));
		}
		return lines;
	}

	private static Component bad(String what) {
		return Component.translatable(KEY + what);
	}
}
