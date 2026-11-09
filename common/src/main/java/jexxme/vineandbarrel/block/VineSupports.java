package jexxme.vineandbarrel.block;

import jexxme.vineandbarrel.tag.VBTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The blocks a grape or hop plant climbs besides the Trellis and FD's ropes: fences, walls, upright chains and bars
 * ({@code #vineandbarrel:makeshift_vine_supports}). The plant grows a {@link VineOnSupportBlock} into such a block, which
 * keeps the block exactly as it was in its block entity, draws it, and gives it back whenever the plant goes.
 * <p>
 * A fence or a wall holds a vine only from inside the plant ({@link #onlyFromInside}); a chain or bars over a bush is
 * climbed like a trellis.
 * <p>
 * While overgrown the block still is what it was for its neighbours: fences, walls, bars and fence gates connect to it,
 * and a lead stays tied to an overgrown fence (our mixins ask {@link #appearance} instead of reading the vine).
 */
public final class VineSupports
{
	/** Half the width across a row (in pixels) up to which a support counts as thin: a fence post (2), bars (1); a wall is 3 or 4. */
	private static final double THIN_HALF_WIDTH = 2.5;
	private static final Map<Level, Map<BlockPos, Stashed>> STASH = new WeakHashMap<>();

	private VineSupports() {
	}

	/**
	 * True for a block in {@code #vineandbarrel:makeshift_vine_supports} that a vine can hold: not one of our vines, and
	 * standing upright if it has an axis (a chain lying on its side holds nothing).
	 */
	public static boolean isMakeshift(BlockState state) {
		if (!state.is(VBTags.MAKESHIFT_VINE_SUPPORTS) || state.getBlock() instanceof VineOnSupportBlock) {
			return false;
		}
		return !state.hasProperty(BlockStateProperties.AXIS) || state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
	}

	/**
	 * True for a fence or a wall: the plant has to stand in it (planted into it, or the post put into a bush), as a trellis
	 * stands in a bush. A bush with one on top stays a bush and doesn't climb it; from inside, the plant climbs the ones
	 * stacked above.
	 */
	public static boolean onlyFromInside(BlockState state) {
		return state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS);
	}

	/** The block a vine at {@code pos} holds, or {@code null} when there is no vine on a support there. */
	public static @Nullable BlockState supportAt(BlockGetter level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof VineSupportBlockEntity vine ? vine.support() : null;
	}

	/** What the neighbours see at {@code pos}: the block inside a vine on a support, otherwise {@code state} itself. */
	public static BlockState appearance(BlockGetter level, BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof VineOnSupportBlock) {
			BlockState support = supportAt(level, pos);
			return support != null ? support : state;
		}
		return state;
	}

	/**
	 * How a vine sits on {@code support}: spread across the faces when it runs one way (a fence row, a wall), wound round
	 * a lone post, a corner or a crossing, and round anything without sides (a chain).
	 */
	public static SupportLook lookOf(BlockState support) {
		boolean alongX = connected(support, Direction.EAST) || connected(support, Direction.WEST);
		boolean alongZ = connected(support, Direction.NORTH) || connected(support, Direction.SOUTH);
		if (alongX == alongZ) {
			return SupportLook.CROSS;
		}
		return SupportLook.faces(isWide(support, alongX), alongX);
	}

	/** A side of a fence, bars (true) or a wall (low or tall) reaching {@code side}. */
	private static boolean connected(BlockState state, Direction side) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals(side.getSerializedName())) {
				Object value = state.getValue(property);
				return Boolean.TRUE.equals(value) || value instanceof WallSide wall && wall != WallSide.NONE;
			}
		}
		return false;
	}

	/** A wall is wide, a fence or bars thin: measured across the row on the block's own outline. */
	private static boolean isWide(BlockState support, boolean alongX) {
		VoxelShape shape = support.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		if (shape.isEmpty()) {
			return false;
		}
		AABB bounds = shape.bounds();
		double across = alongX ? bounds.maxZ - bounds.minZ : bounds.maxX - bounds.minX;
		return across * 16.0 / 2.0 > THIN_HALF_WIDTH;
	}

	// ------------------------------------------------------------------ giving the support back

	/**
	 * Remembers the support of a vine whose block entity is about to go ({@code BlockEntity#preRemoveSideEffects}), so
	 * that {@link VineOnSupportBlock#affectNeighborsAfterRemoval} can put it back right after: the vine went by fire, an
	 * explosion or anything else that isn't a player.
	 */
	static void stash(Level level, BlockPos pos, BlockState support) {
		Map<BlockPos, Stashed> stashed = STASH.computeIfAbsent(level, l -> new HashMap<>());
		long now = level.getGameTime();
		stashed.values().removeIf(entry -> entry.gameTime() != now); // never taken (a removal that told no neighbours)
		stashed.put(pos.immutable(), new Stashed(support, now));
	}

	/** The support stashed for {@code pos} in this very tick (anything older is stale), removed either way. */
	static @Nullable BlockState takeStash(Level level, BlockPos pos) {
		Map<BlockPos, Stashed> stashed = STASH.get(level);
		Stashed entry = stashed == null ? null : stashed.remove(pos);
		return entry != null && entry.gameTime() == level.getGameTime() ? entry.support() : null;
	}

	private record Stashed(BlockState support, long gameTime)
	{
	}
}
