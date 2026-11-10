package jexxme.vineandbarrel.compat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The convention item tags our drinks and food join. The {@code c:} keys are the names later Minecraft versions share
 * on both loaders; on 1.20.1 they are kept as they are (of these only {@code c:foods} is a tag others fill too, Fabric
 * API's). Where a mod of this Minecraft reads a different name, that name is here as well: bread is
 * {@code forge:bread} to Farmer's Delight on Forge and {@code c:foods/breads} to Farmer's Delight Refabricated.
 */
public final class Conventions
{
	public static final TagKey<Item> DRINKS = tag("drinks");
	public static final TagKey<Item> DRINKS_JUICE = tag("drinks/juice");
	public static final TagKey<Item> DRINK_CONTAINING_BOTTLE = tag("drink_containing/bottle");
	public static final TagKey<Item> FOODS = tag("foods");
	public static final TagKey<Item> FOODS_FRUIT = tag("foods/fruit");
	public static final TagKey<Item> FOODS_SOUP = tag("foods/soup");
	public static final TagKey<Item> FOODS_BREAD = tag("foods/bread");
	/** Bread as Farmer's Delight 1.20.1 (Forge) reads it. */
	public static final TagKey<Item> FORGE_BREAD = TagKey.create(Registries.ITEM, new ResourceLocation("forge", "bread"));
	/** Bread as Farmer's Delight Refabricated 1.20.1 (Fabric) reads it. */
	public static final TagKey<Item> FABRIC_BREADS = tag("foods/breads");

	private Conventions() {
	}

	private static TagKey<Item> tag(String path) {
		return TagKey.create(Registries.ITEM, new ResourceLocation("c", path));
	}
}
