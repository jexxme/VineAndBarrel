package jexxme.vineandbarrel.forge.transfer;

import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * The Brew Kettle's hopper access on Forge: its slots as seen from one side, with the kettle's own rules
 * ({@link BrewKettleBlockEntity#mayAutomationInsert}, {@link BrewKettleBlockEntity#mayAutomationExtract}).
 */
public class BrewKettleItemHandler implements IItemHandler, ForgeItemAccess.Journalled
{
	private final IItemHandler inventory;
	private final Direction side;

	/** {@code side} is the kettle's {@link BrewKettleBlockEntity#automationSide}. */
	public BrewKettleItemHandler(IItemHandler inventory, Direction side) {
		this.inventory = inventory;
		this.side = side;
	}

	@Override
	public int getSlots() {
		return inventory.getSlots();
	}

	@Override
	public ItemStack getStackInSlot(int index) {
		return inventory.getStackInSlot(index);
	}

	@Override
	public int getSlotLimit(int index) {
		return inventory.getSlotLimit(index);
	}

	@Override
	public boolean isItemValid(int index, ItemStack stack) {
		return !stack.isEmpty() && BrewKettleBlockEntity.mayAutomationInsert(side, index, stack.copyWithCount(1)) && inventory.isItemValid(index, stack);
	}

	@Override
	public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
		return !stack.isEmpty() && BrewKettleBlockEntity.mayAutomationInsert(side, index, stack.copyWithCount(1)) ? inventory.insertItem(index, stack, simulate) : stack;
	}

	@Override
	public ItemStack extractItem(int index, int amount, boolean simulate) {
		return BrewKettleBlockEntity.mayAutomationExtract(side, index) ? inventory.extractItem(index, amount, simulate) : ItemStack.EMPTY;
	}
}
