package jexxme.vineandbarrel.barrel;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import static jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity.CONTAINER_SLOT;
import static jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity.DRINK_SLOT;
import static jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity.INPUT_SLOT;
import static jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity.VESSEL_SLOT;

/**
 * Hopper access (B14), the same split as the Brew Kettle (and FD's cooking pot): from the top, bases go into the input
 * slot (and can come back out); from the sides and below, empty vessels go into the vessel slot, and the tapped drinks
 * and the empty buckets and bottles left from pouring come out.
 */
public class BarrelItemHandler implements ResourceHandler<ItemResource>
{
	private final ResourceHandler<ItemResource> inventory;
	private final @Nullable Direction side;

	public BarrelItemHandler(ResourceHandler<ItemResource> inventory, @Nullable Direction side) {
		this.inventory = inventory;
		this.side = side;
	}

	private boolean fromTop() {
		return side == null || side == Direction.UP;
	}

	private boolean canInsert(int index) {
		return fromTop() ? index == INPUT_SLOT : index == VESSEL_SLOT;
	}

	private boolean canExtract(int index) {
		return fromTop() ? index == INPUT_SLOT : index == DRINK_SLOT || index == CONTAINER_SLOT;
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
		return canInsert(index) && inventory.isValid(index, resource);
	}

	@Override
	public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
		return canInsert(index) ? inventory.insert(index, resource, amount, transaction) : 0;
	}

	@Override
	public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
		return canExtract(index) ? inventory.extract(index, resource, amount, transaction) : 0;
	}
}
