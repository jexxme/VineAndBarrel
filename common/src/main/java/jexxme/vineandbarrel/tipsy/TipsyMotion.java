package jexxme.vineandbarrel.tipsy;

import jexxme.vineandbarrel.platform.EntityAttachment;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The short body movements of a tipsy player, which every client plays: a hiccup's jolt, a stumble's lurch to one side
 * and a trip forward onto the face. The server counts each one in {@link TipsyRegistry#MOTION} (synced to the player
 * and everyone near), and a client that sees a count go up plays that movement from then on. The curves are pure math
 * over the ticks since it started, so they are tested headless (like {@link TipsyWobble}).
 * <p>
 * Stumble and trip share one movement language: a trip starts exactly like a stumble (the scuff, the dip) and is the
 * stumble that wasn't caught.
 */
public final class TipsyMotion
{
	/** How many of each movement this player has made (they only ever go up, wrapping round). */
	public record Counts(int hiccups, int stumbles, boolean stumbleLeft, int trips)
	{
		public static final Counts NONE = new Counts(0, 0, false, 0);
		/** On the wire as before: two var-ints, the stumble's side, one more var-int. */
		public static final EntityAttachment.SyncCodec<Counts> SYNC_CODEC = new EntityAttachment.SyncCodec<>(
			(buf, counts) -> {
				buf.writeVarInt(counts.hiccups());
				buf.writeVarInt(counts.stumbles());
				buf.writeBoolean(counts.stumbleLeft());
				buf.writeVarInt(counts.trips());
			},
			buf -> new Counts(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));
	}

	// ------------------------------------------------------------------ the hiccup: a sharp jolt up, settled in a few ticks

	/** A hiccup's jolt rises over this many ticks and settles over the next {@link #HICCUP_SETTLE}. */
	public static final float HICCUP_RISE = 1.0F, HICCUP_SETTLE = 4.0F;
	/** At its peak the own view flicks up this many degrees, and the body others see bobs up this far (blocks). */
	public static final float HICCUP_FLICK = 2.5F, HICCUP_BOB = 0.05F;

	// ------------------------------------------------------------------ the stumble: a lurch to the side and a catch step

	/** A stumble lurches over this many ticks and steadies over the next {@link #STUMBLE_SETTLE}. */
	public static final float STUMBLE_RISE = 5.0F, STUMBLE_SETTLE = 14.0F;
	/** The own view dips this far down and rolls this far toward the lurch; the body others see leans this far. */
	public static final float STUMBLE_DIP = 2.5F, STUMBLE_ROLL = 3.0F, STUMBLE_LEAN = 6.0F;
	/** The catch step's scuff comes this many ticks after the stumble's (the foot finding the ground again). */
	public static final int CATCH_STEP_AFTER = 8;

	// ------------------------------------------------------------------ the trip: down on the face, and up again

	/** The trip's start is a stumble's: then, this many ticks in, the body pitches forward. */
	public static final int TRIP_CATCH = 3;
	/** The fall forward takes this long; it ends in the thud. */
	public static final int TRIP_FALL = 7;
	public static final int TRIP_THUD = TRIP_CATCH + TRIP_FALL;
	/** Lying face down, from the thud on (1.2 s). */
	public static final int TRIP_DOWN = 24;
	/** Getting up again (keys and mouse stay held until the end). */
	public static final int TRIP_GET_UP = 15;
	public static final int TRIP_TICKS = TRIP_THUD + TRIP_DOWN + TRIP_GET_UP;
	/** Face down, the body lies this far over (degrees), lifted this much so it rests on the ground. */
	public static final float TRIP_BODY_DEGREES = 90.0F, TRIP_LIFT = 0.15F;
	/**
	 * Face down, the own view looks this far down at the ground, from this high, this far ahead of the feet, tilted this
	 * far onto one side (closer would fill the screen with one block: this still reads as lying in the grass).
	 */
	public static final float TRIP_LOOK = 38.0F, TRIP_EYES = 0.6F, TRIP_FORWARD = 0.9F, TRIP_TILT = 9.0F;
	/** The thud shakes the own view this hard (degrees) for this many ticks. */
	public static final float TRIP_SHAKE = 1.6F, TRIP_SHAKE_TICKS = 7.0F;
	private static final float BOUNCE = 0.06F, BOUNCE_TICKS = 4.0F;

