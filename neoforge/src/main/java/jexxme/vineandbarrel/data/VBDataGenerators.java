package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.advancement.VBAdvancementGenerator;
import jexxme.vineandbarrel.cauldron.VBCauldrons;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import jexxme.vineandbarrel.world.village.VillageData;
import net.minecraft.core.HolderLookup;
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
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import net.neoforged.neoforge.registries.datamaps.builtin.VillagerCompostable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Data generation for 26.3 ({@code ./gradlew runData}, writes {@code src/generated/resources}), set up like FD's:
 * recipes, loot tables and advancements are datapack registry entries; tags, data maps and models are classic providers. Worldgen
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
			VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS, VBBlocks.HOP_VINE_ON_TRELLIS, VBBlocks.GRAPE_VINE_ON_SUPPORT, VBBlocks.WHITE_GRAPE_VINE_ON_SUPPORT,
			VBBlocks.HOP_VINE_ON_SUPPORT, VBBlocks.BARLEY, VBBlocks.WILD_GRAPES,
			VBBlocks.WILD_WHITE_GRAPES, VBBlocks.WILD_HOPS, VBBlocks.WILD_BARLEY, VBBlocks.GRAPE_CRATE, VBBlocks.WHITE_GRAPE_CRATE,
			VBBlocks.HOPS_CRATE, VBBlocks.BARLEY_BALE);
	/** Our bases' cauldrons (no items): block states, the shared level models and the loot (the cauldron itself). */
	static final List<DeferredBlock<? extends Block>> CAULDRON_BLOCKS = List.of(VBCauldrons.GRAPE_JUICE, VBCauldrons.WHITE_GRAPE_JUICE,
			VBCauldrons.SWEET_BERRY_JUICE, VBCauldrons.APPLE_JUICE, VBCauldrons.BARLEY_WORT, VBCauldrons.WHEAT_WORT, VBCauldrons.HONEY_MUST);
	static final List<DeferredItem<? extends Item>> CROP_ITEMS = List.of(VBItems.RED_GRAPES, VBItems.WHITE_GRAPES, VBItems.HOPS, VBItems.TRELLIS,
			VBItems.BARLEY_SEEDS, VBItems.BARLEY, VBItems.WILD_GRAPES, VBItems.WILD_WHITE_GRAPES, VBItems.WILD_HOPS, VBItems.WILD_BARLEY,
			VBItems.GRAPE_CRATE, VBItems.WHITE_GRAPE_CRATE, VBItems.HOPS_CRATE, VBItems.BARLEY_BALE);

	private VBDataGenerators() {
	}

	@SubscribeEvent
	public static void gatherRegistryEntries(GatherDataRegistryEntriesEvent event) {
		event.add(Registries.LOOT_TABLE, new LootTableProvider(Set.of(), List.of(
						new LootTableProvider.SubProviderEntry(VBBlockLoot::new, LootContextParamSets.BLOCK),
						new LootTableProvider.SubProviderEntry(VBHarvestLoot::new, LootContextParamSets.BLOCK_INTERACT),
						new LootTableProvider.SubProviderEntry(VillageData.ChestLoot::new, LootContextParamSets.CHEST))))
				.add(Registries.VILLAGER_TRADE, VillageData::bootstrapTrades)
				.add(Registries.ADVANCEMENT, new AdvancementProvider(List.of(VBAdvancementGenerator::new)))
				.add(VBRecipes.bootstrap());
	}

	@SubscribeEvent
	public static void gatherData(GatherDataEvent.Client event) {
		event.createBlockAndItemTags(VBTagProviders.Blocks::new, (output, lookup, blockTags) -> new VBTagProviders.Items(output, lookup));
		event.createProvider(VBTagProviders.Biomes::new);
		event.createProvider(DataMaps::new);
		event.createProvider(VBModels::new);
		event.createProvider(VBTradeTags::new);
		event.createProvider(VBLootModifiers::new);
		event.createProvider(FdRecipes::new);
	}

	/** Farmers pick up barley seeds, so they must be able to compost them too (as FD does for its seeds). */
	public static class DataMaps extends DataMapProvider
	{
		public DataMaps(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
			super(output, lookupProvider);
		}

		@Override
		protected void gather(HolderLookup.Provider provider) {
			builder(NeoForgeDataMaps.VILLAGER_COMPOSTABLES)
					.add(VBItems.BARLEY_SEEDS.get().builtInRegistryHolder(), new VillagerCompostable(true), false);
		}
	}
}
