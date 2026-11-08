package jexxme.vineandbarrel.barrel;

/** Where a batch in an Aging Barrel stands (DESIGN §4.2): poured in, fermenting for a day, young, then aged after 3 more days. */
public enum BarrelStage
{
	EMPTY,
	FERMENTING,
	YOUNG,
	AGED;

	/** The bung on top shows the stage (B4): a cork when empty or ready, a bubbling airlock while fermenting, red wax once aged. */
	public BungState bung() {
		return switch (this) {
			case FERMENTING -> BungState.AIRLOCK;
			case AGED -> BungState.WAX;
			default -> BungState.CORK;
		};
	}

	/** Whether it is a drink yet (tapped into a vessel), rather than the base (which can still be taken back out, B8). */
	public boolean isDrink() {
		return this == YOUNG || this == AGED;
	}
}
