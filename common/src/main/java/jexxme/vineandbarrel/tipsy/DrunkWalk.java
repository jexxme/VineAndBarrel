package jexxme.vineandbarrel.tipsy;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One player's drunk walk, tick by tick (Tipsy overhaul, step 2; the math is {@link TipsyGait}): what is pressed arrives
 * late, every other step comes slower, the walk curves off its course and back, and a stumble is a misstep to the side.
 * The client feeds it the player's own input each tick ({@code client.DrunkLegs}); it has no client classes, so a test
 * drives it on the server.
 * <p>
 * Never pushes anyone anywhere a sober player wouldn't go: the curve and the misstep only pull where
 * {@link TipsyLegs#safeToWeave} says the ground is safe and loaded, and whatever the late legs carry on with beyond what
 * is pressed (letting go of forward at an edge, opening a screen, backing off) stops at once, as a sober stop would,
 * where {@link TipsyLegs#safeToMove} says that way isn't safe. Not for a moment after the server set the player
 * somewhere else (a correction or a teleport). Sober while sneaking (a bridge can be crossed on purpose), swimming,
 * flying, gliding, riding or climbing, out cold or down after a trip, and with the server's {@code stumbling} switch off.
 */
public final class DrunkWalk
{
	/** A move this far (blocks) in one tick wasn't walking: the server put the player somewhere. */
	public static final double JUMPED = 1.0;
	/** After that, this many ticks without a curve. */
	public static final int SETTLE_TICKS = 20;
	/** In a stumble the legs step out this far (degrees) to the side it lurches to. */
	public static final float STAGGER_TURN = 40.0F;
	/** Late legs carrying on less than this beyond what is pressed don't count. */
	private static final float CARRY_ON = 0.02F;

	/** What the legs do now (they follow what is pressed late), blocks walked, ticks to wait, where the player was. */
	private float moveX, moveY;
	private float walked;
	private int settle;
	private Vec3 last = Vec3.ZERO;
	private @Nullable Player who;

	/** Forgets the player (a new world, a disconnect): the next tick starts sober-steady. */
	public void reset() {
		who = null;
		moveX = 0.0F;
		moveY = 0.0F;
		walked = 0.0F;
		settle = 0;
		last = Vec3.ZERO;
	}

	/** {@return whether this player's walk is drunk right now: tipsy, on the ground on their own legs, not careful} */
	public static boolean walksDrunk(Player player) {
		return TipsyConfig.stumbling() && TipsyLevels.at(Tipsy.getLevel(player)).legs().drunkWalk() && player.onGround() && !player.isShiftKeyDown()
			&& !player.isPassenger() && !player.isInWater() && !player.isSwimming() && !player.isFallFlying() && !player.onClimbable()
			&& !player.getAbilities().flying && !Blackout.isOut(player) && !TipsyLegs.isDown(player);
	}

	/**
	 * {@return what the legs walk this tick} (as the input's move vector: x positive to the left, y forward)
	 *
	 * @param pressed what the player presses
	 * @param lurch   a stumble's lurch now, -1 (right) to 1 (left), 0 none
	 */
	public Vec2 step(Player player, Vec2 pressed, float lurch) {
		if (player != who) {
			who = player;
			moveX = pressed.x;
			moveY = pressed.y;
			walked = 0.0F;
			settle = SETTLE_TICKS;
			last = player.position();
		}
		Vec3 now = player.position();
		if (now.distanceToSqr(last) > JUMPED * JUMPED) {
			settle = SETTLE_TICKS;
		}
		last = now;
		if (player.onGround()) {
			walked += (float) player.getDeltaMovement().horizontalDistance();
		}
		if (!walksDrunk(player)) {
			moveX = pressed.x;
			moveY = pressed.y;
			return pressed;
		}
		TipsyLevels.Legs legs = TipsyLevels.at(Tipsy.getLevel(player)).legs();
		// Going under, the legs give: later still, wider curves, slower
		float under = GoingUnder.under(player, 0.0F);
		float steer = legs.steer() * (1.0F - 0.6F * under);
		moveX = TipsyGait.steer(moveX, pressed.x, steer);
		moveY = TipsyGait.steer(moveY, pressed.y, steer);
		// What the late legs carry on with beyond what is pressed: only where a step that way is safe, else a sober stop
		Vec2 beyond = beyond(new Vec2(moveX, moveY), pressed);
		if (beyond.lengthSquared() > CARRY_ON * CARRY_ON && !TipsyLegs.safeToMove(player, world(player, beyond))) {
			moveX = pressed.x;
			moveY = pressed.y;
		}
		float turn = 0.0F;
		if (settle > 0) {
			settle--;
		} else if (moveY > 0.05F) {
			// The legs walk a curve off the course and back (the body turns with them): the walk itself weaves
			turn = TipsyGait.weave(walked, TipsyGait.phase(player.getId()), legs.weave() * (1.0F + 1.2F * under)) * Math.min(moveY, 1.0F);
			if (turn != 0.0F && !TipsyLegs.safeToWeave(player, side(player, turn), forward(player))) {
				turn = 0.0F;
			}
		}
		// A stumble is a misstep: the legs step out to the side it lurches to, and come back under the body
		if (lurch != 0.0F && moveY > 0.05F) {
			float stagger = turn + STAGGER_TURN * lurch;
			turn = TipsyLegs.safeToWeave(player, side(player, stagger), forward(player)) ? stagger : turn;
		}
		float r = turn * Mth.DEG_TO_RAD;
		float sin = Mth.sin(r), cos = Mth.cos(r);
		// (x positive is to the left: turning left moves forward into the left)
		Vec2 moved = new Vec2(moveX * cos + moveY * sin, moveY * cos - moveX * sin)
			.scale(TipsyGait.stride(player.walkAnimation.position(), legs.stride()) * (1.0F - 0.5F * under));
		return moved.lengthSquared() > 1.0F ? moved.normalized() : moved;
	}

	/**
	 * {@return the part of {@code moving} that what is {@code pressed} doesn't account for} (moving along what is pressed
	 * but less is accounted for; anything further, or to a side, or the other way is not)
	 */
	static Vec2 beyond(Vec2 moving, Vec2 pressed) {
		float length = pressed.length();
		if (length < 1.0E-4F) {
			return moving;
		}
		Vec2 along = pressed.scale(1.0F / length);
		float ahead = Mth.clamp(moving.dot(along), 0.0F, length);
		return moving.add(along.scale(-ahead));
	}

	/** {@return the world direction a move vector (x left, y forward) goes for this player} */
	static Vec3 world(Player player, Vec2 move) {
		float r = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 left = new Vec3(Mth.cos(r), 0.0, Mth.sin(r));
		Vec3 forward = new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
		return left.scale(move.x).add(forward.scale(move.y));
	}

	/** {@return the world direction "to the left" ({@code amount} positive) or right of where the player faces} */
	private static Vec3 side(Player player, float amount) {
		return world(player, new Vec2(amount > 0.0F ? 1.0F : -1.0F, 0.0F));
	}

	private static Vec3 forward(Player player) {
		return world(player, new Vec2(0.0F, 1.0F));
	}
}
