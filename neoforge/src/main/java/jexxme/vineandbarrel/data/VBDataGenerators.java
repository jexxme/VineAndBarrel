package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.advancement.VBAdvancementGenerator;
import jexxme.vineandbarrel.cauldron.VBCauldrons;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import jexxme.vineandbarrel.world.village.VillageData;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import jexxme.vineandbarrel.platform.registry.DeferredBlock;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Data generation for 1.21.1 ({@code ./gradlew runData}, writes {@code common/src/generated/resources}), set up like FD's:
 * recipes, loot tables, advancements, tags, data maps and loot modifiers are classic providers. Worldgen (configured
 * features, placed features, biome modifiers) and the language file are hand-written in {@code common/src/main/resources}.
 * <p>
 * Not generated on this branch: block states and models. NeoForge 21.1 has no counterpart of the 26.x model generators
 * they were written with, so they are hand-kept JSON under {@code common/src/main/resources/assets/vineandbarrel}
 * ({@code blockstates/}, {@code models/}): edit them there. Villager trades are code on 1.21.1
 * ({@code VillageData}), not data.
 */
@EventBusSubscriber(modid = VineAndBarrel.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class VBDataGenerators
{
	/**
	 * The blocks whose loot is generated here (the crops, M1). Other content keeps hand-written loot tables, so the
	 * provider only validates this set.
	 */
	static final List<DeferredBlock<? extends Block>> CROP_BLOCKS = List.of(VBBlocks.GRAPE_BUSH, VBBlocks.WHITE_GRAPE_BUSH, VBBlocks.HOP_BUSH,
			VBBlocks.GRAPE_VINE_ON_ROPE, VBBlocks.WHITE_GRAPE_VINE_ON_ROPE, VBBlocks.HOP_VINE_ON_ROPE, VBBlocks.TRELLIS, VBBlocks.GRAPE_VINE_ON_TRELLIS,
			VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS, VBBlocks.HOP_VINE_ON_TRELLIS, VBBlocks.GRAPE_VINE_ON_SUPPORT, VBBlocks.WHITE_GRAPE_VINE_ON_SUPPORT,
			VBBlocks.HOP_VINE_ON_SUPPORT, VBBlocks.BARLEY, VBBlocks.WILD_GRAPES,
			VBBlocks.WILD_WHITE_GRAPES, VBBlocks.WILD_HOPS, VBBlocks.WILD_BARLEY, VBBlocks.GRAPE_CRATE, VBBlocks.WHITE_GRAPE_CRATE,
			VBBlocks.HOPS_CRATE, VBBlocks.BARLEY_BALE);
	/** Our bases' cauldrons (no items): the loot (the cauldron itself) and the cauldron tag. */
	static final List<DeferredBlock<? extends Block>> CAULDRON_BLOCKS = List.of(VBCauldrons.GRAPE_JUICE, VBCauldrons.WHITE_GRAPE_JUICE,
			VBCauldrons.SWEET_BERRY_JUICE, VBCauldrons.APPLE_JUICE, VBCauldrons.BARLEY_WORT, VBCauldrons.WHEAT_WORT, VBCauldrons.HONEY_MUST);

	private VBDataGenerators() {
	}

	@SubscribeEvent
	public static void gatherData(GatherDataEvent event) {
		if (!event.includeServer()) {
			return;
		}
		ExistingFileHelper files = event.getExistingFileHelper();
		event.createProvider((output, registries) -> new LootTableProvider(output, Set.of(), List.of(
				new LootTableProvider.SubProviderEntry(VBBlockLoot::new, LootContextParamSets.BLOCK),
				// 1.21.1 has no block_interact parameter set: ClimbingCropBlock#pick rolls the harvest tables with the block one
				new LootTableProvider.SubProviderEntry(VBHarvestLoot::new, LootContextParamSets.BLOCK),
				new LootTableProvider.SubProviderEntry(VillageData.ChestLoot::new, LootContextParamSets.CHEST)), registries));
		event.createProvider((output, registries) -> new AdvancementProvider(output, registries, List.of(new VBAdvancementGenerator())));
		event.createProvider(VBRecipes::new);
		event.createBlockAndItemTags((output, lookup) -> new VBTagProviders.Blocks(output, lookup, files),
				(output, lookup, blockTags) -> new VBTagProviders.Items(output, lookup, blockTags, files));
		event.createProvider((output, lookup) -> new VBTagProviders.Biomes(output, lookup, files));
		event.createProvider(DataMaps::new);
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
