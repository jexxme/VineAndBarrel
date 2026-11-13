package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.kettle.BrewKettleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Room to walk in a tavern, worked out from the blocks as they stand: where a player or villager can stand (two blocks
 * of headroom over a floor, or on a stair or slab), how they get from one spot to the next (half a block up or down, a
 * whole one only along a flight of stairs) and which of those spots belong to a free two by two square. From the street
 * joint it then proves:
 * <ul>
 * <li>a way two wide from the street through the door to the bar counter (the main route);</li>
 * <li>a way at least one wide to every seat (a chair or bench at a table or the bar, a stool at the bar), to the kettle
 * and the tavern chest behind the bar, to every cask (beside it, at its foot) and down into the cellar under the floor.</li>
 * </ul>
 * Chairs and benches are stairs that lead nowhere higher (the way a staircase does), so nobody walks over them, and the
 * tops of tables, counters and casks are a whole block up from the floor, so nobody climbs on those either.
 */
public final class TavernWalk
{
	/** A spot a player can stand: its cell (the feet) and the height they stand at. */
	private record Node(BlockPos pos, double height, boolean step) {
	}

	/** What was found: the problems (none when the tavern passes), and a map of every level people walk on. */
	public record Result(List<String> problems, String map) {
		public boolean ok() {
			return problems.isEmpty();
		}
	}

	private final BlockGetter level;
	private final BoundingBox box;
	private final int floorY;
	private final Map<BlockPos, Node> nodes = new HashMap<>();

	private TavernWalk(BlockGetter level, BoundingBox box, int floorY) {
		this.level = level;
		this.box = box;
		this.floorY = floorY;
	}

	/**
	 * Checks the tavern standing in {@code box}, entered from the street at {@code street} (the street joint's cell, at
	 * door height); {@code floorY} is the floor's layer (the hall's floor blocks), everything below it is the cellar.
	 */
	public static Result check(BlockGetter level, BoundingBox box, BlockPos street, int floorY) {
		return new TavernWalk(level, box, floorY).run(street);
	}

	// ------------------------------------------------------------------ the blocks

	private BlockState state(BlockPos pos) {
		return level.getBlockState(pos);
	}

