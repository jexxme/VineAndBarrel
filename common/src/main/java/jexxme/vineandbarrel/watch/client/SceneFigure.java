package jexxme.vineandbarrel.watch.client;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.client.Minecraft;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Someone brewing on the stage: a player figure in the viewer's own skin that holds the real items, swings its arm the
 * way a player does when using a block, turns to look at things and walks. Never added to the level; the scene ticks
 * and draws it, out where the scene's particles live.
 */
final class SceneFigure
{
	private static final float EYE_HEIGHT = 1.62F;
	/** How far a figure turns in a tick, in degrees: unhurried, like someone looking over. */
	private static final float TURN_SPEED = 18.0F;
	/** Entity ids for figures: negative, so they never meet a real entity's (item rendering seeds from the id). */
	private static int nextId = -1_000_000;

	private final Brewer entity;
	private Vec3 pos;
	private float yaw, headYaw, pitch;
	private @Nullable Vec3 lookAt;
	private Vec3 walkFrom;
	private Vec3 walkTarget = Vec3.ZERO;
	private int walkTicks, walked;
	private boolean broken;

	private int drinking;

	SceneFigure(ClientLevel level, String id, boolean viewerSkin, Vec3 pos, float yaw) {
		UUID uuid = UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
		this.entity = new Brewer(level, uuid, viewerSkin ? null : DefaultPlayerSkin.get(uuid));
		entity.setId(nextId--);
		this.pos = pos;
		this.walkFrom = pos;
		this.yaw = this.headYaw = yaw;
		apply();
		entity.setOldPosAndRot();
		entity.settleCloak();
		entity.yBodyRotO = entity.yBodyRot;
		entity.yHeadRotO = entity.yHeadRot;
	}

	AbstractClientPlayer entity() {
		return entity;
	}

	/** Where the figure stands now (stage coordinates). */
	Vec3 pos() {
		return pos;
	}

	void teleport(Vec3 to, float facing) {
		pos = walkFrom = to;
		walkTicks = walked = 0;
		yaw = headYaw = facing;
		lookAt = null;
		// a cape hangs still where the figure now stands (it would swing over from where it stood)
		apply();
		entity.settleCloak();
	}

	void walkTo(Vec3 to, int ticks) {
		walkFrom = pos;
		walkTicks = ticks;
		walked = 0;
		Vec3 delta = to.subtract(pos);
		if (ticks <= 0 || delta.horizontalDistanceSqr() < 1.0E-4) {
			pos = to;
			walkTicks = 0;
			return;
		}
		walkTarget = to;
	}

	/** Turns toward a point: at once (jumping to a step) or over a few ticks (playing). */
	void lookAt(Vec3 target, boolean instant) {
		lookAt = target;
		if (instant) {
			headYaw = yaw = yawTo(target);
			pitch = pitchTo(target);
		}
	}

	void hold(ItemStack stack) {
		entity.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
	}

	/** Raises what it holds to the mouth for a while. */
	void drink(int ticks) {
		drinking = ticks;
		entity.drinking = ticks > 0;
	}

	/** {@return whether it is drinking now} (the renderer raises the arm) */
	boolean isDrinking() {
		return drinking > 0;
	}

	void swing() {
		entity.swing(InteractionHand.MAIN_HAND);
	}

	/** One tick: walking, turning, and the entity's own tick (the arm swing, the legs while walking). */
	void tick() {
		entity.setOldPosAndRot();
		entity.tickCount++;
		entity.yBodyRotO = entity.yBodyRot;
		entity.yHeadRotO = entity.yHeadRot;
		if (walkTicks > 0) {
			walked++;
			float k = Math.min(1.0F, (float) walked / walkTicks);
			pos = walkFrom.lerp(walkTarget, k);
			Vec3 delta = walkTarget.subtract(walkFrom);
			float heading = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
			yaw = approach(yaw, heading);
			headYaw = approach(headYaw, heading);
			pitch = Mth.approach(pitch, 0.0F, 6.0F);
			if (walked >= walkTicks) {
				walkTicks = 0;
			}
		} else if (lookAt != null) {
			yaw = approach(yaw, yawTo(lookAt));
			headYaw = approach(headYaw, yawTo(lookAt));
			pitch = Mth.approach(pitch, pitchTo(lookAt), 8.0F);
		}
		if (drinking > 0) {
			// the head tips back while the drink goes down
			drinking--;
			pitch = Mth.approach(pitch, -25.0F, 8.0F);
			entity.drinking = drinking > 0;
		}
		apply();
		if (!broken) {
			try {
				entity.tick();
			} catch (RuntimeException e) {
				// a mod that hooks entity ticks and wants a real level: the figure stays, only stiff
				broken = true;
				VineAndBarrel.LOGGER.warn("Watch it: the figure can't tick outside a level", e);
			}
			apply();
		}
	}

	private void apply() {
		Vec3 at = ScenePlayer.PARTICLE_ORIGIN.add(pos);
		entity.setPos(at.x, at.y, at.z);
		entity.setYRot(yaw);
		entity.setYBodyRot(yaw);
		entity.setYHeadRot(headYaw);
		entity.setXRot(pitch);
	}

	private float yawTo(Vec3 target) {
		Vec3 d = target.subtract(pos);
		return (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
	}

	private float pitchTo(Vec3 target) {
		Vec3 d = target.subtract(pos.add(0.0, EYE_HEIGHT, 0.0));
		return (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
	}

	private static float approach(float from, float to) {
		return from + Mth.clamp(Mth.wrapDegrees(to - from), -TURN_SPEED, TURN_SPEED);
	}

	/**
	 * The figure itself: a player no server knows, wearing the viewer's skin (or a default one) with every layer of it
	 * shown. Its name is never drawn: name tags only show near the real camera, and it stands far from everything.
	 */
	static final class Brewer extends RemotePlayer
	{
		private static final byte ALL_LAYERS = 0x7F;
		private final @Nullable PlayerSkin skin;
		/** Raising what it holds to the mouth (SceneFigureDrinkMixin gives the arm its pose). */
		boolean drinking;

		Brewer(ClientLevel level, UUID uuid, @Nullable PlayerSkin skin) {
			super(level, new GameProfile(uuid, "Brewer"));
			this.skin = skin;
			entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, ALL_LAYERS);
			setNoGravity(true);
		}

		@Override
		public PlayerSkin getSkin() {
			if (skin != null) {
				return skin;
			}
			LocalPlayer player = Minecraft.getInstance().player;
			return player != null ? player.getSkin() : DefaultPlayerSkin.get(getUUID());
		}

		/** A cape (the viewer's own) hangs where the figure is, instead of trailing in from where it was made. */
		void settleCloak() {
			xCloak = xCloakO = getX();
			yCloak = yCloakO = getY();
			zCloak = zCloakO = getZ();
		}
	}
}
