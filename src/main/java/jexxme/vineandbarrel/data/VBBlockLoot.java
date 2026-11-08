package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.advancements.criterion.StatePropertiesPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.ApplyExplosionDecay;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.loot.CanItemPerformAbility;

import java.util.Set;

/** Block drops. */
public class VBBlockLoot extends BlockLootSubProvider
{
	private final HolderGetter<Enchantment> enchantments;

	public VBBlockLoot(HolderLookup.Provider registries) {
		super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
		this.enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
	}

	@Override
	protected Iterable<Block> getKnownBlocks() {
		return VBDataGenerators.CROP_BLOCKS.stream().map(Holder::value).map(Block.class::cast).toList();
	}

	@Override
	protected void generate() {
		// Bushes and vines drop like the sweet berry bush, but only when ripe (their age 2 is still flowering)
		add(VBBlocks.GRAPE_BUSH.get(), block -> ripeFruit(block, VBItems.RED_GRAPES.get()));
		add(VBBlocks.HOP_BUSH.get(), block -> ripeFruit(block, VBItems.HOPS.get()));
		add(VBBlocks.GRAPE_VINE_ON_ROPE.get(), block -> ripeFruit(block, VBItems.RED_GRAPES.get()));
		add(VBBlocks.HOP_VINE_ON_ROPE.get(), block -> ripeFruit(block, VBItems.HOPS.get()));

		// Barley like wheat
		add(VBBlocks.BARLEY.get(), block -> createCropDrops(block, VBItems.BARLEY.get(), VBItems.BARLEY_SEEDS.get(),
				LootItemBlockStatePropertyCondition.hasBlockStateProperties(block).setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(CropBlock.AGE, 7))));

		// Wild crops exactly like FD's (wild carrots/potatoes for produce-planted crops, wild beetroots for seeded ones)
		add(VBBlocks.WILD_GRAPES.get(), block -> wildCropNoSeeds(block, VBItems.RED_GRAPES.get()));
		add(VBBlocks.WILD_HOPS.get(), block -> wildCropNoSeeds(block, VBItems.HOPS.get()));
		add(VBBlocks.WILD_BARLEY.get(), block -> wildCrop(block, VBItems.BARLEY.get(), VBItems.BARLEY_SEEDS.get()));

		dropSelf(VBBlocks.GRAPE_CRATE.get());
		dropSelf(VBBlocks.HOPS_CRATE.get());
		dropSelf(VBBlocks.BARLEY_BALE.get());
	}

	/** Vanilla's sweet berry bush age-3 pool: 2-3 fruit, Fortune adds up to 1 each level. */
	private LootTable.Builder ripeFruit(Block block, Item fruit) {
		return applyExplosionDecay(block, LootTable.lootTable()
				.withPool(LootPool.lootPool()
						.when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block).setProperties(StatePropertiesPredicate.Builder.properties()
								.hasProperty(ClimbingCropBlock.AGE, ClimbingCropBlock.MAX_AGE)))
						.add(LootItem.lootTableItem(fruit))
						.apply(SetItemCountFunction.setCount(UniformGenerator.between(2, 3)))
						.apply(ApplyBonusCount.addUniformBonusCount(enchantments.getOrThrow(Enchantments.FORTUNE)))));
	}

	/** FD's wildCrop: 20% extra produce, and either the plant itself (shears) or seeds with Fortune. */
	private LootTable.Builder wildCrop(Block block, Item crop, Item seeds) {
		return LootTable.lootTable()
				.withPool(LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1))
						.add(LootItem.lootTableItem(crop))
						.when(LootItemRandomChanceCondition.randomChance(0.2F))
						.when(CanItemPerformAbility.canItemPerformAbility(ItemAbilities.SHEARS_HARVEST).invert()))
				.withPool(LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1))
						.add(AlternativesEntry.alternatives(
								LootItem.lootTableItem(block)
										.when(CanItemPerformAbility.canItemPerformAbility(ItemAbilities.SHEARS_HARVEST)),
								LootItem.lootTableItem(seeds)
										.apply(ApplyExplosionDecay.explosionDecay())
										.apply(ApplyBonusCount.addUniformBonusCount(enchantments.getOrThrow(Enchantments.FORTUNE), 2)))));
	}

	/** FD's wildCropNoSeeds: the plant itself with shears, otherwise the produce with Fortune. */
	private LootTable.Builder wildCropNoSeeds(Block block, Item crop) {
		return LootTable.lootTable()
				.withPool(LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1))
						.add(AlternativesEntry.alternatives(
								LootItem.lootTableItem(block)
										.when(CanItemPerformAbility.canItemPerformAbility(ItemAbilities.SHEARS_HARVEST)),
								LootItem.lootTableItem(crop)
										.apply(ApplyExplosionDecay.explosionDecay())
										.apply(ApplyBonusCount.addUniformBonusCount(enchantments.getOrThrow(Enchantments.FORTUNE), 2)))));
	}
}
