package jexxme.vineandbarrel.fabric;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelItem;
import jexxme.vineandbarrel.barrel.AgingRecipes;
import jexxme.vineandbarrel.barrel.BarrelDispenserBehaviors;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.cauldron.VBCauldrons;
import jexxme.vineandbarrel.drink.Cheers;
import jexxme.vineandbarrel.entity.VBEntityEvents;
import jexxme.vineandbarrel.fabric.transfer.FabricItemAccess;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.registry.VBItems;
import jexxme.vineandbarrel.tipsy.TipsyEvents;
import jexxme.vineandbarrel.world.village.VillageData;
import jexxme.vineandbarrel.world.village.VillageStructures;
import jexxme.vineandbarrel.world.village.VillageType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.fabricmc.fabric.api.registry.VillagerInteractionRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;

/**
 * Vine & Barrel on Fabric: the common setup ({@link VineAndBarrel#init()}), the registration, then Fabric API's events
 * (and our Fabric mixins, see {@code vineandbarrel.fabric.mixins.json}) wired into the common hooks. What NeoForge
 * reads from data maps, biome modifiers and loot modifiers is registered here in code from the same values.
 */
public final class VineAndBarrelFabric implements ModInitializer
{
	private static boolean dispenserBehaviorsAdded;
	private static boolean leatherworkerStatesAdded;

	@Override
	public void onInitialize() {
		VineAndBarrel.init();
		FabricConfig.load();
		ServerConfigPayload.register();
		ModPresence.register();
		FabricRecipeBook.register();
		FabricPlatform.registerAll();
		VineAndBarrel.setup();
		// Our bases in cauldrons, on both sides so the client predicts the click. Each of our cauldrons put its own
		// interaction map into vanilla's list of them when its block was made, so other mods that reach every cauldron
		// reach ours
		VBCauldrons.addInteractions();
		FabricItemAccess.register();
		registerContent();
		registerEvents();
		// The GameTests (dev only), now that everything they name is registered
		FabricGameTestRegistration.registerAll();
	}

	/** What NeoForge takes from data (data maps, biome modifiers, loot modifiers), from the same values. */
	private static void registerContent() {
		// Copper kettles weather and take wax (NeoForge: data/neoforge/data_maps/block/oxidizables.json and waxables.json)
		WeatherState[] stages = WeatherState.values();
		for (WeatherState stage : stages) {
			OxidizableBlocksRegistry.registerWaxableBlockPair(KettleRegistry.KETTLES.get(stage).get(), KettleRegistry.WAXED_KETTLES.get(stage).get());
			OxidizableBlocksRegistry.registerWaxableBlockPair(KettleRegistry.PARTS.get(stage).get(), KettleRegistry.WAXED_PARTS.get(stage).get());
			if (stage.ordinal() + 1 < stages.length) {
				OxidizableBlocksRegistry.registerOxidizableBlockPair(KettleRegistry.KETTLES.get(stage).get(), KettleRegistry.KETTLES.get(stages[stage.ordinal() + 1]).get());
				OxidizableBlocksRegistry.registerOxidizableBlockPair(KettleRegistry.PARTS.get(stage).get(), KettleRegistry.PARTS.get(stages[stage.ordinal() + 1]).get());
			}
		}
		// Composter chances and burn times (NeoForge: the compostables and furnace_fuels data maps); a barrel with a
		// batch in it never burns (AbstractFurnaceBlockEntityMixin)
		VBItems.COMPOSTABLES.forEach((item, chance) -> CompostingChanceRegistry.INSTANCE.add(item.get(), chance));
		VBItems.FUELS.forEach((item, burnTime) -> FuelRegistry.INSTANCE.add(item.get(), burnTime));
		FuelRegistry.INSTANCE.add(BarrelRegistry.AGING_BARREL_ITEM.get(), AgingBarrelItem.BURN_TIME);
		// Farmers compost barley seeds (NeoForge: the villager_compostables data map)
		VillagerInteractionRegistries.registerCompostable(VBItems.BARLEY_SEEDS.get());
		// Wild crops: the same biome modifier files NeoForge reads, as the world's datapacks have them
		FabricWildCrops.register();
		// Our village house loot joins the vanilla house chests (NeoForge: global loot modifiers), also when a datapack
		// replaced the chest's table, as a loot modifier does
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			for (VillageType type : VillageType.values()) {
				if (key.equals(type.vanillaHouseLoot())) {
					// Our own table (loot_table/chests/village, VillageData.houseLoot) by reference, so a datapack's edit to it
					// counts (as with NeoForge's loot modifier, which rolls that table)
					table.withPool(LootPool.lootPool().add(NestedLootTable.lootTableReference(type.houseLoot())));
				}
			}
		});
	}

	private static void registerEvents() {
		// Tipsy (T1-T5). The entity tick, the finished drink and waking up come from our mixins (fabric.mixin)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !TipsyEvents.cancelsIncomingDamage(entity, source));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			// Fabric calls this before vanilla's spectator check; NeoForge's EntityInteract never fires for spectators
			if (player.isSpectator()) {
				return InteractionResult.PASS;
			}
			InteractionResult result = TipsyEvents.onEntityInteract(player, entity);
			return result != null ? result : InteractionResult.PASS;
		});
		// Slurred chat: typed chat messages only, through ServerGamePacketListenerImplMixin (as NeoForge's ServerChatEvent)
		// Foxes learn to pick grapes
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> VBEntityEvents.onJoinLevel(entity));
		// The server's life
		// The server settings per world, read again on /reload and sent to every player (NeoForge's SYNCED config)
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			FabricConfig.loadServer(server);
			ServerConfigPayload.sendToAll(server);
		});
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			FabricConfig.loadServer(server);
			FabricGameTestRegistration.onServerStarting();
			VillageStructures.onServerAboutToStart(server);
			if (!leatherworkerStatesAdded) {
				// A cauldron of a base stays a leatherworker's job site, like every vanilla cauldron: vanilla's own state
				// map, once the point-of-interest types exist (NeoForge: ExtendPoiTypesEvent)
				leatherworkerStatesAdded = true;
				PoiTypes.registerBlockStates(BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(PoiTypes.LEATHERWORKER), VBCauldrons.states());
				// The tavern keeper's job site: every Brew Kettle (NeoForge fills this map as the type is registered)
				PoiTypes.registerBlockStates(BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(jexxme.vineandbarrel.world.village.TavernKeeper.JOB_SITE),
					jexxme.vineandbarrel.world.village.TavernKeeper.kettleStates());
			}
			if (!dispenserBehaviorsAdded) {
				// After every mod's items exist (Fabric has no common setup event after all mods)
				dispenserBehaviorsAdded = true;
				BarrelDispenserBehaviors.register();
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(AgingRecipes::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Cheers.onServerStopped();
			FabricConfig.unloadServer();
		});
		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> AgingRecipes.onDatapackSync(player.level().getServer()));
	}
}
