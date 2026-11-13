package jexxme.vineandbarrel.tipsy;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Everything each Tipsy level does, in one table (Tipsy overhaul, Jexxme 2026-11-13: drunk shows in the body, not on the
 * screen). I to III are the fun part, III is the peak, IV is the turn, V is smashed, then the blackout. Every effect
 * reads its numbers from here, the tooltip ({@link TipsyInfo}) and the wiki export too, so they can't drift apart.
 * <p>
 * The table has one section per part of being drunk (courage, hiccups, legs, eyes, ears, nodding off, chat, the rules),
 * each a list of five rows, I to V. A level is the sections' rows put together ({@link Level}). Each section is its own
 * block below, so work on one part never touches another's lines. Some columns are filled with the overhaul's values
 * before the code that reads them exists (the weave, the eyes, the ears' muffling, nodding off, the chat stages past
 * III; each says "not read yet"): until then nothing reads them, and the game does what it did.
 * <p>
 * Chances are "one in n ticks" (0 = never). Kept free of client classes so it is tested headless.
 */
public final class TipsyLevels
{
	private static final int S = 20;

	// ------------------------------------------------------------------ the sections

	/**
	 * Liquid courage, on the effect itself ({@link TipsyEffect}).
	 *
	 * @param knockbackResistance knockback resistance added (1.0 is full resistance)
	 * @param fallDamageReduction share of fall damage taken away
	 */
	public record Courage(double knockbackResistance, double fallDamageReduction)
	{
		public boolean any() {
			return knockbackResistance > 0.0 || fallDamageReduction > 0.0;
		}
	}

	public static final List<Courage> COURAGE = List.of(
		new Courage(0.10, 0.15),
		new Courage(0.20, 0.25),
		new Courage(0.30, 0.35),
		new Courage(0.0, 0.0),
		new Courage(0.0, 0.0));

	/**
	 * Hiccups come in bouts ({@link Tipsy}).
	 *
	 * @param boutOneIn chance per tick that a bout starts, while none runs
	 * @param fewest    fewest hiccups in a bout
	 * @param most      most hiccups in a bout
	 */
	public record Hiccups(int boutOneIn, int fewest, int most)
	{
		public boolean any() {
			return boutOneIn > 0;
		}
	}

	public static final List<Hiccups> HICCUPS = List.of(
		new Hiccups(0, 0, 0),
		// II gets its own thing: now and then a short bout (about one hiccup every 20 s)
		new Hiccups(45 * S, 2, 3),
		new Hiccups(35 * S, 3, 5),
		new Hiccups(30 * S, 3, 5),
		new Hiccups(25 * S, 3, 5));

	/**
	 * The legs ({@link TipsyLegs}) and the body other players see.
	 *
	 * @param swayDegrees how far the body other players see leans from side to side
	 * @param weave       how far the walk weaves to the side, as a share of a full sideways step (the drunk legs; not
	 *                    read yet)
	 * @param legsOneIn   chance per tick, while walking, of an "unsteady legs" roll: a stumble, now and then a trip
	 * @param tripOneIn   of every this many unsteady-legs rolls one is a trip (sprinting: {@link #SPRINT_TRIP_ONE_IN})
	 * @param drift       walking drifts slowly to the side (the player keeps correcting)
	 */
	public record Legs(float swayDegrees, float weave, int legsOneIn, int tripOneIn, boolean drift)
	{
		public boolean unsteady() {
			return legsOneIn > 0;
		}
	}

	public static final List<Legs> LEGS = List.of(
		new Legs(1.0F, 0.05F, 0, 0, false),
		new Legs(1.8F, 0.12F, 0, 0, false),
		new Legs(2.7F, 0.22F, 0, 0, false),
		new Legs(4.7F, 0.34F, 0, 0, false),
		new Legs(9.4F, 0.46F, 3 * S, 12, true));

	/** Sprinting at V, one in this many unsteady-legs rolls is a trip. */
	public static final int SPRINT_TRIP_ONE_IN = 5;

	/**
	 * What the eyes do (the drunk eyes, a screen shader; not read yet).
	 *
	 * @param softEdges    how soft the screen edges go (0..1)
	 * @param doubleVision how far apart the second image drifts at most, as a share of the screen's width
	 * @param signBlur     how blurred the writing on signs is (0 sharp, 1 unreadable)
	 */
	public record Eyes(float softEdges, float doubleVision, float signBlur)
	{
	}

	public static final List<Eyes> EYES = List.of(
		new Eyes(0.15F, 0.0F, 0.0F),
		new Eyes(0.3F, 0.0F, 0.0F),
		new Eyes(0.45F, 0.004F, 0.35F),
		new Eyes(0.55F, 0.009F, 0.7F),
		new Eyes(0.65F, 0.015F, 1.0F));

