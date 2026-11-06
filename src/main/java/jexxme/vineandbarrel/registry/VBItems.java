package jexxme.vineandbarrel.registry;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Composter chances use vanilla's level for the same kind of thing (26.3: the minecraft:compostable item component):
 * grapes and hops like sweet berries (low), barley like wheat (medium), seeds low, wild crops medium (FD), the bale like
 * a hay bale (medium-high).
 */
public final class VBItems
{
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VineAndBarrel.MODID);

	// ------------------------------------------------------------------ produce (grapes and hops plant their bush, like sweet berries)
	public static final DeferredItem<BlockItem> RED_GRAPES = ITEMS.registerItem("red_grapes",
			p -> new BlockItem(VBBlocks.GRAPE_BUSH.get(), p.useItemDescriptionPrefix()),
			() -> new Item.Properties().food(Foods.SWEET_BERRIES).compostable(ContextIntProviders.COMPOSTABLE_LOW));
	public static final DeferredItem<BlockItem> HOPS = ITEMS.registerItem("hops",
			p -> new BlockItem(VBBlocks.HOP_BUSH.get(), p.useItemDescriptionPrefix()),
			() -> new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_LOW));
	public static final DeferredItem<BlockItem> BARLEY_SEEDS = ITEMS.registerItem("barley_seeds",
			p -> new BlockItem(VBBlocks.BARLEY.get(), p.useItemDescriptionPrefix()),
			() -> new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_LOW));
	public static final DeferredItem<Item> BARLEY = ITEMS.registerSimpleItem("barley",
			() -> new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));

	// ------------------------------------------------------------------ wild crops
	public static final DeferredItem<BlockItem> WILD_GRAPES = block(VBBlocks.WILD_GRAPES, ContextIntProviders.COMPOSTABLE_MEDIUM);
	public static final DeferredItem<BlockItem> WILD_HOPS = block(VBBlocks.WILD_HOPS, ContextIntProviders.COMPOSTABLE_MEDIUM);
	public static final DeferredItem<BlockItem> WILD_BARLEY = block(VBBlocks.WILD_BARLEY, ContextIntProviders.COMPOSTABLE_MEDIUM);

	// ------------------------------------------------------------------ crop storage
	public static final DeferredItem<BlockItem> GRAPE_CRATE = ITEMS.registerSimpleBlockItem(VBBlocks.GRAPE_CRATE);
	public static final DeferredItem<BlockItem> HOPS_CRATE = ITEMS.registerSimpleBlockItem(VBBlocks.HOPS_CRATE);
	public static final DeferredItem<BlockItem> BARLEY_BALE = block(VBBlocks.BARLEY_BALE, ContextIntProviders.COMPOSTABLE_MEDIUM_HIGH);

	private VBItems() {
	}

	private static DeferredItem<BlockItem> block(DeferredBlock<? extends Block> block, ResourceKey<ContextIntProvider> compost) {
		return ITEMS.registerSimpleBlockItem(block, () -> new Item.Properties().compostable(compost));
	}

	public static void register(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
	}
}
