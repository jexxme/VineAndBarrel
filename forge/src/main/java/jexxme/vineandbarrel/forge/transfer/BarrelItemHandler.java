package jexxme.vineandbarrel.forge.transfer;

import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * The Aging Barrel's hopper access on Forge (B14): its slots as seen from one side, with the barrel's own rules
 * ({@link AgingBarrelBlockEntity#mayAutomationInsert}, {@link AgingBarrelBlockEntity#mayAutomationExtract}).
 */
public class BarrelItemHandler implements IItemHandler, ForgeItemAccess.Journalled
{
	private final IItemHandler inventory;
	private final Direction side;

	/** {@code side} is the barrel's {@link AgingBarrelBlockEntity#automationSide}. */
	public BarrelItemHandler(IItemHandler inventory, Direction side) {
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
		return AgingBarrelBlockEntity.mayAutomationInsert(side, index) && inventory.isItemValid(index, stack);
	}

	@Override
	public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
		return AgingBarrelBlockEntity.mayAutomationInsert(side, index) ? inventory.insertItem(index, stack, simulate) : stack;
	}

	@Override
	public ItemStack extractItem(int index, int amount, boolean simulate) {
		return AgingBarrelBlockEntity.mayAutomationExtract(side, index) ? inventory.extractItem(index, amount, simulate) : ItemStack.EMPTY;
	}
}
