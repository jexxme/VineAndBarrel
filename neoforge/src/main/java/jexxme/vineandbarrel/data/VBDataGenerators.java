package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.advancement.VBAdvancementGenerator;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import jexxme.vineandbarrel.world.village.VillageData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import jexxme.vineandbarrel.platform.registry.DeferredBlock;
import jexxme.vineandbarrel.platform.registry.DeferredItem;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Data generation for 26.2 ({@code ./gradlew runData}, writes {@code src/generated/resources}), set up like FD's:
 * villager trades are datapack registry entries; recipes, loot tables, advancements, tags, data maps and models are classic providers. Worldgen
 * (features, placed features, biome modifiers) and the language file are hand-written in {@code src/main/resources}.
 * Client-only because the data run is a client data run and the models need client classes.
 */
@EventBusSubscriber(modid = VineAndBarrel.MODID, value = Dist.CLIENT)
public final class VBDataGenerators
{
	/**
	 * The blocks and items whose block states, models and loot are generated here (the crops, M1). Other content keeps
	 * hand-written resources, so the providers only validate this set. The kettles' block states and per-stage models
	 * are generated too ({@link VBModels}), on their hand-written template models.
	 */
	static final List<DeferredBlock<? extends Block>> CROP_BLOCKS = List.of(VBBlocks.GRAPE_BUSH, VBBlocks.WHITE_GRAPE_BUSH, VBBlocks.HOP_BUSH,
			VBBlocks.GRAPE_VINE_ON_ROPE, VBBlocks.WHITE_GRAPE_VINE_ON_ROPE, VBBlocks.HOP_VINE_ON_ROPE, VBBlocks.TRELLIS, VBBlocks.GRAPE_VINE_ON_TRELLIS,
			VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS, VBBlocks.HOP_VINE_ON_TRELLIS, VBBlocks.BARLEY, VBBlocks.WILD_GRAPES,
			VBBlocks.WILD_WHITE_GRAPES, VBBlocks.WILD_HOPS, VBBlocks.WILD_BARLEY, VBBlocks.GRAPE_CRATE, VBBlocks.WHITE_GRAPE_CRATE,
			VBBlocks.HOPS_CRATE, VBBlocks.BARLEY_BALE);
	static final List<DeferredItem<? extends Item>> CROP_ITEMS = List.of(VBItems.RED_GRAPES, VBItems.WHITE_GRAPES, VBItems.HOPS, VBItems.TRELLIS,
			VBItems.BARLEY_SEEDS, VBItems.BARLEY, VBItems.WILD_GRAPES, VBItems.WILD_WHITE_GRAPES, VBItems.WILD_HOPS, VBItems.WILD_BARLEY,
			VBItems.GRAPE_CRATE, VBItems.WHITE_GRAPE_CRATE, VBItems.HOPS_CRATE, VBItems.BARLEY_BALE);

	private VBDataGenerators() {
	}

	@SubscribeEvent
	public static void gatherData(GatherDataEvent.Client event) {
		event.createProvider((output, registries) -> new LootTableProvider(output, Set.of(), List.of(
				new LootTableProvider.SubProviderEntry(VBBlockLoot::new, LootContextParamSets.BLOCK),
				new LootTableProvider.SubProviderEntry(VBHarvestLoot::new, LootContextParamSets.BLOCK_INTERACT),
				new LootTableProvider.SubProviderEntry(VillageData.ChestLoot::new, LootContextParamSets.CHEST)), registries));
		event.createDatapackRegistryObjects(new RegistrySetBuilder().add(Registries.VILLAGER_TRADE, VillageData::bootstrapTrades));
		event.createProvider((output, registries) -> new AdvancementProvider(output, registries, List.of(new VBAdvancementGenerator())));
		event.createProvider(VBRecipes.Runner::new);
		event.createBlockAndItemTags(VBTagProviders.Blocks::new, (output, lookup, blockTags) -> new VBTagProviders.Items(output, lookup));
		event.createProvider(VBTagProviders.Biomes::new);
		event.createProvider(DataMaps::new);
		event.createProvider(VBModels::new);
		event.createProvider(VBTradeTags::new);
		event.createProvider(VBLootModifiers::new);
		event.createProvider(FdRecipes::new);
	}

	/**
	 * Composter chances ({@link VBItems#COMPOSTABLES}) and burn times ({@link VBItems#FUELS}). Farmers pick up barley
	 * seeds, so they may compost them too (as FD does for its seeds).
	 */
	public static class DataMaps extends DataMapProvider
	{
		public DataMaps(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
			super(output, lookupProvider);
		}

		@Override
		protected void gather(HolderLookup.Provider provider) {
			var compostables = builder(NeoForgeDataMaps.COMPOSTABLES);
			VBItems.COMPOSTABLES.forEach((item, chance) -> compostables.add(item.getKey(), new Compostable(chance, item == VBItems.BARLEY_SEEDS), false));
			var fuels = builder(NeoForgeDataMaps.FURNACE_FUELS);
			VBItems.FUELS.forEach((item, burnTime) -> fuels.add(item.getKey(), new FurnaceFuel(burnTime), false));
			// a full barrel never burns: see VineAndBarrelNeoForge (FurnaceFuelBurnTimeEvent)
			fuels.add(jexxme.vineandbarrel.barrel.BarrelRegistry.AGING_BARREL_ITEM.getKey(),
					new FurnaceFuel(jexxme.vineandbarrel.barrel.AgingBarrelItem.BURN_TIME), false);
		}
	}
}
