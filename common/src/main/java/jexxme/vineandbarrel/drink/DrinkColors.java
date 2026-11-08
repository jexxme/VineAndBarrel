package jexxme.vineandbarrel.drink;

import jexxme.vineandbarrel.kettle.BaseItems;
import net.minecraft.world.item.ItemStack;

/** The liquid colour of anything a barrel or a vessel can hold (DESIGN §9: colour = which drink). */
public final class DrinkColors
{
	/** For a data-pack drink or base we know nothing about: a warm amber. */
	public static final int FALLBACK = 0xB8762E;

	private DrinkColors() {
	}

	/** RGB, no alpha. */
	public static int of(ItemStack stack) {
		if (stack.getItem() instanceof DrinkItem drink) {
			return drink.color();
		}
		int base = BaseItems.liquidColor(stack);
		return base != -1 ? base : FALLBACK;
	}
}
