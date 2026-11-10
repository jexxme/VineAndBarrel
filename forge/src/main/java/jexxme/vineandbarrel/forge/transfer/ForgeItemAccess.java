package jexxme.vineandbarrel.forge.transfer;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.inventory.ItemSlots;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.BrewKettlePartBlock;
import jexxme.vineandbarrel.kettle.KettleMultiblock;
import jexxme.vineandbarrel.platform.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The kettle's and the barrel's item capability (what hoppers and pipes use on Forge), and the GameTests' view of
 * any block's item inventory ({@link SlotAccess}).
 */
public final class ForgeItemAccess implements SlotAccess
{
	private static final ResourceLocation KEY = VineAndBarrel.id("items");

	/** One of our own handlers: its changes inside a {@link Tx} are journalled (see {@link ItemSlotsItemHandler}). */
	interface Journalled
	{
	}

	/** One handler per inventory and one view per rule side. */
	private record Handlers(IItemHandler all, Map<Direction, IItemHandler> sides)
	{
	}

	private final IItemHandler handler;

	private ForgeItemAccess(IItemHandler handler) {
		this.handler = handler;
	}

	/**
	 * K-automation and B14: hoppers above the kettle feed ingredients and containers, at the sides containers, a hopper
	 * below pulls the filled ones; the barrel takes bases from the top and vessels from the sides.
	 * <p>
	 * Forge 47 has capabilities on block entities only, and the common block entity classes can't override
	 * {@code getCapability}: the capability is attached to ours from here. Every kettle block entity, a single kettle's
	 * or a big kettle's controller, hands out its kettle's inventory with the rules of the side it is asked from. The
	 * other blocks of a big kettle have no block entity: hoppers and droppers reach them through
	 * {@code VanillaInventoryCodeHooksMixin}, another mod's pipe only connects where the block entity is.
	 */
	public static void init() {
		MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, (AttachCapabilitiesEvent<BlockEntity> event) -> {
			BlockEntity blockEntity = event.getObject();
			if (blockEntity instanceof BrewKettleBlockEntity || blockEntity instanceof AgingBarrelBlockEntity) {
				Provider provider = new Provider(blockEntity);
				event.addCapability(KEY, provider);
				// Runs when the block entity is removed or its chunk unloads
				event.addListener(provider::invalidate);
			}
		});
	}

	/**
	 * A kettle's or barrel's capability provider. The event comes from the block entity's constructor, before its
	 * inventory exists, so everything is looked up when it is asked for. The optionals are kept per side (pipes cache
	 * them and listen for their end) and are invalidated with the block entity, or when its kettle changes.
	 */
	private static final class Provider implements ICapabilityProvider
	{
		private record Handed(IItemHandler handler, LazyOptional<IItemHandler> optional)
		{
		}

		private final BlockEntity blockEntity;
		private final Handed[] handed = new Handed[Direction.values().length + 1];

		private Provider(BlockEntity blockEntity) {
			this.blockEntity = blockEntity;
		}

		@Override
		public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
			if (capability != ForgeCapabilities.ITEM_HANDLER) {
				return LazyOptional.empty();
			}
			IItemHandler handler = handler(side);
			int index = side == null ? handed.length - 1 : side.ordinal();
			Handed current = handed[index];
			if (current != null && current.handler() != handler) {
				current.optional().invalidate();
				current = null;
			}
			if (handler == null) {
				handed[index] = null;
				return LazyOptional.empty();
			}
			if (current == null) {
				current = new Handed(handler, LazyOptional.of(() -> handler));
				handed[index] = current;
			}
			return current.optional().cast();
		}

		private @Nullable IItemHandler handler(@Nullable Direction side) {
			if (blockEntity instanceof AgingBarrelBlockEntity barrel) {
				return sideView(barrel.getInventory(), AgingBarrelBlockEntity.automationSide(side), false);
			}
			Level level = blockEntity.getLevel();
			BrewKettleBlockEntity kettle = level == null ? null : KettleMultiblock.controller(level, blockEntity.getBlockPos());
			return kettle == null ? null : kettleView(kettle, side);
		}

		private void invalidate() {
			for (int i = 0; i < handed.length; i++) {
				if (handed[i] != null) {
					handed[i].optional().invalidate();
					handed[i] = null;
				}
			}
		}
	}

	/** A kettle's inventory as seen from {@code side} of any of its blocks. */
	public static IItemHandler kettleView(BrewKettleBlockEntity kettle, @Nullable Direction side) {
		return sideView(kettle.getInventory(), BrewKettleBlockEntity.automationSide(side), true);
	}

	/** The kettle a part block at {@code pos} belongs to, or null if there is no kettle part there. */
	public static @Nullable BrewKettleBlockEntity kettleBehindPart(Level level, BlockPos pos) {
		return level.getBlockState(pos).getBlock() instanceof BrewKettlePartBlock ? KettleMultiblock.controller(level, pos) : null;
	}

	private static IItemHandler sideView(ItemSlots slots, Direction side, boolean kettle) {
		Handlers handlers = slots.automationView(s -> new Handlers(new ItemSlotsItemHandler(s), new EnumMap<>(Direction.class)));
		return handlers.sides().computeIfAbsent(side, ruleSide ->
			kettle ? new BrewKettleItemHandler(handlers.all(), ruleSide) : new BarrelItemHandler(handlers.all(), ruleSide));
	}

	/**
	 * What a hopper finds at {@code pos}: the block entity's item capability, a block of a big kettle without one, or a
	 * vanilla container that has no capability (a composter), wrapped as Forge wraps vanilla containers.
	 */
	public static @Nullable SlotAccess of(ServerLevel level, BlockPos pos, @Nullable Direction side) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		IItemHandler handler = blockEntity == null ? null : blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null);
		if (handler == null) {
			BrewKettleBlockEntity kettle = kettleBehindPart(level, pos);
			if (kettle != null) {
				handler = kettleView(kettle, side);
			}
		}
		if (handler == null) {
			Container container = HopperBlockEntity.getContainerAt(level, pos);
			if (container != null) {
				handler = container instanceof WorldlyContainer sided && side != null ? new SidedInvWrapper(sided, side) : new InvWrapper(container);
			}
		}
		return handler == null ? null : new ForgeItemAccess(handler);
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
		if (resource.isEmpty() || amount <= 0 || !ItemStack.isSameItemSameTags(handler.getStackInSlot(slot), resource)) {
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
	 * The GameTests' transaction. Forge 47 has none (an {@code IItemHandler} only knows "simulate"), so it is made
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
