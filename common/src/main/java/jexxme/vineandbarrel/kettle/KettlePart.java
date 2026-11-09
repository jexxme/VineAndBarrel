package jexxme.vineandbarrel.kettle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;

/**
 * One block of a big kettle: its shape and its place in it, as the {@link BrewKettlePartBlock#PART} id.
 * <p>
 * Places are kettle-local: {@code x} to the right of the tap side as seen from above with the tap to the north (east),
 * {@code y} up, {@code z} away from the tap (south). The block at (0, 0, 0) is the kettle's controller: it holds the
 * block entity with the brew. Because places are local, turning or mirroring a kettle (structures) only turns its
 * facing (and mirrors x), and the models are built once for a kettle facing north.
 *
 * @param shape a shape other than {@link KettleShape#SINGLE} (single kettles are the plain kettle block)
 */
public record KettlePart(KettleShape shape, int x, int y, int z)
{
	private static final List<KettlePart> ALL = new ArrayList<>();

	static {
		for (KettleShape shape : KettleShape.values()) {
			if (shape == KettleShape.SINGLE) continue;
			for (int x = 0; x < shape.size(); x++) {
				for (int y = 0; y < shape.height(); y++) {
					for (int z = 0; z < shape.size(); z++) {
						ALL.add(new KettlePart(shape, x, y, z));
					}
				}
			}
		}
	}

	/** How many part ids there are (35: every block of the Big and the Great Kettle). */
	public static int count() {
		return ALL.size();
	}

	public static KettlePart byId(int id) {
		return ALL.get(id);
	}

	public static List<KettlePart> all() {
		return ALL;
	}

	public static int id(KettleShape shape, int x, int y, int z) {
		return ALL.indexOf(new KettlePart(shape, x, y, z));
	}

	public int id() {
		return ALL.indexOf(this);
	}

	public boolean isController() {
		return x == 0 && y == 0 && z == 0;
	}

	public boolean isBottom() {
		return y == 0;
	}

	/** The block that draws the chimney (the top layer's centre; for the Big Kettle the block whose corner is the centre). */
	public boolean isChimney() {
		return this.equals(chimney(shape));
	}

	/** The block that draws the sight glass beside the tap (its level shows the kettle's). */
	public boolean isGauge() {
		return shape == KettleShape.BIG ? x == 1 && y == 0 && z == 0 : x == 2 && y == 1 && z == 0;
	}

	public static KettlePart chimney(KettleShape shape) {
		int c = shape == KettleShape.GREAT ? 1 : 0;
		return new KettlePart(shape, c, shape.height() - 1, c);
	}

	/** This part with its x mirrored (a mirrored structure). */
	public KettlePart mirrored() {
		return new KettlePart(shape, shape.size() - 1 - x, y, z);
	}

	/** The world offset of kettle-local ({@code x}, {@code y}, {@code z}) for a kettle whose tap faces {@code facing}. */
	public static BlockPos offset(Direction facing, int x, int y, int z) {
		return new BlockPos(x, y, z).rotate(rotation(facing));
	}

	/** Where this part's controller is, seen from this part at {@code pos}. */
	public BlockPos controllerFrom(BlockPos pos, Direction facing) {
		return pos.subtract(offset(facing, x, y, z));
	}

	/** Turns a kettle facing north into one facing {@code facing}. */
	public static Rotation rotation(Direction facing) {
		return switch (facing) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** Every block position of a kettle of {@code shape} with its controller at {@code controller}, controller first. */
	public static List<BlockPos> positions(KettleShape shape, BlockPos controller, Direction facing) {
		List<BlockPos> list = new ArrayList<>(shape.blocks());
		for (int y = 0; y < shape.height(); y++) {
			for (int z = 0; z < shape.size(); z++) {
				for (int x = 0; x < shape.size(); x++) {
					list.add(controller.offset(offset(facing, x, y, z)));
				}
			}
		}
		return list;
	}
}
