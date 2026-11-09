package jexxme.vineandbarrel.block;

import net.minecraft.util.StringRepresentable;

/**
 * How a vine sits on a block it climbs other than the trellis ({@link VineOnSupportBlock}): spread across both faces of a
 * fence, bars or a wall that runs one way (thin for a fence post or bars, wide for a wall), or wound round anything
 * else (a chain, a lone post, a corner).
 */
public enum SupportLook implements StringRepresentable
{
	CROSS("cross"), THIN_X("thin_x"), THIN_Z("thin_z"), WIDE_X("wide_x"), WIDE_Z("wide_z");

	private final String name;

	SupportLook(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** Spread across two faces (not wound round). */
	public boolean faces() {
		return this != CROSS;
	}

	/** The faces look along {@code alongX} (the row runs east-west) or north-south. */
	public static SupportLook faces(boolean wide, boolean alongX) {
		return wide ? (alongX ? WIDE_X : WIDE_Z) : (alongX ? THIN_X : THIN_Z);
	}

	/** A quarter turn swaps the row's direction. */
	public SupportLook turned() {
		return switch (this) {
			case THIN_X -> THIN_Z;
			case THIN_Z -> THIN_X;
			case WIDE_X -> WIDE_Z;
			case WIDE_Z -> WIDE_X;
			case CROSS -> CROSS;
		};
	}
}
