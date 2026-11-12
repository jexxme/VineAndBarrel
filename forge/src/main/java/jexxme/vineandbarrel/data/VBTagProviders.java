package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.entity.AnimalFoods;
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
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import jexxme.vineandbarrel.platform.registry.DeferredHolder;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Block, item and biome tags. Every vanilla tag here copies what vanilla does for the matching vanilla thing.
 * <p>
 * Convention tags on 1.20.1 have one name per loader: Forge mods read {@code forge:} tags ({@code forge:crops/onion},
 * {@code forge:tools/knives}), Fabric mods {@code c:} ones ({@code c:crops/onion}, {@code c:tools/knives}), and some
 * differ in more than the namespace ({@code forge:raw_beef} / {@code c:foods/raw_meats/raw_beef}). The data files are
 * shared by both loaders, so: a convention tag we fill is written under both names ({@link #forgeItem} and
 * {@link #commonItem}), and where a recipe or loot table of ours reads one, it reads a tag of ours that holds both
 * loaders' tags as optional entries ({@link #SHEARS}, the {@code vineandbarrel:cooking/...} and
 * {@code vineandbarrel:crafting/...} tags), so one JSON file works on both.
 */
public final class VBTagProviders
{
	private VBTagProviders() {
	}

	// ------------------------------------------------------------------ tags of ours that stand for a tag of either loader
	/** Shears: {@code #forge:shears} (Forge) and {@code #c:shears} (Fabric API); the wild crops' loot tables read it. */
	static final TagKey<Item> SHEARS = ourItem("shears");
	/** Ingredients of the Farmer's Delight recipes ({@link FdRecipes}), next to {@code VBTags.COOKING_*} for the drinks. */
	static final TagKey<Item> COOKING_WHEAT = ourItem("cooking/wheat");
	static final TagKey<Item> COOKING_CARROT = ourItem("cooking/carrot");
	static final TagKey<Item> COOKING_ONION = ourItem("cooking/onion");
	static final TagKey<Item> COOKING_RICE = ourItem("cooking/rice");
	static final TagKey<Item> COOKING_BARLEY = ourItem("cooking/barley");
	static final TagKey<Item> COOKING_RAW_FISH = ourItem("cooking/raw_fish");
	static final TagKey<Item> COOKING_RAW_BEEF = ourItem("cooking/raw_beef");
	static final TagKey<Item> COOKING_RAW_PORK = ourItem("cooking/raw_pork");
	static final TagKey<Item> COOKING_RAW_MUTTON = ourItem("cooking/raw_mutton");
	static final TagKey<Item> COOKING_RAW_CHICKEN = ourItem("cooking/raw_chicken");
	static final TagKey<Item> COOKING_RAW_BACON = ourItem("cooking/raw_bacon");
	/** The cutting board's tool for our wild crops. */
	static final TagKey<Item> COOKING_KNIVES = ourItem("cooking/knives");
	/** Ingredients of the hand-written crafting recipes (aging barrel, brew kettle, mug, wine glass). */
	static final TagKey<Item> CRAFTING_IRON_NUGGETS = ourItem("crafting/iron_nuggets");
	static final TagKey<Item> CRAFTING_COPPER_INGOTS = ourItem("crafting/copper_ingots");
	static final TagKey<Item> CRAFTING_COLORLESS_GLASS = ourItem("crafting/colorless_glass");
	static final TagKey<Item> CRAFTING_WATER_BUCKETS = ourItem("crafting/water_buckets");

	/** Block and item tags take the blocks and items themselves. */
	@SafeVarargs
	private static <T> void add(IntrinsicTagAppender<T> appender, DeferredHolder<? super T, ? extends T>... holders) {
		Arrays.stream(holders).forEach(holder -> appender.add(holder.get()));
	}

	private static TagKey<Item> ourItem(String path) {
		return TagKey.create(Registries.ITEM, VineAndBarrel.id(path));
	}

	/** A Fabric convention tag ({@code c:}). */
	static TagKey<Item> commonItem(String path) {
		return TagKey.create(Registries.ITEM, new ResourceLocation("c", path));
	}

	private static TagKey<Block> commonBlock(String path) {
		return TagKey.create(Registries.BLOCK, new ResourceLocation("c", path));
	}

	private static TagKey<Biome> commonBiome(String path) {
		return TagKey.create(Registries.BIOME, new ResourceLocation("c", path));
	}

	/** A Forge convention tag ({@code forge:}). */
	static TagKey<Item> forgeItem(String path) {
		return TagKey.create(Registries.ITEM, new ResourceLocation("forge", path));
	}

	private static TagKey<Block> forgeBlock(String path) {
		return TagKey.create(Registries.BLOCK, new ResourceLocation("forge", path));
	}

	private static TagKey<Biome> forgeBiome(String path) {
		return TagKey.create(Registries.BIOME, new ResourceLocation("forge", path));
	}

	private static ResourceLocation fdBlock(String path) {
		return new ResourceLocation(VBTags.FARMERS_DELIGHT, path);
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
			vineSupports.addOptionalTag(VBTags.FD_ROPES.location());
			add(vineSupports, VBBlocks.TRELLIS);
			// ... and, with one fewer fruit per pick, fences, walls, chains (upright) and bars; packs add their own here.
			// 1.20.1 has one chain and one kind of bars, and no #minecraft:chains or #minecraft:bars: the blocks themselves
			tag(VBTags.MAKESHIFT_VINE_SUPPORTS).addTag(BlockTags.FENCES).addTag(BlockTags.WALLS)
					.add(net.minecraft.world.level.block.Blocks.CHAIN).add(net.minecraft.world.level.block.Blocks.IRON_BARS);
			// (no #farmersdelight:tomato_supports: neither Farmer's Delight 1.20.1 nor FD Refabricated reads it, so FD's
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

			// Storage blocks, under both loaders' names (forge:storage_blocks/... and c:storage_blocks/...)
			storageBlocks("forge");
			storageBlocks("c");
		}

		private void storageBlocks(String namespace) {
			TagKey<Block> barley = namespace.equals("c") ? commonBlock("storage_blocks/barley") : forgeBlock("storage_blocks/barley");
			TagKey<Block> grape = namespace.equals("c") ? commonBlock("storage_blocks/grape") : forgeBlock("storage_blocks/grape");
			TagKey<Block> hops = namespace.equals("c") ? commonBlock("storage_blocks/hops") : forgeBlock("storage_blocks/hops");
			TagKey<Block> all = namespace.equals("c") ? commonBlock("storage_blocks") : forgeBlock("storage_blocks");
			add(tag(barley), VBBlocks.BARLEY_BALE);
			add(tag(grape), VBBlocks.GRAPE_CRATE, VBBlocks.WHITE_GRAPE_CRATE);
			add(tag(hops), VBBlocks.HOPS_CRATE);
			tag(all).addTag(barley).addTag(grape).addTag(hops);
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
			// C3: barley feeds and breeds everything wheat does; the bale feeds like a hay bale; seeds feed chickens and parrots.
			// Vanilla 1.20.1 has none of these eight tags (each animal's foods are fixed in code): our mixins read them
			// (AnimalFoods), so the same files as on later versions do the same here
			add(tag(AnimalFoods.COW_FOOD), VBItems.BARLEY);
			add(tag(AnimalFoods.SHEEP_FOOD), VBItems.BARLEY);
			add(tag(AnimalFoods.GOAT_FOOD), VBItems.BARLEY);
			add(tag(AnimalFoods.HORSE_FOOD), VBItems.BARLEY, VBItems.BARLEY_BALE);
			add(tag(AnimalFoods.LLAMA_FOOD), VBItems.BARLEY, VBItems.BARLEY_BALE);
			add(tag(AnimalFoods.LLAMA_TEMPT_ITEMS), VBItems.BARLEY_BALE);
			add(tag(AnimalFoods.CHICKEN_FOOD), VBItems.BARLEY_SEEDS);
			add(tag(AnimalFoods.PARROT_FOOD), VBItems.BARLEY_SEEDS);
			// C4: farmers plant barley seeds (and so pick them up), like vanilla's wheat and beetroot and FD's crops. That they
			// also pick up the barley they harvest is code on 1.20.1 (VillagerWantsBarleyMixin): it has no
			// #minecraft:villager_picks_up
			add(tag(ItemTags.VILLAGER_PLANTABLE_SEEDS), VBItems.BARLEY_SEEDS);
			// C5: foxes love grapes (and can be bred with them, like sweet berries)
			add(tag(ItemTags.FOX_FOOD), VBItems.RED_GRAPES, VBItems.WHITE_GRAPES);

			// A drink of either age as one cooking pot ingredient (FdRecipes)
			add(tag(VBTags.COOKING_BARLEY_ALE), DrinkItems.BARLEY_ALE, DrinkItems.AGED_BARLEY_ALE);
			add(tag(VBTags.COOKING_RED_WINE), DrinkItems.RED_WINE, DrinkItems.AGED_RED_WINE);
			add(tag(VBTags.COOKING_HARD_CIDER), DrinkItems.HARD_CIDER, DrinkItems.AGED_HARD_CIDER);
			add(tag(VBTags.COOKING_WHITE_WINE), DrinkItems.WHITE_WINE, DrinkItems.AGED_WHITE_WINE);
			// The grape juices are brewed from these two tags, so Kaleidoscope Tavern's grapes brew like ours (without that
			// mod each tag holds our grape alone). The other way round is data/vineandbarrel/recipes/pressing_tub
			add(tag(VBTags.RED_GRAPES), VBItems.RED_GRAPES);
			tag(VBTags.RED_GRAPES).addOptional(new ResourceLocation(VBTags.KALEIDOSCOPE_TAVERN, "grape"));
			add(tag(VBTags.WHITE_GRAPES), VBItems.WHITE_GRAPES);
			tag(VBTags.WHITE_GRAPES).addOptional(new ResourceLocation(VBTags.KALEIDOSCOPE_TAVERN, "green_grape"));

			add(tag(VBTags.WILD_CROPS_ITEM), VBItems.WILD_GRAPES, VBItems.WILD_WHITE_GRAPES, VBItems.WILD_HOPS, VBItems.WILD_BARLEY);
			add(tag(VBTags.FD_WILD_CROPS_ITEM), VBItems.WILD_GRAPES, VBItems.WILD_WHITE_GRAPES, VBItems.WILD_HOPS, VBItems.WILD_BARLEY);
			// FD's knife cuts straw from ripe barley as from ripe wheat (the barley loot table); both tags are empty without FD
			tag(VBTags.STRAW).addOptional(new ResourceLocation(VBTags.FARMERS_DELIGHT, "straw"));
			tag(VBTags.STRAW_HARVESTERS).addOptionalTag(VBTags.FD_STRAW_HARVESTERS.location());
			add(tag(VBTags.FD_FEASTS_ITEM), jexxme.vineandbarrel.compat.farmersdelight.FDCompat.COQ_AU_VIN_BLOCK_ITEM);

			add(tag(VBTags.SEASONS_AUTUMN_CROPS_ITEM), VBItems.RED_GRAPES, VBItems.WHITE_GRAPES, VBItems.HOPS);
			add(tag(VBTags.SEASONS_SUMMER_CROPS_ITEM), VBItems.HOPS, VBItems.BARLEY_SEEDS);
			add(tag(VBTags.SEASONS_SPRING_CROPS_ITEM), VBItems.BARLEY_SEEDS);

			// Crops, seeds and storage blocks, under both loaders' names (forge:crops/barley and c:crops/barley, ...)
			conventions("forge");
			conventions("c");

			// Either loader's tag as one ingredient or tool: the vanilla item itself where neither Farmer's Delight build
			// fills the tag on its loader (FD Refabricated 1.20.1 reads c:crops/wheat and c:crops/carrot but ships neither),
			// then both loaders' tags as optional entries
			tag(SHEARS).addOptionalTag(forgeItem("shears").location()).addOptionalTag(commonItem("shears").location());
			either(tag(COOKING_WHEAT).add(net.minecraft.world.item.Items.WHEAT), "crops/wheat", "crops/wheat");
			either(tag(COOKING_CARROT).add(net.minecraft.world.item.Items.CARROT), "crops/carrot", "crops/carrot");
			either(tag(COOKING_ONION), "crops/onion", "crops/onion");
			either(tag(COOKING_RICE), "crops/rice", "crops/rice");
			tag(COOKING_BARLEY).addTag(forgeItem("crops/barley")).addTag(commonItem("crops/barley"));
			either(tag(COOKING_RAW_FISH), "raw_fishes", "foods/raw_fishes");
			either(tag(COOKING_RAW_BEEF), "raw_beef", "foods/raw_meats/raw_beef");
			either(tag(COOKING_RAW_PORK), "raw_pork", "foods/raw_meats/raw_pork");
			either(tag(COOKING_RAW_MUTTON), "raw_mutton", "foods/raw_meats/raw_mutton");
			either(tag(COOKING_RAW_CHICKEN), "raw_chicken", "foods/raw_meats/raw_chicken");
			either(tag(COOKING_RAW_BACON), "raw_bacon", "foods/raw_meats/raw_bacon");
			either(tag(COOKING_KNIVES), "tools/knives", "tools/knives");
			either(tag(CRAFTING_IRON_NUGGETS).add(net.minecraft.world.item.Items.IRON_NUGGET), "nuggets/iron", "iron_nuggets");
			either(tag(CRAFTING_COPPER_INGOTS).add(net.minecraft.world.item.Items.COPPER_INGOT), "ingots/copper", "copper_ingots");
			// Fabric API 1.20.1 has no tag of plain glass only (its c:glass_blocks holds the stained ones too)
			tag(CRAFTING_COLORLESS_GLASS).add(net.minecraft.world.item.Items.GLASS).addOptionalTag(forgeItem("glass/colorless").location());
			either(tag(CRAFTING_WATER_BUCKETS).add(net.minecraft.world.item.Items.WATER_BUCKET), "buckets/water", "water_buckets");
		}

		/** Adds the Forge tag and the Fabric tag of the same thing, both optional (each exists on its own loader only). */
		private static void either(TagsProvider.TagAppender<Item> appender, String forgePath, String commonPath) {
			appender.addOptionalTag(forgeItem(forgePath).location()).addOptionalTag(commonItem(commonPath).location());
		}

		private void conventions(String namespace) {
			boolean common = namespace.equals("c");
			TagKey<Item> barley = common ? commonItem("crops/barley") : forgeItem("crops/barley");
			TagKey<Item> grape = common ? commonItem("crops/grape") : forgeItem("crops/grape");
			TagKey<Item> hops = common ? commonItem("crops/hops") : forgeItem("crops/hops");
			add(tag(barley), VBItems.BARLEY);
			add(tag(grape), VBItems.RED_GRAPES, VBItems.WHITE_GRAPES);
			add(tag(hops), VBItems.HOPS);
			tag(common ? commonItem("crops") : forgeItem("crops")).addTag(barley).addTag(grape).addTag(hops);
			TagKey<Item> barleySeeds = common ? commonItem("seeds/barley") : forgeItem("seeds/barley");
			add(tag(barleySeeds), VBItems.BARLEY_SEEDS);
			tag(common ? commonItem("seeds") : forgeItem("seeds")).addTag(barleySeeds);
			TagKey<Item> barleyBlocks = common ? commonItem("storage_blocks/barley") : forgeItem("storage_blocks/barley");
			TagKey<Item> grapeBlocks = common ? commonItem("storage_blocks/grape") : forgeItem("storage_blocks/grape");
			TagKey<Item> hopsBlocks = common ? commonItem("storage_blocks/hops") : forgeItem("storage_blocks/hops");
			add(tag(barleyBlocks), VBItems.BARLEY_BALE);
			add(tag(grapeBlocks), VBItems.GRAPE_CRATE, VBItems.WHITE_GRAPE_CRATE);
			add(tag(hopsBlocks), VBItems.HOPS_CRATE);
			tag(common ? commonItem("storage_blocks") : forgeItem("storage_blocks")).addTag(barleyBlocks).addTag(grapeBlocks).addTag(hopsBlocks);
		}
	}

	// ------------------------------------------------------------------ biomes (wild crops, DESIGN 3)
	public static class Biomes extends TagsProvider<Biome>
	{
		public Biomes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, ExistingFileHelper existingFileHelper) {
			super(output, Registries.BIOME, lookupProvider, VineAndBarrel.MODID, existingFileHelper);
		}

		private static ResourceLocation bop(String path) {
			return new ResourceLocation("biomesoplenty", path);
		}

		/**
		 * 1.20.1 has no biome tags both loaders share (later versions' {@code c:is_forest}, {@code c:is_plains}, ...), and
		 * the two loaders' own don't hold the same biomes (Forge's {@code forge:is_plains} also has the meadow and snowy
		 * plains, Fabric's {@code c:mountain_slope} only the snowy slopes). So each list names what the later versions' tag
		 * holds (a vanilla tag, or the vanilla biomes themselves) and then both loaders' tags as optional entries, through
		 * which other mods' biomes join. Vanilla biomes those optional tags add are all outside the modifiers' temperature
		 * ranges, except the meadow for barley on Forge: see the barley blacklist.
		 */
		@Override
		protected void addTags(HolderLookup.Provider provider) {
			// Tag-based, so Biomes O' Plenty, Terralith and others join automatically; the modifiers add a temperature filter
			plains(forest(tag(VBTags.WILD_GRAPES_WHITELIST)));
			// White grapes like it cooler (0.4-0.6): the birch forests, and the meadows and cherry groves on mountain slopes (the
			// colder groves and snowy slopes are left out by temperature). Birch forests, at exactly 0.6, are where red and white meet
			forest(tag(VBTags.WILD_WHITE_GRAPES_WHITELIST)).add(net.minecraft.world.level.biome.Biomes.SNOWY_SLOPES).add(net.minecraft.world.level.biome.Biomes.MEADOW).add(net.minecraft.world.level.biome.Biomes.GROVE).add(net.minecraft.world.level.biome.Biomes.CHERRY_GROVE)
					.addOptionalTag(forgeBiome("is_slope").location()).addOptionalTag(commonBiome("mountain_slope").location());
			forest(tag(VBTags.WILD_HOPS_WHITELIST)).addTag(BiomeTags.IS_RIVER).addOptionalTag(commonBiome("river").location());
			plains(tag(VBTags.WILD_BARLEY_WHITELIST)).addTag(BiomeTags.IS_SAVANNA).addOptionalTag(commonBiome("savanna").location());

			// Named Biomes O' Plenty biomes, always (no temperature filter); optional, so nothing breaks without BoP
			tag(VBTags.WILD_GRAPES_EXTRA).addOptional(bop("mediterranean_forest")).addOptional(bop("orchard"));
			tag(VBTags.WILD_WHITE_GRAPES_EXTRA).addOptional(bop("lavender_field")).addOptional(bop("lavender_forest"));
			tag(VBTags.WILD_HOPS_EXTRA).addOptional(bop("woodland")).addOptional(bop("seasonal_forest"));
			tag(VBTags.WILD_BARLEY_EXTRA).addOptional(bop("field")).addOptional(bop("pasture")).addOptional(bop("prairie"));

			// Never in mushroom islands or caves; the named biomes are left to their own modifier so they never get it twice
			never(tag(VBTags.WILD_GRAPES_BLACKLIST)).addTag(VBTags.WILD_GRAPES_EXTRA);
			never(tag(VBTags.WILD_WHITE_GRAPES_BLACKLIST)).addTag(VBTags.WILD_WHITE_GRAPES_EXTRA);
			never(tag(VBTags.WILD_HOPS_BLACKLIST)).addTag(VBTags.WILD_HOPS_EXTRA);
			// The meadow: Forge counts it as plains (#forge:is_plains) and it is warm enough for barley, but wild barley
			// never grew there on later versions (their plains tag is the plains and sunflower plains only)
			never(tag(VBTags.WILD_BARLEY_BLACKLIST)).add(net.minecraft.world.level.biome.Biomes.MEADOW).addTag(VBTags.WILD_BARLEY_EXTRA);
		}

		/** Later versions' {@code c:is_forest}: vanilla's forests. */
		private static TagAppender<Biome> forest(TagAppender<Biome> appender) {
			return appender.addTag(BiomeTags.IS_FOREST).addOptionalTag(commonBiome("forest").location());
		}

		/** Later versions' {@code c:is_plains}: the plains and sunflower plains. */
		private static TagAppender<Biome> plains(TagAppender<Biome> appender) {
			return appender.add(net.minecraft.world.level.biome.Biomes.PLAINS).add(net.minecraft.world.level.biome.Biomes.SUNFLOWER_PLAINS)
					.addOptionalTag(forgeBiome("is_plains").location()).addOptionalTag(commonBiome("plains").location());
		}

		/** Later versions' {@code c:is_mushroom} and {@code c:is_underground}: the mushroom fields and the three cave biomes. */
		private static TagAppender<Biome> never(TagAppender<Biome> appender) {
			return appender.add(net.minecraft.world.level.biome.Biomes.MUSHROOM_FIELDS).add(net.minecraft.world.level.biome.Biomes.LUSH_CAVES).add(net.minecraft.world.level.biome.Biomes.DRIPSTONE_CAVES).add(net.minecraft.world.level.biome.Biomes.DEEP_DARK)
					.addOptionalTag(forgeBiome("is_mushroom").location()).addOptionalTag(forgeBiome("is_underground").location())
					.addOptionalTag(commonBiome("mushroom").location()).addOptionalTag(commonBiome("underground").location());
		}
	}
}
