package jexxme.vineandbarrel.tipsy;

import jexxme.vineandbarrel.advancement.VBAdvancements;
import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Server-side Tipsy hooks: the side effects and sync, the cures, and slurred chat. */
@EventBusSubscriber(modid = VineAndBarrel.MODID)
public final class TipsyEvents
{
	private TipsyEvents() {
	}

	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) {
			return;
		}
		if (entity instanceof ServerPlayer player && player.hasData(TipsyRegistry.BLACKOUT)) {
			Blackout.tick(player);
		}
		boolean tipsy = entity.hasEffect(TipsyRegistry.TIPSY);
		if (tipsy && !TipsyConfig.enabled()) {
			// Non-alcoholic mode switched on mid-game (or the effect came from a command): nobody stays tipsy
			entity.removeEffect(TipsyRegistry.TIPSY);
			tipsy = false;
		}
		if (tipsy || entity.hasData(TipsyRegistry.HEAVY_EFFECTS_OWNED)
				|| (entity instanceof Player && entity.getExistingDataOrNull(TipsyRegistry.SYNCED_LEVEL) != null)) {
			Tipsy.tick(entity);
		}
	}

	/** T3: a water bottle (or anything else in {@code #vineandbarrel:sobering_drinks}) lowers Tipsy by one. */
	@SubscribeEvent
	public static void onFinishUsingItem(LivingEntityUseItemEvent.Finish event) {
		if (Tipsy.isSoberingDrink(event.getItem())) {
			Tipsy.lowerLevel(event.getEntity());
		}
	}

	/**
	 * T2 "sleep it off": sleeping through the night clears Tipsy, the way it resets the phantom timer. Vanilla wakes
	 * sleepers with (not forced, no list update) only when the night was skipped; leaving the bed early doesn't count.
	 */
	@SubscribeEvent
	public static void onWakeUp(PlayerWakeUpEvent event) {
		if (!event.wakeImmediately() && !event.updateLevel()) {
			if (Tipsy.getLevel(event.getEntity()) > 0) {
				VBAdvancements.award(VBAdvancements.SLEPT_IT_OFF, event.getEntity());
			}
			Tipsy.clear(event.getEntity());
		}
	}

	/** Out cold: nothing hurts while the screen is black (the wake-up spot is checked to be safe, too). */
	@SubscribeEvent
	public static void onIncomingDamage(LivingIncomingDamageEvent event) {
		if (event.getEntity() instanceof Player player && Blackout.isOut(player)) {
			event.setCanceled(true);
		}
	}

	/** Cut off at the bar: at Tipsy IV and up, villagers and traders won't trade (checked on both sides, so no flicker). */
	@SubscribeEvent
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (event.getTarget() instanceof AbstractVillager trader && Rowdy.cutOff(event.getEntity())) {
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.SUCCESS);
			if (!event.getLevel().isClientSide()) {
				Rowdy.refuse(trader);
			}
		}
	}

	@SubscribeEvent
	public static void onChat(ServerChatEvent event) {
		ServerPlayer player = event.getPlayer();
		long seed = player.level().getGameTime() ^ player.getUUID().getLeastSignificantBits();
		String raw = event.getRawText();
		String slurred = Tipsy.slurChat(player, raw, seed);
		if (!slurred.equals(raw)) {
			event.setMessage(Component.literal(slurred));
		}
	}
}
