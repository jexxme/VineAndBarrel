package jexxme.vineandbarrel.barrel;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Aging Barrel's status line (B10, B15) on an empty-hand right-click, e.g. "Wedding Wine · Red Wine · 5 servings ·
 * aging, aged in 3 days", and the hints for things it refuses (B8, B9). Shown on the action bar, like FD's messages.
 */
public final class BarrelStatus
{
	private static final int DAY = 24000;
	/** Under a minute to go. */
	private static final int SOON = 1200;
	private static final String KEY = "message.vineandbarrel.aging_barrel.";

	private BarrelStatus() {
	}

	public static Component of(AgingBarrelBlockEntity barrel) {
		Component name = barrel.getCustomName();
		if (barrel.stage() == BarrelStage.EMPTY) {
			return Component.translatable(KEY + "empty", name != null ? name : barrel.getName());
		}
		ItemStack drink = barrel.drink();
		MutableComponent title = name != null ? Component.translatable(KEY + "named", name, drink.getHoverName()) : drink.getHoverName().copy();
		return Component.translatable(KEY + "status", title, servings(barrel.servings()), state(barrel));
	}

	public static Component servings(int servings) {
		return servings == 1 ? Component.translatable(KEY + "one_serving") : Component.translatable(KEY + "servings", servings);
	}

	/** The stage and when the next one comes, e.g. "fermenting, ready tomorrow morning" (also shown by Jade). */
	public static Component state(AgingBarrelBlockEntity barrel) {
		long left = barrel.ticksToNextStage();
		Level level = barrel.getLevel();
		return switch (barrel.stage()) {
			case FERMENTING -> Component.translatable(KEY + "fermenting", when(level, left));
			case YOUNG -> Component.translatable(KEY + "aging", when(level, left));
			default -> Component.translatable(KEY + "aged");
		};
	}

	/**
	 * When something {@code ticksLeft} ticks from now happens, in words: "this evening", "tomorrow morning", "in 3
	 * days". Days start at sunrise (day time 0) and include the following night, as in vanilla.
	 */
	public static Component when(@Nullable Level level, long ticksLeft) {
		boolean clock = level != null && !level.dimensionType().hasFixedTime();
		return when(clock ? level.getDefaultClockTime() : -1L, ticksLeft);
	}

	/** As {@link #when(Level, long)}, for a clock time ({@code -1} where there is no day: the Nether, the End). */
	public static Component when(long clockTime, long ticksLeft) {
		if (ticksLeft <= SOON) {
			return Component.translatable(KEY + "when.soon");
		}
		if (clockTime < 0L) {
			long days = (ticksLeft + DAY - 1) / DAY;
			return days <= 1 ? Component.translatable(KEY + "when.within_a_day") : Component.translatable(KEY + "when.in_days", days);
		}
		long target = clockTime + ticksLeft;
		long days = Math.floorDiv(target, DAY) - Math.floorDiv(clockTime, DAY);
		String part = partOfDay(Math.floorMod(target, DAY));
		if (days == 0) {
			return Component.translatable(KEY + "when.today." + part);
		}
		if (days == 1) {
			return Component.translatable(KEY + "when.tomorrow." + part);
		}
		return Component.translatable(KEY + "when.in_days", days);
	}

	/** Vanilla's day: sunrise at 0, noon at 6000, sunset at 12000, night from 13000. */
	public static String partOfDay(long timeOfDay) {
		if (timeOfDay < 6000) return "morning";
		if (timeOfDay < 12000) return "afternoon";
		if (timeOfDay < 13000) return "evening";
		return "night";
	}

	// ------------------------------------------------------------------ hints (nothing is used up)

	public static Component wrongVessel(ItemStack drink, ItemStack vessel) {
		return Component.translatable(KEY + "wrong_vessel", drink.getHoverName(), vessel.getHoverName());
	}

	public static Component refusal(AgingBarrelBlockEntity barrel, AgingBarrelBlockEntity.Refusal refusal) {
		return switch (refusal) {
			case OTHER_BASE -> Component.translatable(KEY + "other_base", barrel.content().getHoverName());
			case ALREADY_A_DRINK -> Component.translatable(KEY + "already_a_drink", barrel.drink().getHoverName());
			case FULL -> Component.translatable(KEY + "full");
			default -> Component.empty();
		};
	}

	public static Component stillFermenting(AgingBarrelBlockEntity barrel) {
		return Component.translatable(KEY + "still_fermenting", barrel.drink().getHoverName(), when(barrel.getLevel(), barrel.ticksToNextStage()));
	}

	public static Component bucketNeedsFour() {
		return Component.translatable(KEY + "bucket_needs_four", AgingBarrelBlockEntity.SERVINGS_PER_BUCKET);
	}
}
