package jexxme.vineandbarrel.kettle;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code vineandbarrel:brewing}: the Brew Kettle's recipe, shaped like FD's {@code farmersdelight:cooking} (MIT).
 * <pre>
 * {
 *   "type": "vineandbarrel:brewing",
 *   "ingredients": ["minecraft:sweet_berries", ..., "minecraft:sugar"],   (1 to 6, shapeless)
 *   "result": {"id": "vineandbarrel:sweet_berry_juice", "count": 4},      (bottle form; count = servings per batch)
 *   "bucket": {"id": "vineandbarrel:sweet_berry_juice_bucket"},           (the same brew as one bucket = 4 servings)
 *   "recipe_book_tab": "juices", "experience": 0.35, "brewingtime": 200
 * }
 * </pre>
 * The files are the same on every Minecraft version: an ingredient is written as newer Minecraft writes it (an item id,
 * {@code "#"} and a tag id, or a list of item ids), which {@link #INGREDIENT_CODEC} reads on this one.
 */
public class BrewingRecipe implements Recipe<RecipeInput>
{
	/** An item id or {@code "#"} and a tag id. */
	private static final Codec<Ingredient> ID_INGREDIENT = Codec.STRING.comapFlatMap(BrewingRecipe::ingredientOf,
		ingredient -> BuiltInRegistries.ITEM.getKey(ingredient.getItems()[0].getItem()).toString());
	/** A list of item ids: any of them. */
	private static final Codec<Ingredient> ID_LIST_INGREDIENT = Codec.STRING.comapFlatMap(BrewingRecipe::itemOf, item -> BuiltInRegistries.ITEM.getKey(item).toString())
		.listOf(1, Integer.MAX_VALUE).xmap(items -> Ingredient.of(items.stream().map(ItemStack::new)),
			ingredient -> java.util.Arrays.stream(ingredient.getItems()).map(ItemStack::getItem).toList());
	/**
	 * The recipe files' ingredients (see the class comment), and this Minecraft's own form too ({@code {"item": ...}},
	 * {@code {"tag": ...}}, a list of those), which is also what is written.
	 */
	private static final Codec<Ingredient> INGREDIENT_CODEC = Codec.either(Ingredient.CODEC_NONEMPTY, Codec.either(ID_INGREDIENT, ID_LIST_INGREDIENT))
		.xmap(either -> either.map(ingredient -> ingredient, ids -> ids.map(ingredient -> ingredient, ingredient -> ingredient)), Either::left);

	public static final MapCodec<BrewingRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
		Codec.STRING.optionalFieldOf("group", "").forGetter(BrewingRecipe::getGroup),
		BrewingRecipeBookTab.CODEC.optionalFieldOf("recipe_book_tab", BrewingRecipeBookTab.MISC).forGetter(BrewingRecipe::tab),
		INGREDIENT_CODEC.listOf(1, BrewKettleBlockEntity.INPUT_SLOTS).fieldOf("ingredients").forGetter(BrewingRecipe::ingredients),
		ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
		ItemStack.STRICT_CODEC.fieldOf("bucket").forGetter(r -> r.bucket),
		Codec.FLOAT.optionalFieldOf("experience", 0.0F).forGetter(BrewingRecipe::experience),
		Codec.INT.optionalFieldOf("brewingtime", 200).forGetter(BrewingRecipe::brewingTime)
	).apply(inst, BrewingRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, BrewingRecipe> STREAM_CODEC = StreamCodec.of(BrewingRecipe::toNetwork, BrewingRecipe::fromNetwork);

	private final String group;
	private final BrewingRecipeBookTab tab;
	private final List<Ingredient> ingredients;
	/** Never changed or handed out: {@link #result()} and {@link #bucket()} give copies. */
	private final ItemStack result;
	private final ItemStack bucket;
	private final float experience;
	private final int brewingTime;

	public BrewingRecipe(String group, BrewingRecipeBookTab tab, List<Ingredient> ingredients, ItemStack result, ItemStack bucket, float experience, int brewingTime) {
		this.group = group;
		this.tab = tab;
		this.ingredients = List.copyOf(ingredients);
		this.result = result.copy();
		this.bucket = bucket.copy();
		this.experience = experience;
		this.brewingTime = brewingTime;
	}

	private static DataResult<Ingredient> ingredientOf(String id) {
		if (id.startsWith("#")) {
			return ResourceLocation.read(id.substring(1)).map(tag -> Ingredient.of(TagKey.create(Registries.ITEM, tag)));
		}
		return itemOf(id).map(Ingredient::of);
	}

	private static DataResult<Item> itemOf(String id) {
		return ResourceLocation.read(id).flatMap(key -> BuiltInRegistries.ITEM.getOptional(key).map(DataResult::success)
			.orElseGet(() -> DataResult.error(() -> "Unknown item '" + id + "'")));
	}

	/** The kettle recipe book's tab this recipe is on. */
	public BrewingRecipeBookTab tab() {
		return tab;
	}

	public List<Ingredient> ingredients() {
		return ingredients;
	}

	/** The brew in its bottle form (count = servings of one batch). */
	public ItemStack result() {
		return result.copy();
	}

	/** The brew as one bucket: a whole batch. */
	public ItemStack bucket() {
		return bucket.copy();
	}

	public int servings() {
		return result.getCount();
	}

	public float experience() {
		return experience;
	}

	public int brewingTime() {
		return brewingTime;
	}

	@Override
	public boolean matches(RecipeInput input, Level level) {
		return consumption(input) != null;
	}

	/**
	 * How many items one batch takes from each ingredient slot, or {@code null} if the input doesn't make this recipe.
	 * <p>
	 * The six slots are one pool: every ingredient entry takes one item from any slot that holds a match, so a hopper's
	 * stack of 4 berries in one slot counts as 4 berry ingredients. Every non-empty slot must hold something this recipe
	 * uses (no foreign leftovers), and the entries prefer slots this batch hasn't touched yet, so FD's layout (one
	 * ingredient per slot, N items each = N batches) still takes exactly one item from every slot.
	 */
	public int @Nullable [] consumption(RecipeInput input) {
		int slots = Math.min(BrewKettleBlockEntity.INPUT_SLOTS, input.size());
		ItemStack[] stacks = new ItemStack[slots];
		int total = 0;
		for (int i = 0; i < slots; ++i) {
			ItemStack stack = input.getItem(i);
			stacks[i] = stack;
			if (stack.isEmpty()) {
				continue;
			}
			if (!usesAny(stack)) {
				return null;
			}
			total += stack.getCount();
		}
		if (total < ingredients.size()) {
			return null;
		}
		for (Ingredient ingredient : ingredients) {
			boolean found = false;
			for (ItemStack stack : stacks) {
				found |= !stack.isEmpty() && ingredient.test(stack);
			}
			if (!found) {
				return null;
			}
		}
		int[] taken = new int[slots];
		return assign(0, stacks, taken) ? taken : null;
	}

	private boolean usesAny(ItemStack stack) {
		for (Ingredient ingredient : ingredients) {
			if (ingredient.test(stack)) {
				return true;
			}
		}
		return false;
	}

	/** Depth-first: gives ingredient entry {@code index} and every later one an item, backtracking if a later one can't get one. */
	private boolean assign(int index, ItemStack[] stacks, int[] taken) {
		if (index == ingredients.size()) {
			return true;
		}
		Ingredient ingredient = ingredients.get(index);
		List<Integer> candidates = new ArrayList<>();
		for (int i = 0; i < stacks.length; ++i) {
			if (!stacks[i].isEmpty() && taken[i] < stacks[i].getCount() && ingredient.test(stacks[i])) {
				candidates.add(i);
			}
		}
		// Untouched slots first (FD's one item per slot), then the slot with the most left, then the lowest index
		candidates.sort(Comparator.<Integer>comparingInt(i -> taken[i])
			.thenComparingInt(i -> taken[i] - stacks[i].getCount())
			.thenComparingInt(i -> i));
		for (int slot : candidates) {
			++taken[slot];
			if (assign(index + 1, stacks, taken)) {
				return true;
			}
			--taken[slot];
		}
		return false;
	}

	@Override
	public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
		return result.copy();
	}

	/** The six slots are one pool (see {@link #consumption}): any grid that holds the ingredients will do. */
	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= ingredients.size();
	}

	/** For the recipe book, which only reads it. */
	@Override
	public ItemStack getResultItem(HolderLookup.Provider registries) {
		return result;
	}

	/** For the recipe book: what it counts in the player's inventory and places in the grid. */
	@Override
	public NonNullList<Ingredient> getIngredients() {
		NonNullList<Ingredient> list = NonNullList.create();
		list.addAll(ingredients);
		return list;
	}

	@Override
	public boolean showNotification() {
		return false;
	}

	@Override
	public String getGroup() {
		return group;
	}

	@Override
	public RecipeSerializer<BrewingRecipe> getSerializer() {
		return KettleRegistry.BREWING_SERIALIZER.get();
	}

	@Override
	public RecipeType<BrewingRecipe> getType() {
		return KettleRegistry.BREWING.get();
	}

	private static BrewingRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
		String group = buffer.readUtf();
		BrewingRecipeBookTab tab = buffer.readEnum(BrewingRecipeBookTab.class);
		int count = buffer.readVarInt();
		List<Ingredient> ingredients = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			ingredients.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
		}
		ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
		ItemStack bucket = ItemStack.STREAM_CODEC.decode(buffer);
		float experience = buffer.readFloat();
		int brewingTime = buffer.readVarInt();
		return new BrewingRecipe(group, tab, ingredients, result, bucket, experience, brewingTime);
	}

	private static void toNetwork(RegistryFriendlyByteBuf buffer, BrewingRecipe recipe) {
		buffer.writeUtf(recipe.group);
		buffer.writeEnum(recipe.tab);
		buffer.writeVarInt(recipe.ingredients.size());
		for (Ingredient ingredient : recipe.ingredients) {
			Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
		}
		ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
		ItemStack.STREAM_CODEC.encode(buffer, recipe.bucket);
		buffer.writeFloat(recipe.experience);
		buffer.writeVarInt(recipe.brewingTime);
	}
}