	/** How high the block's collision reaches in its cell (0 when nothing collides). */
	private double top(BlockPos pos) {
		var shape = state(pos).getCollisionShape(level, pos);
		return shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y);
	}

	/** Whether someone can be in this cell: nothing collides, or only a carpet or thin snow, or it's a door or open gate. */
	private boolean free(BlockPos pos) {
		BlockState state = state(pos);
		if (state.getBlock() instanceof DoorBlock || (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN))
			|| isLadder(pos)) {
			return true;
		}
		if (!state.getFluidState().isEmpty()) {
			return false;
		}
		return top(pos) <= 0.2;
	}

	/** A rung: someone on a ladder is in its cell. */
	private boolean isLadder(BlockPos pos) {
		return state(pos).getBlock() instanceof net.minecraft.world.level.block.LadderBlock;
	}

	/** A trapdoor in a floor with a ladder under it (1.2's cellar vaults): a player opens it and climbs. */
	private boolean isHatch(BlockPos pos) {
		return state(pos).getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock && isLadder(pos.below());
	}

	private static boolean isStep(BlockState state) {
		return (state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM)
			|| (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM);
	}

	/** The spot in this cell, if someone can stand there: on a floor below, or on a stair or slab in the cell. */
	private Node nodeAt(BlockPos pos) {
		BlockState state = state(pos);
		if (isStep(state)) {
			return free(pos.above()) && free(pos.above(2)) ? new Node(pos, pos.getY() + 0.5, true) : null;
		}
		if (!free(pos) || !free(pos.above())) {
			return null;
		}
		double below = top(pos.below());
		if (below < 0.8 || below > 1.0) {
			return null;
		}
		return new Node(pos, pos.getY() - 1 + below + (top(pos) <= 0.2 ? top(pos) : 0), false);
	}

	/** A stair someone sits on rather than climbs: nothing higher to climb to on its tall side, and no step below it. */
	private boolean isSeatStair(Node node) {
		BlockState state = state(node.pos());
		if (!(state.getBlock() instanceof StairBlock)) {
			return false;
		}
		Direction facing = state.getValue(StairBlock.FACING);
		Node up = nodes.get(node.pos().relative(facing));
		Node upper = nodes.get(node.pos().relative(facing).above());
		// the top step of a flight that turns off at the top: the step before it lies one lower behind it
		Node before = nodes.get(node.pos().relative(facing.getOpposite()).below());
		return !((up != null && up.height() >= node.height() + 0.4) || (upper != null && upper.height() >= node.height() + 0.4) || (before != null && before.step()));
	}

	/** Whether someone can walk between these two spots side by side. */
	private boolean joined(Node a, Node b, Direction from) {
		double dh = b.height() - a.height();
		if (Math.abs(dh) <= 0.55) {
			return true;
		}
		if (Math.abs(dh) > 1.05) {
			return false;
		}
		// a whole block only along a flight of stairs: the lower spot is a stair whose tall side faces the higher one
		Node low = dh > 0 ? a : b;
		Direction up = dh > 0 ? from : from.getOpposite();
		BlockState state = state(low.pos());
		return state.getBlock() instanceof StairBlock && state.getValue(StairBlock.FACING) == up;
	}

	private List<Node> neighbours(Node node) {
		List<Node> out = new ArrayList<>();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int dy = -1; dy <= 1; dy++) {
				Node next = nodes.get(node.pos().relative(d).above(dy));
				if (next != null && joined(node, next, d)) {
					out.add(next);
				}
			}
		}
		// up and down a ladder, rung by rung (1.2's lower cellar rooms)
		BlockPos p = node.pos();
		for (BlockPos q : List.of(p.below(), p.above())) {
			Node next = nodes.get(q);
			if (next != null && (isLadder(p) || isLadder(q))) {
				out.add(next);
			}
		}
		// through a hatch: from the floor over it down to the first rung with headroom, and back up
		if (isHatch(p.below())) {
			for (BlockPos q = p.below(2); isLadder(q) && box.isInside(q); q = q.below()) {
				Node rung = nodes.get(q);
				if (rung != null) {
					out.add(rung);
					break;
				}
			}
		}
		if (isLadder(p)) {
			BlockPos q = p.above();
			while (isLadder(q) && box.isInside(q)) {
				q = q.above();
			}
			if (isHatch(q) && nodes.get(q.above()) != null && (nodes.get(p.above()) == null)) {
				out.add(nodes.get(q.above()));
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ the check

	private Result run(BlockPos street) {
		BoundingBox scan = box.inflatedBy(0);
		for (BlockPos p : BlockPos.betweenClosed(scan.minX(), scan.minY() + 1, scan.minZ(), scan.maxX(), scan.maxY() - 1, scan.maxZ())) {
			Node node = nodeAt(p.immutable());
			if (node != null) {
				nodes.put(node.pos(), node);
			}
		}
		// seats are furniture, not floor
		Set<BlockPos> seats = new HashSet<>();
		for (Node node : List.copyOf(nodes.values())) {
			if (node.step() && isSeatStair(node)) {
				seats.add(node.pos());
			}
		}
		seats.forEach(nodes::remove);
		List<String> problems = new ArrayList<>();
		Node start = nodes.get(street);
		if (start == null) {
			problems.add("nowhere to stand at the street joint " + rel(street));
			return new Result(problems, "");
		}
		// one wide: everywhere a single person gets to, and how far each spot is from the street
		Map<BlockPos, Integer> distance = distances(start, nodes.keySet());
		Set<BlockPos> reached = distance.keySet();
		// two wide: spots in a free two by two square
		Set<BlockPos> clear = new HashSet<>();
		for (Node node : nodes.values()) {
			for (int ox = -1; ox <= 0; ox++) {
				for (int oz = -1; oz <= 0; oz++) {
					if (square(node.pos().offset(ox, 0, oz), node.height())) {
						clear.add(node.pos());
					}
				}
			}
		}
		// the door: the nearest door to the street; the main route starts just inside it
		BlockPos door = distance.entrySet().stream().filter(e -> state(e.getKey()).getBlock() instanceof DoorBlock)
			.min(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(null);
		Set<BlockPos> wide = new HashSet<>();
		if (door == null) {
			problems.add("no door on the way in from the street");
		} else {
			for (Node inside : neighbours(nodes.get(door))) {
				if (distance.getOrDefault(inside.pos(), 0) > distance.get(door) && clear.contains(inside.pos())) {
					wide.addAll(reach(inside, clear));
				}
			}
			if (wide.isEmpty()) {
				problems.add("no room two wide inside the door at " + rel(door));
			}
		}

		// the targets
		List<BlockPos> counters = new ArrayList<>(), tables = new ArrayList<>(), casks = new ArrayList<>(), seatList = new ArrayList<>();
		BlockPos chest = null, kettle = null;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = state(p);
			BlockPos pos = p.immutable();
			boolean drinksOn = state(pos.above()).is(BarrelRegistry.PLACED_DRINKS.get());
			// a table: a trapdoor top, or its leg (an upside-down stair) with drinks on it
			if ((s.getBlock() instanceof TrapDoorBlock && !s.getValue(TrapDoorBlock.OPEN) && s.getValue(TrapDoorBlock.HALF) == Half.TOP)
				|| (drinksOn && s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.TOP)) {
				tables.add(pos);
			} else if (drinksOn && top(pos) >= 0.9 && !(s.getBlock() instanceof StairBlock)) {
				counters.add(pos);
			}
			if (s.is(BarrelRegistry.AGING_BARREL.get()) && !s.getValue(AgingBarrelBlock.STACKED)) {
				casks.add(pos);
			}
			if (s.is(Blocks.CHEST)) {
				chest = pos;
			}
			if (s.getBlock() instanceof BrewKettleBlock) {
				kettle = pos.below();
			}
		}
		Set<BlockPos> furniture = new HashSet<>(tables);
		furniture.addAll(counters);
		for (BlockPos seat : seats) {
			Direction front = state(seat).getValue(StairBlock.FACING).getOpposite();
			if (furniture.contains(seat.relative(front))) {
				seatList.add(seat);
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (state(p).getBlock() instanceof FenceBlock && state(p.above()).getBlock() instanceof CarpetBlock) {
				BlockPos stool = p.immutable();
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if (furniture.contains(stool.relative(d))) {
						seatList.add(stool);
						break;
					}
				}
			}
		}
		if (counters.isEmpty()) {
			problems.add("no bar counter (a block with drinks on it)");
		} else if (counters.stream().noneMatch(c -> beside(c, wide, 0))) {
			problems.add("no way two wide from the door to the bar counter " + rels(counters));
		}
		for (BlockPos seat : seatList) {
			if (!beside(seat, reached, 1)) {
				problems.add("no way to the seat at " + rel(seat));
			}
		}
		for (BlockPos cask : casks) {
			if (!beside(cask, reached, 0)) {
				problems.add("no way to the cask at " + rel(cask));
			}
		}
		if (chest == null || !beside(chest, reached, 0)) {
			problems.add("no way to the tavern chest" + (chest == null ? "" : " at " + rel(chest)));
		}
		if (kettle == null || !beside(kettle, reached, 0)) {
			problems.add("no way to the kettle" + (kettle == null ? "" : " at " + rel(kettle)));
		}
		boolean cellar = reached.stream().anyMatch(p -> p.getY() < floorY);
		if (!cellar) {
			problems.add("no way down into the cellar");
		}
		if (casks.stream().noneMatch(c -> c.getY() < floorY)) {
			problems.add("no casks in the cellar");
		}
		return new Result(problems, map(reached, wide, seats, Set.copyOf(casks), Set.copyOf(counters)));
	}

	/** Whether a free two by two square of spots at about this height has its corner here. */
	private boolean square(BlockPos corner, double height) {
		for (int dx = 0; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				Node n = nodes.get(corner.offset(dx, 0, dz));
				if (n == null || Math.abs(n.height() - height) > 0.55) {
					return false;
				}
			}
		}
		return true;
	}

	/** Every spot reachable from {@code start} through {@code allowed}, with the number of steps to it. */
	private Map<BlockPos, Integer> distances(Node start, Set<BlockPos> allowed) {
		Map<BlockPos, Integer> seen = new HashMap<>();
		ArrayDeque<Node> queue = new ArrayDeque<>();
		seen.put(start.pos(), 0);
		queue.add(start);
		while (!queue.isEmpty()) {
			Node node = queue.poll();
			for (Node next : neighbours(node)) {
				if (allowed.contains(next.pos()) && !seen.containsKey(next.pos())) {
					seen.put(next.pos(), seen.get(node.pos()) + 1);
					queue.add(next);
				}
			}
		}
		return seen;
	}

	private Set<BlockPos> reach(Node start, Set<BlockPos> allowed) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<Node> queue = new ArrayDeque<>();
		seen.add(start.pos());
		queue.add(start);
		while (!queue.isEmpty()) {
			Node node = queue.poll();
			for (Node next : neighbours(node)) {
				if (allowed.contains(next.pos()) && seen.add(next.pos())) {
					queue.add(next);
				}
			}
		}
		return seen;
	}

	/** Whether one of the spots beside this block (at its foot, up to {@code up} higher) is in {@code set}. */
	private static boolean beside(BlockPos block, Set<BlockPos> set, int up) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int dy = 0; dy <= up; dy++) {
				if (set.contains(block.relative(d).above(dy))) {
					return true;
				}
			}
		}
		return false;
	}

	private String rel(BlockPos pos) {
		return (pos.getX() - box.minX()) + " " + (pos.getY() - box.minY()) + " " + (pos.getZ() - box.minZ());
	}

	private String rels(List<BlockPos> list) {
		return list.stream().map(this::rel).toList().toString();
	}

	/**
	 * Every level someone stands on, seen from above (x across, z down the page, relative to the box): 2 reached two
	 * wide, 1 reached one wide only, o a spot nobody reaches, h a seat, c a cask, = the counter, # anything else.
	 */
	private String map(Set<BlockPos> reached, Set<BlockPos> wide, Set<BlockPos> seats, Set<BlockPos> casks, Set<BlockPos> counters) {
		TreeSet<Integer> levels = new TreeSet<>();
		for (BlockPos p : reached) {
			levels.add(p.getY());
		}
		StringBuilder out = new StringBuilder();
		for (int y : levels) {
			out.append("y ").append(y - box.minY()).append('\n');
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int x = box.minX(); x <= box.maxX(); x++) {
					BlockPos p = new BlockPos(x, y, z);
					char ch;
					if (wide.contains(p)) {
						ch = '2';
					} else if (reached.contains(p)) {
						ch = '1';
					} else if (nodes.containsKey(p)) {
						ch = 'o';
					} else if (seats.contains(p) || (state(p).getBlock() instanceof FenceBlock && state(p.above()).getBlock() instanceof CarpetBlock)) {
						ch = 'h';
					} else if (casks.contains(p) || state(p).is(BarrelRegistry.AGING_BARREL.get())) {
						ch = 'c';
					} else if (counters.contains(p)) {
						ch = '=';
					} else if (state(p).isAir()) {
						ch = ' ';
					} else {
						ch = '#';
					}
					out.append(ch);
				}
				out.append('\n');
			}
		}
		return out.toString();
	}
}
