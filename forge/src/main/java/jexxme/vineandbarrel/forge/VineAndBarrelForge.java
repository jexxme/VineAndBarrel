package jexxme.vineandbarrel.forge;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelItem;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.drink.CheersEvent;
import jexxme.vineandbarrel.drink.DrankTogether;
import jexxme.vineandbarrel.barrel.AgingRecipes;
import jexxme.vineandbarrel.barrel.BarrelDispenserBehaviors;
import jexxme.vineandbarrel.cauldron.VBCauldrons;
import jexxme.vineandbarrel.drink.Cheers;
import jexxme.vineandbarrel.entity.VBEntityEvents;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.forge.client.ForgeClient;
import jexxme.vineandbarrel.forge.transfer.ForgeItemAccess;
import jexxme.vineandbarrel.registry.VBItems;
import jexxme.vineandbarrel.tipsy.TipsyEvents;
import jexxme.vineandbarrel.tipsy.TipsyRegistry;
import jexxme.vineandbarrel.world.village.VillageStructures;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.GameData;

import java.util.HashMap;
import java.util.Map;

/**
 * Vine & Barrel on Forge: the common setup ({@link VineAndBarrel#init()}), then Forge's events wired into the common
 * hooks. Everything the mod does itself lives in the common classes.
 */
@Mod(VineAndBarrel.MODID)
public final class VineAndBarrelForge
{
	private static final ResourceLocation FD_MILK_BOTTLE = FDCompat.id("milk_bottle");
	/** How deep this thread is inside {@code finishUsingItem} of Farmer's Delight's milk bottle (see {@code LivingEntityMixin}). */
	private static final ThreadLocal<Integer> FINISHING_MILK_BOTTLE = ThreadLocal.withInitial(() -> 0);
	/** Burn times by item, from {@link VBItems#FUELS} and the barrel (NeoForge 21.1: the furnace_fuels data map), built on first use. */
	private static volatile Map<Item, Integer> fuels;

