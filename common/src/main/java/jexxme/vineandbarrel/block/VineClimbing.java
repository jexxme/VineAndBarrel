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
 * Climbing a support: our Trellis, or a Farmer's Delight rope. Adapted from FD's tomatoes ({@code TomatoBlock#climbRopeAbove},
 * {@code HangingTomatoBlock#placeRope}; Farmer's Delight by vectorwing, MIT, see NOTICE_farmersdelight.txt).
 * <p>
 * What counts as a support is the {@code #vineandbarrel:vine_supports} tag: the Trellis, plus {@code #farmersdelight:ropes}
 * as an optional entry. A rope grows the vine-on-rope block (which puts the rope back when it goes), the Trellis the
 * vine-on-trellis block (which puts the trellis back). Anything else added to the tag is ignored: a vine needs a block
 * of its own that draws and restores that support. There is no compile-time dependency on FD: without FD the rope tag is
 * empty and only the Trellis remains.
 * <p>
 * The height limit holds however a vine got there: a vine taller than {@link #MAX_HEIGHT} (a piston pushed it onto
 * another plant) can't survive, drops its fruit and leaves its support.
 */
public final class VineClimbing
{
	/** A climbing plant is at most 3 blocks tall including the bush, on a trellis as on a rope (FD's tomatoes). */
	public static final int MAX_HEIGHT = 3;

	private VineClimbing() {
	}

	public static boolean isSupport(BlockState state) {
		return state.is(VBTags.VINE_SUPPORTS);
	}

	public static boolean isRope(BlockState state) {
		return state.is(VBTags.FD_ROPES);
	}

	/** How tall the plant is from {@code pos} downwards: the bush plus every vine of it stacked on top, rope or trellis. */
	public static int heightAt(LevelReader level, BlockPos pos, ClimbingCropBlock crop) {
		int height = 0;
		BlockPos.MutableBlockPos cursor = pos.mutable();
		while (true) {
			BlockState state = level.getBlockState(cursor);
			if (crop.isVine(state)) {
				height++;
				cursor.move(0, -1, 0);
			} else {
				return state.is(crop.bushBlock()) ? height + 1 : height;
			}
		}
	}

	/** True when there is a support directly above {@code pos} and the plant may still grow one block taller. */
	public static boolean canClimb(LevelReader level, BlockPos pos, ClimbingCropBlock crop) {
		BlockState above = level.getBlockState(pos.above());
		return isSupport(above) && crop.vineOn(above) != null && heightAt(level, pos, crop) < MAX_HEIGHT;
	}

	/** Grows the vine onto the support above. The support is now inside the vine block (its model draws it). */
	public static void climb(Level level, BlockPos pos, ClimbingCropBlock crop) {
		BlockPos above = pos.above();
		BlockState vine = crop.vineOn(level.getBlockState(above));
		if (vine != null) {
			level.setBlockAndUpdate(above, vine);
		}
	}

	/** A vine survives on its own plant (bush or vine below), within the height limit, in light (FD's tomato vine rule). */
	public static boolean vineCanSurvive(LevelReader level, BlockPos pos, ClimbingCropBlock vine) {
		return vine.isPlant(level.getBlockState(pos.below())) && heightAt(level, pos, vine) <= MAX_HEIGHT
				&& (level.getRawBrightness(pos, 0) >= 8 || level.canSeeSky(pos));
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
