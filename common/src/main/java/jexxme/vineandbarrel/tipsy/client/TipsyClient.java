package jexxme.vineandbarrel.tipsy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.BlackoutPose;
import jexxme.vineandbarrel.tipsy.Rowdy;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.TipsyGait;
import jexxme.vineandbarrel.tipsy.TipsySounds;
import jexxme.vineandbarrel.tipsy.TipsyInfo;
import jexxme.vineandbarrel.tipsy.TipsyLegs;
import jexxme.vineandbarrel.tipsy.TipsyLevels;
import jexxme.vineandbarrel.tipsy.TipsyMotion;
import jexxme.vineandbarrel.tipsy.TipsyNod;
import jexxme.vineandbarrel.tipsy.TipsyRegistry;
import jexxme.vineandbarrel.tipsy.TipsyWobble;
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import net.minecraft.ChatFormatting;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client side of Tipsy: what this player sees (the hiccup's flick, a stumble's dip, a trip face down), what they feel
 * in their keys (the drunk walk, held still while down after a trip) and how every tipsy player looks to the others
 * (the sway, the jolts, the trip). Tipsy never moves the own view on its own: only with the body. The math lives in
 * {@link TipsyWobble}, {@link TipsyGait} and {@link TipsyMotion} so it is tested headless. Each loader calls these from
 * its own client events (NeoForge: {@code NeoForgeClient}; Fabric: Fabric API callbacks and our client mixins).
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
		if (effect.is(TipsyRegistry.HANGOVER)) {
			if (tooltip.isEmpty()) {
				tooltip.add(effect.getEffect().value().getDisplayName());
			}
			for (String line : List.of("glare", "wince", "hunger")) {
				tooltip.add(Component.translatable("effect.vineandbarrel.hangover.info." + line).withStyle(ChatFormatting.RED));
			}
			return;
		}
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
		if (level != seenLevel) {
			// a new world, or none (disconnected): nothing carries over from the last one
			seenLevel = level;
			MOVES.clear();
			DrunkLegs.reset();
			DrunkEyes.reset();
			DrunkEars.reset();
			HangoverClient.reset();
		}
		if (level == null) {
			return;
		}
		for (AbstractClientPlayer other : level.players()) {
			track(other);
		}
		if (player != null) {
			heartbeat(player);
			DrunkLegs.tick(player);
			DrunkEyes.tick(player);
			DrunkEars.tick(player);
			HangoverClient.tick(player);
		}
	}

	/** The world this client was in last tick. */
	private static @Nullable ClientLevel seenLevel;

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
			// in your own head: not placed in the world, so the woolly ears (DrunkEars) leave it clear
			Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(TipsyRegistry.HEARTBEAT_SOUND.value().getLocation(),
				SoundSource.PLAYERS, (float) (TipsySounds.HEARTBEAT_VOLUME * volume), 0.95F + player.getRandom().nextFloat() * 0.1F,
				SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
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
		/** The body's lean into the curve it walks (degrees, positive to its left), this tick and last, and where it walked a tick ago. */
		float lean, leanO, headingO;

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
		boolean own = player == Minecraft.getInstance().player;
		if (counts.stumbles() != moves.seen.stumbles()) {
			moves.stumbleAt = now;
			moves.left = counts.stumbleLeft();
			if (own) {
				// the stumble's small give to the side, from where this player really is now
				push(player, TipsyLegs.ownStumble(player, player.position().subtract(player.xo, player.yo, player.zo), counts.stumbleLeft()));
			}
		}
		if (own && counts.lunges() != moves.seen.lunges()) {
			// a wild swing's step forward
			push(player, Rowdy.ownLunge(player));
		}
		if (counts.trips() != moves.seen.trips()) {
			moves.tripAt = now;
		}
		moves.seen = counts;
		// The body leans into the curve it walks, as every client sees it move (nothing extra on the wire)
		moves.leanO = moves.lean;
		float heading = TipsyGait.heading(player.getX() - player.xo, player.getZ() - player.zo, moves.headingO);
		float turning = TipsyGait.turning(moves.headingO, heading);
		moves.headingO = heading;
		Integer level = TipsyRegistry.SYNCED_LEVEL.getExisting(player);
		float most = level == null ? 0.0F : TipsyLevels.at(level).legs().swayDegrees() * BODY_CURVE_LEAN;
		float target = most <= 0.0F || !player.onGround() ? 0.0F : TipsyGait.leanTarget(turning, most);
		moves.lean = TipsyGait.ease(moves.lean, target);
	}

	/** Adds {@code push} to how this client's own player moves (it moves here, not on the server). */
	private static void push(Player player, Vec3 push) {
		if (push != Vec3.ZERO) {
			player.setDeltaMovement(player.getDeltaMovement().add(push));
		}
	}

	/** Of the level's sway, how far the body others see leans into a curve it walks. */
	private static final float BODY_CURVE_LEAN = 0.7F;

	/** {@return how far this player's body leans this frame (degrees, positive to its left): into its curve, and its standing sway} */
	public static float bodyLean(Player player, float partial) {
		Integer level = TipsyRegistry.SYNCED_LEVEL.getExisting(player);
		float most = level == null ? 0.0F : TipsyLevels.at(level).legs().swayDegrees();
		Moves moves = moves(player);
		float moving = moves == null ? 0.0F : Mth.lerp(partial, moves.leanO, moves.lean);
		// Walking, the steps carry the sway: the slow loop of standing fades to a little
		float standing = 1.0F - 0.7F * Mth.clamp(player.walkAnimation.speed(partial) * 1.5F, 0.0F, 1.0F);
		// going under, the body sways hard, standing or not
		standing = Math.max(standing, 2.5F * jexxme.vineandbarrel.tipsy.GoingUnder.under(player, partial));
		return moving + standing * TipsyGait.standingSway(most, player.tickCount + partial, TipsyGait.phase(player.getId()));
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

	/** The camera's roll for a lean to the left (Minecraft's roll turns the picture the other way round). */
	private static final float LEAN_ROLL = -1.0F;

	/** {@return how much Tipsy may move this player's own view: their "drunk screen" setting, 0 to 1} */
	private static float intensity() {
		return (float) Math.clamp(TipsyConfig.drunkScreen(), 0.0, 1.0);
	}

	/**
	 * What to add to the camera's angles this frame ({@link TipsyWobble.Angles#NONE} for nothing): only what the body
	 * does (the lean of the drunk walk, a hiccup's flick, a stumble's dip, a trip's shake and its look at the ground) and
	 * the blackout's roll; Tipsy never turns the view on its own. The one camera hook both loaders call.
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
			(LEAN_ROLL * DrunkLegs.lean(player, partial) + Math.signum(lurch) * TipsyMotion.STUMBLE_ROLL * Math.abs(lurch) + shake * Mth.sin(trip * 2.7F)) * intensity,
			(TipsyMotion.STUMBLE_DIP * Math.abs(lurch) - TipsyMotion.HICCUP_FLICK * jolt + shake * 0.6F * Mth.cos(trip * 3.1F)) * intensity,
			0.0F);
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		if (firstPerson) {
			// Nodding off: the head goes down, and comes up with a jerk
			float nod = TipsyNod.nod(player, partial);
			angles = angles.plus(0.0F, (nod >= 0.0F ? TipsyNod.HEAD_DROP * nod : TipsyNod.JERK * nod) * intensity, 0.0F);
		}
		angles = angles.scaled(distortion(minecraft));
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
	 * Right after the player's input was read each tick: out cold or down after a trip, the keys do nothing; otherwise the
	 * drunk walk ({@link DrunkLegs}): the legs answer late and weave, and you keep correcting.
	 */
	public static void onMovementInput(Player player, Input input) {
		BlackoutClient.onMovementInput(player, input);
		if (TipsyMotion.tripHolds(tripElapsed(player, 0.0F))) {
			BlackoutClient.holdStill(input);
			return;
		}
		DrunkLegs.onMovementInput(player, input);
	}

	/**
	 * {@return a chat message as this player says it, right before it is sent (and signed: secure chat stays valid)}
	 * Worse with each level ({@link Tipsy#slurChat}), when the server allows it; commands are sent as typed, and links
	 * and mentions in a message stay as typed. Other players' messages arrive as they said them.
	 */
	public static String ownChat(String message) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? message : Tipsy.ownChat(player, message, player.getRandom().nextLong());
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
	 * HUD: the lids sinking as you nod off ({@link TipsyNod}). Vanilla's Darkness Pulsing slider scales them.
	 */
	public static void renderVision(GuiGraphics gui, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || Blackout.isOut(player)) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float nod = TipsyNod.nod(player, partial);
		float darkness = minecraft.options.darknessEffectScale().get().floatValue();
		if (nod > 0.0F && darkness > 0.0F) {
			BlackoutClient.drawLids(gui, TipsyNod.LIDS * nod * darkness);
		}
		// the morning after: a wince at a loud noise
		HangoverClient.render(gui, partial, darkness);
	}

	// ------------------------------------------------------------------ T1: how others see you

	/**
	 * How a player's body is posed for {@link #beforeRenderLiving}: the sway level (0 = none), a blacked-out player on
	 * the ground (falling on the face, lying, getting up; null if not), a hiccup's jolt, a stumble's lurch (-1 right to 1
	 * left), how far a trip has them down on the face, the body's lean (degrees, positive to its left) and its slow sway
	 * back and forth (degrees, positive forward), and how far into nodding off it is.
	 */
	public record BodyPose(int swayLevel, BlackoutPose.@Nullable Body blackout, float jolt, float lurch, float fallen, float lean, float forward, float nod)
	{
	}

	/**
	 * The player being drawn right now and how the model poses head and limbs for them ({@link #poseBlackout}), from
	 * {@link #beforeRenderLiving} to {@link #afterRenderLiving}, at the same moment as the body (1.21.1 has no render
	 * states to carry it): their blackout on the ground ({@link BlackoutPose#body}), how far the arms are out for balance
	 * in a stumble (-1 lurching right to 1 left), how far a Rowdy swing's arm is drawn back (0 to 1), and how far into
	 * nodding off they are.
	 */
	private static @Nullable Posing posing;
	/**
	 * What was posing before each render still in progress: an entity drawn inside another one's render (by another
	 * mod, between its Pre and its model's posing) hands the outer one's back when it is done.
	 */
	private static final ArrayDeque<Posing> POSING_BEFORE = new ArrayDeque<>();
	/** What {@link #POSING_BEFORE} holds for "nothing was posing" (the deque takes no nulls). */
	private static final Posing NOBODY = new Posing(null, null, 0.0F, 0.0F, 0.0F);

	private record Posing(@Nullable LivingEntity entity, BlackoutPose.@Nullable Body body, float balance, float windup, float nod)
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
		float forward = sway > 0 ? TipsyGait.standingSwayForward(TipsyLevels.at(sway).legs().swayDegrees(), player.tickCount + partial,
			TipsyGait.phase(player.getId())) : 0.0F;
		return new BodyPose(sway, blackout, jolt, lurch, fallen, sway > 0 ? bodyLean(player, partial) : 0.0F, forward, TipsyNod.nod(player, partial));
	}

	/** The model's sideways rotation for a lean to the body's left (as a stumble's lurch to the left: positive). */
	private static final float BODY_LEAN_ROLL = 1.0F;

	/**
	 * Before a living entity is drawn (as late as possible, after other mods): lays a blacked-out player on the ground
	 * (and gets them up again), lays a tripped one on the face, or leans a tipsy one (with a hiccup's bob and a stumble's
	 * lurch), by its {@link #bodyPose} at this partial tick (1.21.1 has no render states: the entity is asked right here),
	 * and hands the model what it poses on the limbs ({@link #poseBlackout}). Returns true if a pose was pushed, which
	 * {@link #afterRenderLiving} must then pop.
	 */
	public static boolean beforeRenderLiving(LivingEntity entity, float partial, PoseStack stack) {
		POSING_BEFORE.push(posing == null ? NOBODY : posing);
		posing = null;
		BodyPose pose = bodyPose(entity, partial);
		float windup = entity instanceof Player player ? jexxme.vineandbarrel.tipsy.Rowdy.windup(player, partial) : 0.0F;
		if (pose != null || windup > 0.0F) {
			// What the model poses on head and limbs: the blackout on the ground, the arms out in a stumble, a Rowdy swing's
			// arm drawn back, the chin sinking as they nod off
			posing = new Posing(entity, pose == null ? null : pose.blackout(), pose == null || pose.fallen() > 0.0F ? 0.0F : pose.lurch(), windup,
				pose == null ? 0.0F : pose.nod());
		}
		if (pose == null || entity.hasPose(Pose.SLEEPING)) {
			return false;
		}
		float scale = entity.getScale();
		float facing = 180.0F - bodyRot(entity, partial);
		BlackoutPose.Body blackout = pose.blackout();
		if (blackout != null) {
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
		// The body tips onto each foot as it lands, in step with the legs, and leans into its curve (the walk as the game
		// draws it: none riding or dead)
		boolean walks = !entity.isPassenger() && entity.isAlive();
		float step = pose.swayLevel() > 0 && walks ? TipsyGait.stepRoll(entity.walkAnimation.position(partial), entity.walkAnimation.speed(partial),
			TipsyLevels.at(pose.swayLevel()).legs().stepRoll()) : 0.0F;
		float sway = BODY_LEAN_ROLL * (pose.lean() + step) + TipsyMotion.STUMBLE_LEAN * pose.lurch();
		// Nodding off, the body sags forward over the feet, and straightens with the jerk
		float sag = pose.nod() > 0.0F ? TipsyNod.SAG * pose.nod() : 0.25F * TipsyNod.SAG * pose.nod();
		stack.pushPose();
		// A hiccup jerks the shoulders up; the lean is sideways around the feet, relative to where the body faces
		stack.translate(0.0F, TipsyMotion.HICCUP_BOB * pose.jolt() * scale, 0.0F);
		stack.mulPose(Axis.YP.rotationDegrees(facing));
		stack.mulPose(Axis.ZP.rotationDegrees(sway));
		// (facing this way, the body's front is -Z: a forward sag turns the top toward it)
		stack.mulPose(Axis.XP.rotationDegrees(-(sag + pose.forward())));
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
	 * the hands hold come along. A tipsy one standing has the chin sinking as they nod off, the arms out for balance in a
	 * stumble and the arm drawn back for a Rowdy swing. Called by {@code jexxme.vineandbarrel.mixin.HumanoidModelMixin}
	 * with whether it posed this model's head the last time; {@return whether it posed it now}
	 */
	public static boolean poseBlackout(HumanoidModel<?> model, LivingEntity entity, boolean posedBefore) {
		Posing now = posing != null && entity == posing.entity() ? posing : null;
		BlackoutPose.Body body = now == null ? null : now.body();
		if (body == null) {
			if (posedBefore) {
				// The game never sets the head's roll and place itself (one model draws every player): back to its own
				restoreHead(model);
			}
			if (now != null) {
				nod(model, now.nod());
				balance(model, now.balance());
				windup(model, now.windup());
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

	/**
	 * Nodding off: the chin sinks to the chest, and comes up with the jerk (the head's pitch as the game set it from where
	 * the player looks, kept within a straight look up or down; later versions do it on the render state's pitch).
	 */
	private static void nod(HumanoidModel<?> model, float nod) {
		if (nod == 0.0F) {
			return;
		}
		float pitch = model.head.xRot * Mth.RAD_TO_DEG + (nod > 0.0F ? TipsyNod.CHIN * nod : 0.2F * TipsyNod.CHIN * nod);
		model.head.xRot = Mth.clamp(pitch, -90.0F, 90.0F) * Mth.DEG_TO_RAD;
		model.hat.copyFrom(model.head);
	}

	/** In a stumble the arms fly out for balance (radians, fully out), the one on the side it lurches to higher. */
	private static final float ARMS_OUT = 1.1F, ARM_HIGHER = 0.35F;

	/**
	 * The hand in your own view as a Rowdy swing draws back ({@code jexxme.vineandbarrel.mixin.ItemInHandRendererMixin}):
	 * back toward you, down and out, turned a little, so you feel the fist being cocked; the game's own swing then
	 * throws it forward. A pose, not a distortion: always shown.
	 */
	public static void windupHand(PoseStack pose, boolean rightArm, float partial) {
		LocalPlayer player = Minecraft.getInstance().player;
		float windup = player == null ? 0.0F : jexxme.vineandbarrel.tipsy.Rowdy.windup(player, partial);
		if (windup <= 0.0F) {
			return;
		}
		float side = rightArm ? 1.0F : -1.0F;
		pose.translate(side * 0.16F * windup, -0.22F * windup, 0.3F * windup);
		pose.mulPose(Axis.YP.rotationDegrees(side * 25.0F * windup));
		pose.mulPose(Axis.ZP.rotationDegrees(-side * 15.0F * windup));
	}

	/** A Rowdy swing drawn back: the right arm cocked back and out (radians at the most). */
	private static final float WINDUP_BACK = 1.3F, WINDUP_OUT = 0.35F;

	private static void windup(HumanoidModel<?> model, float windup) {
		if (windup <= 0.0F) {
			return;
		}
		model.rightArm.xRot = Mth.lerp(windup, model.rightArm.xRot, WINDUP_BACK);
		model.rightArm.zRot += WINDUP_OUT * windup;
	}

	/** A stumble's catch: both arms out to the sides for balance, as far as the lurch goes (-1 right to 1 left). */
	private static void balance(HumanoidModel<?> model, float lurch) {
		if (lurch == 0.0F) {
			return;
		}
		float out = ARMS_OUT * Math.abs(lurch);
		// (out to the side: the right arm's roll goes up, the left's down)
		model.rightArm.zRot += out * (1.0F + (lurch < 0.0F ? ARM_HIGHER : -ARM_HIGHER));
		model.leftArm.zRot -= out * (1.0F + (lurch > 0.0F ? ARM_HIGHER : -ARM_HIGHER));
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
		posing = before == null || before == NOBODY ? null : before;
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
	}
}
