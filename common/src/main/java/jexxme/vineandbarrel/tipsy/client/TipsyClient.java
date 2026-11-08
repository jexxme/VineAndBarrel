package jexxme.vineandbarrel.tipsy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.BlackoutPose;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.TipsySounds;
import jexxme.vineandbarrel.tipsy.TipsyInfo;
import jexxme.vineandbarrel.tipsy.TipsyLevels;
import jexxme.vineandbarrel.tipsy.TipsyMotion;
import jexxme.vineandbarrel.tipsy.TipsyRegistry;
import jexxme.vineandbarrel.tipsy.TipsyVision;
import jexxme.vineandbarrel.tipsy.TipsyWobble;
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client side of Tipsy: what this player sees (the sway, the hiccup's flick, a stumble's dip, a trip face down, the FOV
 * breathing, the dark edges and the blinks), what they feel in their keys (the drift at V, held still while down after a
 * trip) and how every tipsy player looks to the others (the sway, the jolts, the trip). The math lives in
 * {@link TipsyWobble}, {@link TipsyMotion} and {@link TipsyVision} so it is tested headless. Each loader calls these from
 * its own client events (NeoForge: {@code NeoForgeClient}; Fabric: Fabric API callbacks and our client mixins).
 * <p>
 * What moves the screen follows the player's own settings: {@code wobbleIntensity} (0 = never), vanilla's Distortion
 * Effects (the camera, squared like vanilla's Nausea) and FOV Effects (the breathing). Poses (lying down after a trip)
 * are not distortions and always show.
 */
public final class TipsyClient
{
	/** The HUD layer for tunnel vision and blinks, by id (NeoForge GUI layers, Fabric HUD elements). */
	public static final Identifier VISION_LAYER = VineAndBarrel.id("tipsy_vision");
	/** Per tick, how far the shown wobble eases toward the real level (about 1.5 s to settle). */
	private static final float EASE = 0.05F;
	/** Per tick, how fast Tipsy's screen effects fade out when a blackout starts, and back in after. */
	private static final float PAUSE_EASE = 0.15F;
	/** How far the moved camera stays from a block in its way. */
	private static final double CAMERA_MARGIN = 0.2;
	/** At V, walking drifts this far to the side at most (a share of a full sideways step). */
	private static final float DRIFT = 0.3F;

	private static float wobbleLevel;
	private static float wobbleLevelO;
	/** 1 normally, 0 while blacked out: Tipsy pauses then (the blackout has the screen). */
	private static float awake = 1.0F;
	private static float awakeO = 1.0F;

	/** The movements each player is in, as this client saw them start. */
	private static final Map<Player, Moves> MOVES = new WeakHashMap<>();

	private TipsyClient() {
	}

	public static void register(ClientRegistrar registrar) {
		registrar.particle(TipsyRegistry.HICCUP_PARTICLE.get(), HiccupParticle.Provider::new);
		BlackoutClient.register(registrar);
	}

	/** Hovering Tipsy in the inventory's effect list says what this level does. */
	public static void onEffectTooltip(MobEffectInstance effect, List<Component> tooltip) {
		if (!effect.is(TipsyRegistry.TIPSY)) {
			return;
		}
		if (tooltip.isEmpty()) {
			// The wide effect list shows name and time itself and has no tooltip; ours needs a title
			tooltip.add(Component.translatable("potion.withAmplifier", effect.getEffect().value().getDisplayName(),
				Component.translatable("enchantment.level." + (effect.getAmplifier() + 1))));
		}
		tooltip.addAll(TipsyInfo.lines(effect.getAmplifier() + 1));
	}

	/**
	 * Tipsy's side effects (and the blackout's) come without an icon, but the inventory lists every effect anyway. For
	 * these vanilla effects (Slowness, Mining Fatigue, Weakness, Nausea) the inventory shows an instance only when it
	 * has its icon, so a potion's or a command's still shows exactly as in vanilla.
	 */
	public static boolean isVisibleInInventory(MobEffectInstance instance) {
		return instance.showIcon();
	}

	/** The vanilla effects {@link #isVisibleInInventory} is for. */
	public static final List<Holder<MobEffect>> SIDE_EFFECTS = List.of(MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS, MobEffects.NAUSEA);

	// ------------------------------------------------------------------ the tick

	/** At the end of every client tick. */
	public static void onClientTick() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		float target = player == null ? 0.0F : Tipsy.getLevel(player);
		wobbleLevelO = wobbleLevel;
		wobbleLevel += (target - wobbleLevel) * EASE;
		if (Math.abs(target - wobbleLevel) < 0.01F) {
			wobbleLevel = target;
		}
		awakeO = awake;
		awake = Mth.approach(awake, player != null && Blackout.isOut(player) ? 0.0F : 1.0F, PAUSE_EASE);
		ClientLevel level = minecraft.level;
		if (level == null) {
			MOVES.clear();
			return;
		}
		for (AbstractClientPlayer other : level.players()) {
			track(other);
		}
		if (player != null && TipsyConfig.wobbleIntensity() <= 0.0) {
			stillScreen(player);
		}
		if (player != null) {
			heartbeat(player);
		}
	}

	/** Ticks until the next heartbeat. */
	private static int heartbeatIn;

	/**
	 * Tipsy V: this player hears their own heart (nobody else does), unless blacked out or with the volume at 0. The
	 * first beat comes right away, then one about every {@link TipsySounds#HEARTBEAT_GAP} ticks.
	 */
	private static void heartbeat(LocalPlayer player) {
		double volume = TipsyConfig.heartbeatVolume();
		if (Tipsy.getLevel(player) < TipsySounds.HEARTBEAT_LEVEL || Blackout.isOut(player) || volume <= 0.0) {
			heartbeatIn = 0;
			return;
		}
		if (--heartbeatIn <= 0) {
			TipsySounds.heartbeat(player, volume);
			heartbeatIn = TipsySounds.HEARTBEAT_GAP - 1 + player.getRandom().nextInt(3);
		}
	}

	/**
	 * Wobble off means Tipsy never moves this player's screen, its Nausea spells included: Tipsy's own Nausea (and the
	 * blackout's shakes) come without an icon, so an icon-less Nausea is dropped here, on this client only. The server
	 * keeps it, nobody else sees a difference, and a potion's Nausea (with its icon) stays.
	 */
	private static void stillScreen(LocalPlayer player) {
		MobEffectInstance nausea = player.getEffect(MobEffects.NAUSEA);
		if (nausea != null && !nausea.showIcon()) {
			player.removeEffect(MobEffects.NAUSEA);
		}
	}

	// ------------------------------------------------------------------ the movements every client plays

	/** When this client saw each of a player's movements start (in that player's ticks), and the counts seen last. */
	private static final class Moves
	{
		TipsyMotion.Counts seen;
		float hiccupAt = -1000.0F;
		float stumbleAt = -1000.0F;
		boolean left;
		float tripAt = -1000.0F;

		Moves(TipsyMotion.Counts seen) {
			this.seen = seen;
		}
	}

	private static void track(Player player) {
		TipsyMotion.Counts counts = TipsyRegistry.MOTION.getExisting(player);
		if (counts == null) {
			counts = TipsyMotion.Counts.NONE;
		}
		Moves moves = MOVES.get(player);
		if (moves == null) {
			// First sight (joined, came into view): what happened before is history, nothing to play
			MOVES.put(player, new Moves(counts));
			return;
		}
		float now = player.tickCount;
		if (counts.hiccups() != moves.seen.hiccups()) {
			moves.hiccupAt = now;
		}
		if (counts.stumbles() != moves.seen.stumbles()) {
			moves.stumbleAt = now;
			moves.left = counts.stumbleLeft();
		}
		if (counts.trips() != moves.seen.trips()) {
			moves.tripAt = now;
		}
		moves.seen = counts;
	}

	private static @Nullable Moves moves(Player player) {
		return MOVES.get(player);
	}

	/** {@return how far into a hiccup's jolt this player is (0 to 1)} */
	public static float hiccupJolt(Player player, float partial) {
		Moves moves = moves(player);
		return moves == null ? 0.0F : TipsyMotion.hiccupJolt(player.tickCount + partial - moves.hiccupAt);
	}

	/** {@return how far this player lurches to the side, -1 (right) to 1 (left): a stumble, or a trip's start} */
	public static float stumbleLurch(Player player, float partial) {
		Moves moves = moves(player);
		if (moves == null) {
			return 0.0F;
		}
		float stumble = TipsyMotion.stumbleLurch(player.tickCount + partial - moves.stumbleAt) * (moves.left ? 1.0F : -1.0F);
		float trip = TipsyMotion.tripStumble(tripElapsed(player, partial));
		return Math.abs(trip) > Math.abs(stumble) ? trip : stumble;
	}

	/** {@return the ticks since this player's last trip started (large when long ago)} */
	public static float tripElapsed(Player player, float partial) {
		Moves moves = moves(player);
		return moves == null ? Float.MAX_VALUE : player.tickCount + partial - moves.tripAt;
	}

	/** {@return how far a trip has this player down on the face, 0 to 1} */
	public static float tripFallen(Player player, float partial) {
		return TipsyMotion.tripFallen(tripElapsed(player, partial));
	}

	// ------------------------------------------------------------------ own camera

	/** {@return the vanilla Distortion Effects factor for camera movement, squared like vanilla's Nausea} */
	private static float distortion(Minecraft minecraft) {
		float scale = minecraft.options.screenEffectScale().get().floatValue();
		return scale * scale;
	}

	private static float intensity() {
		return (float) Math.max(0.0, TipsyConfig.wobbleIntensity());
	}

	/**
	 * What to add to the camera's angles this frame ({@link TipsyWobble.Angles#NONE} for nothing): the sway, a hiccup's
	 * flick, a stumble's dip, a trip's look at the ground, and the blackout's roll. The one camera hook both loaders call.
	 */
	public static TipsyWobble.Angles cameraAngles(@Nullable Entity cameraEntity, float partial) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || cameraEntity != player) {
			return TipsyWobble.Angles.NONE;
		}
		float intensity = intensity();
		float time = player.tickCount + partial;
		float paused = Mth.lerp(partial, awakeO, awake);
		float level = Mth.lerp(partial, wobbleLevelO, wobbleLevel);
		TipsyWobble.Angles angles = TipsyWobble.camera(level, time, intensity).scaled(paused);
		float jolt = hiccupJolt(player, partial);
		float lurch = stumbleLurch(player, partial);
		float trip = tripElapsed(player, partial);
		float shake = TipsyMotion.tripShake(trip) * TipsyMotion.TRIP_SHAKE;
		angles = angles.plus(
			(Math.signum(lurch) * TipsyMotion.STUMBLE_ROLL * Math.abs(lurch) + shake * Mth.sin(trip * 2.7F)) * intensity,
			(TipsyMotion.STUMBLE_DIP * Math.abs(lurch) - TipsyMotion.HICCUP_FLICK * jolt + shake * 0.6F * Mth.cos(trip * 3.1F)) * intensity,
			0.0F);
		angles = angles.scaled(distortion(minecraft));
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		float fallen = tripFallen(player, partial);
		if (fallen > 0.0F && firstPerson) {
			// A pose, not a distortion: face down, you look at the ground, whatever the settings
			angles = angles.plus(BlackoutPose.side(player.getId()) * TipsyMotion.TRIP_TILT * fallen,
				(TipsyMotion.TRIP_LOOK - player.getXRot()) * fallen, 0.0F);
		}
		// Lying on the ground after a blackout, the view rolls onto its side too
		return angles.plus(BlackoutClient.cameraRoll(cameraEntity, partial) * distortion(minecraft), 0.0F, 0.0F);
	}

	/** {@return what the field of view is multiplied by this frame: the breathing at V (FOV Effects scales it)} */
	public static float fovFactor(@Nullable Entity cameraEntity, float partial) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || cameraEntity != player) {
			return 1.0F;
		}
		float effects = minecraft.options.fovEffectScale().get().floatValue();
		float level = Mth.lerp(partial, wobbleLevelO, wobbleLevel) * Mth.lerp(partial, awakeO, awake);
		return TipsyWobble.fov(level, player.tickCount + partial, intensity() * effects);
	}

	/**
	 * {@return where {@code jexxme.vineandbarrel.mixin.CameraMixin} moves the first-person camera: down to the ground
	 * after a blackout, and down and forward to where the face lies after a trip}
	 */
	public static Vec3 cameraOffset(Camera camera, float partial) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (camera.isDetached() || camera.entity() != player || player == null) {
			return Vec3.ZERO;
		}
		double blackout = BlackoutClient.cameraDrop(camera, partial);
		float fallen = tripFallen(player, partial);
		if (fallen <= 0.0F) {
			return blackout > 0.0 ? new Vec3(0.0, -blackout, 0.0) : Vec3.ZERO;
		}
		Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(TipsyMotion.TRIP_FORWARD * fallen);
		double drop = Math.max(blackout, (player.getEyeHeight() - TipsyMotion.TRIP_EYES) * fallen);
		return clamped(camera, new Vec3(forward.x, -drop, forward.z));
	}

	/** {@return the offset, cut short where a block is in the way (pushed against a wall, the view stays out of it)} */
	private static Vec3 clamped(Camera camera, Vec3 offset) {
		Vec3 eye = camera.position();
		Vec3 target = eye.add(offset);
		BlockHitResult hit = camera.entity().level().clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
			camera.entity()));
		if (hit.getType() == HitResult.Type.MISS) {
			return offset;
		}
		// Stop a little short of the block, so its face isn't right in the near plane
		Vec3 reach = hit.getLocation().subtract(eye);
		double length = Math.max(0.0, reach.length() - CAMERA_MARGIN);
		return reach.lengthSqr() < 1.0E-6 ? Vec3.ZERO : reach.normalize().scale(length);
	}

	// ------------------------------------------------------------------ own keys and mouse

	/**
	 * Right after the player's input was read each tick: out cold or down after a trip, the keys do nothing; at V,
	 * walking forward drifts slowly to one side and then the other, and you keep correcting.
	 */
	public static void onMovementInput(Player player, ClientInput input) {
		BlackoutClient.onMovementInput(player, input);
		if (TipsyMotion.tripHolds(tripElapsed(player, 0.0F))) {
			input.keyPresses = Input.EMPTY;
			input.moveVector = Vec2.ZERO;
			return;
		}
		float drift = drift(player);
		if (drift != 0.0F && input.moveVector.y > 0.0F) {
			Vec2 moved = new Vec2(input.moveVector.x + drift, input.moveVector.y);
			input.moveVector = moved.lengthSquared() > 1.0F ? moved.normalized() : moved;
		}
	}

	/** {@return how far the legs pull to the side right now (0 = not at all)} */
	private static float drift(Player player) {
		if (!TipsyConfig.stumbling() || !TipsyLevels.at(Tipsy.getLevel(player)).drift() || !player.onGround() || player.isShiftKeyDown()
			|| player.isPassenger() || player.isInWater() || Blackout.isOut(player)) {
			return 0.0F;
		}
		float time = player.tickCount;
		float phase = (player.getId() * 0.618034F) % 1.0F * Mth.TWO_PI;
		return DRIFT * (0.75F * Mth.sin(time * 0.021F + phase) + 0.25F * Mth.sin(time * 0.053F + phase * 1.7F));
	}

	/** The sensitivity the mouse turns the view with: out cold or down after a trip, it doesn't turn. */
	public static double mouseSensitivity(double sensitivity) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && TipsyMotion.tripHolds(tripElapsed(player, 0.0F))) {
			return BlackoutClient.NO_TURNING;
		}
		return BlackoutClient.mouseSensitivity(sensitivity);
	}

	/** No hand in view while out cold or down on the face. True skips drawing the first-person hands. */
	public static boolean hidesHands() {
		LocalPlayer player = Minecraft.getInstance().player;
		return BlackoutClient.hidesHands() || player != null && tripFallen(player, 0.0F) > 0.0F;
	}

	// ------------------------------------------------------------------ tunnel vision and heavy-eyed blinks

	private static final Identifier VIGNETTE = VineAndBarrel.id("textures/gui/tipsy_vignette.png");
	private static final int VIGNETTE_SIZE = 128;

	/**
	 * Tunnel vision and blinks: above the vanilla camera overlays (pumpkin, powder snow), under the crosshair and the HUD, which
	 * stay readable. Both scale with vanilla's Darkness Pulsing slider, as the wobble does with Distortion Effects.
	 */
	public static void renderVision(GuiGraphicsExtractor gui, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !TipsyConfig.visionEffects()) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float paused = Mth.lerp(partial, awakeO, awake);
		if (paused <= 0.0F) {
			// Out cold: the blackout's own lids have the screen
			return;
		}
		float darkness = minecraft.options.darknessEffectScale().get().floatValue();
		if (darkness <= 0.0F) {
			return;
		}
		int width = gui.guiWidth();
		int height = gui.guiHeight();
		float vignette = TipsyVision.vignette(Mth.lerp(partial, wobbleLevelO, wobbleLevel)) * paused * darkness;
		if (vignette > 0.0F) {
			int color = ARGB.color(Math.round(vignette * 255.0F), 0xFFFFFF);
			gui.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, VIGNETTE_SIZE, VIGNETTE_SIZE,
				VIGNETTE_SIZE, VIGNETTE_SIZE, color);
		}
		float closed = TipsyVision.eyelids(Tipsy.getLevel(player), player.tickCount + partial, player.getId()) * paused * darkness;
		if (closed > 0.0F) {
			BlackoutClient.drawLids(gui, closed);
		}
	}

	// ------------------------------------------------------------------ T1: how others see you

	/**
	 * What a player's render state carries for {@link #beforeRenderLiving}: the sway level (0 = none), how far a
	 * blacked-out player lies tipped over, a hiccup's jolt, a stumble's lurch (-1 right to 1 left) and how far a trip has
	 * them down on the face.
	 */
	public record BodyPose(int swayLevel, float lying, float jolt, float lurch, float fallen)
	{
	}

	/** {@return the body pose of this player for its render state, or null if it stands as vanilla draws it} */
	public static @Nullable BodyPose bodyPose(Entity avatar) {
		if (!(avatar instanceof Player player)) {
			return null;
		}
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Integer level = TipsyRegistry.SYNCED_LEVEL.getExisting(avatar);
		int sway = level != null && level > 0 ? level : 0;
		float lying = BlackoutClient.lying(player, partial);
		float jolt = hiccupJolt(player, partial);
		float lurch = stumbleLurch(player, partial);
		float fallen = tripFallen(player, partial);
		if (sway == 0 && lying <= 0.0F && jolt <= 0.0F && lurch == 0.0F && fallen <= 0.0F) {
			return null;
		}
		return new BodyPose(sway, lying, jolt, lurch, fallen);
	}

	/** How far a blacked-out player lies tipped over on the ground (0 to 1) for its render state, or null if not at all. */
	public static @Nullable Float blackoutLying(Entity avatar) {
		float lying = avatar instanceof Player player
			? BlackoutClient.lying(player, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) : 0.0F;
		return lying > 0.0F ? lying : null;
	}

	/**
	 * Before a living entity is drawn (as late as possible, after other mods): tips a blacked-out player over, lays a
	 * tripped one on the face, or leans a tipsy one (with a hiccup's bob and a stumble's lurch). {@code pose} is the
	 * {@link #bodyPose} its render state carries. Returns true if a pose was pushed, which {@link #afterRenderLiving}
	 * must then pop.
	 */
	public static boolean beforeRenderLiving(LivingEntityRenderState state, @Nullable BodyPose pose, PoseStack stack) {
		if (pose == null || state.hasPose(Pose.SLEEPING)) {
			return false;
		}
		int entityId = state instanceof AvatarRenderState avatar ? avatar.id : 0;
		float facing = 180.0F - state.bodyRot;
		if (pose.lying() > 0.0F) {
			// A blacked-out player falls over sideways around the feet, lifted a little so the body rests on the ground
			// (always shown: it is the blackout's show)
			stack.pushPose();
			stack.translate(0.0F, BlackoutPose.LIFT * pose.lying() * state.scale, 0.0F);
			stack.rotateDegrees(Axis.YP, facing);
			stack.rotateDegrees(Axis.ZP, BlackoutPose.bodyAngle(pose.lying(), BlackoutPose.side(entityId)));
			stack.rotateDegrees(Axis.YP, -facing);
			return true;
		}
		if (pose.fallen() > 0.0F) {
			// Tripped: forward onto the face around the feet, the way vanilla lays a swimmer flat (always shown)
			stack.pushPose();
			stack.translate(0.0F, TipsyMotion.TRIP_LIFT * pose.fallen() * state.scale, 0.0F);
			stack.rotateDegrees(Axis.YP, facing);
			stack.rotateDegrees(Axis.XP, -TipsyMotion.TRIP_BODY_DEGREES * pose.fallen());
			stack.rotateDegrees(Axis.YP, -facing);
			return true;
		}
		// Off for motion sickness: with the wobble at 0 nothing tipsy moves on this screen, other players' sway included
		if (!TipsyConfig.otherPlayersSway() || TipsyConfig.wobbleIntensity() <= 0.0) {
			return false;
		}
		float sway = TipsyWobble.bodySway(pose.swayLevel(), state.ageInTicks, entityId) + TipsyMotion.STUMBLE_LEAN * pose.lurch();
		stack.pushPose();
		// A hiccup jerks the shoulders up; the lean is sideways around the feet, relative to where the body faces
		stack.translate(0.0F, TipsyMotion.HICCUP_BOB * pose.jolt() * state.scale, 0.0F);
		stack.rotateDegrees(Axis.YP, facing);
		stack.rotateDegrees(Axis.ZP, sway);
		stack.rotateDegrees(Axis.YP, -facing);
		return true;
	}

	/** After a living entity was drawn: undoes {@link #beforeRenderLiving}'s pose if it pushed one. */
	public static void afterRenderLiving(boolean pushed, PoseStack pose) {
		if (pushed) {
			pose.popPose();
		}
	}
}
