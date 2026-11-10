package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.tag.VBTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider.IntrinsicTagAppender;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import jexxme.vineandbarrel.platform.registry.DeferredHolder;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/** Block, item and biome tags. Every vanilla tag here copies what vanilla does for the matching vanilla thing. */
public final class VBTagProviders
{
	private VBTagProviders() {
	}

	/** Block and item tags take the blocks and items themselves. */
	@SafeVarargs
	private static <T> void add(IntrinsicTagAppender<T> appender, DeferredHolder<? super T, ? extends T>... holders) {
		Arrays.stream(holders).forEach(holder -> appender.add(holder.get()));
	}

	private static TagKey<Item> commonItem(String path) {
		return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
	}

	private static TagKey<Block> commonBlock(String path) {
		return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
	}

	private static ResourceLocation fdBlock(String path) {
		return ResourceLocation.fromNamespaceAndPath(VBTags.FARMERS_DELIGHT, path);
	}

	// ------------------------------------------------------------------ blocks
	public static class Blocks extends BlockTagsProvider
	{
		public Blocks(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, ExistingFileHelper existingFileHelper) {
			super(output, lookupProvider, VineAndBarrel.MODID, existingFileHelper);
		}

		@Override
		protected void addTags(HolderLookup.Provider provider) {
			// C2: bees help them grow (barley through #minecraft:crops, like wheat; the bushes like the sweet berry bush)
			add(tag(BlockTags.BEE_GROWABLES), VBBlocks.GRAPE_BUSH, VBBlocks.WHITE_GRAPE_BUSH, VBBlocks.HOP_BUSH, VBBlocks.GRAPE_VINE_ON_ROPE,
					VBBlocks.WHITE_GRAPE_VINE_ON_ROPE, VBBlocks.HOP_VINE_ON_ROPE, VBBlocks.GRAPE_VINE_ON_TRELLIS, VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS,
					VBBlocks.HOP_VINE_ON_TRELLIS, VBBlocks.GRAPE_VINE_ON_SUPPORT, VBBlocks.WHITE_GRAPE_VINE_ON_SUPPORT, VBBlocks.HOP_VINE_ON_SUPPORT);
			add(tag(BlockTags.CROPS), VBBlocks.BARLEY);
			// the bushes too: a trellis in one is solid, and farmland under it would otherwise dry out to dirt
			add(tag(BlockTags.MAINTAINS_FARMLAND), VBBlocks.BARLEY, VBBlocks.GRAPE_BUSH, VBBlocks.WHITE_GRAPE_BUSH, VBBlocks.HOP_BUSH);
			// FD: wild crops are small flowers, a vine on a rope stays climbable like the rope
			add(tag(BlockTags.SMALL_FLOWERS), VBBlocks.WILD_GRAPES, VBBlocks.WILD_WHITE_GRAPES, VBBlocks.WILD_HOPS, VBBlocks.WILD_BARLEY);
			add(tag(BlockTags.CLIMBABLE), VBBlocks.GRAPE_VINE_ON_ROPE, VBBlocks.WHITE_GRAPE_VINE_ON_ROPE, VBBlocks.HOP_VINE_ON_ROPE);
			add(tag(BlockTags.MINEABLE_WITH_AXE), VBBlocks.GRAPE_CRATE, VBBlocks.WHITE_GRAPE_CRATE, VBBlocks.HOPS_CRATE);
			// The Trellis is wood like a ladder, and a vine on it still has the trellis inside. Not #minecraft:climbable (unlike a
			// vine on an FD rope): a plant is at most three blocks tall, all within reach from the ground
			add(tag(BlockTags.MINEABLE_WITH_AXE), VBBlocks.TRELLIS, VBBlocks.GRAPE_VINE_ON_TRELLIS, VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS,
					VBBlocks.HOP_VINE_ON_TRELLIS, VBBlocks.GRAPE_VINE_ON_SUPPORT, VBBlocks.WHITE_GRAPE_VINE_ON_SUPPORT, VBBlocks.HOP_VINE_ON_SUPPORT);
			// What the bushes climb: the Trellis, and FD's ropes when FD is there
			IntrinsicTagAppender<Block> vineSupports = tag(VBTags.VINE_SUPPORTS);
			vineSupports.addOptionalTag(VBTags.FD_ROPES);
			add(vineSupports, VBBlocks.TRELLIS);
			// ... and, with one fewer fruit per pick, fences, walls, chains (upright) and bars; packs add their own here.
			// 1.21.1 has one chain and one kind of bars, and no #minecraft:chains or #minecraft:bars: the blocks themselves
			tag(VBTags.MAKESHIFT_VINE_SUPPORTS).addTag(BlockTags.FENCES).addTag(BlockTags.WALLS)
					.add(net.minecraft.world.level.block.Blocks.CHAIN).add(net.minecraft.world.level.block.Blocks.IRON_BARS);
			// (no #farmersdelight:tomato_supports: neither Farmer's Delight 1.21.1 nor FD Refabricated reads it, so FD's
			// tomatoes stay on ropes here)
			// B16: the Aging Barrel is mined fastest with an axe, like the vanilla barrel
			add(tag(BlockTags.MINEABLE_WITH_AXE), BarrelRegistry.AGING_BARREL);
			add(tag(BlockTags.MINEABLE_WITH_HOE), VBBlocks.BARLEY_BALE);
			// Our bases' cauldrons are cauldrons, like vanilla's water cauldron: pickaxe, motion-blocking, mobs path around them
			VBDataGenerators.CAULDRON_BLOCKS.forEach(cauldron -> tag(BlockTags.CAULDRONS).add(cauldron.get()));

			add(tag(VBTags.WILD_CROPS), VBBlocks.WILD_GRAPES, VBBlocks.WILD_WHITE_GRAPES, VBBlocks.WILD_HOPS, VBBlocks.WILD_BARLEY);
			// The blackout's hay ending: a bale is a bed, if you're tired enough
			IntrinsicTagAppender<Block> hayBales = tag(VBTags.HAY_BALES).add(net.minecraft.world.level.block.Blocks.HAY_BLOCK);
			hayBales.addOptional(fdBlock("straw_bale")).addOptional(fdBlock("rice_bale"));
			add(hayBales, VBBlocks.BARLEY_BALE);
			// C7: the bale makes campfire signal smoke like a hay bale (our mixin), and FD's tag for FD's own mixin
			add(tag(VBTags.CAMPFIRE_SIGNAL_SMOKE), VBBlocks.BARLEY_BALE);
			add(tag(VBTags.FD_CAMPFIRE_SIGNAL_SMOKE), VBBlocks.BARLEY_BALE);
			add(tag(VBTags.FD_WILD_CROPS), VBBlocks.WILD_GRAPES, VBBlocks.WILD_WHITE_GRAPES, VBBlocks.WILD_HOPS, VBBlocks.WILD_BARLEY);
			// FD's feasts, next to its own (blocks and items)
			add(tag(VBTags.FD_FEASTS), jexxme.vineandbarrel.compat.farmersdelight.FDCompat.COQ_AU_VIN_BLOCK);

			// Serene Seasons (DESIGN 3): grapes (red and white) autumn, hops summer + autumn, barley spring + summer
			add(tag(VBTags.SEASONS_AUTUMN_CROPS), VBBlocks.GRAPE_BUSH, VBBlocks.GRAPE_VINE_ON_ROPE, VBBlocks.GRAPE_VINE_ON_TRELLIS,
					VBBlocks.GRAPE_VINE_ON_SUPPORT, VBBlocks.WHITE_GRAPE_BUSH, VBBlocks.WHITE_GRAPE_VINE_ON_ROPE, VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS,
					VBBlocks.WHITE_GRAPE_VINE_ON_SUPPORT, VBBlocks.HOP_BUSH, VBBlocks.HOP_VINE_ON_ROPE, VBBlocks.HOP_VINE_ON_TRELLIS,
					VBBlocks.HOP_VINE_ON_SUPPORT);
			add(tag(VBTags.SEASONS_SUMMER_CROPS), VBBlocks.HOP_BUSH, VBBlocks.HOP_VINE_ON_ROPE, VBBlocks.HOP_VINE_ON_TRELLIS,
					VBBlocks.HOP_VINE_ON_SUPPORT, VBBlocks.BARLEY);
			add(tag(VBTags.SEASONS_SPRING_CROPS), VBBlocks.BARLEY);

			add(tag(commonBlock("storage_blocks/barley")), VBBlocks.BARLEY_BALE);
			add(tag(commonBlock("storage_blocks/grape")), VBBlocks.GRAPE_CRATE, VBBlocks.WHITE_GRAPE_CRATE);
			add(tag(commonBlock("storage_blocks/hops")), VBBlocks.HOPS_CRATE);
			tag(Tags.Blocks.STORAGE_BLOCKS).addTag(commonBlock("storage_blocks/barley"))
					.addTag(commonBlock("storage_blocks/grape")).addTag(commonBlock("storage_blocks/hops"));
		}
	}