	/**
	 * What the ears do.
	 *
	 * @param muffle    how woolly the world sounds (0 clear, 1 as if through a wall; not read yet)
	 * @param heartbeat how loud you hear your own heart, a share of {@link TipsySounds#HEARTBEAT_VOLUME} (0 = not at all)
	 */
	public record Ears(float muffle, float heartbeat)
	{
	}

	public static final List<Ears> EARS = List.of(
		new Ears(0.0F, 0.0F),
		new Ears(0.0F, 0.0F),
		new Ears(0.3F, 0.0F),
		new Ears(0.5F, 0.0F),
		new Ears(0.65F, 1.0F));

	/**
	 * Nodding off on your feet when standing still (not read yet).
	 *
	 * @param stillTicks standing still this long before the first nod (0 = never)
	 * @param cycleTicks one nod: sinking, the jerk awake, a pause
	 */
	public record Nod(int stillTicks, int cycleTicks)
	{
		public boolean any() {
			return stillTicks > 0;
		}
	}

	public static final List<Nod> NOD = List.of(
		new Nod(0, 0),
		new Nod(0, 0),
		new Nod(0, 0),
		new Nod(70, 150),
		new Nod(50, 110));

	/**
	 * How chat comes out ({@link TipsySlur}).
	 *
	 * @param stage 0 as typed, 1 the odd *hic*, 2 slurred but easy to read, 3 hard to read, 4 gibberish. For now every
	 *              stage from {@link #SLURRED} on comes out slurred the one way it always did, and stage 1 as typed
	 *              (the stages themselves are not read yet).
	 */
	public record Chat(int stage)
	{
		/** The first stage that slurs today. */
		public static final int SLURRED = 2;

		public boolean slurs() {
			return stage >= SLURRED;
		}
	}

	public static final List<Chat> CHAT = List.of(
		new Chat(0),
		new Chat(1),
		new Chat(2),
		new Chat(3),
		new Chat(4));

	/**
	 * The rules of being drunk, which the overhaul keeps as they are.
	 *
	 * @param buffs           drinks still give their buff (and the earlier drinks' buffs stay)
	 * @param rowdySwingOneIn chance per tick of a wild punch ({@link Rowdy})
	 * @param cutOff          villagers and traders won't trade
	 */
	public record Rules(boolean buffs, int rowdySwingOneIn, boolean cutOff)
	{
		public boolean rowdy() {
			return rowdySwingOneIn > 0;
		}
	}

	public static final List<Rules> RULES = List.of(
		new Rules(true, 0, false),
		new Rules(true, 0, false),
		new Rules(true, 0, false),
		new Rules(false, 30 * S, true),
		new Rules(false, 15 * S, true));

	// ------------------------------------------------------------------ the levels

	/** One level: its row of every section. */
	public record Level(int level, Courage courage, Hiccups hiccups, Legs legs, Eyes eyes, Ears ears, Nod nod, Chat chat, Rules rules)
	{
	}

	/** Sober: nothing at all (and every rule as sober). */
	public static final Level SOBER = new Level(0, new Courage(0.0, 0.0), new Hiccups(0, 0, 0), new Legs(0.0F, 0.0F, 0, 0, false),
		new Eyes(0.0F, 0.0F, 0.0F), new Ears(0.0F, 0.0F), new Nod(0, 0), new Chat(0), new Rules(true, 0, false));

	/** Level I to V. */
	public static final List<Level> LEVELS = List.of(level(1), level(2), level(3), level(4), level(5));

	private static Level level(int level) {
		int i = level - 1;
		return new Level(level, COURAGE.get(i), HICCUPS.get(i), LEGS.get(i), EYES.get(i), EARS.get(i), NOD.get(i), CHAT.get(i), RULES.get(i));
	}

	private TipsyLevels() {
	}

	/** {@return what this level does; 0 and below is sober, above V is V} */
	public static Level at(int level) {
		if (level <= 0) {
			return SOBER;
		}
		return LEVELS.get(Math.min(level, LEVELS.size()) - 1);
	}

	/** {@return the first level that does this, or 0 if none does} */
	public static int first(Predicate<Level> does) {
		for (Level level : LEVELS) {
			if (does.test(level)) {
				return level.level();
			}
		}
		return 0;
	}

	/** One section of the table: its name, its five rows and how a level picks its row (for tests and the wiki export). */
	public record Section(String name, List<? extends Record> rows, Function<Level, ? extends Record> of)
	{
	}

	/** {@return every section, in the table's order} */
	public static List<Section> sections() {
		return List.of(new Section("courage", COURAGE, Level::courage), new Section("hiccups", HICCUPS, Level::hiccups),
			new Section("legs", LEGS, Level::legs), new Section("eyes", EYES, Level::eyes), new Section("ears", EARS, Level::ears),
			new Section("nod", NOD, Level::nod), new Section("chat", CHAT, Level::chat), new Section("rules", RULES, Level::rules));
	}
}
