package jexxme.vineandbarrel.kettle;

import net.minecraft.util.StringRepresentable;

/** What the kettle stands on or hangs from (K1), exactly FD's {@code CookingPotSupport}. */
public enum KettleSupport implements StringRepresentable
{
	NONE("none"),
	TRAY("tray"),
	HANDLE("handle");

	private final String name;

	KettleSupport(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	@Override
	public String toString() {
		return name;
	}
}
