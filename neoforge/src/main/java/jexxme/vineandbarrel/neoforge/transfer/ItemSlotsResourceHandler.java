package jexxme.vineandbarrel.neoforge.transfer;

import jexxme.vineandbarrel.inventory.ItemSlots;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A block entity's {@link ItemSlots} as a NeoForge {@code ResourceHandler}: the same rules as NeoForge's own
 * {@code ItemStacksResourceHandler} (which the kettle and the barrel used before the split), working on the common
 * slots. Changes are journalled per slot and reported once the outermost transaction commits.
 */
public class ItemSlotsResourceHandler implements ResourceHandler<ItemResource>
{
	private final ItemSlots slots;
	private final List<SlotJournal> journals;

	public ItemSlotsResourceHandler(ItemSlots slots) {
		this.slots = slots;
		this.journals = new ArrayList<>(slots.size());
		for (int i = 0; i < slots.size(); i++) {
			journals.add(new SlotJournal(i));
		}
	}

	@Override
	public int size() {
		return slots.size();
	}

	@Override
	public ItemResource getResource(int index) {
		Objects.checkIndex(index, size());
		return ItemResource.of(slots.getStack(index));
	}

	@Override
	public long getAmountAsLong(int index) {
		Objects.checkIndex(index, size());
		return slots.getAmount(index);
	}

	@Override
	public long getCapacityAsLong(int index, ItemResource resource) {
		Objects.checkIndex(index, size());
		return slots.getCapacityAsInt(index, resource.toStack(1));
	}

	@Override
	public boolean isValid(int index, ItemResource resource) {
		return slots.isValid(index, resource.toStack(1));
	}

	@Override
	public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
		Objects.checkIndex(index, size());
		TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
		ItemStack current = slots.getStack(index);
		ItemStack one = resource.toStack(1);
		if ((current.isEmpty() || resource.matches(current)) && slots.isValid(index, one)) {
			int inserted = Math.min(amount, slots.getCapacity(index, one) - current.getCount());
			if (inserted > 0) {
				journals.get(index).updateSnapshots(transaction);
				slots.setQuietly(index, resource.toStack(current.getCount() + inserted));
				return inserted;
			}
		}
		return 0;
	}

	@Override
	public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
		Objects.checkIndex(index, size());
		TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
		ItemStack current = slots.getStack(index);
		if (resource.matches(current)) {
			int extracted = Math.min(amount, current.getCount());
			if (extracted > 0) {
				journals.get(index).updateSnapshots(transaction);
				slots.setQuietly(index, resource.toStack(current.getCount() - extracted));
				return extracted;
			}
		}
		return 0;
	}

	private final class SlotJournal extends SnapshotJournal<ItemStack>
	{
		private final int index;

		private SlotJournal(int index) {
			this.index = index;
		}

		@Override
		protected ItemStack createSnapshot() {
			return slots.getStack(index).copy();
		}

		@Override
		protected void revertToSnapshot(ItemStack snapshot) {
			slots.setQuietly(index, snapshot);
		}

		@Override
		protected void onRootCommit(ItemStack originalState) {
			slots.notifyChanged(index, originalState);
		}
	}
}
