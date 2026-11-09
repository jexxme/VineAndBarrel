package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.advancements.predicates.ItemPredicate;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.compat.farmersdelight.FeastBlock;
import jexxme.vineandbarrel.tag.VBTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.entries.TagEntry;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.core.Holder;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.level.storage.loot.predicates.MatchBlock;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.level.storage.loot.predicates.InvertedLootItemCondition;

import java.util.Set;

/** Block drops. */
public class VBBlockLoot extends BlockLootSubProvider
{
	public VBBlockLoot(LootTableSubProvider.Context output) {
		super(Set.of(), FeatureFlags.REGISTRY.allFlags(), output);
	}

	@Override
	protected Iterable<Block> getKnownBlocks() {
		java.util.List<Block> blocks = new java.util.ArrayList<>();
		VBDataGenerators.CROP_BLOCKS.forEach(block -> blocks.add(block.get()));
		blocks.add(FDCompat.COQ_AU_VIN_BLOCK.get());
		VBDataGenerators.CAULDRON_BLOCKS.forEach(block -> blocks.add(block.get()));
		return blocks;
	}

	@Override
	protected void generate() {
		// Bushes and vines drop like the sweet berry bush: fruit when half-ripe or ripe. A vine leaves its rope or trellis in
		// place (the block puts it back), so neither is in its loot
		add(VBBlocks.GRAPE_BUSH.get(), block -> fruit(block, VBItems.RED_GRAPES.get()));
		add(VBBlocks.WHITE_GRAPE_BUSH.get(), block -> fruit(block, VBItems.WHITE_GRAPES.get()));
		add(VBBlocks.HOP_BUSH.get(), block -> fruit(block, VBItems.HOPS.get()));
		add(VBBlocks.GRAPE_VINE_ON_ROPE.get(), block -> fruit(block, VBItems.RED_GRAPES.get()));
		add(VBBlocks.WHITE_GRAPE_VINE_ON_ROPE.get(), block -> fruit(block, VBItems.WHITE_GRAPES.get()));
		add(VBBlocks.HOP_VINE_ON_ROPE.get(), block -> fruit(block, VBItems.HOPS.get()));
		add(VBBlocks.GRAPE_VINE_ON_TRELLIS.get(), block -> fruit(block, VBItems.RED_GRAPES.get()));
		add(VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS.get(), block -> fruit(block, VBItems.WHITE_GRAPES.get()));
		add(VBBlocks.HOP_VINE_ON_TRELLIS.get(), block -> fruit(block, VBItems.HOPS.get()));
		// On a fence, wall, chain or bars: one fewer than on a trellis (the block it held stays in place, as with a trellis)
		add(VBBlocks.GRAPE_VINE_ON_SUPPORT.get(), block -> fewerFruit(block, VBItems.RED_GRAPES.get()));
		add(VBBlocks.WHITE_GRAPE_VINE_ON_SUPPORT.get(), block -> fewerFruit(block, VBItems.WHITE_GRAPES.get()));
		add(VBBlocks.HOP_VINE_ON_SUPPORT.get(), block -> fewerFruit(block, VBItems.HOPS.get()));
		dropSelf(VBBlocks.TRELLIS.get());
		// A cauldron of a base drops the cauldron, like vanilla's water cauldron (the liquid is lost)
		VBDataGenerators.CAULDRON_BLOCKS.forEach(cauldron -> dropOther(cauldron.get(), Items.CAULDRON));

		// Barley like wheat, and like FD's straw_from_mature_wheat: FD's knife also cuts a straw from ripe barley (#vineandbarrel:straw
		// holds FD's straw and #vineandbarrel:straw_harvesters FD's knives, both empty without FD, so the same table works everywhere)
		add(VBBlocks.BARLEY.get(), block -> {
			var ripe = MatchBlock.blockMatches(blocks, block, StatePropertiesPredicate.Builder.properties().hasProperty(CropBlock.AGE, 7));
			return createCropDrops(block, VBItems.BARLEY.get(), VBItems.BARLEY_SEEDS.get(), ripe)
					.withPool(LootPool.lootPool()
							.when(ripe)
							.when(MatchTool.toolMatches(ItemPredicate.Builder.item().of(items, VBTags.STRAW_HARVESTERS)))
							.add(TagEntry.tagContents(items.getOrThrow(VBTags.STRAW))));
		});

		// Wild crops exactly like FD's (wild carrots/potatoes for produce-planted crops, wild beetroots for seeded ones)
		add(VBBlocks.WILD_GRAPES.get(), block -> wildCropNoSeeds(block, VBItems.RED_GRAPES.get()));
		add(VBBlocks.WILD_WHITE_GRAPES.get(), block -> wildCropNoSeeds(block, VBItems.WHITE_GRAPES.get()));
		add(VBBlocks.WILD_HOPS.get(), block -> wildCropNoSeeds(block, VBItems.HOPS.get()));
		add(VBBlocks.WILD_BARLEY.get(), block -> wildCrop(block, VBItems.BARLEY.get(), VBItems.BARLEY_SEEDS.get()));

		dropSelf(VBBlocks.GRAPE_CRATE.get());
		dropSelf(VBBlocks.WHITE_GRAPE_CRATE.get());
		dropSelf(VBBlocks.HOPS_CRATE.get());
		dropSelf(VBBlocks.BARLEY_BALE.get());

		// The Coq au Vin feast: whole, itself; once served from, only the bones (like FD's roast chicken leaves bone meal)
		Block feast = FDCompat.COQ_AU_VIN_BLOCK.get();
		var whole = MatchBlock.blockMatches(blocks, feast, StatePropertiesPredicate.Builder.properties().hasProperty(FeastBlock.SERVINGS, FeastBlock.MAX_SERVINGS));
		add(feast, LootTable.lootTable()
				.withPool(LootPool.lootPool().when(whole).add(LootItem.lootTableItem(feast)))
				.withPool(LootPool.lootPool().when(InvertedLootItemCondition.invert(whole)).add(LootItem.lootTableItem(Items.BONE))));
	}

