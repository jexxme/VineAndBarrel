package jexxme.vineandbarrel.kettle;

/**
 * The kettle's proportions in pixels, as its models draw them (the template models are generated from the same numbers):
 * the body stands 2 px in from the edges, 9 px tall on the Kettle and up to 6 px under the top on the big ones, a
 * two-step hood (3.5 px) sits on it, then a collar and a slim chimney (4, 5, 6 px) whose cap sits half a block into the
 * block above the kettle.
 */
public final class KettleGeometry
{
	private KettleGeometry() {
	}

	public static double bodyTop(KettleShape shape) {
		return shape == KettleShape.SINGLE ? 9.0D : 16.0D * shape.size() - 6.0D;
	}

	/** The top of the hood, where the chimney's collar sits. */
	public static double hoodTop(KettleShape shape) {
		return bodyTop(shape) + 3.5D;
	}

	/** Half the chimney's width for a kettle of {@code size} (1, 2 or 3 blocks). */
	public static double chimneyHalfWidth(int size) {
		return switch (size) {
			case 1 -> 2.0D;
			case 2 -> 2.5D;
			default -> 3.0D;
		};
	}

	/** The top of a chimney that isn't extended (cap included), from the kettle's bottom. */
	public static double chimneyTop(KettleShape shape) {
		return 16.0D * shape.size() + 8.0D + 1.75D;
	}

	/** The top of an extension piece's cap, from that piece's bottom. */
	public static final double PIECE_TOP = 8.0D + 1.75D;
}
