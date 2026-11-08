package jexxme.vineandbarrel.neoforge;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.drink.CheersEvent;
import jexxme.vineandbarrel.drink.DrankTogether;
import jexxme.vineandbarrel.barrel.AgingRecipes;
import jexxme.vineandbarrel.barrel.BarrelDispenserBehaviors;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.drink.Cheers;
import jexxme.vineandbarrel.entity.VBEntityEvents;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.neoforge.client.NeoForgeClient;
import jexxme.vineandbarrel.neoforge.transfer.NeoForgeItemAccess;
import jexxme.vineandbarrel.tipsy.TipsyEvents;
import jexxme.vineandbarrel.world.village.VillageStructures;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Vine & Barrel on NeoForge: the common setup ({@link VineAndBarrel#init()}), then NeoForge's events wired into the
 * common hooks. Everything the mod does itself lives in the common classes.
 */
@Mod(VineAndBarrel.MODID)
public final class VineAndBarrelNeoForge
{
	public VineAndBarrelNeoForge(IEventBus modEventBus, ModContainer modContainer) {
		NeoForgePlatform.init(modEventBus);
		VineAndBarrel.init();
		NeoForgeConfig.register(modContainer);
		AddFeaturesByFilterBiomeModifier.register(modEventBus);

		modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
			VineAndBarrel.setup();
			BarrelDispenserBehaviors.register();
		}));
		modEventBus.addListener(NeoForgeItemAccess::registerCapabilities);

		IEventBus game = NeoForge.EVENT_BUS;
		// 26.2: a barrel with a batch in it never burns (its burn time comes from the furnace_fuels data map)
		game.addListener(net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent.class, event -> {
			if (jexxme.vineandbarrel.barrel.AgingBarrelItem.fuelBlocked(event.getItemStack())) {
				event.setBurnTime(0);
			}
		});
		// Cheers! reaches bus listeners as the CheersEvent it always was
		DrankTogether.addListener(cheers -> game.post(new CheersEvent(cheers.level(), cheers.players())));
		// Tipsy (T1-T5): side effects and sync, the cures, the blackout, cut off at the bar, slurred chat
		game.addListener(EntityTickEvent.Post.class, event -> TipsyEvents.onEntityTick(event.getEntity()));
		game.addListener(LivingEntityUseItemEvent.Finish.class, event -> TipsyEvents.onFinishUsingItem(event.getEntity(), event.getItem()));
		game.addListener(PlayerWakeUpEvent.class, event -> TipsyEvents.onWakeUp(event.getEntity(), event.wakeImmediately(), event.updateLevel()));
		game.addListener(CanContinueSleepingEvent.class, event -> {
			if (event.getEntity() instanceof Player player && TipsyEvents.keepsSleeping(player)) {
				event.setContinueSleeping(true);
			}
		});
		game.addListener(LivingIncomingDamageEvent.class, event -> {
			if (TipsyEvents.cancelsIncomingDamage(event.getEntity(), event.getSource())) {
				event.setCanceled(true);
			}
		});
		game.addListener(PlayerInteractEvent.EntityInteract.class, event -> {
			InteractionResult result = TipsyEvents.onEntityInteract(event.getEntity(), event.getTarget());
			if (result != null) {
				event.setCanceled(true);
				event.setCancellationResult(result);
			}
		});
		game.addListener(ServerChatEvent.class, event -> {
			Component slurred = TipsyEvents.onChat(event.getPlayer(), event.getRawText());
			if (slurred != null) {
				event.setMessage(slurred);
			}
		});
		// Foxes learn to pick grapes
		game.addListener(EntityJoinLevelEvent.class, event -> VBEntityEvents.onJoinLevel(event.getEntity()));
		// The server's life: taverns and farm crops in the village pools, recipes checked and sent, cheers forgotten
		game.addListener(ServerAboutToStartEvent.class, event -> VillageStructures.onServerAboutToStart(event.getServer()));
		game.addListener(ServerStartedEvent.class, event -> AgingRecipes.onServerStarted(event.getServer()));
		game.addListener(ServerStoppedEvent.class, event -> Cheers.onServerStopped());
		game.addListener(OnDatapackSyncEvent.class, event -> {
			// Recipe viewers (JEI) need the brewing recipes; the barrel needs the aging ones to claim the right clicks
			event.sendRecipes(KettleRegistry.BREWING.get(), BarrelRegistry.AGING.get());
			AgingRecipes.onDatapackSync(event.getPlayerList().getServer());
		});

		if (FMLEnvironment.getDist() == Dist.CLIENT) {
			NeoForgeClient.init(modEventBus, modContainer);
		}
	}
}
