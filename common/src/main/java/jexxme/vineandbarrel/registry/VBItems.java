package jexxme.vineandbarrel.registry;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.block.ClimbingPlantItem;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import jexxme.vineandbarrel.platform.registry.DeferredBlock;
import jexxme.vineandbarrel.platform.registry.DeferredItem;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Composter chances use vanilla's level for the same kind of thing (26.2: NeoForge's compostables data map, written by
 * {@code VBDataGenerators} from {@link #COMPOSTABLES}):
 * grapes (red and white) and hops like sweet berries (low), barley like wheat (medium), seeds low, wild crops medium (FD),
 * the bale like a hay bale (medium-high).
 */
public final class VBItems
{
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VineAndBarrel.MODID);
	/** Every compostable item and its chance; vanilla's chances, under the names 26.3 gives them. */
	public static final Map<DeferredItem<?>, Float> COMPOSTABLES = new LinkedHashMap<>();
	/** Every item that burns as fuel, and its burn time (26.2: NeoForge's furnace_fuels data map). */
	public static final Map<DeferredItem<?>, Integer> FUELS = new LinkedHashMap<>();
	private static final float COMPOSTABLE_LOW = 0.3F;
	private static final float COMPOSTABLE_MEDIUM = 0.65F;
	private static final float COMPOSTABLE_MEDIUM_HIGH = 0.85F;
	private static final int COOKING_TIME_WOOD_BLOCKS = 300;

	// ------------------------------------------------------------------ produce (grapes and hops plant their bush, like sweet berries,
	// also straight into a fence, wall, chain or bars standing on soil)
	public static final DeferredItem<BlockItem> RED_GRAPES = compostable(ITEMS.registerItem("red_grapes",
			p -> new ClimbingPlantItem(VBBlocks.GRAPE_BUSH.get(), p.useItemDescriptionPrefix()),
			() -> new Item.Properties().food(Foods.SWEET_BERRIES)), COMPOSTABLE_LOW);
	public static final DeferredItem<BlockItem> WHITE_GRAPES = compostable(ITEMS.registerItem("white_grapes",
			p -> new ClimbingPlantItem(VBBlocks.WHITE_GRAPE_BUSH.get(), p.useItemDescriptionPrefix()),
			() -> new Item.Properties().food(Foods.SWEET_BERRIES)), COMPOSTABLE_LOW);
	public static final DeferredItem<BlockItem> HOPS = compostable(ITEMS.registerItem("hops",
			p -> new ClimbingPlantItem(VBBlocks.HOP_BUSH.get(), p.useItemDescriptionPrefix()),
			() -> new Item.Properties()), COMPOSTABLE_LOW);
	public static final DeferredItem<BlockItem> BARLEY_SEEDS = compostable(ITEMS.registerItem("barley_seeds",
			p -> new BlockItem(VBBlocks.BARLEY.get(), p.useItemDescriptionPrefix()),
			() -> new Item.Properties()), COMPOSTABLE_LOW);
	public static final DeferredItem<Item> BARLEY = compostable(ITEMS.registerSimpleItem("barley",
			() -> new Item.Properties()), COMPOSTABLE_MEDIUM);

	// ------------------------------------------------------------------ the Trellis the bushes climb
	public static final DeferredItem<BlockItem> TRELLIS = fuel(ITEMS.registerSimpleBlockItem(VBBlocks.TRELLIS),
			COOKING_TIME_WOOD_BLOCKS); // a ladder's burn time

	// ------------------------------------------------------------------ wild crops
	public static final DeferredItem<BlockItem> WILD_GRAPES = block(VBBlocks.WILD_GRAPES, COMPOSTABLE_MEDIUM);
	public static final DeferredItem<BlockItem> WILD_HOPS = block(VBBlocks.WILD_HOPS, COMPOSTABLE_MEDIUM);
	public static final DeferredItem<BlockItem> WILD_WHITE_GRAPES = block(VBBlocks.WILD_WHITE_GRAPES, COMPOSTABLE_MEDIUM);
	public static final DeferredItem<BlockItem> WILD_BARLEY = block(VBBlocks.WILD_BARLEY, COMPOSTABLE_MEDIUM);

	// ------------------------------------------------------------------ crop storage
	public static final DeferredItem<BlockItem> GRAPE_CRATE = ITEMS.registerSimpleBlockItem(VBBlocks.GRAPE_CRATE);
	public static final DeferredItem<BlockItem> WHITE_GRAPE_CRATE = ITEMS.registerSimpleBlockItem(VBBlocks.WHITE_GRAPE_CRATE);
	public static final DeferredItem<BlockItem> HOPS_CRATE = ITEMS.registerSimpleBlockItem(VBBlocks.HOPS_CRATE);
	public static final DeferredItem<BlockItem> BARLEY_BALE = block(VBBlocks.BARLEY_BALE, COMPOSTABLE_MEDIUM_HIGH);

	private VBItems() {
	}

	private static DeferredItem<BlockItem> block(DeferredBlock<? extends Block> block, float compost) {
		return compostable(ITEMS.registerSimpleBlockItem(block), compost);
	}

	private static <T extends DeferredItem<?>> T compostable(T item, float chance) {
		COMPOSTABLES.put(item, chance);
		return item;
	}

	private static <T extends DeferredItem<?>> T fuel(T item, int burnTime) {
		FUELS.put(item, burnTime);
		return item;
	}

	public static void register() {
		ITEMS.register();
	}
}
