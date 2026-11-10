package jexxme.vineandbarrel.tag;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

/**
 * Our own tags, plus the tags of other mods we read or fill. Foreign tags are only referenced by id: there is no
 * compile-time dependency on Farmer's Delight or Serene Seasons, and an unknown tag is simply empty.
 */
public final class VBTags
{
	private VBTags() {
	}

	// ------------------------------------------------------------------ ours
	/** Blocks that make a campfire above them send up signal smoke (the Barley Bale, like a hay bale). */
	public static final TagKey<Block> CAMPFIRE_SIGNAL_SMOKE = block(VineAndBarrel.MODID, "campfire_signal_smoke");
	public static final TagKey<Block> WILD_CROPS = block(VineAndBarrel.MODID, "wild_crops");
	/** Bales soft enough to sleep on: the hay bale, our Barley Bale, FD's straw and rice bales. */
	public static final TagKey<Block> HAY_BALES = block(VineAndBarrel.MODID, "hay_bales");
	/**
	 * What a ripe grape or hop bush climbs: the Trellis, and {@code #farmersdelight:ropes} as an optional entry. A rope
	 * grows a vine on a rope (the rope comes back when the vine goes), the Trellis a vine on a trellis (the trellis comes
	 * back). Anything else in the tag is ignored (see {@code VineClimbing}).
	 */
	public static final TagKey<Block> VINE_SUPPORTS = block(VineAndBarrel.MODID, "vine_supports");
	/**
	 * Blocks a grape or hop plant also climbs, with one fewer fruit per pick than on a trellis: fences, walls, chains
	 * (standing upright) and bars, plus whatever a pack adds. The vine keeps the exact block inside and gives it back
	 * (see {@code VineSupports}).
	 */
	public static final TagKey<Block> MAKESHIFT_VINE_SUPPORTS = block(VineAndBarrel.MODID, "makeshift_vine_supports");
	public static final TagKey<Item> WILD_CROPS_ITEM = item(VineAndBarrel.MODID, "wild_crops");
	/** FD's straw (empty without FD), and the tools that cut it from ripe barley: FD's straw harvesters, its knives. */
	/**
	 * A drink young or aged, as one ingredient of a Farmer's Delight cooking pot recipe (on this Minecraft FD's pot
	 * reads each ingredient as one item or one tag, never a list).
	 */
	public static final TagKey<Item> COOKING_BARLEY_ALE = item(VineAndBarrel.MODID, "cooking/barley_ale");
	public static final TagKey<Item> COOKING_RED_WINE = item(VineAndBarrel.MODID, "cooking/red_wine");
	public static final TagKey<Item> COOKING_HARD_CIDER = item(VineAndBarrel.MODID, "cooking/hard_cider");
	public static final TagKey<Item> COOKING_WHITE_WINE = item(VineAndBarrel.MODID, "cooking/white_wine");
	public static final TagKey<Item> STRAW = item(VineAndBarrel.MODID, "straw");
	public static final TagKey<Item> STRAW_HARVESTERS = item(VineAndBarrel.MODID, "straw_harvesters");

	public static final TagKey<Biome> WILD_GRAPES_WHITELIST = biome(VineAndBarrel.MODID, "wild_grapes_whitelist");
	public static final TagKey<Biome> WILD_GRAPES_BLACKLIST = biome(VineAndBarrel.MODID, "wild_grapes_blacklist");
	public static final TagKey<Biome> WILD_GRAPES_EXTRA = biome(VineAndBarrel.MODID, "wild_grapes_extra");
	public static final TagKey<Biome> WILD_WHITE_GRAPES_WHITELIST = biome(VineAndBarrel.MODID, "wild_white_grapes_whitelist");
	public static final TagKey<Biome> WILD_WHITE_GRAPES_BLACKLIST = biome(VineAndBarrel.MODID, "wild_white_grapes_blacklist");
	public static final TagKey<Biome> WILD_WHITE_GRAPES_EXTRA = biome(VineAndBarrel.MODID, "wild_white_grapes_extra");
	public static final TagKey<Biome> WILD_HOPS_WHITELIST = biome(VineAndBarrel.MODID, "wild_hops_whitelist");
	public static final TagKey<Biome> WILD_HOPS_BLACKLIST = biome(VineAndBarrel.MODID, "wild_hops_blacklist");
	public static final TagKey<Biome> WILD_HOPS_EXTRA = biome(VineAndBarrel.MODID, "wild_hops_extra");
	public static final TagKey<Biome> WILD_BARLEY_WHITELIST = biome(VineAndBarrel.MODID, "wild_barley_whitelist");
	public static final TagKey<Biome> WILD_BARLEY_BLACKLIST = biome(VineAndBarrel.MODID, "wild_barley_blacklist");
	public static final TagKey<Biome> WILD_BARLEY_EXTRA = biome(VineAndBarrel.MODID, "wild_barley_extra");

