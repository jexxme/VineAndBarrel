package jexxme.vineandbarrel.compat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The convention item tags ({@code c:}) our drinks and food join, which more mods read. NeoForge's {@code Tags.Items}
 * and Fabric's {@code ConventionalItemTags} name the same tags; these keys work on both.
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

	private Conventions() {
	}

	private static TagKey<Item> tag(String path) {
		return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
	}
}
