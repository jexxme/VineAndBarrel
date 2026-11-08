package jexxme.vineandbarrel.block;

import jexxme.vineandbarrel.tag.VBTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Climbing Farmer's Delight ropes, adapted from FD's tomatoes ({@code TomatoBlock#climbRopeAbove},
 * {@code HangingTomatoBlock#placeRope}; Farmer's Delight by vectorwing, MIT, see NOTICE_farmersdelight.txt).
 * <p>
 * Works through the {@code #farmersdelight:ropes} tag only, so there is no compile-time dependency on FD: without FD the
 * tag is empty and the bushes simply stay bushes.
 */
public final class RopeClimbing
{
	/** A climbing plant is at most 3 blocks tall including the bush, exactly like FD's tomatoes. */
	public static final int MAX_HEIGHT = 3;

	private RopeClimbing() {
	}

	public static boolean isRope(BlockState state) {
		return state.is(VBTags.FD_ROPES);
	}

	/** How tall the plant is from {@code pos} downwards: the bush plus every vine block stacked on it. */
	public static int heightAt(LevelReader level, BlockPos pos, Block bush, Block vine) {
		int height = 0;
		BlockPos.MutableBlockPos cursor = pos.mutable();
		while (true) {
			BlockState state = level.getBlockState(cursor);
			if (state.is(vine)) {
				height++;
				cursor.move(0, -1, 0);
			} else {
				return state.is(bush) ? height + 1 : height;
			}
		}
	}

	/** True when there is a rope directly above {@code pos} and the plant may still grow one block taller. */
	public static boolean canClimb(LevelReader level, BlockPos pos, Block bush, Block vine) {
		return isRope(level.getBlockState(pos.above())) && heightAt(level, pos, bush, vine) < MAX_HEIGHT;
	}

	/** Grows the vine onto the rope above. The rope is now inside the vine block (its model draws the rope). */
	public static void climb(Level level, BlockPos pos, Block vine) {
		level.setBlockAndUpdate(pos.above(), vine.defaultBlockState());
	}

	/**
	 * The rope to put back where a vine was removed. FD's own rope when FD is installed, otherwise the first rope in the
	 * tag (another mod's rope, or the test stand-in), otherwise air.
	 */
	public static BlockState ropeState(Level level, BlockPos pos) {
		Optional<Holder.Reference<Block>> fdRope = BuiltInRegistries.BLOCK.get(VBTags.FD_ROPE);
		Block rope = fdRope.map(Holder::value).filter(block -> block.defaultBlockState().is(VBTags.FD_ROPES))
				.orElseGet(() -> BuiltInRegistries.BLOCK.get(VBTags.FD_ROPES)
						.flatMap(set -> set.stream().findFirst())
						.map(Holder::value)
						.orElse(Blocks.AIR));
		return Block.updateFromNeighbourShapes(rope.defaultBlockState(), level, pos);
	}
}
