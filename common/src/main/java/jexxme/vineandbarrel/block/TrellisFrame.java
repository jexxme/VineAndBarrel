package jexxme.vineandbarrel.block;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The frame a trellis row draws by itself (no extra block, nothing stored): a post on a side where the row ends, a rail
 * along the top where no lattice goes on above, and a cap on an end post at the top. A row is every lattice piece side
 * by side on the same line: bare trellises, vines on them, a bush with a trellis in its own block, and a bush drawn as the
 * base of the trellis vine above it (which carries the end posts down to the ground, no rail).
 * <p>
 * Worked out from the neighbours when the block is drawn (the loaders' model wrappers add these {@link Part}s), so rows
 * from before the frame existed, and rows in structures, are framed too.
 */
public final class TrellisFrame
{
	/** A piece of the frame, a model drawn for an east-west row (turned a quarter for a north-south one). */
	public enum Part
	{
		POST_LEFT("trellis_post_left"), POST_RIGHT("trellis_post_right"), RAIL("trellis_rail"), CAP_LEFT("trellis_cap_left"),
		CAP_RIGHT("trellis_cap_right");

		private final Identifier model;

		Part(String model) {
			this.model = VineAndBarrel.id("block/" + model);
		}

		public Identifier model() {
			return model;
		}
	}

	private TrellisFrame() {
	}

	/** The line a block draws the lattice along (a trellis, a vine on one, a bush in or under one), or {@code null}. */
	public static Direction.@Nullable Axis latticeAxis(BlockState state) {
		if (state.getBlock() instanceof TrellisBlock || state.getBlock() instanceof VineOnTrellisBlock) {
			return state.getValue(TrellisBlock.AXIS);
		}
		if (state.getBlock() instanceof ClimbingBushBlock) {
			Direction.Axis inside = state.getValue(ClimbingBushBlock.TRELLIS).axis();
			if (inside != null) {
				return inside;
			}
			return switch (state.getValue(ClimbingBushBlock.SUPPORT)) {
				case TRELLIS_X -> Direction.Axis.X;
				case TRELLIS_Z -> Direction.Axis.Z;
				default -> null;
			};
		}
		return null;
	}

	private static boolean continues(BlockState neighbour, Direction.Axis axis) {
		return latticeAxis(neighbour) == axis;
	}

	/**
	 * The frame pieces {@code state} at {@code pos} draws: the posts on the west and east ends of an east-west row (north
	 * and south of a north-south one, drawn turned), the rail and caps where nothing lattice is above. A bush under a
	 * trellis (not in one) only carries the posts.
	 */
	public static List<Part> parts(BlockGetter level, BlockPos pos, BlockState state) {
		Direction.Axis axis = latticeAxis(state);
		if (axis == null) {
			return List.of();
		}
		boolean left = !continues(level.getBlockState(pos.relative(axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH)), axis);
		boolean right = !continues(level.getBlockState(pos.relative(axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH)), axis);
		boolean underTrellis = state.getBlock() instanceof ClimbingBushBlock && state.getValue(ClimbingBushBlock.TRELLIS) == BushTrellis.NONE;
		boolean top = !underTrellis && !continues(level.getBlockState(pos.above()), axis);
		java.util.ArrayList<Part> parts = new java.util.ArrayList<>(5);
		if (left) parts.add(Part.POST_LEFT);
		if (right) parts.add(Part.POST_RIGHT);
		if (top) {
			parts.add(Part.RAIL);
			if (left) parts.add(Part.CAP_LEFT);
			if (right) parts.add(Part.CAP_RIGHT);
		}
		return parts;
	}

	/** True for a block whose model gets the frame added: the trellis, its vines, and the bushes (when under a trellis). */
	public static boolean draws(BlockState state) {
		return state.getBlock() instanceof TrellisBlock || state.getBlock() instanceof VineOnTrellisBlock
				|| state.getBlock() instanceof ClimbingBushBlock;
	}
}
