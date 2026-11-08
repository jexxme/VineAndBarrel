package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.block.ClimbingBushBlock;
import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.registry.VBBlocks;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.MatchBlock;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * Right-click harvest of a grape (red or white) or hop plant ({@code block_interact} tables), built like vanilla's
 * {@code harvest/sweet_berry_bush}: 1-2 fruit, plus one more when the plant is ripe (age 3), so 1-2 half-ripe and 2-3
 * ripe. One table per crop serves its bush and both of its vines.
 */
public class VBHarvestLoot implements LootTableSubProvider
{
	private final LootTableSubProvider.Context output;
	private final HolderGetter<Block> blocks;

	public VBHarvestLoot(LootTableSubProvider.Context output) {
		this.output = output;
		this.blocks = output.lookup(Registries.BLOCK);
	}

	@Override
	public void run() {
		output.accept(VBBlocks.HARVEST_GRAPES, harvest(VBBlocks.GRAPE_BUSH.get()));
		output.accept(VBBlocks.HARVEST_HOPS, harvest(VBBlocks.HOP_BUSH.get()));
		output.accept(VBBlocks.HARVEST_WHITE_GRAPES, harvest(VBBlocks.WHITE_GRAPE_BUSH.get()));
	}

	private LootTable.Builder harvest(ClimbingBushBlock bush) {
		BlockPredicate.Builder ripe = BlockPredicate.Builder.block().of(blocks, bush, bush.ropeVine(), bush.trellisVine())
				.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(ClimbingCropBlock.AGE, ClimbingCropBlock.MAX_AGE));
		return LootTable.lootTable()
				.withPool(LootPool.lootPool()
						.setRolls(ContextIntProviders.exactly(1))
						.add(LootItem.lootTableItem(bush.produce()).apply(SetItemCountFunction.setCount(ContextIntProviders.exactly(1)))
								.when(MatchBlock.blockMatches(ripe))))
				.withPool(LootPool.lootPool()
						.setRolls(ContextIntProviders.exactly(1))
						.add(LootItem.lootTableItem(bush.produce()).apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 2)))));
	}
}
