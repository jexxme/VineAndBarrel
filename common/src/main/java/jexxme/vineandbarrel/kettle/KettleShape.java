package jexxme.vineandbarrel.kettle;

import java.util.List;

/**
 * The three Brew Kettles (decided with Jexxme for the redesign): the Kettle (one block), the Big Kettle (a 2x2x2 cube)
 * and the Great Kettle (a 3x3x3 cube). Nothing in between. Placing kettles into a cube joins them (see
 * {@link KettleMultiblock}).
 * <p>
 * Every block adds one batch's worth ({@link BrewKettleBlockEntity#SERVINGS_PER_BLOCK}) and {@link
 * BrewKettleBlockEntity#MAX_SERVINGS} of room; every heated block of its bottom layer boils one block's share.
 */
public enum KettleShape
{
	SINGLE(1),
	BIG(2),
	GREAT(3);

	/**
	 * Biggest first: the shapes {@link KettleMultiblock} joins kettles into, in the order it tries them. The Great Kettle
	 * is dormant (not in 1.1.0: 27 kettles don't join), its parts stay registered for when it comes back.
	 */
	public static final List<KettleShape> BIGGEST_FIRST = List.of(BIG);

	private final int size;

	KettleShape(int size) {
		this.size = size;
	}

	/** Width, depth and height in blocks. */
	public int size() {
		return size;
	}

	public int height() {
		return size;
	}

	public int blocks() {
		return size * size * size;
	}

	/** Blocks in the bottom layer: the ones a fire can heat. */
	public int bottomBlocks() {
		return size * size;
	}

	/** "2x2x2": the model name part. */
	public String id() {
		return size + "x" + size + "x" + size;
	}
}
