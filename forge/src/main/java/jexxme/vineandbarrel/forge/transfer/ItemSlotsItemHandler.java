package jexxme.vineandbarrel.forge.transfer;

import jexxme.vineandbarrel.inventory.ItemSlots;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.Objects;

/**
 * A block entity's {@link ItemSlots} as a Forge {@code IItemHandler}: the same rules as the 26.x resource handler
 * (which followed NeoForge's own {@code ItemStacksResourceHandler}), working on the common slots. Forge 47 has no
 * transactions: a change is made and reported at once, unless a GameTest transaction is open
 * ({@link ForgeItemAccess.Tx}), which journals it per slot and reports it when it commits.
 */
public class ItemSlotsItemHandler implements IItemHandler
{
	private final ItemSlots slots;

	public ItemSlotsItemHandler(ItemSlots slots) {
		this.slots = slots;
	}

	@Override
	public int getSlots() {
		return slots.size();
	}

	/** The slot's own stack, as {@code IItemHandler} allows: callers must not change it. */
	@Override
	public ItemStack getStackInSlot(int index) {
		Objects.checkIndex(index, getSlots());
		return slots.getStack(index);
	}

	@Override
	public int getSlotLimit(int index) {
		Objects.checkIndex(index, getSlots());
		return slots.getCapacity(index, ItemStack.EMPTY);
	}

	@Override
	public boolean isItemValid(int index, ItemStack stack) {
		return slots.isValid(index, stack);
	}

	@Override
	public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
		Objects.checkIndex(index, getSlots());
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack current = slots.getStack(index);
		if ((current.isEmpty() || ItemStack.isSameItemSameTags(stack, current)) && slots.isValid(index, stack)) {
			int inserted = Math.min(stack.getCount(), slots.getCapacity(index, stack) - current.getCount());
			if (inserted > 0) {
				if (!simulate) {
					change(index, stack.copyWithCount(current.getCount() + inserted));
				}
				return inserted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
			}
		}
		return stack;
	}

	@Override
	public ItemStack extractItem(int index, int amount, boolean simulate) {
		Objects.checkIndex(index, getSlots());
		ItemStack current = slots.getStack(index);
		int extracted = Math.min(amount, current.getCount());
		if (current.isEmpty() || extracted <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack taken = current.copyWithCount(extracted);
		if (!simulate) {
			change(index, extracted == current.getCount() ? ItemStack.EMPTY : current.copyWithCount(current.getCount() - extracted));
		}
		return taken;
	}

	private void change(int index, ItemStack stack) {
		ForgeItemAccess.Tx transaction = ForgeItemAccess.Tx.open();
		if (transaction != null) {
			transaction.snapshot(slots, index);
			slots.setQuietly(index, stack);
		} else {
			ItemStack previous = slots.getStack(index).copy();
			slots.setQuietly(index, stack);
			slots.notifyChanged(index, previous);
		}
	}
}
