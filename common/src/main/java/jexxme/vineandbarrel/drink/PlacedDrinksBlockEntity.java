package jexxme.vineandbarrel.drink;

import jexxme.vineandbarrel.platform.hooks.DataPacketReceiver;
import com.mojang.logging.LogUtils;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** The drinks on a placed-drinks block (D1): one item per spot, each keeping its own drink (and its glint, name, ...). */
public class PlacedDrinksBlockEntity extends BlockEntity implements DataPacketReceiver
{
	private static final Logger LOGGER = LogUtils.getLogger();

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

	/** 26.x: drop what stands here when the block goes (broken, popped by a piston, no support left); water only waterlogs it. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel serverLevel) {
			Containers.dropContents(serverLevel, pos, items);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		for (int i = 0; i < items.size(); i++) {
			items.set(i, ItemStack.EMPTY);
		}
		for (ItemStackWithSlot entry : input.listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
			if (entry.isValidInContainer(items.size())) {
				items.set(entry.slot(), entry.stack());
			}
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ValueOutput.TypedOutputList<ItemStackWithSlot> list = output.list("Items", ItemStackWithSlot.CODEC);
		for (int i = 0; i < items.size(); i++) {
			if (!items.get(i).isEmpty()) {
				list.add(new ItemStackWithSlot(i, items.get(i)));
			}
		}
	}

	@Override
	public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
			saveAdditional(output);
			return output.buildResult();
		}
	}

	@Override
	public void onDataPacket(Connection connection, ValueInput input) {
		loadWithComponents(input);
		// Each spot's liquid is tinted in its own drink's colour: re-render when a drink changes
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}
}
