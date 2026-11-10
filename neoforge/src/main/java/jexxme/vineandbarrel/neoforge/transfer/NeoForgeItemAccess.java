package jexxme.vineandbarrel.neoforge.transfer;

import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.inventory.ItemSlots;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.KettleMultiblock;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.platform.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The kettle's and the barrel's item capability (what hoppers and pipes use on NeoForge), and the GameTests' view of
 * any block's item capability ({@link SlotAccess}).
 */
public final class NeoForgeItemAccess implements SlotAccess
{
	/** One of our own handlers: its changes inside a {@link Tx} are journalled (see {@link ItemSlotsItemHandler}). */
	interface Journalled
	{
	}

	/** One handler per inventory and one view per rule side. */
	private record Handlers(IItemHandler all, Map<Direction, IItemHandler> sides)
	{
	}

	private final IItemHandler handler;

	private NeoForgeItemAccess(IItemHandler handler) {
		this.handler = handler;
	}

	/**
	 * K-automation and B14: hoppers above the kettle feed ingredients and containers, at the sides containers, a hopper
	 * below pulls the filled ones; the barrel takes bases from the top and vessels from the sides.
	 */
	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		// Every kettle block, single or part of a big kettle, hands out its kettle's inventory (the controller's), with the
		// rules of the side it is asked from (the same single provider as on Fabric)
		java.util.List<net.minecraft.world.level.block.Block> kettles = new java.util.ArrayList<>(KettleRegistry.allKettleBlocks());
		kettles.addAll(KettleRegistry.allPartBlocks());
		event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
			BrewKettleBlockEntity kettle = KettleMultiblock.controller(level, pos);
			return kettle == null ? null : sideView(kettle.getInventory(), BrewKettleBlockEntity.automationSide(side), true);
		}, kettles.toArray(net.minecraft.world.level.block.Block[]::new));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BarrelRegistry.AGING_BARREL_BE.get(),
			(be, side) -> sideView(be.getInventory(), AgingBarrelBlockEntity.automationSide(side), false));
	}

	private static IItemHandler sideView(ItemSlots slots, Direction side, boolean kettle) {
		Handlers handlers = slots.automationView(s -> new Handlers(new ItemSlotsItemHandler(s), new EnumMap<>(Direction.class)));
		return handlers.sides().computeIfAbsent(side, ruleSide ->
			kettle ? new BrewKettleItemHandler(handlers.all(), ruleSide) : new BarrelItemHandler(handlers.all(), ruleSide));
	}

	public static @Nullable SlotAccess of(ServerLevel level, BlockPos pos, @Nullable Direction side) {
		IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
		return handler == null ? null : new NeoForgeItemAccess(handler);
	}

	@Override
	public int size() {
		return handler.getSlots();
	}

	@Override
	public ItemStack get(int slot) {
		return handler.getStackInSlot(slot).copy();
	}

	@Override
	public int insert(int slot, ItemStack resource, int amount, SlotAccess.Tx transaction) {
		if (resource.isEmpty() || amount <= 0) {
			return 0;
		}
		boolean journalled = handler instanceof Journalled;
		int inserted = amount - handler.insertItem(slot, resource.copyWithCount(amount), !journalled).getCount();
		if (!journalled && inserted > 0) {
			((Tx) transaction).onCommit(() -> handler.insertItem(slot, resource.copyWithCount(inserted), false));
		}
		return inserted;
	}

	@Override
	public int extract(int slot, ItemStack resource, int amount, SlotAccess.Tx transaction) {
		if (resource.isEmpty() || amount <= 0 || !ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), resource)) {
			return 0;
		}
		boolean journalled = handler instanceof Journalled;
		int extracted = handler.extractItem(slot, amount, !journalled).getCount();
		if (!journalled && extracted > 0) {
			((Tx) transaction).onCommit(() -> handler.extractItem(slot, extracted, false));
		}
		return extracted;
	}

	/**
	 * The GameTests' transaction. This NeoForge has none (an {@code IItemHandler} only knows "simulate"), so it is made
	 * here, and means what the 26.x one meant for the tests:
	 * <ul>
	 * <li>Our own handlers (kettle, barrel) change their slots at once, so a test sees each step, but quietly: every
	 * changed slot is remembered with what it held, put back if the transaction closes without a commit, and reported
	 * to the block entity when it commits (the 26.x snapshot journal).</li>
	 * <li>Another mod's handler can't be put back, so it is only asked ("simulate") while the transaction is open and
	 * really changed when it commits. Two steps on the same slot of such a handler don't see each other.</li>
	 * </ul>
	 * Only one can be open at a time, on the server thread.
	 */
	public static final class Tx implements SlotAccess.Tx
	{
		private static @Nullable Tx open;

		private record Snapshot(ItemSlots slots, int index, ItemStack stack)
		{
		}

		private final List<Snapshot> snapshots = new ArrayList<>();
		private final List<Runnable> onCommit = new ArrayList<>();
		private boolean committed;

		private Tx() {
		}

		public static Tx openRoot() {
			if (open != null) {
				throw new IllegalStateException("An item transaction is already open");
			}
			open = new Tx();
			return open;
		}

		/** The transaction open right now, or null: then changes count at once. */
		static @Nullable Tx open() {
			return open;
		}

		/** Remembers what a slot holds before its first change in this transaction. */
		void snapshot(ItemSlots slots, int index) {
			for (Snapshot snapshot : snapshots) {
				if (snapshot.slots() == slots && snapshot.index() == index) {
					return;
				}
			}
			snapshots.add(new Snapshot(slots, index, slots.getStack(index).copy()));
		}

		void onCommit(Runnable action) {
			onCommit.add(action);
		}

		@Override
		public void commit() {
			if (open != this) {
				throw new IllegalStateException("This item transaction is closed");
			}
			committed = true;
			// Closed first: what the block entities do when they hear of the change counts at once
			open = null;
			onCommit.forEach(Runnable::run);
			snapshots.forEach(snapshot -> snapshot.slots().notifyChanged(snapshot.index(), snapshot.stack()));
		}

		@Override
		public void close() {
			if (open == this) {
				open = null;
			}
			if (!committed) {
				for (int i = snapshots.size() - 1; i >= 0; i--) {
					Snapshot snapshot = snapshots.get(i);
					snapshot.slots().setQuietly(snapshot.index(), snapshot.stack());
				}
			}
		}
	}
}
