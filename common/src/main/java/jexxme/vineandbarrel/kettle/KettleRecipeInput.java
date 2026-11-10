package jexxme.vineandbarrel.kettle;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import jexxme.vineandbarrel.inventory.ItemSlots;

/**
 * The kettle's ingredient slots as a recipe input (only slots 0-5 are ever matched). Like vanilla's crafting input it hands
 * out the slots' own stacks, without copying: recipes only read them. On this Minecraft a recipe's input is a
 * {@link Container}; this one is read-only (change the slots themselves).
 */
public record KettleRecipeInput(ItemSlots handler) implements Container
{
	@Override
	public ItemStack getItem(int slot) {
		return handler.getStack(slot);
	}

	@Override
	public int getContainerSize() {
		return BrewKettleBlockEntity.INPUT_SLOTS;
	}

	@Override
	public boolean isEmpty() {
		for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
			if (!handler.getStack(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		throw new UnsupportedOperationException("A recipe input is read-only");
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		throw new UnsupportedOperationException("A recipe input is read-only");
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		throw new UnsupportedOperationException("A recipe input is read-only");
	}

	@Override
	public void setChanged() {
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		throw new UnsupportedOperationException("A recipe input is read-only");
	}
}
