package jexxme.vineandbarrel.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * C3/C7: farm animals eat our crops. From 1.20.5 on what an animal eats and follows is an item tag
 * ({@code minecraft:cow_food}, {@code minecraft:llama_tempt_items}, ...) and our data files simply add to those. On this
 * Minecraft each animal has its foods fixed in code, so the same tags (still filled by the same data files; vanilla
 * itself doesn't know them here) are read by our mixins on top of the fixed foods: chickens and parrots take barley
 * seeds, cows, sheep and goats barley, horses and llamas barley and the Barley Bale, and a llama follows the bale.
 */
public final class AnimalFoods
{
	public static final TagKey<Item> CHICKEN_FOOD = tag("chicken_food");
	public static final TagKey<Item> COW_FOOD = tag("cow_food");
	public static final TagKey<Item> SHEEP_FOOD = tag("sheep_food");
	public static final TagKey<Item> GOAT_FOOD = tag("goat_food");
	public static final TagKey<Item> HORSE_FOOD = tag("horse_food");
	public static final TagKey<Item> LLAMA_FOOD = tag("llama_food");
	public static final TagKey<Item> LLAMA_TEMPT_ITEMS = tag("llama_tempt_items");
	/** What tames a parrot. */
	public static final TagKey<Item> PARROT_FOOD = tag("parrot_food");

	private AnimalFoods() {
	}

	/** Whether this animal eats the stack by its food tag (to breed, grow up, heal), besides its vanilla foods. */
	public static boolean eats(Animal animal, ItemStack stack) {
		TagKey<Item> food = foodOf(animal);
		return food != null && stack.is(food);
	}

	/** Whether this animal follows a player holding the stack by its tag, besides what vanilla tempts it with. */
	public static boolean tempts(Mob mob, ItemStack stack) {
		TagKey<Item> tempting = mob instanceof Llama ? LLAMA_TEMPT_ITEMS : mob instanceof AbstractHorse ? null : mob instanceof Animal animal ? foodOf(animal) : null;
		return tempting != null && stack.is(tempting);
	}

	/** The food tag of the animals whose food is a tag of its own on the newer versions and that we feed. */
	private static @Nullable TagKey<Item> foodOf(Animal animal) {
		if (animal instanceof Llama) {
			return LLAMA_FOOD;
		}
		if (animal instanceof AbstractHorse) {
			return HORSE_FOOD;
		}
		if (animal instanceof Chicken) {
			return CHICKEN_FOOD;
		}
		if (animal instanceof Cow) {
			return COW_FOOD;
		}
		if (animal instanceof Sheep) {
			return SHEEP_FOOD;
		}
		if (animal instanceof Goat) {
			return GOAT_FOOD;
		}
		return null;
	}

	private static TagKey<Item> tag(String path) {
		return TagKey.create(Registries.ITEM, new ResourceLocation(path));
	}
}