	/** Vanilla's sweet berry bush pools: 2-3 fruit when ripe, 1-2 when half-ripe, Fortune adds up to 1 each level. */
	private LootTable.Builder fruit(Block block, Item fruit) {
		return applyExplosionDecay(block, LootTable.lootTable()
				.withPool(fruitPool(block, fruit, ClimbingCropBlock.MAX_AGE, 2, 3))
				.withPool(fruitPool(block, fruit, ClimbingCropBlock.HALF_RIPE_AGE, 1, 2)));
	}

	/** One fewer than the sweet berry bush, for a vine on a fence, wall, chain or bars: 1-2 when ripe, 1 when half-ripe, plus Fortune. */
	private LootTable.Builder fewerFruit(Block block, Item fruit) {
		return applyExplosionDecay(block, LootTable.lootTable()
				.withPool(fruitPool(block, fruit, ClimbingCropBlock.MAX_AGE, 1, 2))
				.withPool(fruitPool(block, fruit, ClimbingCropBlock.HALF_RIPE_AGE, 1, 1)));
	}

	private LootPool.Builder fruitPool(Block block, Item fruit, int age, int min, int max) {
		return LootPool.lootPool()
				.when(MatchBlock.blockMatches(blocks, block, StatePropertiesPredicate.Builder.properties().hasProperty(ClimbingCropBlock.AGE, age)))
				.add(LootItem.lootTableItem(fruit))
				.apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max)))
				.apply(ApplyBonusCount.addUniformBonusCount(enchantments.getOrThrow(Enchantments.FORTUNE)));
	}

	/**
	 * Shears: vanilla's own {@code minecraft:tool/can_shear} predicate (as for tall grass; NeoForge widens it to every tool
	 * that can shear) or any tool in the common {@code #c:tools/shear} tag, so modded shears work on Fabric too.
	 */
	private LootItemCondition.Builder shears() {
		return new AnyOfCondition.Builder(MatchTool.toolMatches(ItemPredicate.Builder.item().of(items, SHEAR_TOOLS))).or(hasShears());
	}

	private static final TagKey<Item> SHEAR_TOOLS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/shear"));

	/**
	 * FD's wildCrop, with the produce always instead of 20% of the time (so a patch of wild barley makes a wort, as a patch
	 * of wild grapes makes a juice): one produce, and either the plant itself (shears) or seeds with Fortune.
	 */
	private LootTable.Builder wildCrop(Block block, Item crop, Item seeds) {
		return LootTable.lootTable()
				.withPool(LootPool.lootPool()
						.setRolls(ContextIntProviders.exactly(1))
						.add(LootItem.lootTableItem(crop).apply(ApplyExplosionDecay.explosionDecay()))
						.when(InvertedLootItemCondition.invert(shears())))
				.withPool(LootPool.lootPool()
						.setRolls(ContextIntProviders.exactly(1))
						.add(AlternativesEntry.alternatives(
								LootItem.lootTableItem(block)
										.when(shears()),
								LootItem.lootTableItem(seeds)
										.apply(ApplyExplosionDecay.explosionDecay())
										.apply(ApplyBonusCount.addUniformBonusCount(enchantments.getOrThrow(Enchantments.FORTUNE), 2)))));
	}

	/** FD's wildCropNoSeeds: the plant itself with shears, otherwise the produce with Fortune. */
	private LootTable.Builder wildCropNoSeeds(Block block, Item crop) {
		return LootTable.lootTable()
				.withPool(LootPool.lootPool()
						.setRolls(ContextIntProviders.exactly(1))
						.add(AlternativesEntry.alternatives(
								LootItem.lootTableItem(block)
										.when(shears()),
								LootItem.lootTableItem(crop)
										.apply(ApplyExplosionDecay.explosionDecay())
										.apply(ApplyBonusCount.addUniformBonusCount(enchantments.getOrThrow(Enchantments.FORTUNE), 2)))));
	}
}
