package jexxme.vineandbarrel.tag;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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
	public static final TagKey<Item> WILD_CROPS_ITEM = item(VineAndBarrel.MODID, "wild_crops");

	public static final TagKey<Biome> WILD_GRAPES_WHITELIST = biome(VineAndBarrel.MODID, "wild_grapes_whitelist");
	public static final TagKey<Biome> WILD_GRAPES_BLACKLIST = biome(VineAndBarrel.MODID, "wild_grapes_blacklist");
	public static final TagKey<Biome> WILD_GRAPES_EXTRA = biome(VineAndBarrel.MODID, "wild_grapes_extra");
	public static final TagKey<Biome> WILD_HOPS_WHITELIST = biome(VineAndBarrel.MODID, "wild_hops_whitelist");
	public static final TagKey<Biome> WILD_HOPS_BLACKLIST = biome(VineAndBarrel.MODID, "wild_hops_blacklist");
	public static final TagKey<Biome> WILD_HOPS_EXTRA = biome(VineAndBarrel.MODID, "wild_hops_extra");
	public static final TagKey<Biome> WILD_BARLEY_WHITELIST = biome(VineAndBarrel.MODID, "wild_barley_whitelist");
	public static final TagKey<Biome> WILD_BARLEY_BLACKLIST = biome(VineAndBarrel.MODID, "wild_barley_blacklist");
	public static final TagKey<Biome> WILD_BARLEY_EXTRA = biome(VineAndBarrel.MODID, "wild_barley_extra");

	// ------------------------------------------------------------------ Farmer's Delight (optional)
	public static final String FARMERS_DELIGHT = "farmersdelight";
	/** FD's ropes. A mature grape or hop bush with one of these above climbs it (like FD's tomatoes). */
	public static final TagKey<Block> FD_ROPES = block(FARMERS_DELIGHT, "ropes");
	public static final TagKey<Block> FD_CAMPFIRE_SIGNAL_SMOKE = block(FARMERS_DELIGHT, "campfire_signal_smoke");
	public static final TagKey<Block> FD_WILD_CROPS = block(FARMERS_DELIGHT, "wild_crops");
	public static final TagKey<Item> FD_WILD_CROPS_ITEM = item(FARMERS_DELIGHT, "wild_crops");
	/** FD's own rope block: put back when a vine on a rope is removed (FD's "rope permanence"). */
	public static final Identifier FD_ROPE = Identifier.fromNamespaceAndPath(FARMERS_DELIGHT, "rope");

	// ------------------------------------------------------------------ Serene Seasons (optional)
	public static final String SERENE_SEASONS = "sereneseasons";
	public static final TagKey<Block> SEASONS_SPRING_CROPS = block(SERENE_SEASONS, "spring_crops");
	public static final TagKey<Block> SEASONS_SUMMER_CROPS = block(SERENE_SEASONS, "summer_crops");
	public static final TagKey<Block> SEASONS_AUTUMN_CROPS = block(SERENE_SEASONS, "autumn_crops");
	public static final TagKey<Item> SEASONS_SPRING_CROPS_ITEM = item(SERENE_SEASONS, "spring_crops");
	public static final TagKey<Item> SEASONS_SUMMER_CROPS_ITEM = item(SERENE_SEASONS, "summer_crops");
	public static final TagKey<Item> SEASONS_AUTUMN_CROPS_ITEM = item(SERENE_SEASONS, "autumn_crops");

	private static TagKey<Block> block(String namespace, String path) {
		return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(namespace, path));
	}

	private static TagKey<Item> item(String namespace, String path) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(namespace, path));
	}

	private static TagKey<Biome> biome(String namespace, String path) {
		return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(namespace, path));
	}
}
