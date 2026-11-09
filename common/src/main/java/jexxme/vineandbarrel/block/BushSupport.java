package jexxme.vineandbarrel.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a grape or hop bush grows into (the block above it). Under a trellis or a rope the bush is drawn as the base of
 * the vine above (the same planes and the vine's own leaves, its trunk running up into the stem), so bush and vine read
 * as one plant; on its own it is the round bush.
 */
public enum BushSupport implements StringRepresentable
{
	NONE("none"), TRELLIS_X("trellis_x"), TRELLIS_Z("trellis_z"), ROPE("rope"),
	/** Under a fence, wall or bars running one way: the base of a vine spread across its faces (no lattice). */
	FACES_X("faces_x"), FACES_Z("faces_z");

	private final String name;

	BushSupport(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** The look for a bush of {@code crop} with {@code above} on top of it. */
	public static BushSupport of(ClimbingCropBlock crop, BlockState above) {
		if (above.getBlock() instanceof TrellisBlock || above.is(crop.trellisVine())) {
			return above.getValue(TrellisBlock.AXIS) == Direction.Axis.X ? TRELLIS_X : TRELLIS_Z;
		}
		if (VineClimbing.isRope(above) || above.is(crop.ropeVine())) {
			return ROPE;
		}
		SupportLook look = above.is(crop.supportVine()) ? above.getValue(VineOnSupportBlock.LOOK)
				: VineSupports.isMakeshift(above) ? VineSupports.lookOf(above) : null;
		if (look != null) {
			return switch (look) {
				case CROSS -> ROPE; // wound round a chain or a lone post, like round a rope
				case THIN_X, WIDE_X -> FACES_X;
				case THIN_Z, WIDE_Z -> FACES_Z;
			};
		}
		return NONE;
	}
}
