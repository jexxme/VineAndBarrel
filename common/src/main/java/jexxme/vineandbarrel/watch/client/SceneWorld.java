package jexxme.vineandbarrel.watch.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The little stage a scene plays on: real block states and block entities, no level behind them. It answers what block
 * rendering asks (neighbours for culling and ambient occlusion, tints, block entities for tints) with everything in full
 * light, the way blocks look in an inventory.
 */
public final class SceneWorld implements BlockAndTintGetter
{
	static final int HEIGHT = 8;

	private final Map<BlockPos, BlockState> blocks = new HashMap<>();
	private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
	private final @Nullable Holder<Biome> biome;
	/** Never asked for light (everything here is in full light): an engine without light, only so there is one. */
	private final LevelLightEngine noLight = new LevelLightEngine(new LightChunkGetter()
	{
		@Override
		public @Nullable LightChunk getChunkForLighting(int chunkX, int chunkZ) {
			return null;
		}

		@Override
		public BlockGetter getLevel() {
			return SceneWorld.this;
		}
	}, false, false);

	SceneWorld(@Nullable Holder<Biome> biome) {
		this.biome = biome;
	}

	void clear() {
		blocks.clear();
		blockEntities.clear();
	}

	/** Sets a block; a block entity of the same type stays (so a barrel keeps its contents when its state changes). */
	void set(BlockPos pos, BlockState state) {
		BlockPos at = pos.immutable();
		if (state.isAir()) {
			blocks.remove(at);
			blockEntities.remove(at);
			return;
		}
		blocks.put(at, state);
		BlockEntity existing = blockEntities.get(at);
		if (existing != null && existing.getType().isValid(state)) {
			existing.setBlockState(state);
		} else if (state.getBlock() instanceof EntityBlock entityBlock) {
			BlockEntity created = entityBlock.newBlockEntity(at, state);
			if (created != null) {
				blockEntities.put(at, created);
			} else {
				blockEntities.remove(at);
			}
		} else {
			blockEntities.remove(at);
		}
	}

	Map<BlockPos, BlockState> blocks() {
		return Collections.unmodifiableMap(blocks);
	}

	Map<BlockPos, BlockEntity> blockEntities() {
		return Collections.unmodifiableMap(blockEntities);
	}

	@Nullable Holder<Biome> biome() {
		return biome;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return blockEntities.get(pos);
	}

	/** How bright a face is by the way it looks, as in the overworld: the top brightest, the bottom darkest. */
	@Override
	public float getShade(Direction direction, boolean shade) {
		if (!shade) {
			return 1.0F;
		}
		return switch (direction) {
			case DOWN -> 0.5F;
			case UP -> 1.0F;
			case NORTH, SOUTH -> 0.8F;
			case WEST, EAST -> 0.6F;
		};
	}

	@Override
	public LevelLightEngine getLightEngine() {
		return noLight;
	}

	@Override
	public int getBrightness(LightLayer layer, BlockPos pos) {
		return 15;
	}

	@Override
	public int getRawBrightness(BlockPos pos, int darkening) {
		return 15;
	}

	@Override
	public int getBlockTint(BlockPos pos, ColorResolver color) {
		return biome == null ? -1 : color.getColor(biome.value(), pos.getX(), pos.getZ());
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}

	@Override
	public int getMinBuildHeight() {
		return 0;
	}
}
