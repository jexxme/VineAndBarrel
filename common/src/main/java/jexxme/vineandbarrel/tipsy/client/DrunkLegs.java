package jexxme.vineandbarrel.tipsy.client;

import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.DrunkWalk;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyGait;
import jexxme.vineandbarrel.tipsy.TipsyLevels;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * The drunk walk on this client (Tipsy overhaul, step 2): the player's own legs walk {@link DrunkWalk} (late, uneven,
 * curving, never anywhere a sober player wouldn't go), and the own view leans a little into the curve and onto each
 * foot. Movement is the client's in vanilla, so the weave is ordinary walking to the server and to everyone else:
 * friends see the real path.
 */
public final class DrunkLegs
{
	/** The local player's walk. */
	private static final DrunkWalk WALK = new DrunkWalk();

	/** The own view's lean into the curve (degrees, positive to the left), this tick and last, and where the walk went a tick ago. */
	private static float lean, leanO, headingO;

	private DrunkLegs() {
	}

	/** {@return whether this player's walk is drunk right now} */
	static boolean walksDrunk(Player player) {
		return DrunkWalk.walksDrunk(player);
	}

	/**
	 * Right after the player's input was read each tick: the legs take the steered direction late, unevenly, and curve.
	 * 1.21.1 hands the keys over as two impulses, already slowed for sneaking or crawling and not yet shortened to one
	 * step; the walk takes them as later versions do (one step at most, the slowing put back on after), and a walk that
	 * isn't drunk leaves them exactly as they came.
	 */
	static void onMovementInput(Player player, Input input) {
		float slowed = player instanceof LocalPlayer local && local.isMovingSlowly() ? (float) player.getAttributeValue(Attributes.SNEAKING_SPEED) : 1.0F;
		if (slowed <= 0.0F) {
			WALK.step(player, Vec2.ZERO, 0.0F);
			return;
		}
		Vec2 pressed = new Vec2(input.leftImpulse / slowed, input.forwardImpulse / slowed);
		if (pressed.lengthSquared() > 1.0F) {
			pressed = pressed.normalized();
		}
		Vec2 walked = WALK.step(player, pressed, TipsyClient.stumbleLurch(player, 0.0F));
		if (DrunkWalk.walksDrunk(player)) {
			input.leftImpulse = walked.x * slowed;
			input.forwardImpulse = walked.y * slowed;
		}
	}

	/** A new world or a disconnect: the walk and the lean start afresh. */
	static void reset() {
		WALK.reset();
		lean = 0.0F;
		leanO = 0.0F;
		headingO = 0.0F;
	}

	// ------------------------------------------------------------------ the own view's lean

	/**
	 * Once a client tick: the own view eases toward leaning into the curve the walk takes (its heading turning), the way
	 * a body leans into a bend.
	 */
	static void tick(LocalPlayer player) {
		leanO = lean;
		Vec3 motion = player.position().subtract(player.xo, player.yo, player.zo);
		float heading = TipsyGait.heading(motion.x, motion.z, headingO);
		float turning = TipsyGait.turning(headingO, heading);
		headingO = heading;
		float target = 0.0F;
		if (Tipsy.getLevel(player) > 0 && !Blackout.isOut(player) && player.onGround()) {
			target = TipsyGait.leanTarget(turning, TipsyLevels.at(Tipsy.getLevel(player)).legs().leanDegrees());
		}
		lean = TipsyGait.ease(lean, target);
	}

	/** {@return the own view's lean this frame (degrees, positive to the left): into the curve, and a little onto each foot} */
	static float lean(LocalPlayer player, float partial) {
		float step = 0.0F;
		if (Tipsy.getLevel(player) > 0 && walksDrunk(player)) {
			step = TipsyGait.OWN_STEP_SHARE * TipsyGait.stepRoll(player.walkAnimation.position(partial), player.walkAnimation.speed(partial),
				TipsyLevels.at(Tipsy.getLevel(player)).legs().stepRoll());
		}
		return Mth.lerp(partial, leanO, lean) + step;
	}
}
