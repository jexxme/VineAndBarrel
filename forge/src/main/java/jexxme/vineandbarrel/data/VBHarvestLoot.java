package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.block.ClimbingBushBlock;
import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.registry.VBBlocks;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceLocation;
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
 * Right-click harvest of a grape (red or white) or hop plant, built like the {@code harvest/sweet_berry_bush} table of
 * later Minecraft versions ({@code block} tables here: 1.20.1 has no {@code block_interact} type, see
 * {@code ClimbingCropBlock#pick}): 1-2 fruit, plus one more when the plant is ripe (age 3), so 1-2 half-ripe and 2-3
 * ripe. One table per crop serves its bush and its vines on a rope or a trellis; a vine on a fence, wall, chain or bars has
 * its own with one fewer (1 half-ripe, 1-2 ripe).
 */
public class VBHarvestLoot implements LootTableSubProvider
{
	@Override
	public void generate(BiConsumer<ResourceLocation, LootTable.Builder> output) {
		output.accept(VBBlocks.HARVEST_GRAPES, harvest(VBBlocks.GRAPE_BUSH.get()));
		output.accept(VBBlocks.HARVEST_HOPS, harvest(VBBlocks.HOP_BUSH.get()));
		output.accept(VBBlocks.HARVEST_WHITE_GRAPES, harvest(VBBlocks.WHITE_GRAPE_BUSH.get()));
		output.accept(VBBlocks.HARVEST_GRAPES_ON_SUPPORT, harvestOnSupport(VBBlocks.GRAPE_BUSH.get()));
		output.accept(VBBlocks.HARVEST_HOPS_ON_SUPPORT, harvestOnSupport(VBBlocks.HOP_BUSH.get()));
		output.accept(VBBlocks.HARVEST_WHITE_GRAPES_ON_SUPPORT, harvestOnSupport(VBBlocks.WHITE_GRAPE_BUSH.get()));
	}

	/** Off a fence, wall, chain or bars: one fruit, and maybe one more when ripe (so 1 half-ripe, 1-2 ripe). */
	private static LootTable.Builder harvestOnSupport(ClimbingBushBlock bush) {
		return LootTable.lootTable()
				.withPool(LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1))
						.add(LootItem.lootTableItem(bush.produce()).apply(SetItemCountFunction.setCount(UniformGenerator.between(0, 1))))
						.when(ripe(bush.supportVine())))
				.withPool(LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1))
						.add(LootItem.lootTableItem(bush.produce()).apply(SetItemCountFunction.setCount(ConstantValue.exactly(1)))));
	}

	private static LootTable.Builder harvest(ClimbingBushBlock bush) {
		// no block-set predicate for loot: one ripe check per block of the plant
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