	private TipsyMotion() {
	}

	// ------------------------------------------------------------------ server: counting

	static Counts counts(LivingEntity entity) {
		Counts counts = TipsyRegistry.MOTION.getExisting(entity);
		return counts == null ? Counts.NONE : counts;
	}

	/** Counts a hiccup (players only: only they are drawn moving). */
	static void hiccup(LivingEntity entity) {
		if (entity instanceof Player) {
			Counts c = counts(entity);
			TipsyRegistry.MOTION.set(entity, new Counts(c.hiccups() + 1, c.stumbles(), c.stumbleLeft(), c.trips()));
		}
	}

	static void stumble(LivingEntity entity, boolean left) {
		if (entity instanceof Player) {
			Counts c = counts(entity);
			TipsyRegistry.MOTION.set(entity, new Counts(c.hiccups(), c.stumbles() + 1, left, c.trips()));
		}
	}

	static void trip(LivingEntity entity) {
		if (entity instanceof Player) {
			Counts c = counts(entity);
			TipsyRegistry.MOTION.set(entity, new Counts(c.hiccups(), c.stumbles(), c.stumbleLeft(), c.trips() + 1));
		}
	}

	// ------------------------------------------------------------------ the curves (ticks since the movement started)

	/** {@return how far into its jolt a hiccup is, 0 to 1 and back (0 when it's over)} */
	public static float hiccupJolt(float elapsed) {
		return pulse(elapsed, HICCUP_RISE, HICCUP_SETTLE);
	}

	/** {@return how far a stumble has lurched, 0 to 1 and back} */
	public static float stumbleLurch(float elapsed) {
		return pulse(elapsed, STUMBLE_RISE, STUMBLE_SETTLE);
	}

	/**
	 * {@return how far a trip has the body down, 0 standing to 1 face down: the stumble's start, a fall that speeds up,
	 * a little rock back after the thud, lying there, then getting up}
	 */
	public static float tripFallen(float elapsed) {
		if (elapsed <= TRIP_CATCH || elapsed >= TRIP_TICKS) {
			return 0.0F;
		}
		if (elapsed < TRIP_THUD) {
			float t = (elapsed - TRIP_CATCH) / TRIP_FALL;
			return t * t;
		}
		float getUp = TRIP_TICKS - TRIP_GET_UP;
		if (elapsed > getUp) {
			return smooth(1.0F - (elapsed - getUp) / TRIP_GET_UP);
		}
		if (elapsed < TRIP_THUD + BOUNCE_TICKS) {
			return 1.0F - BOUNCE * Mth.sin((elapsed - TRIP_THUD) / BOUNCE_TICKS * Mth.PI);
		}
		return 1.0F;
	}

	/** {@return the trip's own start: the stumble that wasn't caught (the lurch part of {@link #stumbleLurch})} */
	public static float tripStumble(float elapsed) {
		return elapsed < TRIP_THUD ? stumbleLurch(elapsed) : 0.0F;
	}

	/** {@return whether a trip this far in still holds the keys and the mouse} */
	public static boolean tripHolds(float elapsed) {
		return elapsed >= 0.0F && elapsed < TRIP_TICKS;
	}

	/** {@return how hard the thud still shakes the view, 1 at the thud fading to 0} */
	public static float tripShake(float elapsed) {
		float since = elapsed - TRIP_THUD;
		if (since < 0.0F || since >= TRIP_SHAKE_TICKS) {
			return 0.0F;
		}
		float fade = 1.0F - since / TRIP_SHAKE_TICKS;
		return fade * fade;
	}

	/** Rises over {@code rise} ticks, eases back over {@code settle}. */
	private static float pulse(float elapsed, float rise, float settle) {
		if (elapsed <= 0.0F || elapsed >= rise + settle) {
			return 0.0F;
		}
		if (elapsed < rise) {
			return smooth(elapsed / rise);
		}
		float t = 1.0F - (elapsed - rise) / settle;
		return t * t;
	}

	private static float smooth(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}
}
