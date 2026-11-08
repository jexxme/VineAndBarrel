package jexxme.vineandbarrel.kettle;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.transfer.StacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * An {@link ItemStacksResourceHandler} that loads slot by slot, like vanilla containers do (from Farmer's Delight
 * Neoforged, MIT). The NeoForge default decodes the whole list at once: one unreadable stack (an item from a removed
 * mod) would throw the whole inventory away, and a saved list of another length would resize the handler.
 */
public class SafeItemStacksResourceHandler extends ItemStacksResourceHandler
{
	public SafeItemStacksResourceHandler(int size) {
		super(size);
	}

	@Override
	public void deserialize(ValueInput input) {
		int index = 0;
		for (ValueInput entry : input.childrenListOrEmpty(StacksResourceHandler.VALUE_IO_KEY)) {
			if (index >= stacks.size()) {
				VineAndBarrel.LOGGER.warn("Ignoring saved items beyond slot {} (inventory has {} slots)", index, stacks.size());
				break;
			}
			stacks.set(index++, entry.read(ItemStack.MAP_CODEC).orElse(ItemStack.EMPTY));
		}
		while (index < stacks.size()) {
			stacks.set(index++, ItemStack.EMPTY);
		}
	}
}
