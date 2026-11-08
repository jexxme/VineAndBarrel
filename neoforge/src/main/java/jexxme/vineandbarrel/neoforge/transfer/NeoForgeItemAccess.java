package jexxme.vineandbarrel.neoforge.transfer;

import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.inventory.ItemSlots;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.platform.SlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The kettle's and the barrel's item capability (what hoppers and pipes use on NeoForge), and the GameTests' view of
 * any block's item capability ({@link SlotAccess}).
 */
public final class NeoForgeItemAccess implements SlotAccess
{
	/** One handler per inventory (it keeps the transaction journals, so every side must share it) and one view per rule side. */
	private record Handlers(ResourceHandler<ItemResource> all, Map<Direction, ResourceHandler<ItemResource>> sides)
	{
	}

	private final ResourceHandler<ItemResource> handler;

	private NeoForgeItemAccess(ResourceHandler<ItemResource> handler) {
		this.handler = handler;
	}

	/**
	 * K-automation and B14: hoppers above the kettle feed ingredients and containers, at the sides containers, a hopper
	 * below pulls the filled ones; the barrel takes bases from the top and vessels from the sides.
	 */
	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(Capabilities.Item.BLOCK, KettleRegistry.BREW_KETTLE_BE.get(),
			(be, side) -> sideView(be.getInventory(), BrewKettleBlockEntity.automationSide(side), true));
		event.registerBlockEntity(Capabilities.Item.BLOCK, BarrelRegistry.AGING_BARREL_BE.get(),
			(be, side) -> sideView(be.getInventory(), AgingBarrelBlockEntity.automationSide(side), false));
	}

	private static ResourceHandler<ItemResource> sideView(ItemSlots slots, Direction side, boolean kettle) {
		Handlers handlers = slots.automationView(s -> new Handlers(new ItemSlotsResourceHandler(s), new EnumMap<>(Direction.class)));
		return handlers.sides().computeIfAbsent(side, ruleSide ->
			kettle ? new BrewKettleItemHandler(handlers.all(), ruleSide) : new BarrelItemHandler(handlers.all(), ruleSide));
	}

	public static @Nullable SlotAccess of(ServerLevel level, BlockPos pos, @Nullable Direction side) {
		ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
		return handler == null ? null : new NeoForgeItemAccess(handler);
	}

	@Override
	public int size() {
		return handler.size();
	}

	@Override
	public ItemStack get(int slot) {
		return handler.getResource(slot).toStack(handler.getAmountAsInt(slot));
	}

	@Override
	public int insert(int slot, ItemStack resource, int amount, SlotAccess.Tx transaction) {
		return handler.insert(slot, ItemResource.of(resource), amount, ((Tx) transaction).transaction);
	}

	@Override
	public int extract(int slot, ItemStack resource, int amount, SlotAccess.Tx transaction) {
		return handler.extract(slot, ItemResource.of(resource), amount, ((Tx) transaction).transaction);
	}

	/** A NeoForge transaction. */
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
