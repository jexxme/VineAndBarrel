package jexxme.vineandbarrel.tipsy;

import jexxme.vineandbarrel.advancement.VBAdvancements;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import org.jspecify.annotations.Nullable;

/**
 * Server-side Tipsy hooks: the side effects and sync, the cures, and slurred chat. Each loader calls these from its own
 * events (NeoForge: {@code NeoForgeEvents}; Fabric: Fabric API callbacks and our Fabric mixins).
 */
public final class TipsyEvents
{
	private TipsyEvents() {
	}

	/** After every entity tick. */
	public static void onEntityTick(Entity tickingEntity) {
		if (!(tickingEntity instanceof LivingEntity entity) || entity.level().isClientSide()) {
			return;
		}
		if (entity instanceof ServerPlayer player && TipsyRegistry.BLACKOUT.has(player)) {
			Blackout.tick(player);
		}
		boolean tipsy = entity.hasEffect(TipsyRegistry.TIPSY.get());
		if (tipsy && !TipsyConfig.enabled()) {
			// Non-alcoholic mode switched on mid-game (or the effect came from a command): nobody stays tipsy
			entity.removeEffect(TipsyRegistry.TIPSY.get());
			tipsy = false;
		}
		// Tipsy, or was a tick ago: the last tick lets go of the side effects, the sync and the night's bookkeeping
		if (tipsy || TipsyRegistry.HEAVY_EFFECTS_OWNED.has(entity) || TipsyRegistry.LEGS.has(entity) || TipsyRegistry.DRINK_BUFFS.has(entity)
				|| (entity instanceof Player && TipsyRegistry.SYNCED_LEVEL.getExisting(entity) != null)) {
			Tipsy.tick(entity);
		}
	}

	/** T3: a water bottle (or anything else in {@code #vineandbarrel:sobering_drinks}) lowers Tipsy by one. */
	public static void onFinishUsingItem(LivingEntity entity, ItemStack item) {
		if (Tipsy.isSoberingDrink(item)) {
			Tipsy.lowerLevel(entity);
		}
	}

	/**
	 * T2 "sleep it off": sleeping through the night clears Tipsy, the way it resets the phantom timer. Vanilla wakes
	 * sleepers with (not forced, no list update) only when the night was skipped; leaving the bed early doesn't count.
	 */
	public static void onWakeUp(Player player, boolean wakeImmediately, boolean updateLevel) {
		// A blackout's bed isn't a night's sleep, even when the others skip the night meanwhile
		if (!wakeImmediately && !updateLevel && !Blackout.isOut(player)) {
			if (Tipsy.getLevel(player) > 0) {
				VBAdvancements.award(VBAdvancements.SLEPT_IT_OFF, player);
			}
			Tipsy.clear(player);
		}
	}

	/**
	 * A blackout's bed is lain in by day too (vanilla would wake you at once); it ends at its own time, well before
	 * vanilla's deep sleep, so the night is never skipped. True keeps the player asleep although the bed's rule says no
	 * (NeoForge: {@code CanContinueSleepingEvent}; Fabric: our {@code PlayerMixin}). Only the time of day is overruled:
	 * a bed that is gone still ends it.
	 */
	public static boolean keepsSleeping(Player player) {
		return Blackout.isOut(player) && player.getSleepingPos()
			.map(bed -> player.level().getBlockState(bed).getBlock() instanceof BedBlock).orElse(false);
	}

	/**
	 * Out cold, nobody can beat you up and you can't get hurt falling over (Jexxme): while blacked out, damage from a
	 * mob, a player or anything they shot or set off is cancelled, and so is fall damage. Everything else still counts,
	 * the way it would for anyone lying there: {@code /kill}, the void, fire and lava, drowning, starving (the wake-up
	 * spot is checked to be safe, so these only come from where you blacked out). True cancels the damage.
	 */
	public static boolean cancelsIncomingDamage(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Player player) || !Blackout.isOut(player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return source.is(DamageTypeTags.IS_FALL) || source.getEntity() != null || source.getDirectEntity() != null;
	}

	/**
	 * Cut off at the bar: at Tipsy IV and up, villagers and traders won't trade (checked on both sides, so no flicker).
	 * Returns the result to cancel the interaction with, or null to let it happen.
	 */
	public static @Nullable InteractionResult onEntityInteract(Player player, Entity target) {
		if (target instanceof AbstractVillager trader && Rowdy.cutOff(player)) {
			if (!player.level().isClientSide()) {
				Rowdy.refuse(trader);
			}
			return InteractionResult.SUCCESS;
		}
		return null;
	}

	/** The chat message a player's text becomes (slurred), or null to leave it as it is. */
	public static @Nullable Component onChat(ServerPlayer player, String raw) {
		long seed = player.level().getGameTime() ^ player.getUUID().getLeastSignificantBits();
		String slurred = Tipsy.slurChat(player, raw, seed);
		return slurred.equals(raw) ? null : Component.literal(slurred);
	}
}
