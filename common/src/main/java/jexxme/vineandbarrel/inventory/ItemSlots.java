package jexxme.vineandbarrel.inventory;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * A block entity's item slots, the same on both loaders (what NeoForge's {@code ItemStacksResourceHandler} was for the
 * kettle and the barrel). Hoppers and pipes reach it through the loader's own item API: a {@code ResourceHandler} on
 * NeoForge, a {@code Storage} on Fabric, both built on these methods. It is also a vanilla {@link Container}, so menu
 * slots can show it ({@link MenuSlot}).
 * <p>
 * The save format is NeoForge's ({@code "stacks"}: a list of optional stacks), so worlds keep their contents; loading
 * goes slot by slot like vanilla containers (from Farmer's Delight Neoforged, MIT): one unreadable stack (an item from
 * a removed mod) never throws the whole inventory away, and a saved list of another length never resizes it.
 */
public class ItemSlots implements Container
{
	public static final String VALUE_IO_KEY = "stacks";

	private final NonNullList<ItemStack> stacks;
	private @Nullable Object automationView;

	public ItemSlots(int size) {
		this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
	}

	public int size() {
		return stacks.size();
	}

	/** The stack in a slot itself, not a copy: never change it, write changes with {@link #set}. */
	public ItemStack getStack(int index) {
		return stacks.get(index);
	}

	/** A copy of the stack in a slot, with its real count. */
	public ItemStack copyStack(int index) {
		return stacks.get(index).copy();
	}

	public int getAmount(int index) {
		return stacks.get(index).getCount();
	}

	/** Puts (a copy of) {@code stack} into a slot. */
	public void set(int index, ItemStack stack) {
		ItemStack previous = stacks.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		onContentsChanged(index, previous);
	}

	/** Whether this slot may hold {@code stack} at all (empty always fits). */
	public boolean isValid(int index, ItemStack stack) {
		return true;
	}

	/** How many of {@code stack} this slot holds at most. */
	public int getCapacity(int index, ItemStack stack) {
		return stack.isEmpty() ? Item.ABSOLUTE_MAX_STACK_SIZE : Math.min(stack.getMaxStackSize(), Item.ABSOLUTE_MAX_STACK_SIZE);
	}

	/** {@link #getCapacity}, or 0 if the slot can't take {@code stack} at all. */
	public int getCapacityAsInt(int index, ItemStack stack) {
		return stack.isEmpty() || isValid(index, stack) ? getCapacity(index, stack) : 0;
	}

	/**
	 * Called after a slot changed, with what it held before. {@code index} is -1 when a menu slot changed a stack in
	 * place (vanilla's {@link #setChanged()}).
	 */
	protected void onContentsChanged(int index, ItemStack previousContents) {
	}

	/**
	 * Changes a slot's stack without telling {@link #onContentsChanged} (for the loaders' transactional item APIs,
	 * which report the change themselves once it is committed).
	 */
	public void setQuietly(int index, ItemStack stack) {
		stacks.set(index, stack);
	}

	/** For the loaders' item APIs: report a committed change. */
	public void notifyChanged(int index, ItemStack previousContents) {
		onContentsChanged(index, previousContents);
	}

	/**
	 * The loader's item-API view of these slots (NeoForge resource handlers, Fabric storages), made on first use and
	 * kept with the slots, so every hopper and pipe shares one (its transaction journal included).
	 */
	@SuppressWarnings("unchecked")
	public <T> T automationView(Function<ItemSlots, T> factory) {
		if (automationView == null) {
			automationView = factory.apply(this);
		}
		return (T) automationView;
	}

	public void serialize(ValueOutput output) {
		output.store(VALUE_IO_KEY, ItemStack.OPTIONAL_CODEC.listOf(), stacks);
	}

	public void deserialize(ValueInput input) {
		int index = 0;
		for (ValueInput entry : input.childrenListOrEmpty(VALUE_IO_KEY)) {
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

	// ------------------------------------------------------------------ vanilla Container, for menu slots

	@Override
	public int getContainerSize() {
		return size();
	}

	@Override
	public boolean isEmpty() {
		return stacks.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return stacks.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack current = stacks.get(slot);
		if (current.isEmpty() || count <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack previous = current.copy();
		ItemStack taken = current.split(count);
		onContentsChanged(slot, previous);
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack previous = stacks.set(slot, ItemStack.EMPTY);
		onContentsChanged(slot, previous);
		return previous;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		ItemStack previous = stacks.set(slot, stack);
		onContentsChanged(slot, previous);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return isValid(slot, stack);
	}

	@Override
	public void setChanged() {
		onContentsChanged(-1, ItemStack.EMPTY);
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < stacks.size(); i++) {
			set(i, ItemStack.EMPTY);
		}
	}

	/** A menu slot showing one of these slots; only takes what the slot may hold, up to its capacity. */
	public static class MenuSlot extends Slot
	{
		private final ItemSlots slots;

		public MenuSlot(ItemSlots slots, int index, int x, int y) {
			super(slots, index, x, y);
			this.slots = slots;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return slots.isValid(getContainerSlot(), stack);
		}

		@Override
		public int getMaxStackSize(ItemStack stack) {
			return Math.min(stack.getMaxStackSize(), slots.getCapacity(getContainerSlot(), stack));
		}
	}
}
