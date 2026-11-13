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
import jexxme.vineandbarrel.tipsy.TipsyWobble;
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client side of Tipsy: what this player sees (the hiccup's flick, a stumble's dip, a trip face down), what they feel
 * in their keys (the drift at V, held still while down after a trip) and how every tipsy player looks to the others
 * (the sway, the jolts, the trip). Tipsy never moves the own view on its own: only with the body. The math lives in
 * {@link TipsyWobble} and {@link TipsyMotion} so it is tested headless. Each loader calls these from its own client
 * events (NeoForge: {@code NeoForgeClient}; Fabric: Fabric API callbacks and our client mixins).
 * <p>
 * What moves the own view follows the player's settings: "drunk screen" ({@link TipsyConfig#drunkScreen}, 0 = never)
 * and vanilla's Distortion Effects (squared like vanilla's Nausea). Poses (lying down after a trip) are not distortions
 * and always show.
 */
public final class TipsyClient
{
	/** The HUD layer for what the eyes do, by id (NeoForge GUI layers; Fabric draws it from its Gui mixin); see {@link #renderVision}. */
	public static final ResourceLocation VISION_LAYER = VineAndBarrel.id("tipsy_vision");
	/** Per tick, how fast Tipsy's screen effects fade out when a blackout starts, and back in after. */
	private static final float PAUSE_EASE = 0.15F;
	/** How far the moved camera stays from a block in its way. */
	private static final double CAMERA_MARGIN = 0.2;
	/** At V, walking drifts this far to the side at most (a share of a full sideways step). */
	private static final float DRIFT = 0.3F;

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
	 * The blackout's Slowness comes without an icon, but the inventory lists every effect anyway. For the vanilla effects
	 * in {@link #SIDE_EFFECTS} the inventory shows an instance only when it has its icon, so a potion's or a command's
	 * still shows exactly as in vanilla.
	 */
	public static boolean isVisibleInInventory(MobEffectInstance instance) {
		return instance.showIcon();
	}

	/** The vanilla effects {@link #isVisibleInInventory} is for: the blackout's Slowness (it holds the body still). */
	public static final List<Holder<MobEffect>> SIDE_EFFECTS = List.of(MobEffects.MOVEMENT_SLOWDOWN);

	// ------------------------------------------------------------------ the tick

	/** At the end of every client tick. */
	public static void onClientTick() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
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
		if (player != null) {
			heartbeat(player);
		}
	}

	/** Ticks until the next heartbeat. */
	private static int heartbeatIn;

	/**
	 * From {@link TipsySounds#HEARTBEAT_LEVEL} this player hears their own heart (nobody else does), as loud as the level
	 * says ({@link TipsyLevels.Ears#heartbeat}), unless blacked out or with the volume at 0. The first beat comes right
	 * away, then one about every {@link TipsySounds#HEARTBEAT_GAP} ticks.
	 */
	private static void heartbeat(LocalPlayer player) {
		double volume = TipsyConfig.heartbeatVolume() * TipsyLevels.at(Tipsy.getLevel(player)).ears().heartbeat();
		if (Blackout.isOut(player) || volume <= 0.0) {
			heartbeatIn = 0;
			return;
		}
		if (--heartbeatIn <= 0) {
			TipsySounds.heartbeat(player, volume);
			heartbeatIn = TipsySounds.HEARTBEAT_GAP - 1 + player.getRandom().nextInt(3);
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

	/** {@return how much Tipsy may move this player's own view: their "drunk screen" setting, 0 to 1} */
	private static float intensity() {
		return (float) Math.clamp(TipsyConfig.drunkScreen(), 0.0, 1.0);
	}

	/**
	 * What to add to the camera's angles this frame ({@link TipsyWobble.Angles#NONE} for nothing): only what the body
	 * does (a hiccup's flick, a stumble's dip, a trip's shake and its look at the ground) and the blackout's roll; Tipsy
	 * never turns the view on its own. The one camera hook both loaders call.
	 */
	public static TipsyWobble.Angles cameraAngles(@Nullable Entity cameraEntity, float partial) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || cameraEntity != player) {
			return TipsyWobble.Angles.NONE;
		}
		float intensity = intensity() * Mth.lerp(partial, awakeO, awake);
		float jolt = hiccupJolt(player, partial);
		float lurch = stumbleLurch(player, partial);
		float trip = tripElapsed(player, partial);
		float shake = TipsyMotion.tripShake(trip) * TipsyMotion.TRIP_SHAKE;
		TipsyWobble.Angles angles = TipsyWobble.Angles.NONE.plus(
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
		// Out on the ground after a blackout the eyes go where the head goes (a pose: always), its roll scaled like the rest
		TipsyWobble.Angles head = BlackoutClient.cameraTurn(cameraEntity, partial);
		return angles.plus(head.roll() * distortion(minecraft), head.pitch(), head.yaw());
	}

	/**
	 * {@return where {@code jexxme.vineandbarrel.mixin.CameraMixin} moves the first-person camera: into the head of a
	 * blacked-out player, as it falls to the ground and comes up again, and down and forward to where the face lies after a
	 * trip}
	 */
	public static Vec3 cameraOffset(Camera camera, float partial) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (camera.isDetached() || camera.getEntity() != player || player == null) {
			return Vec3.ZERO;
		}
		Vec3 blackout = BlackoutClient.cameraShift(camera, partial);
		float fallen = tripFallen(player, partial);
		if (fallen <= 0.0F) {
			// Into the head, wherever it is (a wall stays out of the view)
			return blackout == Vec3.ZERO ? blackout : clamped(camera, blackout);
		}
		Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(TipsyMotion.TRIP_FORWARD * fallen);
		double drop = Math.max(-blackout.y, (player.getEyeHeight() - TipsyMotion.TRIP_EYES) * fallen);
		return clamped(camera, new Vec3(forward.x, -drop, forward.z));
	}

