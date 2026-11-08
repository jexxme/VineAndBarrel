package jexxme.vineandbarrel.kettle;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import jexxme.vineandbarrel.inventory.ItemSlots;

/**
 * The kettle's ingredient slots as a recipe input (only slots 0-5 are ever matched). Like vanilla's crafting input it hands
 * out the slots' own stacks, without copying: recipes only read them.
 */
public record KettleRecipeInput(ItemSlots handler) implements RecipeInput
{
	@Override
	public ItemStack getItem(int slot) {
		return handler.getStack(slot);
	}

	@Override
	public int size() {
		return BrewKettleBlockEntity.INPUT_SLOTS;
	}
}