	// ------------------------------------------------------------------ Farmer's Delight (optional)
	public static final String FARMERS_DELIGHT = "farmersdelight";
	/** FD's ropes. A mature grape or hop bush with one of these above climbs it (like FD's tomatoes), through {@link #VINE_SUPPORTS}. */
	public static final TagKey<Block> FD_ROPES = block(FARMERS_DELIGHT, "ropes");
	public static final TagKey<Block> FD_CAMPFIRE_SIGNAL_SMOKE = block(FARMERS_DELIGHT, "campfire_signal_smoke");
	public static final TagKey<Block> FD_WILD_CROPS = block(FARMERS_DELIGHT, "wild_crops");
	public static final TagKey<Item> FD_WILD_CROPS_ITEM = item(FARMERS_DELIGHT, "wild_crops");
	public static final TagKey<Item> FD_STRAW_HARVESTERS = item(FARMERS_DELIGHT, "straw_harvesters");
	public static final TagKey<Block> FD_FEASTS = block(FARMERS_DELIGHT, "feasts");
	/**
	 * What FD's tomato vines climb besides ropes, where Farmer's Delight Neoforged reads it (NeoForge): we put the Trellis
	 * in it. FD Refabricated doesn't read it, so on Fabric tomatoes stay on ropes.
	 */
	public static final TagKey<Block> FD_TOMATO_SUPPORTS = block(FARMERS_DELIGHT, "tomato_supports");
	public static final TagKey<Item> FD_FEASTS_ITEM = item(FARMERS_DELIGHT, "feasts");
	/** FD's own rope block: put back when a vine on a rope is removed (FD's "rope permanence"). */
	public static final ResourceLocation FD_ROPE = new ResourceLocation(FARMERS_DELIGHT, "rope");

	// ------------------------------------------------------------------ Serene Seasons (optional)
	public static final String SERENE_SEASONS = "sereneseasons";
	public static final TagKey<Block> SEASONS_SPRING_CROPS = block(SERENE_SEASONS, "spring_crops");
	public static final TagKey<Block> SEASONS_SUMMER_CROPS = block(SERENE_SEASONS, "summer_crops");
	public static final TagKey<Block> SEASONS_AUTUMN_CROPS = block(SERENE_SEASONS, "autumn_crops");
	public static final TagKey<Item> SEASONS_SPRING_CROPS_ITEM = item(SERENE_SEASONS, "spring_crops");
	public static final TagKey<Item> SEASONS_SUMMER_CROPS_ITEM = item(SERENE_SEASONS, "summer_crops");
	public static final TagKey<Item> SEASONS_AUTUMN_CROPS_ITEM = item(SERENE_SEASONS, "autumn_crops");

	private static TagKey<Block> block(String namespace, String path) {
		return TagKey.create(Registries.BLOCK, new ResourceLocation(namespace, path));
	}

	private static TagKey<Item> item(String namespace, String path) {
		return TagKey.create(Registries.ITEM, new ResourceLocation(namespace, path));
	}

	private static TagKey<Biome> biome(String namespace, String path) {
		return TagKey.create(Registries.BIOME, new ResourceLocation(namespace, path));
	}
}
