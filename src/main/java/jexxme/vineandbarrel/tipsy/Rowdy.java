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
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rowdy (Tipsy IV and up, playtest 1): now and then the arm swings on its own and throws a real punch at whoever is
 * right in front, fist strength whatever the hand holds. Never at pets, iron golems or armor stands; at other players
 * only where the server allows PvP. And the bar cuts you off: villagers and traders won't trade with you.
 */
public final class Rowdy
{
	public static final int ROWDY_LEVEL = 4;
	/** A fist's damage (half a heart) and a fist's knockback, whatever is held. */
	public static final float PUNCH_DAMAGE = 1.0F;
	private static final double PUNCH_KNOCKBACK = 0.4;

	private Rowdy() {
	}

	/** Chance roll for one tick: a wild swing about every 30 s at level 4 and every 15 s at level 5. */
	public static boolean rollsSwing(int level, RandomSource random) {
		return switch (level) {
			case 0, 1, 2, 3 -> false;
			case 4 -> random.nextInt(600) == 0;
			default -> random.nextInt(300) == 0;
		};
	}

	/** Once a tick on the server, for tipsy players. */
	static void tick(ServerPlayer player) {
		if (TipsyConfig.rowdy() && !player.isSpectator() && !player.isSleeping() && !Blackout.isOut(player)
				&& rollsSwing(Tipsy.getLevel(player), player.getRandom())) {
			swing(player);
		}
	}

	/** One wild swing: the arm, the whoosh, and a punch on whoever stands right in front (if anyone may be hit). */
	public static @Nullable Entity swing(ServerPlayer player) {
		ServerLevel level = player.level();
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Entity target = inFront(player);
		if (target == null || !mayHit(player, target)) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8F, 0.8F);
			return null;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 1.0F, 0.9F);
		var source = level.damageSources().playerAttack(player);
		if (target.hurtServer(level, source, PUNCH_DAMAGE) && target instanceof LivingEntity living) {
			Vec3 look = player.getLookAngle();
			living.knockback(PUNCH_KNOCKBACK, -look.x, -look.z, source, PUNCH_DAMAGE);
		}
		return target;
	}

	/** {@return the entity under the crosshair within reach} */
	static @Nullable Entity inFront(ServerPlayer player) {
		double reach = player.entityInteractionRange();
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
		if (target instanceof OwnableEntity pet && pet.getOwnerReference() != null) {
			return false;
		}
		if (target instanceof Player other) {
			return player.canHarmPlayer(other);
		}
		return true;
	}

	/** {@return whether a villager or trader turns this player away: Tipsy IV and up, with rowdy on} */
	public static boolean cutOff(Player player) {
		return TipsyConfig.enabled() && TipsyConfig.rowdy() && Tipsy.getLevel(player) >= ROWDY_LEVEL;
	}

	/** The villager shakes its head ("Hmm.", like when it has nothing to trade). */
	public static void refuse(AbstractVillager trader) {
		// Like a villager with nothing to sell: head shake and its "no" (setUnhappy is private to Villager)
		trader.setUnhappyCounter(40);
		trader.playSound(trader instanceof Villager ? SoundEvents.VILLAGER_NO : SoundEvents.WANDERING_TRADER_NO, 1.0F, 1.0F);
	}
}
