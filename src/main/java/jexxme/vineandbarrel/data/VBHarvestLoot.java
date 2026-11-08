package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.function.BiConsumer;

/**
 * Right-click harvest of a ripe grape or hop plant ({@code block_interact} tables), the yield of vanilla's
 * {@code harvest/sweet_berry_bush} at age 3: 2-3 fruit.
 */
public class VBHarvestLoot implements LootTableSubProvider
{
	public VBHarvestLoot(HolderLookup.Provider registries) {
	}

	@Override
	public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output) {
		output.accept(VBBlocks.HARVEST_GRAPES, ripeHarvest(VBItems.RED_GRAPES.get()));
		output.accept(VBBlocks.HARVEST_HOPS, ripeHarvest(VBItems.HOPS.get()));
	}

	private static LootTable.Builder ripeHarvest(Item fruit) {
		return LootTable.lootTable().withPool(LootPool.lootPool()
				.setRolls(ConstantValue.exactly(1))
				.add(LootItem.lootTableItem(fruit).apply(SetItemCountFunction.setCount(UniformGenerator.between(2, 3)))));
	}
}
