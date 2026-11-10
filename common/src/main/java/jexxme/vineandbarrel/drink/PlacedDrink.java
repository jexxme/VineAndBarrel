package jexxme.vineandbarrel.drink;

import net.minecraft.util.StringRepresentable;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What stands on one of the four spots of a placed-drinks block (D1): nothing, a mug or a wine glass, empty, full or
 * full of an aged drink. An aged drink's surface glows (playtest 1), so its own state picks its own, emissive model.
 */
public enum PlacedDrink implements StringRepresentable
{
	NONE("none"),
	MUG("mug"),
	MUG_FULL("mug_full"),
	GLASS("glass"),
	GLASS_FULL("glass_full"),
	MUG_AGED("mug_aged"),
	GLASS_AGED("glass_aged");

	private final String name;

	PlacedDrink(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public boolean isFull() {
		return this == MUG_FULL || this == GLASS_FULL || isAged();
	}

	/** Full of an aged drink: the liquid surface renders fullbright. */
	public boolean isAged() {
		return this == MUG_AGED || this == GLASS_AGED;
	}

	public boolean isMug() {
		return this == MUG || this == MUG_FULL || this == MUG_AGED;
	}

	/** How it looks once put down, or null if this item can't be put down. A drink looks like its vessel, full. */
	public static @Nullable PlacedDrink of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		if (stack.is(DrinkItems.MUG.get())) {
			return MUG;
		}
		if (stack.is(DrinkItems.WINE_GLASS.get())) {
			return GLASS;
		}
		if (stack.getItem() instanceof DrinkItem drink) {
			Item vessel = AgingBarrelBlockEntity.vesselOf(stack);
			if (vessel == DrinkItems.MUG.get()) {
				return drink.isAged() ? MUG_AGED : MUG_FULL;
			}
			if (vessel == DrinkItems.WINE_GLASS.get()) {
				return drink.isAged() ? GLASS_AGED : GLASS_FULL;
			}
		}
		return null;
	}
}
