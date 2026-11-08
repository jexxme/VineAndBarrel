package jexxme.vineandbarrel.neoforge.transfer;

import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The Aging Barrel's hopper access on NeoForge (B14): its slots as seen from one side, with the barrel's own rules
 * ({@link AgingBarrelBlockEntity#mayAutomationInsert}, {@link AgingBarrelBlockEntity#mayAutomationExtract}).
 */
public class BarrelItemHandler implements ResourceHandler<ItemResource>
{
	private final ResourceHandler<ItemResource> inventory;
	private final Direction side;

	/** {@code side} is the barrel's {@link AgingBarrelBlockEntity#automationSide}. */
	public BarrelItemHandler(ResourceHandler<ItemResource> inventory, Direction side) {
		this.inventory = inventory;
		this.side = side;
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
		return AgingBarrelBlockEntity.mayAutomationInsert(side, index) && inventory.isValid(index, resource);
	}

	@Override
	public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
		return AgingBarrelBlockEntity.mayAutomationInsert(side, index) ? inventory.insert(index, resource, amount, transaction) : 0;
	}

	@Override
	public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
		return AgingBarrelBlockEntity.mayAutomationExtract(side, index) ? inventory.extract(index, resource, amount, transaction) : 0;
	}
}
