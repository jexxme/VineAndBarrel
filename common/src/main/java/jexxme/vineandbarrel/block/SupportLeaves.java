package jexxme.vineandbarrel.block;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;

import java.util.ArrayList;
import java.util.List;

/**
 * The leaves of a vine on a fence, wall, bars or chain ({@link VineOnSupportBlock}), worked out from the block it holds
 * each time it is drawn (the loaders' model wrappers add these pieces, like the trellis frame).
 * <p>
 * On a fence, a wall or bars the leaves follow the block's own shape: on the post, and along each arm that reaches a
 * neighbour (a fence's rails, a wall's low or tall side, the bars), close to the faces, so the block keeps its outline
 * and a row reads as a fence, a wall or bars with a vine on it. Round a chain (and anything else without arms) the vine
 * is two crossed planes through the middle, the chain's own crossed planes between them.
 * <p>
 * Each piece is a model per vine block, age and root ({@link #model}), built for a row running east-west or an arm
 * reaching west, and drawn turned a quarter at a time ({@link Placed#turn}: 0 as built, 1 north, 2 east, 3 south for an
 * arm; 0 east-west, 1 north-south for a post's faces).
 */
public final class SupportLeaves
{
	/** A piece of the leaves, and how many quarter turns it is drawn in. */
	public enum Piece
	{
		FENCE_POST("fence_post", 2), FENCE_ARM("fence_arm", 4), WALL_POST("wall_post", 2), WALL_ARM_LOW("wall_arm_low", 4),
		WALL_ARM_TALL("wall_arm_tall", 4), BARS_POST("bars_post", 2), BARS_ARM("bars_arm", 4), CROSS("cross", 1);

		private final String name;
		private final int turns;

		Piece(String name, int turns) {
			this.name = name;
			this.turns = turns;
		}

		public String pieceName() {
			return name;
		}

		/** How many quarter turns of this piece are drawn (1, 2 or 4). */
		public int turns() {
			return turns;
		}
	}

	/** A piece in one of its quarter turns. */
	public record Placed(Piece piece, int turn)
	{
	}

	/** The arms in turn order: an arm piece built reaching west, turned to reach north, east, south. */
	private static final Direction[] ARMS = {Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH};

	private SupportLeaves() {
	}

	/** The pieces of leaves on {@code support}. */
	public static List<Placed> of(BlockState support) {
		List<Placed> pieces = new ArrayList<>(6);
		if (isWall(support)) {
			boolean any = false;
			for (int turn = 0; turn < 4; turn++) {
				WallSide side = support.getValue(wallSide(ARMS[turn]));
				if (side != WallSide.NONE) {
					pieces.add(new Placed(side == WallSide.TALL ? Piece.WALL_ARM_TALL : Piece.WALL_ARM_LOW, turn));
					any = true;
				}
			}
			if (support.getValue(BlockStateProperties.UP) || !any) {
				pieces.add(new Placed(Piece.WALL_POST, 0));
				pieces.add(new Placed(Piece.WALL_POST, 1));
			}
			return pieces;
		}
		if (!hasArms(support)) {
			pieces.add(new Placed(Piece.CROSS, 0));
			return pieces;
		}
		boolean fence = support.is(BlockTags.FENCES);
		boolean alongX = false;
		boolean alongZ = false;
		for (int turn = 0; turn < 4; turn++) {
			if (support.getValue(side(ARMS[turn]))) {
				pieces.add(new Placed(fence ? Piece.FENCE_ARM : Piece.BARS_ARM, turn));
				if (ARMS[turn].getAxis() == Direction.Axis.X) {
					alongX = true;
				} else {
					alongZ = true;
				}
			}
		}
		boolean lone = !alongX && !alongZ;
		if (fence) {
			// the post's faces across each way an arm runs (both ways on a lone post or a corner)
			if (alongX || lone) pieces.add(new Placed(Piece.FENCE_POST, 0));
			if (alongZ || lone) pieces.add(new Placed(Piece.FENCE_POST, 1));
		} else if (lone) {
			pieces.add(new Placed(Piece.BARS_POST, 0));
			pieces.add(new Placed(Piece.BARS_POST, 1));
		}
		return pieces;
	}

	/** True for a support without arms (a chain): the vine is crossed planes through it. */
	public static boolean crossed(BlockState support) {
		return !isWall(support) && !hasArms(support);
	}

	/**
	 * The model of {@code piece} for the vine block {@code vine} at {@code age}, as its root ({@code base}: the bush's art)
	 * or as a vine above it (the trellis vine's art).
	 */
	public static ResourceLocation model(Block vine, Piece piece, boolean base, int age) {
		return VineAndBarrel.id("block/" + BuiltInRegistries.BLOCK.getKey(vine).getPath() + "_" + piece.pieceName() + (base ? "_base" : "") + "_stage" + age);
	}

	private static boolean isWall(BlockState state) {
		return state.hasProperty(BlockStateProperties.UP) && state.hasProperty(BlockStateProperties.NORTH_WALL);
	}

	private static boolean hasArms(BlockState state) {
		return state.hasProperty(BlockStateProperties.NORTH) && state.hasProperty(BlockStateProperties.EAST)
				&& state.hasProperty(BlockStateProperties.SOUTH) && state.hasProperty(BlockStateProperties.WEST);
	}

	private static BooleanProperty side(Direction direction) {
		return switch (direction) {
			case NORTH -> BlockStateProperties.NORTH;
			case EAST -> BlockStateProperties.EAST;
			case SOUTH -> BlockStateProperties.SOUTH;
			default -> BlockStateProperties.WEST;
		};
	}

	private static EnumProperty<WallSide> wallSide(Direction direction) {
		return switch (direction) {
			case NORTH -> BlockStateProperties.NORTH_WALL;
			case EAST -> BlockStateProperties.EAST_WALL;
			case SOUTH -> BlockStateProperties.SOUTH_WALL;
			default -> BlockStateProperties.WEST_WALL;
		};
	}
}
