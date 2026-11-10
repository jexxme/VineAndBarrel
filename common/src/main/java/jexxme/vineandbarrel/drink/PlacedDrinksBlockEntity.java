package jexxme.vineandbarrel.drink;

import jexxme.vineandbarrel.platform.hooks.DataPacketReceiver;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** The drinks on a placed-drinks block (D1): one item per spot, each keeping its own drink (and its glint, name, ...). */
public class PlacedDrinksBlockEntity extends BlockEntity implements DataPacketReceiver
{
	private final NonNullList<ItemStack> items = NonNullList.withSize(PlacedDrinksBlock.SPOTS, ItemStack.EMPTY);

	public PlacedDrinksBlockEntity(BlockPos pos, BlockState state) {
		super(BarrelRegistry.PLACED_DRINKS_BE.get(), pos, state);
	}

	/** A copy of the item on this spot. */
	public ItemStack getItem(int spot) {
		return items.get(spot).copy();
	}

	/** The item on this spot itself, for reading only (the renderer asks every frame). */
	public ItemStack peekItem(int spot) {
		return items.get(spot);
	}

	public void setItem(int spot, ItemStack stack) {
		items.set(spot, stack.copyWithCount(stack.isEmpty() ? 0 : 1));
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	public int count() {
		int n = 0;
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) n++;
		}
		return n;
	}

	/**
	 * Drop what stands here when the block goes (broken, popped by a piston, no support left); water only waterlogs it.
	 * Called by {@link PlacedDrinksBlock#onRemove} (26.x: the block entity's own preRemoveSideEffects).
	 */
	public void dropContents() {
		if (level instanceof ServerLevel serverLevel) {
			Containers.dropContents(serverLevel, worldPosition, items);
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		for (int i = 0; i < items.size(); i++) {
			items.set(i, ItemStack.EMPTY);
		}
		// "Items": a list of stacks with their "Slot", as 26.x writes it; each stack in this Minecraft's own form
		ContainerHelper.loadAllItems(tag, items);
		// Each spot's liquid is tinted in its own drink's colour: re-render when a drink changes. Here and not only in
		// onDataPacket: 1.20.1's loaders hand an update packet to load unless their glue calls onDataPacket
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		ContainerHelper.saveAllItems(tag, items);
	}

	@Override
	public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = new CompoundTag();
		saveAdditional(tag);
		return tag;
	}

	@Override
	public void onDataPacket(Connection connection, CompoundTag tag, HolderLookup.Provider registries) {
		load(tag);
	}
}
