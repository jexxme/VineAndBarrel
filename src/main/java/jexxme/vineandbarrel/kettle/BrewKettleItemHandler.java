package jexxme.vineandbarrel.kettle;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Hopper access, like a cauldron that works itself: the brew lives in the kettle and only ever leaves in a container.
 * <ul>
 * <li>top: ingredients go into the grid, empty bottles and buckets into the container buffer;</li>
 * <li>sides: empty bottles and buckets into the container buffer;</li>
 * <li>bottom: the filled ones come out of the output buffer.</li>
 * </ul>
 * Nothing pulls ingredients, the brew or the empty containers back out.
 */
public class BrewKettleItemHandler implements ResourceHandler<ItemResource>
{
	private final ResourceHandler<ItemResource> inventory;
	private final @Nullable Direction side;

	public BrewKettleItemHandler(ResourceHandler<ItemResource> inventory, @Nullable Direction side) {
		this.inventory = inventory;
		this.side = side;
	}

	private boolean fromTop() {
		return side == null || side == Direction.UP;
	}

	/** Whether this side may put {@code resource} into slot {@code index}. */
	private boolean mayInsert(int index, ItemResource resource) {
		if (side == Direction.DOWN) {
			return false;
		}
		boolean container = resource.is(KettleTags.KETTLE_CONTAINERS);
		if (index == BrewKettleBlockEntity.CONTAINER_SLOT) {
			return container;
		}
		return fromTop() && index < BrewKettleBlockEntity.INPUT_SLOTS && !container;
	}

	private boolean mayExtract(int index) {
		return side == Direction.DOWN && index == BrewKettleBlockEntity.OUTPUT_SLOT;
	}

	@Override
	public int size() {
		return inventory.size();
	}

	@Override
	public ItemResource getResource(int index) {
		return inventory.getResource(index);
	}

	@Override
	public long getAmountAsLong(int index) {
		return inventory.getAmountAsLong(index);
	}

	@Override
	public long getCapacityAsLong(int index, ItemResource resource) {
		return inventory.getCapacityAsLong(index, resource);
	}

	@Override
	public boolean isValid(int index, ItemResource resource) {
		return mayInsert(index, resource) && inventory.isValid(index, resource);
	}

	@Override
	public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
		return mayInsert(index, resource) ? inventory.insert(index, resource, amount, transaction) : 0;
	}

	@Override
	public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
		return mayExtract(index) ? inventory.extract(index, resource, amount, transaction) : 0;
	}
}
