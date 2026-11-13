package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.advancement.VBAdvancementGenerator;
import jexxme.vineandbarrel.cauldron.VBCauldrons;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.world.village.VillageData;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.ForgeAdvancementProvider;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import jexxme.vineandbarrel.platform.registry.DeferredBlock;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Data generation for 1.20.1 ({@code ./gradlew :forge:runData}, writes {@code common/src/generated/resources}), set up
 * like FD's: recipes, loot tables, advancements, tags and loot modifiers are classic providers. Worldgen (configured
 * features, placed features, biome modifiers) and the language file are hand-written in {@code common/src/main/resources}.
 * <p>
 * Not generated on this branch: block states and models. Forge 47 has no counterpart of the 26.x model generators
 * they were written with, so they are hand-kept JSON under {@code common/src/main/resources/assets/vineandbarrel}
 * ({@code blockstates/}, {@code models/}): edit them there. Villager trades are code on 1.20.1
 * ({@code VillageData}), not data. Forge 47 has no data maps either: composter chances, burn times and the kettles'
 * copper stages are code in the loader glue ({@code VBItems.COMPOSTABLES}, {@code VBItems.FUELS}, {@code KettleRegistry}).
 */
@Mod.EventBusSubscriber(modid = VineAndBarrel.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
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
		DataGenerator generator = event.getGenerator();
		PackOutput output = generator.getPackOutput();
		CompletableFuture<HolderLookup.Provider> registries = event.getLookupProvider();
		generator.addProvider(true, new LootTables(output, List.of(
				new LootTableProvider.SubProviderEntry(VBBlockLoot::new, LootContextParamSets.BLOCK),
				// 1.20.1 has no block_interact parameter set: ClimbingCropBlock#pick rolls the harvest tables with the block one
				new LootTableProvider.SubProviderEntry(VBHarvestLoot::new, LootContextParamSets.BLOCK),
				new LootTableProvider.SubProviderEntry(VillageData.ChestLoot::new, LootContextParamSets.CHEST))));
		generator.addProvider(true, new ForgeAdvancementProvider(output, registries, files,
				List.of((lookup, saver, existingFiles) -> new VBAdvancementGenerator().generate(lookup, saver))));
		generator.addProvider(true, new VBRecipes(output));
		VBTagProviders.Blocks blockTags = generator.addProvider(true, new VBTagProviders.Blocks(output, registries, files));
		generator.addProvider(true, new VBTagProviders.Items(output, registries, blockTags.contentsGetter(), files));
		generator.addProvider(true, new VBTagProviders.Biomes(output, registries, files));
		generator.addProvider(true, new VBLootModifiers(output));
		generator.addProvider(true, new FdRecipes(output));
	}

	/**
	 * Our loot tables. The tavern chest draws once from vanilla's village house chest (1.2), a table this provider doesn't
	 * hold on this Minecraft (26.x resolves it): that one reference is left to the GameTests (world_tavern_chest_loot),
	 * everything else is checked as usual.
	 */
	private static final class LootTables extends LootTableProvider
	{
		LootTables(PackOutput output, List<LootTableProvider.SubProviderEntry> tables) {
			super(output, Set.of(), tables);
		}

		@Override
		protected void validate(java.util.Map<net.minecraft.resources.ResourceLocation, net.minecraft.world.level.storage.loot.LootTable> map,
				net.minecraft.world.level.storage.loot.ValidationContext context) {
			net.minecraft.world.level.storage.loot.ValidationContext own = new net.minecraft.world.level.storage.loot.ValidationContext(LootContextParamSets.ALL_PARAMS,
				new Tables(map));
			super.validate(map, own);
			own.getProblems().forEach((where, problem) -> {
				if (!problem.startsWith("Unknown loot table called minecraft:chests/village/")) {
					context.forChild(where).reportProblem(problem);
				}
			});
		}
	}

	/** Resolves references to the tables being generated, as vanilla's provider does. */
	private record Tables(java.util.Map<net.minecraft.resources.ResourceLocation, net.minecraft.world.level.storage.loot.LootTable> map)
		implements net.minecraft.world.level.storage.loot.LootDataResolver
	{
		@Override
		@SuppressWarnings("unchecked")
		public <T> T getElement(net.minecraft.world.level.storage.loot.LootDataId<T> id) {
			return id.type() == net.minecraft.world.level.storage.loot.LootDataType.TABLE ? (T) map.get(id.location()) : null;
		}
	}
}
