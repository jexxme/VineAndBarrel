package jexxme.vineandbarrel.registry;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.block.ClimbingPlantItem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import jexxme.vineandbarrel.platform.registry.DeferredBlock;
import jexxme.vineandbarrel.platform.registry.DeferredItem;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

/**
 * Composter chances use vanilla's level for the same kind of thing (26.3: the minecraft:compostable item component):
 * grapes (red and white) and hops like sweet berries (low), barley like wheat (medium), seeds low, wild crops medium (FD),
 * the bale like a hay bale (medium-high).
 */
public final class VBItems
{
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VineAndBarrel.MODID);

	// ------------------------------------------------------------------ produce (grapes and hops plant their bush, like sweet berries,
	// also straight into a fence, wall, chain or bars standing on soil)
	public static final DeferredItem<BlockItem> RED_GRAPES = ITEMS.registerItem("red_grapes",
			p -> new ClimbingPlantItem(VBBlocks.GRAPE_BUSH.get(), p.useItemDescriptionPrefix()),
			() -> new Item.Properties().food(Foods.SWEET_BERRIES).compostable(ContextIntProviders.COMPOSTABLE_LOW));
	public static final DeferredItem<BlockItem> WHITE_GRAPES = ITEMS.registerItem("white_grapes",
			p -> new ClimbingPlantItem(VBBlocks.WHITE_GRAPE_BUSH.get(), p.useItemDescriptionPrefix()),
			() -> new Item.Properties().food(Foods.SWEET_BERRIES).compostable(ContextIntProviders.COMPOSTABLE_LOW));
	public static final DeferredItem<BlockItem> HOPS = ITEMS.registerItem("hops",
			p -> new ClimbingPlantItem(VBBlocks.HOP_BUSH.get(), p.useItemDescriptionPrefix()),
			() -> new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_LOW));
	public static final DeferredItem<BlockItem> BARLEY_SEEDS = ITEMS.registerItem("barley_seeds",
			p -> new BlockItem(VBBlocks.BARLEY.get(), p.useItemDescriptionPrefix()),
			() -> new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_LOW));
	public static final DeferredItem<Item> BARLEY = ITEMS.registerSimpleItem("barley",
			() -> new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));

	// ------------------------------------------------------------------ the Trellis the bushes climb
	public static final DeferredItem<BlockItem> TRELLIS = ITEMS.registerSimpleBlockItem(VBBlocks.TRELLIS,
			p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS)); // a ladder's burn time

	// ------------------------------------------------------------------ wild crops
	public static final DeferredItem<BlockItem> WILD_GRAPES = block(VBBlocks.WILD_GRAPES, ContextIntProviders.COMPOSTABLE_MEDIUM);
	public static final DeferredItem<BlockItem> WILD_HOPS = block(VBBlocks.WILD_HOPS, ContextIntProviders.COMPOSTABLE_MEDIUM);
	public static final DeferredItem<BlockItem> WILD_WHITE_GRAPES = block(VBBlocks.WILD_WHITE_GRAPES, ContextIntProviders.COMPOSTABLE_MEDIUM);
	public static final DeferredItem<BlockItem> WILD_BARLEY = block(VBBlocks.WILD_BARLEY, ContextIntProviders.COMPOSTABLE_MEDIUM);

	// ------------------------------------------------------------------ crop storage
	public static final DeferredItem<BlockItem> GRAPE_CRATE = ITEMS.registerSimpleBlockItem(VBBlocks.GRAPE_CRATE);
	public static final DeferredItem<BlockItem> WHITE_GRAPE_CRATE = ITEMS.registerSimpleBlockItem(VBBlocks.WHITE_GRAPE_CRATE);
	public static final DeferredItem<BlockItem> HOPS_CRATE = ITEMS.registerSimpleBlockItem(VBBlocks.HOPS_CRATE);
	public static final DeferredItem<BlockItem> BARLEY_BALE = block(VBBlocks.BARLEY_BALE, ContextIntProviders.COMPOSTABLE_MEDIUM_HIGH);

	private VBItems() {
	}

	private static DeferredItem<BlockItem> block(DeferredBlock<? extends Block> block, ResourceKey<ContextIntProvider> compost) {
		return ITEMS.registerSimpleBlockItem(block, () -> new Item.Properties().compostable(compost));
	}

	public static void register() {
		ITEMS.register();
	}
}
