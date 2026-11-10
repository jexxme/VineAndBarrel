package jexxme.vineandbarrel.block;

import com.mojang.logging.LogUtils;
import jexxme.vineandbarrel.platform.hooks.DataPacketReceiver;
import jexxme.vineandbarrel.registry.VBBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The block a {@link VineOnSupportBlock} grew into (a spruce fence, a mossy wall, a waxed copper chain), exactly as it
 * was: drawn under the leaves, asked by the neighbours, and put back when the plant goes.
 */
public class VineSupportBlockEntity extends BlockEntity implements DataPacketReceiver
{
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String SUPPORT = "support";

	private BlockState support = Blocks.AIR.defaultBlockState();

	public VineSupportBlockEntity(BlockPos pos, BlockState state) {
		super(VBBlockEntities.VINE_SUPPORT.get(), pos, state);
	}

	/** The block inside, or {@code null} if there is none (a vine placed by a command). */
	public @Nullable BlockState support() {
		return support.isAir() ? null : support;
	}

	public void setSupport(BlockState support) {
		support = holdable(support);
		if (support == this.support) {
			return;
		}
		this.support = support;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		support = holdable(tag.contains(SUPPORT)
				? BlockState.CODEC.parse(NbtOps.INSTANCE, tag.get(SUPPORT))
						.resultOrPartial(error -> LOGGER.warn("Vine support at {}: {}", worldPosition, error)).orElse(Blocks.AIR.defaultBlockState())
				: Blocks.AIR.defaultBlockState());
	}

	/**
	 * Only a plain block can be held: never another vine on a support or anything with a block entity of its own (it could
	 * come from a command or a pasted structure), which would draw and collide through itself without end.
	 */
	private static BlockState holdable(BlockState support) {
		return support.getBlock() instanceof VineOnSupportBlock || support.hasBlockEntity() ? Blocks.AIR.defaultBlockState() : support;
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (!support.isAir()) {
			BlockState.CODEC.encodeStart(NbtOps.INSTANCE, support)
					.resultOrPartial(error -> LOGGER.warn("Vine support at {}: {}", worldPosition, error))
					.ifPresent(encoded -> tag.put(SUPPORT, encoded));
		}
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
		// The support is drawn with the vine: draw the block again now that the client knows it
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}
}