	/** {@return the offset, cut short where a block is in the way (pushed against a wall, the view stays out of it)} */
	private static Vec3 clamped(Camera camera, Vec3 offset) {
		Vec3 eye = camera.getPosition();
		Vec3 target = eye.add(offset);
		BlockHitResult hit = camera.getEntity().level().clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
			camera.getEntity()));
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
	public static void onMovementInput(Player player, Input input) {
		BlackoutClient.onMovementInput(player, input);
		if (TipsyMotion.tripHolds(tripElapsed(player, 0.0F))) {
			BlackoutClient.holdStill(input);
			return;
		}
		float drift = drift(player);
		if (drift != 0.0F && input.forwardImpulse > 0.0F) {
			// 1.21.1 hands the impulses over already slowed (crawling) and not yet shortened to one step: the drift is
			// added to the full step, as in later versions, and the slowing put back on
			float slowed = input.forwardImpulse;
			Vec2 step = new Vec2(input.leftImpulse / slowed, 1.0F);
			if (step.lengthSquared() > 1.0F) {
				step = step.normalized();
			}
			Vec2 moved = new Vec2(step.x + drift, step.y);
			if (moved.lengthSquared() > 1.0F) {
				moved = moved.normalized();
			}
			input.leftImpulse = moved.x * slowed;
			input.forwardImpulse = moved.y * slowed;
		}
	}

	/** {@return how far the legs pull to the side right now (0 = not at all)} */
	private static float drift(Player player) {
		if (!TipsyConfig.stumbling() || !TipsyLevels.at(Tipsy.getLevel(player)).legs().drift() || !player.onGround() || player.isShiftKeyDown()
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

	// ------------------------------------------------------------------ the eyes

	/**
	 * The layer for what the eyes do, above the vanilla camera overlays (pumpkin, powder snow), under the crosshair and the
	 * HUD. Tipsy draws nothing here at the moment: the dark edges and the random blinks are gone (Tipsy overhaul); the
	 * drunk eyes and nodding off's lids come here.
	 */
	public static void renderVision(GuiGraphics gui, DeltaTracker delta) {
	}

	// ------------------------------------------------------------------ T1: how others see you

	/**
	 * How a player's body is posed for {@link #beforeRenderLiving}: the sway level (0 = none), a blacked-out player on
	 * the ground (falling on the face, lying, getting up; null if not), a hiccup's jolt, a stumble's lurch (-1 right to 1
	 * left) and how far a trip has them down on the face.
	 */
	public record BodyPose(int swayLevel, BlackoutPose.@Nullable Body blackout, float jolt, float lurch, float fallen)
	{
	}

	/**
	 * The player being drawn right now and their blackout on the ground ({@link BlackoutPose#body}), from
	 * {@link #beforeRenderLiving} to {@link #afterRenderLiving}: the model poses head and limbs from it
	 * ({@link #poseBlackout}), at the same moment as the body (1.21.1 has no render states to carry it).
	 */
	private static @Nullable LivingEntity posing;
	private static BlackoutPose.@Nullable Body posingBody;
	/**
	 * What was posing before each render still in progress: an entity drawn inside another one's render (by another
	 * mod, between its Pre and its model's posing) hands the outer one's back when it is done.
	 */
	private static final ArrayDeque<Posing> POSING_BEFORE = new ArrayDeque<>();

	private record Posing(@Nullable LivingEntity entity, BlackoutPose.@Nullable Body body)
	{
	}

	/** {@return the body pose of this player at this partial tick, or null if it stands as vanilla draws it} */
	public static @Nullable BodyPose bodyPose(Entity avatar, float partial) {
		if (!(avatar instanceof Player player)) {
			return null;
		}
		Integer level = TipsyRegistry.SYNCED_LEVEL.getExisting(avatar);
		int sway = level != null && level > 0 ? level : 0;
		BlackoutPose.Body blackout = BlackoutClient.body(player, partial);
		float jolt = hiccupJolt(player, partial);
		float lurch = stumbleLurch(player, partial);
		float fallen = tripFallen(player, partial);
		if (sway == 0 && blackout == null && jolt <= 0.0F && lurch == 0.0F && fallen <= 0.0F) {
			return null;
		}
		return new BodyPose(sway, blackout, jolt, lurch, fallen);
	}

	/**
	 * Before a living entity is drawn (as late as possible, after other mods): lays a blacked-out player on the ground
	 * (and gets them up again), lays a tripped one on the face, or leans a tipsy one (with a hiccup's bob and a stumble's
	 * lurch), by its {@link #bodyPose} at this partial tick (1.21.1 has no render states: the entity is asked right here).
	 * Returns true if a pose was pushed, which {@link #afterRenderLiving} must then pop.
	 */
	public static boolean beforeRenderLiving(LivingEntity entity, float partial, PoseStack stack) {
		POSING_BEFORE.push(new Posing(posing, posingBody));
		posing = null;
		posingBody = null;
		BodyPose pose = bodyPose(entity, partial);
		if (pose == null || entity.hasPose(Pose.SLEEPING)) {
			return false;
		}
		int entityId = entity.getId();
		float scale = entity.getScale();
		float facing = 180.0F - bodyRot(entity, partial);
		BlackoutPose.Body blackout = pose.blackout();
		if (blackout != null) {
			posing = entity;
			posingBody = blackout;
			// Out on the ground: the whole body turns around its hip, wherever the hip is now, turned the way it fell (head
			// and limbs are posed on the model: poseBlackout); always shown, it is the blackout's show
			stack.pushPose();
			stack.mulPose(Axis.YP.rotationDegrees(facing - blackout.turn()));
			stack.translate(blackout.hip().x * scale, blackout.hip().y * scale, blackout.hip().z * scale);
			stack.mulPose(Axis.XP.rotationDegrees(-blackout.lean()));
			stack.translate(0.0, -BlackoutPose.HIP * scale, 0.0);
			stack.mulPose(Axis.YP.rotationDegrees(-facing));
			return true;
		}
		if (pose.fallen() > 0.0F) {
			// Tripped: forward onto the face around the feet, the way vanilla lays a swimmer flat (always shown)
			stack.pushPose();
			stack.translate(0.0F, TipsyMotion.TRIP_LIFT * pose.fallen() * scale, 0.0F);
			stack.mulPose(Axis.YP.rotationDegrees(facing));
			stack.mulPose(Axis.XP.rotationDegrees(-TipsyMotion.TRIP_BODY_DEGREES * pose.fallen()));
			stack.mulPose(Axis.YP.rotationDegrees(-facing));
			return true;
		}
		// Off for motion sickness: with "drunk screen" at 0 nothing tipsy moves on this screen, other players' sway included
		if (!TipsyConfig.otherPlayersSway() || TipsyConfig.drunkScreen() <= 0.0) {
			return false;
		}
		float sway = TipsyWobble.bodySway(pose.swayLevel(), entity.tickCount + partial, entityId) + TipsyMotion.STUMBLE_LEAN * pose.lurch();
		stack.pushPose();
		// A hiccup jerks the shoulders up; the lean is sideways around the feet, relative to where the body faces
		stack.translate(0.0F, TipsyMotion.HICCUP_BOB * pose.jolt() * scale, 0.0F);
		stack.mulPose(Axis.YP.rotationDegrees(facing));
		stack.mulPose(Axis.ZP.rotationDegrees(sway));
		stack.mulPose(Axis.YP.rotationDegrees(-facing));
		return true;
	}

	/** {@return where the body faces as vanilla's renderer draws it: on a living mount the body follows the mount} */
	private static float bodyRot(LivingEntity entity, float partial) {
		float body = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
		if (entity.isPassenger() && entity.getVehicle() instanceof LivingEntity mount) {
			float head = Mth.rotLerp(partial, entity.yHeadRotO, entity.yHeadRot);
			float turn = Mth.clamp(Mth.wrapDegrees(head - Mth.rotLerp(partial, mount.yBodyRotO, mount.yBodyRot)), -85.0F, 85.0F);
			body = head - turn;
			if (turn * turn > 2500.0F) {
				body += turn * 0.2F;
			}
		}
		return body;
	}

	/**
	 * At the end of a humanoid model's posing (the player's, and through it the sleeves, trousers and jacket; the armour
	 * copies it): a blacked-out player on the ground has head and limbs where the fall, lying and getting up put them, with
	 * only as much of the game's own posing (looking around, the arms' idle sway, a held item's arm) as
	 * {@link BlackoutPose.Body#vanilla} lets through (none while down, all of it standing); the hat, the armour and what
	 * the hands hold come along. Called by {@code jexxme.vineandbarrel.mixin.HumanoidModelMixin} with whether it posed
	 * this model the last time; {@return whether it posed it now}
	 */
	public static boolean poseBlackout(HumanoidModel<?> model, LivingEntity entity, boolean posedBefore) {
		BlackoutPose.Body body = entity == posing ? posingBody : null;
		if (body == null) {
			if (posedBefore) {
				// The game never sets the head's roll and place itself (one model draws every player): back to its own
				restoreHead(model);
			}
			return false;
		}
		// The game's own pose starts from the model's own head roll and place, as on the newer games
		restoreHead(model);
		float vanilla = body.vanilla();
		pose(model.head, body.head(), vanilla);
		pose(model.rightArm, body.rightArm(), vanilla);
		pose(model.leftArm, body.leftArm(), vanilla);
		pose(model.rightLeg, body.rightLeg(), vanilla);
		pose(model.leftLeg, body.leftLeg(), vanilla);
		// The hat was copied from the head before (the player model copies the sleeves, trousers and jacket after this)
		model.hat.copyFrom(model.head);
		return true;
	}

	private static void restoreHead(HumanoidModel<?> model) {
		PartPose initial = model.head.getInitialPose();
		model.head.zRot = initial.zRot;
		model.head.x = initial.x;
		model.head.z = initial.z;
		model.hat.copyFrom(model.head);
	}

	private static void pose(ModelPart part, BlackoutPose.Limb limb, float vanilla) {
		part.xRot = limb.xRot() + vanilla * part.xRot;
		part.yRot = limb.yRot() + vanilla * part.yRot;
		part.zRot = limb.zRot() + vanilla * part.zRot;
		part.x += limb.x();
		part.y += limb.y();
		part.z += limb.z();
	}

	/**
	 * {@return the radius of an entity's round shadow, from the game's {@code radius}: a blacked-out player's shrinks away
	 * as the body goes down on the face (it lies ahead of the feet, where the shadow is) and grows back as the hip comes
	 * up again; 0 draws none} Called by {@code jexxme.vineandbarrel.mixin.EntityRendererMixin}.
	 */
	public static float blackoutShadow(Entity entity, float radius) {
		if (!(entity instanceof Player player) || radius <= 0.0F) {
			return radius;
		}
		// The partial tick the game draws this entity with (none while the game's ticking is frozen for it), as its body
		Minecraft minecraft = Minecraft.getInstance();
		boolean frozen = minecraft.level != null && minecraft.level.tickRateManager().isEntityFrozen(entity);
		BlackoutPose.Body body = BlackoutClient.body(player, minecraft.getTimer().getGameTimeDeltaPartialTick(!frozen));
		if (body == null) {
			return radius;
		}
		float up = Mth.clamp((float) (body.hip().y / BlackoutPose.HIP), 0.0F, 1.0F);
		return up * up < 0.05F ? 0.0F : radius * up * up;
	}

	/** After a living entity was drawn: undoes {@link #beforeRenderLiving}'s pose if it pushed one. */
	public static void afterRenderLiving(boolean pushed, PoseStack pose) {
		Posing before = POSING_BEFORE.poll();
		posing = before == null ? null : before.entity();
		posingBody = before == null ? null : before.body();
		if (pushed) {
			pose.popPose();
		}
	}

	/**
	 * At the end of every client tick (each loader's tick event): no render is in progress then, so nothing poses any
	 * more, even if a render never reached its end (an exception in a renderer, a cancelled render's missing Post).
	 */
	public static void endRenderPoses() {
		POSING_BEFORE.clear();
		posing = null;
		posingBody = null;
	}
}
