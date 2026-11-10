package jexxme.vineandbarrel.compat.jade;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.drink.PlacedDrinksBlock;
import jexxme.vineandbarrel.drink.PlacedDrinksBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.Identifiers;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

/** A table of drinks in Jade: the title is the drink you look at ("Aged Red Wine"), not the block's generic name. */
public enum PlacedDrinksProvider implements IBlockComponentProvider
{
	INSTANCE;

	private static final ResourceLocation UID = VineAndBarrel.id("placed_drinks");

	@Override
	public ResourceLocation getUid() {
		return UID;
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		ItemStack drink = aimed(accessor);
		if (!drink.isEmpty()) {
			// Jade 11 has no replace: the name is the first line
			tooltip.remove(Identifiers.CORE_OBJECT_NAME);
			tooltip.add(0, IThemeHelper.get().title(drink.getHoverName()), Identifiers.CORE_OBJECT_NAME);
		}
	}

	/** {@return the drink under the crosshair, or the first one on the table} */
	static ItemStack aimed(BlockAccessor accessor) {
		if (!(accessor.getBlockEntity() instanceof PlacedDrinksBlockEntity drinks)) {
			return ItemStack.EMPTY;
		}
		ItemStack aimed = drinks.getItem(PlacedDrinksBlock.spotAt(accessor.getPosition(), accessor.getHitResult().getLocation()));
		if (!aimed.isEmpty()) {
			return aimed;
		}
		for (int i = 0; i < 4; i++) {
			if (!drinks.getItem(i).isEmpty()) {
				return drinks.getItem(i);
			}
		}
		return ItemStack.EMPTY;
	}
}
