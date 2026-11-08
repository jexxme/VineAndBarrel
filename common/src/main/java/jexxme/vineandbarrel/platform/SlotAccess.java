package jexxme.vineandbarrel.platform;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * What pipes and hoppers see of a block's inventory from one side, through the loader's own item API (NeoForge's
 * {@code ResourceHandler}, Fabric's {@code Storage}), for the GameTests: they check the real automation path on both
 * loaders. Shaped like NeoForge's handler, with a loader transaction ({@link Platform#openTransaction()}).
 */
public interface SlotAccess
{
	int size();

	/** A copy of the stack in a slot, with its real count. */
	ItemStack get(int slot);

	/** Puts up to {@code amount} items like {@code resource} into a slot; returns how many went in. */
	int insert(int slot, ItemStack resource, int amount, Tx transaction);

	/** Takes up to {@code amount} items like {@code resource} out of a slot; returns how many came out. */
	int extract(int slot, ItemStack resource, int amount, Tx transaction);

	/**
	 * Puts up to {@code amount} items like {@code resource} into the inventory as a whole, the way a hopper does (the
	 * block picks the slots); returns how many went in.
	 */
	default int insertAny(ItemStack resource, int amount, Tx transaction) {
		int inserted = 0;
		for (int slot = 0; slot < size() && inserted < amount; slot++) {
			inserted += insert(slot, resource, amount - inserted, transaction);
		}
		return inserted;
	}

	/** Takes up to {@code amount} items like {@code resource} out of the inventory as a whole, the way a hopper does. */
	default int extractAny(ItemStack resource, int amount, Tx transaction) {
		int extracted = 0;
		for (int slot = 0; slot < size() && extracted < amount; slot++) {
			extracted += extract(slot, resource, amount - extracted, transaction);
		}
		return extracted;
	}

	/** One item of this kind, as the "resource" the methods above take (NeoForge's {@code ItemResource.of}). */
	static ItemStack of(ItemLike item) {
		return new ItemStack(item);
	}

	/** {@code stack}'s kind (item and components), count 1. */
	static ItemStack of(ItemStack stack) {
		return stack.copyWithCount(1);
	}

	/** A loader transaction: what happens inside counts only if it is committed before it closes. */
	interface Tx extends AutoCloseable
	{
		void commit();

		@Override
		void close();
	}
}
