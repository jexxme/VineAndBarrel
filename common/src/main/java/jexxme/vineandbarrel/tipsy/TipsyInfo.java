package jexxme.vineandbarrel.tipsy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * What Tipsy does at the current level, for the effect's tooltip in the inventory, in vanilla's style: the good part in
 * blue with its numbers (like a potion's attribute lines), the drawbacks in red as a few short words. Only what you
 * can't see or feel yourself is named (the sway, the dark edges, the blinks and the hiccups speak for themselves), and
 * a drawback the server switched off isn't. Built from {@link TipsyLevels}, so it can't drift from the effects; kept
 * free of client classes so it is tested headless.
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
		List<Component> lines = new ArrayList<>(good(level));
		// The drawbacks as short words, a few per line, so the tooltip stays small
		List<Component> bad = drawbacks(level);
		for (int i = 0; i < bad.size(); i += PER_LINE) {
			MutableComponent line = Component.empty().withStyle(ChatFormatting.RED);
			for (int j = i; j < Math.min(i + PER_LINE, bad.size()); j++) {
				line.append(j == i ? Component.empty() : Component.literal(", ")).append(bad.get(j));
			}
			lines.add(line);
		}
		return lines;
	}

	/** The liquid courage, one blue line per number, the way vanilla lists a potion's attribute changes. */
	public static List<Component> good(int level) {
		TipsyLevels.Level at = TipsyLevels.at(level);
		List<Component> lines = new ArrayList<>();
		if (level <= 0) {
			return lines;
		}
		if (at.knockbackResistance() > 0.0) {
			lines.add(Component.translatable(KEY + "knockback_resistance", percent(at.knockbackResistance())).withStyle(ChatFormatting.BLUE));
		}
		if (at.fallDamageReduction() > 0.0) {
			lines.add(Component.translatable(KEY + "fall_damage", percent(at.fallDamageReduction())).withStyle(ChatFormatting.BLUE));
		}
		return lines;
	}

	/** The drawbacks active at {@code level} that you can't see for yourself, as short words. */
	public static List<Component> drawbacks(int level) {
		TipsyLevels.Level at = TipsyLevels.at(level);
		List<Component> words = new ArrayList<>();
		if (level <= 0) {
			return words;
		}
		if (at.slurs() && TipsyConfig.slurredChat()) {
			words.add(word("slurred"));
		}
		if (at.weakness()) {
			words.add(word("weakness"));
		}
		if (at.rowdy() && TipsyConfig.rowdy()) {
			words.add(word("rowdy"));
		}
		if (!at.buffs()) {
			words.add(word("no_buffs"));
		}
		if (at.cutOff() && TipsyConfig.cutOff()) {
			words.add(word("cut_off"));
		}
		if (at.slowness() >= 0 || at.miningFatigue() >= 0) {
			words.add(word("sluggish"));
		}
		if (at.unsteadyLegs() && TipsyConfig.stumbling()) {
			words.add(word("stumbling"));
			if (at.tripOneIn() > 0) {
				words.add(word("tripping"));
			}
		}
		return words;
	}

	private static String percent(double share) {
		return Long.toString(Math.round(share * 100.0));
	}

	private static Component word(String what) {
		return Component.translatable(KEY + what);
	}
}