	// ------------------------------------------------------------------ items
	public static class Items extends ItemTagsProvider
	{
		public Items(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
				CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, ExistingFileHelper existingFileHelper) {
			super(output, lookupProvider, blockTags, VineAndBarrel.MODID, existingFileHelper);
		}

		@Override
		protected void addTags(HolderLookup.Provider provider) {
			// C3: barley feeds and breeds everything wheat does; the bale feeds like a hay bale; seeds feed chickens and parrots
			add(tag(ItemTags.COW_FOOD), VBItems.BARLEY);
			add(tag(ItemTags.SHEEP_FOOD), VBItems.BARLEY);
			add(tag(ItemTags.GOAT_FOOD), VBItems.BARLEY);
			add(tag(ItemTags.HORSE_FOOD), VBItems.BARLEY, VBItems.BARLEY_BALE);
			add(tag(ItemTags.LLAMA_FOOD), VBItems.BARLEY, VBItems.BARLEY_BALE);
			add(tag(ItemTags.LLAMA_TEMPT_ITEMS), VBItems.BARLEY_BALE);
			add(tag(ItemTags.CHICKEN_FOOD), VBItems.BARLEY_SEEDS);
			add(tag(ItemTags.PARROT_FOOD), VBItems.BARLEY_SEEDS);
			// C4: farmers plant barley seeds (and so pick them up), like vanilla's wheat and beetroot and FD's crops. That they
			// also pick up the barley they harvest is code on 1.21.1 (VillagerWantsBarleyMixin): it has no
			// #minecraft:villager_picks_up
			add(tag(ItemTags.VILLAGER_PLANTABLE_SEEDS), VBItems.BARLEY_SEEDS);
			// C5: foxes love grapes (and can be bred with them, like sweet berries)
			add(tag(ItemTags.FOX_FOOD), VBItems.RED_GRAPES, VBItems.WHITE_GRAPES);

			// A drink of either age as one cooking pot ingredient (FdRecipes)
			add(tag(VBTags.COOKING_BARLEY_ALE), DrinkItems.BARLEY_ALE, DrinkItems.AGED_BARLEY_ALE);
			add(tag(VBTags.COOKING_RED_WINE), DrinkItems.RED_WINE, DrinkItems.AGED_RED_WINE);
			add(tag(VBTags.COOKING_HARD_CIDER), DrinkItems.HARD_CIDER, DrinkItems.AGED_HARD_CIDER);
			add(tag(VBTags.COOKING_WHITE_WINE), DrinkItems.WHITE_WINE, DrinkItems.AGED_WHITE_WINE);
			add(tag(VBTags.WILD_CROPS_ITEM), VBItems.WILD_GRAPES, VBItems.WILD_WHITE_GRAPES, VBItems.WILD_HOPS, VBItems.WILD_BARLEY);
			add(tag(VBTags.FD_WILD_CROPS_ITEM), VBItems.WILD_GRAPES, VBItems.WILD_WHITE_GRAPES, VBItems.WILD_HOPS, VBItems.WILD_BARLEY);
			// FD's knife cuts straw from ripe barley as from ripe wheat (the barley loot table); both tags are empty without FD
			tag(VBTags.STRAW).addOptional(ResourceLocation.fromNamespaceAndPath(VBTags.FARMERS_DELIGHT, "straw"));
			tag(VBTags.STRAW_HARVESTERS).addOptionalTag(VBTags.FD_STRAW_HARVESTERS);
			add(tag(VBTags.FD_FEASTS_ITEM), jexxme.vineandbarrel.compat.farmersdelight.FDCompat.COQ_AU_VIN_BLOCK_ITEM);

			add(tag(VBTags.SEASONS_AUTUMN_CROPS_ITEM), VBItems.RED_GRAPES, VBItems.WHITE_GRAPES, VBItems.HOPS);
			add(tag(VBTags.SEASONS_SUMMER_CROPS_ITEM), VBItems.HOPS, VBItems.BARLEY_SEEDS);
			add(tag(VBTags.SEASONS_SPRING_CROPS_ITEM), VBItems.BARLEY_SEEDS);

			add(tag(commonItem("crops/barley")), VBItems.BARLEY);
			add(tag(commonItem("crops/grape")), VBItems.RED_GRAPES, VBItems.WHITE_GRAPES);
			add(tag(commonItem("crops/hops")), VBItems.HOPS);
			tag(Tags.Items.CROPS).addTag(commonItem("crops/barley")).addTag(commonItem("crops/grape")).addTag(commonItem("crops/hops"));
			add(tag(commonItem("seeds/barley")), VBItems.BARLEY_SEEDS);
			tag(Tags.Items.SEEDS).addTag(commonItem("seeds/barley"));
			add(tag(commonItem("storage_blocks/barley")), VBItems.BARLEY_BALE);
			add(tag(commonItem("storage_blocks/grape")), VBItems.GRAPE_CRATE, VBItems.WHITE_GRAPE_CRATE);
			add(tag(commonItem("storage_blocks/hops")), VBItems.HOPS_CRATE);
			tag(Tags.Items.STORAGE_BLOCKS).addTag(commonItem("storage_blocks/barley"))
					.addTag(commonItem("storage_blocks/grape")).addTag(commonItem("storage_blocks/hops"));
		}
	}

