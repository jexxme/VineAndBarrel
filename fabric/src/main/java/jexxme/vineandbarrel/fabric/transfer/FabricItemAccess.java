package jexxme.vineandbarrel.fabric.transfer;

import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.inventory.ItemSlots;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.KettleMultiblock;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.platform.SlotAccess;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedSlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The kettle's and the barrel's item storage on Fabric (what hoppers and pipes use through Fabric's Transfer API),
 * with the same rules per side as on NeoForge, and the GameTests' view of any block's item storage ({@link SlotAccess}).
 */
public final class FabricItemAccess implements SlotAccess
{
	private final Storage<ItemVariant> storage;

	private FabricItemAccess(Storage<ItemVariant> storage) {
		this.storage = storage;
	}

	/** Once, after registration. */
	public static void register() {
		// Every kettle block, single or part of a big kettle, hands out its kettle's inventory (the controller's), with the
		// rules of the side it is asked from. One provider for all of them: a block-entity provider would claim the parts
		// too, and most parts have no block entity.
		java.util.List<net.minecraft.world.level.block.Block> kettles = new java.util.ArrayList<>(KettleRegistry.allKettleBlocks());
		kettles.addAll(KettleRegistry.allPartBlocks());
		ItemStorage.SIDED.registerForBlocks((level, pos, state, be, side) -> {
			BrewKettleBlockEntity kettle = KettleMultiblock.controller(level, pos);
			return kettle == null ? null : sideView(kettle.getInventory(), BrewKettleBlockEntity.automationSide(side), true);
		}, kettles.toArray(net.minecraft.world.level.block.Block[]::new));
		ItemStorage.SIDED.registerForBlockEntity((be, side) -> sideView(be.getInventory(), AgingBarrelBlockEntity.automationSide(side), false),
			BarrelRegistry.AGING_BARREL_BE.get());
	}

	/** One storage per slot (each keeps its transaction snapshots, so every side must share them) and one view per rule side. */
	private record Storages(List<SlotStorage> slots, Map<Direction, CombinedSlottedStorage<ItemVariant, SingleSlotStorage<ItemVariant>>> sides)
	{
	}

	private static CombinedSlottedStorage<ItemVariant, SingleSlotStorage<ItemVariant>> sideView(ItemSlots inventory, Direction side, boolean kettle) {
		Storages storages = inventory.automationView(s -> {
			List<SlotStorage> slots = new ArrayList<>(s.size());
			for (int i = 0; i < s.size(); i++) {
				slots.add(new SlotStorage(s, i));
			}
			return new Storages(slots, new EnumMap<>(Direction.class));
		});
		return storages.sides().computeIfAbsent(side, ruleSide -> {
			List<SingleSlotStorage<ItemVariant>> filtered = new ArrayList<>();
			for (SlotStorage slot : storages.slots()) {
				filtered.add(new SidedSlot(slot, ruleSide, kettle));
			}
			return new CombinedSlottedStorage<>(filtered);
		});
	}

	/** One slot of the common inventory, with its validity and capacity. */
	private static final class SlotStorage extends SingleStackStorage
	{
		private final ItemSlots slots;
		private final int index;

		private SlotStorage(ItemSlots slots, int index) {
			this.slots = slots;
			this.index = index;
		}

		@Override
		protected ItemStack getStack() {
			return slots.getStack(index);
		}

		@Override
		protected void setStack(ItemStack stack) {
			slots.setQuietly(index, stack);
		}

		@Override
		protected boolean canInsert(ItemVariant variant) {
			return slots.isValid(index, variant.toStack(1));
		}

		@Override
		protected int getCapacity(ItemVariant variant) {
			return slots.getCapacity(index, variant.toStack(1));
		}

		@Override
		protected void onFinalCommit() {
			slots.notifyChanged(index, ItemStack.EMPTY);
		}
	}

	/** A slot as one side sees it: the kettle's or the barrel's automation rules on top of the shared slot. */
	private record SidedSlot(SlotStorage slot, Direction side, boolean kettle) implements SingleSlotStorage<ItemVariant>
	{
		private boolean mayInsert(ItemVariant variant) {
			return kettle ? BrewKettleBlockEntity.mayAutomationInsert(side, slot.index, variant.toStack(1))
				: AgingBarrelBlockEntity.mayAutomationInsert(side, slot.index);
		}

		private boolean mayExtract() {
			return kettle ? BrewKettleBlockEntity.mayAutomationExtract(side, slot.index) : AgingBarrelBlockEntity.mayAutomationExtract(side, slot.index);
		}

		@Override
		public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			return mayInsert(resource) ? slot.insert(resource, maxAmount, transaction) : 0;
		}

		@Override
		public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			return mayExtract() ? slot.extract(resource, maxAmount, transaction) : 0;
		}

		@Override
		public boolean isResourceBlank() {
			return slot.isResourceBlank();
		}

		@Override
		public ItemVariant getResource() {
			return slot.getResource();
		}

		@Override
		public long getAmount() {
			return slot.getAmount();
		}

		@Override
		public long getCapacity() {
			return slot.getCapacity();
		}
	}

	// ------------------------------------------------------------------ the GameTests' view

	public static @Nullable SlotAccess of(ServerLevel level, BlockPos pos, @Nullable Direction side) {
		Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, side);
		return storage == null ? null : new FabricItemAccess(storage);
	}

	@Override
	public int size() {
		return storage instanceof SlottedStorage<ItemVariant> slotted ? slotted.getSlotCount() : 1;
	}

	private Storage<ItemVariant> slot(int index) {
		return storage instanceof SlottedStorage<ItemVariant> slotted ? slotted.getSlot(index) : storage;
	}

	@Override
	public ItemStack get(int slot) {
		if (slot(slot) instanceof SingleSlotStorage<ItemVariant> view) {
			return view.getResource().toStack((int) view.getAmount());
		}
		return ItemStack.EMPTY;
	}

	@Override
	public int insert(int slot, ItemStack resource, int amount, SlotAccess.Tx transaction) {
		return (int) slot(slot).insert(ItemVariant.of(resource), amount, ((Tx) transaction).transaction);
	}

	@Override
	public int extract(int slot, ItemStack resource, int amount, SlotAccess.Tx transaction) {
		return (int) slot(slot).extract(ItemVariant.of(resource), amount, ((Tx) transaction).transaction);
	}

	@Override
	public int insertAny(ItemStack resource, int amount, SlotAccess.Tx transaction) {
		return (int) storage.insert(ItemVariant.of(resource), amount, ((Tx) transaction).transaction);
	}

	@Override
	public int extractAny(ItemStack resource, int amount, SlotAccess.Tx transaction) {
		return (int) storage.extract(ItemVariant.of(resource), amount, ((Tx) transaction).transaction);
	}

	/** A Fabric transaction. */
	public record Tx(Transaction transaction) implements SlotAccess.Tx
	{
		@Override
		public void commit() {
			transaction.commit();
		}

		@Override
		public void close() {
			transaction.close();
		}
	}
}