	public VineAndBarrelForge() {
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		ForgePlatform.init(modEventBus);
		VineAndBarrel.init();
		ForgeConfig.register();
		AddFeaturesByFilterBiomeModifier.register(modEventBus);
		AddTableLootModifier.register(modEventBus);

		modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
			VineAndBarrel.setup();
			BarrelDispenserBehaviors.register();
			// Our bases in cauldrons: the buckets and bottles go into the cauldrons' interaction maps
			VBCauldrons.addInteractions();
			// Composter chances (NeoForge 21.1: the compostables data map): vanilla's own table on Forge 47
			VBItems.COMPOSTABLES.forEach((item, chance) -> ComposterBlock.COMPOSTABLES.put(item.get(), chance.floatValue()));
			addLeatherworkerCauldrons();
		}));
		// Hoppers and pipes at the kettle and the barrel
		ForgeItemAccess.init();
		// Copper kettles scrape, wax and are cleaned by lightning the vanilla way too
		ForgeCopper.init();

		IEventBus game = MinecraftForge.EVENT_BUS;
		// Forge rebuilds its block state table of job sites from the registry whenever a world is left, without ours
		game.addListener((ServerAboutToStartEvent event) -> addLeatherworkerCauldrons());
		// What burns (NeoForge 21.1: the furnace_fuels data map); a barrel with a batch in it never does
		game.addListener((FurnaceFuelBurnTimeEvent event) -> {
			ItemStack stack = event.getItemStack();
			if (AgingBarrelItem.fuelBlocked(stack)) {
				event.setBurnTime(0);
			} else {
				Integer burnTime = fuels().get(stack.getItem());
				if (burnTime != null) {
					event.setBurnTime(burnTime);
				}
			}
		});
		// Cheers! reaches bus listeners as the CheersEvent it always was
		DrankTogether.addListener(cheers -> game.post(new CheersEvent(cheers.level(), cheers.players())));
		// Tipsy (T1-T5): side effects and sync (after each entity's tick: ServerLevelMixin), the cures, the blackout, cut
		// off at the bar, slurred chat
		game.addListener((LivingEntityUseItemEvent.Finish event) -> TipsyEvents.onFinishUsingItem(event.getEntity(), event.getItem()));
		game.addListener((PlayerWakeUpEvent event) -> TipsyEvents.onWakeUp(event.getEntity(), event.wakeImmediately(), event.updateLevel()));
		// Forge 47 asks one thing of a sleeper each tick, whether the time of day lets it sleep on: exactly what a blackout
		// overrules (a bed that is gone still ends it, that is vanilla's own check elsewhere)
		game.addListener((SleepingTimeCheckEvent event) -> {
			if (TipsyEvents.keepsSleeping(event.getEntity())) {
				event.setResult(Event.Result.ALLOW);
			}
		});
		// Before anything else of the hit happens (Forge 47 has no later "incoming damage" event)
		game.addListener((LivingAttackEvent event) -> {
			if (TipsyEvents.cancelsIncomingDamage(event.getEntity(), event.getSource())) {
				event.setCanceled(true);
			}
		});
		game.addListener((PlayerInteractEvent.EntityInteract event) -> {
			InteractionResult result = TipsyEvents.onEntityInteract(event.getEntity(), event.getTarget());
			if (result != null) {
				event.setCanceled(true);
				event.setCancellationResult(result);
			}
		});
		// Farmer's Delight's milk bottle takes one random effect of those a milk bucket cures, after asking this event.
		// Tipsy isn't its to clear: the bottle lowers it by one level (#vineandbarrel:sobering_drinks, when the drink is
		// finished). A milk bucket still clears it, and so does everything else that removes the effect
		game.addListener((MobEffectEvent.Remove event) -> {
			if (FINISHING_MILK_BOTTLE.get() > 0 && event.getEffect() == TipsyRegistry.TIPSY.get()) {
				event.setCanceled(true);
			}
		});
		// Foxes learn to pick grapes
		game.addListener((EntityJoinLevelEvent event) -> VBEntityEvents.onJoinLevel(event.getEntity()));
		// The server's life: taverns and farm crops in the village pools, recipes checked and sent, cheers forgotten
		game.addListener((ServerAboutToStartEvent event) -> VillageStructures.onServerAboutToStart(event.getServer()));
		game.addListener((ServerStartedEvent event) -> AgingRecipes.onServerStarted(event.getServer()));
		game.addListener((ServerStoppedEvent event) -> Cheers.onServerStopped());
		// (This Minecraft sends every recipe to the clients itself: recipe viewers and the barrel read them there)
		game.addListener((OnDatapackSyncEvent event) -> AgingRecipes.onDatapackSync(event.getPlayerList().getServer()));

		if (FMLEnvironment.dist == Dist.CLIENT) {
			ForgeClient.init(modEventBus);
		}
	}

	/**
	 * A cauldron of a base stays a leatherworker's job site, like every vanilla cauldron (NeoForge 21.1:
	 * {@code ExtendPoiTypesEvent}). Forge 47 has no such event: the states go straight into the table
	 * {@code PoiTypes.forState} reads on Forge. Nothing is replaced, so calling it again is harmless.
	 */
	private static void addLeatherworkerCauldrons() {
		PoiType leatherworker = BuiltInRegistries.POINT_OF_INTEREST_TYPE.get(PoiTypes.LEATHERWORKER);
		if (leatherworker != null) {
			Map<BlockState, PoiType> jobSites = GameData.getBlockStatePointOfInterestTypeMap();
			VBCauldrons.states().forEach(state -> jobSites.putIfAbsent(state, leatherworker));
		}
	}

	private static Map<Item, Integer> fuels() {
		Map<Item, Integer> result = fuels;
		if (result == null) {
			result = new HashMap<>();
			for (var fuel : VBItems.FUELS.entrySet()) {
				result.put(fuel.getKey().get(), fuel.getValue());
			}
			result.put(BarrelRegistry.AGING_BARREL_ITEM.get(), AgingBarrelItem.BURN_TIME);
			fuels = result;
		}
		return result;
	}

	public static boolean isMilkBottle(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(FD_MILK_BOTTLE);
	}

	/** Called by {@code LivingEntityMixin} around the milk bottle's {@code finishUsingItem}: +1 going in, -1 coming out. */
	public static void finishingMilkBottle(int change) {
		FINISHING_MILK_BOTTLE.set(FINISHING_MILK_BOTTLE.get() + change);
	}
}