	// ------------------------------------------------------------------ biomes (wild crops, DESIGN 3)
	public static class Biomes extends TagsProvider<Biome>
	{
		public Biomes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, ExistingFileHelper existingFileHelper) {
			super(output, Registries.BIOME, lookupProvider, VineAndBarrel.MODID, existingFileHelper);
		}

		private static ResourceLocation bop(String path) {
			return ResourceLocation.fromNamespaceAndPath("biomesoplenty", path);
		}

		@Override
		protected void addTags(HolderLookup.Provider provider) {
			// Tag-based, so Biomes O' Plenty, Terralith and others join automatically; the modifiers add a temperature filter
			tag(VBTags.WILD_GRAPES_WHITELIST).addTag(Tags.Biomes.IS_FOREST).addTag(Tags.Biomes.IS_PLAINS);
			// White grapes like it cooler (0.4-0.6): the birch forests, and the meadows and cherry groves on mountain slopes (the
			// colder groves and snowy slopes are left out by temperature). Birch forests, at exactly 0.6, are where red and white meet
			tag(VBTags.WILD_WHITE_GRAPES_WHITELIST).addTag(Tags.Biomes.IS_FOREST).addTag(Tags.Biomes.IS_MOUNTAIN_SLOPE);
			tag(VBTags.WILD_HOPS_WHITELIST).addTag(Tags.Biomes.IS_FOREST).addTag(Tags.Biomes.IS_RIVER);
			tag(VBTags.WILD_BARLEY_WHITELIST).addTag(Tags.Biomes.IS_PLAINS).addTag(Tags.Biomes.IS_SAVANNA);

			// Named Biomes O' Plenty biomes, always (no temperature filter); optional, so nothing breaks without BoP
			tag(VBTags.WILD_GRAPES_EXTRA).addOptional(bop("mediterranean_forest")).addOptional(bop("orchard"));
			tag(VBTags.WILD_WHITE_GRAPES_EXTRA).addOptional(bop("lavender_field")).addOptional(bop("lavender_forest"));
			tag(VBTags.WILD_HOPS_EXTRA).addOptional(bop("woodland")).addOptional(bop("seasonal_forest"));
			tag(VBTags.WILD_BARLEY_EXTRA).addOptional(bop("field")).addOptional(bop("pasture")).addOptional(bop("prairie"));

			// Never in mushroom islands or caves; the named biomes are left to their own modifier so they never get it twice
			tag(VBTags.WILD_GRAPES_BLACKLIST).addTag(Tags.Biomes.IS_MUSHROOM).addTag(Tags.Biomes.IS_UNDERGROUND).addTag(VBTags.WILD_GRAPES_EXTRA);
			tag(VBTags.WILD_WHITE_GRAPES_BLACKLIST).addTag(Tags.Biomes.IS_MUSHROOM).addTag(Tags.Biomes.IS_UNDERGROUND)
					.addTag(VBTags.WILD_WHITE_GRAPES_EXTRA);
			tag(VBTags.WILD_HOPS_BLACKLIST).addTag(Tags.Biomes.IS_MUSHROOM).addTag(Tags.Biomes.IS_UNDERGROUND).addTag(VBTags.WILD_HOPS_EXTRA);
			tag(VBTags.WILD_BARLEY_BLACKLIST).addTag(Tags.Biomes.IS_MUSHROOM).addTag(Tags.Biomes.IS_UNDERGROUND).addTag(VBTags.WILD_BARLEY_EXTRA);
		}
	}
}
