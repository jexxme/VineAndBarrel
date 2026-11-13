package jexxme.vineandbarrel.tipsy;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rowdy (from Tipsy IV, playtest 1): now and then the arm swings on its own and throws a real punch at whoever is
 * right in front, fist strength whatever the hand holds. Never at pets, iron golems or armor stands; at other players
 * only where the server allows PvP. And the bar cuts you off: villagers and traders won't trade with you. Each has its
 * own server switch ({@code rowdy}, {@code cutOff}): the punches do real harm (a villager remembers, a golem gets
 * angry), the cut-off doesn't.
 */
public final class Rowdy
{
	public static final int ROWDY_LEVEL = TipsyLevels.first(level -> level.rules().rowdy());
	public static final int CUT_OFF_LEVEL = TipsyLevels.first(level -> level.rules().cutOff());
	/** A fist's damage (half a heart) and a fist's knockback, whatever is held. */
	public static final float PUNCH_DAMAGE = 1.0F;
	private static final double PUNCH_KNOCKBACK = 0.4;

	private Rowdy() {
	}

	/** Chance roll for one tick ({@link TipsyLevels.Rules#rowdySwingOneIn}): a wild swing about every 30 s at IV, every 15 s at V. */
	public static boolean rollsSwing(int level, RandomSource random) {
		int oneIn = TipsyLevels.at(level).rules().rowdySwingOneIn();
		return oneIn > 0 && random.nextInt(oneIn) == 0;
	}

	/** Once a tick on the server, for tipsy players (from {@link Tipsy#tick}: never while out cold or down after a trip). */
	static void tick(ServerPlayer player, TipsyLevels.Level at) {
		if (TipsyConfig.rowdy() && !player.isSpectator() && !player.isSleeping() && rollsSwing(at.level(), player.getRandom())) {
			swing(player);
		}
	}

	/** One wild swing: the arm, the whoosh, and a punch on whoever stands right in front (if anyone may be hit). */
	public static @Nullable Entity swing(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		player.swing(InteractionHand.MAIN_HAND, true);
		Entity target = inFront(player);
		if (target == null || !mayHit(player, target)) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8F, 0.8F);
			return null;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 1.0F, 0.9F);
		var source = level.damageSources().playerAttack(player);
		if (target.hurt(source, PUNCH_DAMAGE) && target instanceof LivingEntity living) {
			Vec3 look = player.getLookAngle();
			living.knockback(PUNCH_KNOCKBACK, -look.x, -look.z);
		}
		return target;
	}

	/** How far a player reaches an entity on 1.20.1 (no reach attribute yet): vanilla's 3 blocks, 6 in creative mode. */
	private static final double REACH = 3.0, CREATIVE_REACH = 6.0;

	/** {@return the entity under the crosshair within reach} */
	static @Nullable Entity inFront(ServerPlayer player) {
		double reach = player.isCreative() ? CREATIVE_REACH : REACH;
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));
		AABB area = player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, area,
			entity -> !entity.isSpectator() && entity.isPickable() && entity != player, reach * reach);
		return hit == null ? null : hit.getEntity();
	}

	/** Never pets, iron golems or armor stands; other players only where PvP is on. */
	public static boolean mayHit(ServerPlayer player, Entity target) {
		if (!(target instanceof LivingEntity) || target instanceof ArmorStand || target instanceof IronGolem) {
			return false;
		}
		if (target instanceof OwnableEntity pet && pet.getOwnerUUID() != null) {
			return false;
		}
		if (target instanceof Player other) {
			return player.canHarmPlayer(other);
		}
		return true;
	}

	/** {@return whether a villager or trader turns this player away: from Tipsy IV, with the cut-off on} */
	public static boolean cutOff(Player player) {
		return TipsyConfig.enabled() && TipsyConfig.cutOff() && TipsyLevels.at(Tipsy.getLevel(player)).rules().cutOff();
	}

	/** The villager shakes its head ("Hmm.", like when it has nothing to trade). */
	public static void refuse(AbstractVillager trader) {
		// Like a villager with nothing to sell: head shake and its "no" (setUnhappy is private to Villager)
		trader.setUnhappyCounter(40);
		trader.playSound(trader instanceof Villager ? SoundEvents.VILLAGER_NO : SoundEvents.WANDERING_TRADER_NO, 1.0F, 1.0F);
	}
}
