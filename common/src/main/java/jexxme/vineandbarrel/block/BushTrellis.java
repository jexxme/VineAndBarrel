package jexxme.vineandbarrel.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * The trellis standing in a grape or hop bush's own block, if any, and the way its panel runs: the bush was planted into
 * a trellis, or a trellis was put into the bush. It is drawn with the bush as the foot of the vine, and stays standing
 * when the plant goes.
 */
public enum BushTrellis implements StringRepresentable
{
	NONE("none", null), X("x", Direction.Axis.X), Z("z", Direction.Axis.Z);

	private final String name;
	private final Direction.@Nullable Axis axis;

	BushTrellis(String name, Direction.@Nullable Axis axis) {
		this.name = name;
		this.axis = axis;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** {@return the way the panel runs, or null without a trellis} */
	public Direction.@Nullable Axis axis() {
		return axis;
	}

	/** The trellis whose panel runs along {@code axis} (a horizontal one). */
	public static BushTrellis of(Direction.Axis axis) {
		return axis == Direction.Axis.X ? X : Z;
	}
}
