package jexxme.vineandbarrel.entity;

import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.mixin.BeeAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * C2: bees help grape and hop bushes (and their vines on a rope) grow. Vanilla's {@code Bee.BeeGrowCropGoal} only
 * knows crops, stems, the sweet berry bush and cave vines by class, so {@code #minecraft:bee_growables} alone does
 * nothing for ours. This is the missing case, run from the goal's tick with the goal's own odds
 * ({@code BeeGrowCropGoalMixin}): the same two blocks below the bee, the same particles, the same per-pollination cap.
 */
public final class BeeGrowth
{
	/** Vanilla's {@code adjustedTickDelay(30)} for a goal that doesn't update every tick: 30 halved, rounded up. */
	public static final int ONE_IN = 15;

	private BeeGrowth() {
	}

	/**
	 * One growth pass of a pollinating bee over our crops.
	 *
	 * @return the last position that grew, or {@code null}
	 */
	public static @Nullable BlockPos grow(Bee bee) {
		Level level = bee.level();
		BlockPos grown = null;
		for (int i = 1; i <= 2; i++) {
			BlockPos below = bee.blockPosition().below(i);
			BlockState state = level.getBlockState(below);
			if (!state.is(BlockTags.BEE_GROWABLES)) {
				continue;
			}
			BlockState next = ClimbingCropBlock.beeGrowState(state);
			if (next != null) {
				level.levelEvent(2011, below, 15);
				level.setBlockAndUpdate(below, next);
				((BeeAccessor) bee).vineandbarrel$incrementCropsGrown();
				grown = below;
			}
		}
		return grown;
	}
}
