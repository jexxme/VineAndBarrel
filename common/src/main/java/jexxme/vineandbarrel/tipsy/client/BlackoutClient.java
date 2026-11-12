package jexxme.vineandbarrel.tipsy.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.BlackoutEnding;
import jexxme.vineandbarrel.tipsy.BlackoutPose;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.TipsyRegistry;
import jexxme.vineandbarrel.tipsy.TipsyWobble;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The blackout on the client: the stars above the head of everyone who is out, and for the player themselves the
 * camera in the head as they fall on the face, lie there and get up again, keys and mouse held still, no hand, and no
 * vanilla bed screen (there is nothing to leave: the blackout gets you up), and the closing and opening eyes
 * ({@link #renderBlackout}). The body is drawn by {@link TipsyClient#beforeRenderLiving}, with the other poses; the math
 * is {@link BlackoutPose}. Each loader calls these hooks from its own client events (NeoForge:
 * {@code NeoForgeClient}; Fabric: Fabric API callbacks and our client mixins), at the same spots.
 */
public final class BlackoutClient
{
	/** The HUD layer of the closed eyes, by id (NeoForge GUI layers; Fabric draws it from its Gui mixin): above all others. */
	public static final ResourceLocation LAYER = VineAndBarrel.id("blackout");
	/** The stars circle this far above the middle of the head. */
	private static final double STARS_ABOVE = 0.45;
	/** Where stars circle this tick (one per player who is out), so the stars of one who gets up fade at once. */
	private static final List<Vec3> STARS = new ArrayList<>();

	private BlackoutClient() {
	}

	/** The dizzy stars' particle (from {@link TipsyClient#register}). */
	static void register(jexxme.vineandbarrel.platform.client.ClientRegistrar registrar) {
		registrar.particle(TipsyRegistry.DIZZY_STAR_PARTICLE.get(), DizzyStarParticle.Provider::new);
	}

	/** {@return the ticks left of this player's blackout, minus the partial tick (0 when not out)} */
	public static float left(Player player, float partial) {
		int left = Blackout.ticksLeft(player);
		return left <= 0 ? 0.0F : Math.max(0.0F, left - partial);
	}

	/**
	 * {@return this player out on the ground right now, falling on the face, lying there or getting up
	 * ({@link BlackoutPose#body}), or null (not out, or in a bed or a boat)}
	 */
	public static BlackoutPose.@Nullable Body body(Player player, float partial) {
		float left = left(player, partial);
		if (left <= 0.0F || !onTheGround(player)) {
			return null;
		}
		return BlackoutPose.body(left, BlackoutPose.side(player.getId()), turn(player), player.getViewXRot(partial));
	}

	/**
	 * {@return how far (degrees of yaw) this player's body turns as it falls: to the way the server chose as they blacked
	 * out, where it has room to lie on the face ({@link TipsyRegistry#BLACKOUT_YAW}), the same on every client} Once the
	 * server has turned the player that way too (while the screen is black) it comes to 0, smoothly as the turn arrives.
	 */
	static float turn(Player player) {
		Float yaw = TipsyRegistry.BLACKOUT_YAW.getExisting(player);
		return yaw == null ? 0.0F : Mth.wrapDegrees(yaw - player.getYHeadRot());
	}

	/**
	 * Whether this blacked-out player lies on the ground (not in a bed or a boat). Not while gliding with an elytra,
	 * swimming or crawling either: those have the game's own pose (the body is already flat), which the fall's would only
	 * be piled onto.
	 */
	private static boolean onTheGround(Player player) {
		return !player.isSleeping() && !player.isPassenger() && !player.isFallFlying() && !player.isVisuallySwimming()
			&& Blackout.pose(player) == BlackoutEnding.Pose.GROUND;
	}

	// ------------------------------------------------------------------ the stars

	/** At the end of every client tick. */
	public static void onClientTick() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			// A fallen body lies the way its head looks: the body's own turn lags behind it (up to 50 degrees) and would
			// make it fall a little differently on every client
			if (Blackout.isOut(player) && !player.isSleeping() && !player.isPassenger()) {
				player.setYBodyRot(player.getYHeadRot());
				player.yBodyRotO = player.yBodyRot;
			}
		}
		STARS.clear();
		for (AbstractClientPlayer player : level.players()) {
			Vec3 stars = starCenter(player);
			if (stars != null) {
				STARS.add(stars);
			}
		}
		if (level.getGameTime() % DizzyStarParticle.SPAWN_EVERY != 0) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			// Not around your own eyes: in first person they would circle the camera
			if (player.isInvisible() || player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
				continue;
			}
			Vec3 stars = starCenter(player);
			if (stars != null) {
				level.addParticle(TipsyRegistry.DIZZY_STAR_PARTICLE.get(), stars.x, stars.y, stars.z, 0.0, 0.0, 0.0);
			}
		}
	}

	/** {@return whether stars still circle around {@code center}: someone there is still out (not getting up yet)} */
	static boolean starsCircle(double x, double y, double z) {
		for (Vec3 stars : STARS) {
			if (stars.distanceToSqr(x, y, z) < 0.5) {
				return true;
			}
		}
		return false;
	}

	/** {@return the middle of the stars' circle above this player's head while they are out, or null} */
	static @Nullable Vec3 starCenter(Player player) {
		int left = Blackout.ticksLeft(player);
		if (left < Blackout.GET_UP) {
			// Out of the bed or the boat: gone. Off the ground they go as the get-up starts (lyingStill)
			return null;
		}
		if (player.isSleeping()) {
			return player.position().add(0.0, STARS_ABOVE + 0.2, 0.0);
		}
		if (player.isPassenger() || player.isFallFlying() || player.isVisuallySwimming()) {
			// Sitting, gliding or crawling in the game's own pose: above the eyes
			return player.getEyePosition().add(0.0, STARS_ABOVE + 0.15, 0.0);
		}
		if (Blackout.pose(player) != BlackoutEnding.Pose.GROUND || !BlackoutPose.lyingStill(left)) {
			return null;
		}
		// Above the head lying on the ground, ahead of the feet
		float scale = player.getScale();
		Vec3 head = BlackoutPose.toWorld(player.getYHeadRot() + turn(player), BlackoutPose.lyingHead()).scale(scale);
		return player.position().add(head).add(0.0, STARS_ABOVE * scale, 0.0);
	}

	// ------------------------------------------------------------------ the player's own view

	/**
	 * Out on the ground, the eyes go where the head goes ({@link BlackoutPose.Body#viewPitch}, the view's yaw and roll):
	 * down into the ground as the body falls, sideways along it with the cheek on the ground, up again with the head.
	 * {@return the roll (the cheek on the ground, a tilt of the head; {@link TipsyClient#cameraAngles} scales it by
	 * Distortion Effects), and the pitch and the yaw to add (a pose: always), for this camera entity}
	 */
	public static TipsyWobble.Angles cameraTurn(@Nullable Entity cameraEntity, float partial) {
		LocalPlayer player = firstPerson(cameraEntity);
		BlackoutPose.Body body = player == null ? null : body(player, partial);
		if (body == null) {
			return TipsyWobble.Angles.NONE;
		}
		float roll = body.viewRoll();
		if (TipsyConfig.wobbleIntensity() <= 0.0 && left(player, partial) < Blackout.GROUND_GET_UP - BlackoutPose.LOOK) {
			// Up off the cheek, the head's tilt is a sway: none with the wobble off
			roll = 0.0F;
		}
		return new TipsyWobble.Angles(roll, body.viewPitch() - player.getViewXRot(partial),
			Mth.wrapDegrees(player.getYHeadRot() + body.turn() + body.viewYaw() - player.getViewYRot(partial)));
	}

	/**
	 * {@return where the first-person camera moves from the eyes ({@link TipsyClient#cameraOffset}): into the head, as it
	 * falls to the ground, lies there and comes up again}
	 */
	public static Vec3 cameraShift(Camera camera, float partial) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (camera.isDetached() || camera.getEntity() != player || player == null) {
			return Vec3.ZERO;
		}
		BlackoutPose.Body body = body(player, partial);
		if (body == null) {
			return Vec3.ZERO;
		}
		// The pose starts and ends with the eyes where a standing player has them: the player's own eye height (crouched
		// under a low ceiling, say) counts as much as the game's pose does, so neither end jumps
		float scale = player.getScale();
		Vec3 eye = BlackoutPose.toWorld(player.getYHeadRot() + body.turn(), body.eye()).scale(scale);
		double standing = BlackoutPose.STANDING_EYES * scale;
		return eye.subtract(0.0, standing + (player.getEyeHeight() - standing) * (1.0F - body.vanilla()), 0.0);
	}

	/** {@return this client's own player when {@code cameraEntity} is them, seen in first person, or null} */
	private static @Nullable LocalPlayer firstPerson(@Nullable Entity cameraEntity) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		return player != null && cameraEntity == player && minecraft.options.getCameraType().isFirstPerson() ? player : null;
	}

	/**
	 * Out cold, the keys do nothing (the server slows you to a stop as well; this keeps jumping out too). Right after the
	 * player's input was read each tick.
	 */
	public static void onMovementInput(Player player, Input input) {
		if (Blackout.isOut(player)) {
			holdStill(input);
		}
	}

	/** No key pressed and no movement (1.21.1: the input's own fields; sprinting needs a forward impulse, so it ends too). */
	static void holdStill(Input input) {
		input.up = false;
		input.down = false;
		input.left = false;
		input.right = false;
		input.jumping = false;
		input.shiftKeyDown = false;
		input.leftImpulse = 0.0F;
		input.forwardImpulse = 0.0F;
	}

	/** Vanilla turns by (s * 0.6 + 0.2)^3: this sensitivity makes it zero. */
	public static final double NO_TURNING = -1.0 / 3.0;

	/** ...and neither does the mouse: you lie where you fell, looking where you fell. The sensitivity to turn with. */
	public static double mouseSensitivity(double sensitivity) {
		return isOutHere() ? NO_TURNING : sensitivity;
	}

	/** No hand in view while out. True skips drawing the first-person hands. */
	public static boolean hidesHands() {
		return isOutHere();
	}

	/** Vanilla darkens the view the longer you sleep: in a blackout's bed the eyes are opening instead. True skips it. */
	public static boolean hidesSleepOverlay() {
		return isOutHere();
	}

	/** The blackout's bed has no "Leave Bed": it ends by itself, in less than five seconds. True keeps the screen from opening. */
	public static boolean blocksScreen(@Nullable Screen screen) {
		return screen instanceof InBedChatScreen && isOutHere();
	}

	// ------------------------------------------------------------------ the eyes

	/** The blackout layer, above all others: covers everything, the HUD included (and is not part of the optional vision effects). */
	public static void renderBlackout(GuiGraphics gui, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		Integer left = player == null ? null : TipsyRegistry.BLACKOUT.getExisting(player);
		if (left == null || left <= 0) {
			return;
		}
		float closed = BlackoutPose.eyesClosed(left - delta.getGameTimeDeltaPartialTick(false));
		if (closed > 0.0F) {
			drawLids(gui, Mth.clamp(closed, 0.0F, 1.0F));
		}
	}

	/**
	 * Two lids with a soft edge that meet in the middle; fully closed they cover the whole view (also the tipsy blinks).
	 * Drawn like vanilla's sleep overlay, over whatever was drawn before it (1.21.1's HUD layers lie at different depths).
	 */
	static void drawLids(GuiGraphics gui, float closed) {
		int width = gui.guiWidth();
		int height = gui.guiHeight();
		int feather = Math.max(4, height / 8);
		int lid = Math.round(closed * (height / 2.0F + feather));
		gui.fill(RenderType.guiOverlay(), 0, 0, width, lid - feather, 0xFF000000);
		gui.fillGradient(RenderType.guiOverlay(), 0, lid - feather, width, lid, 0xFF000000, 0x00000000, 0);
		gui.fillGradient(RenderType.guiOverlay(), 0, height - lid, width, height - lid + feather, 0x00000000, 0xFF000000, 0);
		gui.fill(RenderType.guiOverlay(), 0, height - lid + feather, width, height, 0xFF000000);
	}

	/** Whether this client's own player is out cold. */
	private static boolean isOutHere() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && Blackout.isOut(player);
	}
}
