package jexxme.vineandbarrel.kettle;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/** The kettle's ingredient slots as a recipe input (only slots 0-5 are ever matched). */
public record KettleRecipeInput(ItemStacksResourceHandler handler) implements RecipeInput
{
	@Override
	public ItemStack getItem(int slot) {
		return handler.getResource(slot).toStack(handler.getAmountAsInt(slot));
	}

	@Override
	public int size() {
		return BrewKettleBlockEntity.INPUT_SLOTS;
	}
}
