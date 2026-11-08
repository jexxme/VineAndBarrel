package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.block.ClimbingBushBlock;
import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.registry.VBBlocks;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.function.BiConsumer;

/**
 * Right-click harvest of a grape (red or white) or hop plant ({@code block_interact} tables), built like vanilla's
 * {@code harvest/sweet_berry_bush}: 1-2 fruit, plus one more when the plant is ripe (age 3), so 1-2 half-ripe and 2-3
 * ripe. One table per crop serves its bush and both of its vines.
 */
public class VBHarvestLoot implements LootTableSubProvider
{
	public VBHarvestLoot(HolderLookup.Provider registries) {
	}

	@Override
	public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output) {
		output.accept(VBBlocks.HARVEST_GRAPES, harvest(VBBlocks.GRAPE_BUSH.get()));
		output.accept(VBBlocks.HARVEST_HOPS, harvest(VBBlocks.HOP_BUSH.get()));
		output.accept(VBBlocks.HARVEST_WHITE_GRAPES, harvest(VBBlocks.WHITE_GRAPE_BUSH.get()));
	}

	private static LootTable.Builder harvest(ClimbingBushBlock bush) {
		// 26.2 has no block-set predicate for loot: one ripe check per block of the plant
		AnyOfCondition.Builder ripe = AnyOfCondition.anyOf(ripe(bush), ripe(bush.ropeVine()), ripe(bush.trellisVine()));
		return LootTable.lootTable()
				.withPool(LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1))
						.add(LootItem.lootTableItem(bush.produce()).apply(SetItemCountFunction.setCount(ConstantValue.exactly(1)))
								.when(ripe)))
				.withPool(LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1))
						.add(LootItem.lootTableItem(bush.produce()).apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2)))));
	}

	private static LootItemBlockStatePropertyCondition.Builder ripe(Block block) {
		return LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
				.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(ClimbingCropBlock.AGE, ClimbingCropBlock.MAX_AGE));
	}
}
