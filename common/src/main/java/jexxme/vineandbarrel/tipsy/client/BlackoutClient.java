package jexxme.vineandbarrel.tipsy.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.BlackoutEnding;
import jexxme.vineandbarrel.tipsy.BlackoutPose;
import jexxme.vineandbarrel.tipsy.TipsyRegistry;
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

/**
 * The blackout on the client: the stars above the head of everyone who is out, and for the player themselves the
 * tipped, lowered camera while lying on the ground, keys and mouse held still, no hand, and no vanilla bed screen
 * (there is nothing to leave: the blackout gets you up), and the closing and opening eyes ({@link #renderBlackout}). The
 * body that tips over is drawn by {@link TipsyClient#beforeRenderLiving}, with the other poses; the math is
 * {@link BlackoutPose}. Each loader calls these hooks from its own client events (NeoForge:
 * {@code NeoForgeClient}; Fabric: Fabric API callbacks and our client mixins), at the same spots.
 */
public final class BlackoutClient
{
	/** The HUD layer of the closed eyes, by id (NeoForge GUI layers; Fabric draws it from its Gui mixin): above all others. */
	public static final ResourceLocation LAYER = VineAndBarrel.id("blackout");
	/** The head is about this high above the feet; the stars circle this far above it. */
	private static final double HEAD = 1.6, STARS_ABOVE = 0.45;

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

	/** {@return how far this player lies tipped over on the ground (0 when not out, in bed or in a boat)} */
	public static float lying(Player player, float partial) {
		float left = left(player, partial);
		if (left <= 0.0F || player.isSleeping() || player.isPassenger() || Blackout.pose(player) != BlackoutEnding.Pose.GROUND) {
			return 0.0F;
		}
		return BlackoutPose.lying(left);
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

	/** {@return the middle of the stars' circle above this player's head while they are out, or null} */
	static @Nullable Vec3 starCenter(Player player) {
		int left = Blackout.ticksLeft(player);
		if (left < Blackout.GET_UP) {
			return null;
		}
		if (player.isSleeping()) {
			return player.position().add(0.0, STARS_ABOVE + 0.2, 0.0);
		}
		if (player.isPassenger()) {
			return player.getEyePosition().add(0.0, STARS_ABOVE + 0.15, 0.0);
		}
		if (Blackout.pose(player) != BlackoutEnding.Pose.GROUND || !BlackoutPose.lyingStill(left)) {
			return null;
		}
		float scale = player.getScale();
		Vec3 head = BlackoutPose.tipped(player.getYHeadRot(), BlackoutPose.bodyAngle(1.0F, BlackoutPose.side(player.getId())), 1.0F, HEAD * scale);
		return player.position().add(head).add(0.0, STARS_ABOVE * scale, 0.0);
	}

	// ------------------------------------------------------------------ the player's own view

	/**
	 * Lying on the ground, the view rolls onto its side (first person only; the third-person view shows the body). The
	 * degrees to add to the camera's roll for this camera entity, before Distortion Effects ({@link TipsyClient#cameraAngles}).
	 */
	public static float cameraRoll(@Nullable Entity cameraEntity, float partial) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || cameraEntity != player || !minecraft.options.getCameraType().isFirstPerson()) {
			return 0.0F;
		}
		float lying = lying(player, partial);
		return lying > 0.0F ? BlackoutPose.side(player.getId()) * BlackoutPose.CAMERA_ROLL * lying : 0.0F;
	}

	/** {@return how far the first-person camera goes down lying on the ground ({@link TipsyClient#cameraOffset})} */
	public static double cameraDrop(Camera camera, float partial) {
		Minecraft minecraft = Minecraft.getInstance();
		if (camera.isDetached() || camera.getEntity() != minecraft.player || minecraft.player == null) {
			return 0.0;
		}
		float lying = lying(minecraft.player, partial);
		return lying <= 0.0F ? 0.0 : (minecraft.player.getEyeHeight() - BlackoutPose.LYING_EYES) * lying;
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
