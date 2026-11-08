package jexxme.vineandbarrel.barrel;

import net.minecraft.util.StringRepresentable;

/** The bung on top of an Aging Barrel (B4). */
public enum BungState implements StringRepresentable
{
	CORK("cork"),
	AIRLOCK("airlock"),
	WAX("wax");

	private final String name;

	BungState(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
