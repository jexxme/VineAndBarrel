package jexxme.vineandbarrel.neoforge.transfer;

import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The Brew Kettle's hopper access on NeoForge: its slots as seen from one side, with the kettle's own rules
 * ({@link BrewKettleBlockEntity#mayAutomationInsert}, {@link BrewKettleBlockEntity#mayAutomationExtract}).
 */
public class BrewKettleItemHandler implements ResourceHandler<ItemResource>
{
	private final ResourceHandler<ItemResource> inventory;
	private final Direction side;

	/** {@code side} is the kettle's {@link BrewKettleBlockEntity#automationSide}. */
	public BrewKettleItemHandler(ResourceHandler<ItemResource> inventory, Direction side) {
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
		return BrewKettleBlockEntity.mayAutomationInsert(side, index, resource.toStack(1)) && inventory.isValid(index, resource);
	}

	@Override
	public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
		return BrewKettleBlockEntity.mayAutomationInsert(side, index, resource.toStack(1)) ? inventory.insert(index, resource, amount, transaction) : 0;
	}

	@Override
	public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
		return BrewKettleBlockEntity.mayAutomationExtract(side, index) ? inventory.extract(index, resource, amount, transaction) : 0;
	}
}
