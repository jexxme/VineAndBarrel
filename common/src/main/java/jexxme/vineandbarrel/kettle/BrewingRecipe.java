package jexxme.vineandbarrel.kettle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
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
 * {@code "#"} and a tag id, or a list of item ids) and a stack as {@code {"id": ..., "count": ...}}, which
 * {@link #fromJson} reads on this one (this Minecraft's recipes are read from JSON by hand, and know their own id).
 */
public class BrewingRecipe implements Recipe<Container>
{
	private final ResourceLocation id;
	private final String group;
	private final BrewingRecipeBookTab tab;
	private final List<Ingredient> ingredients;
	/** Never changed or handed out: {@link #result()} and {@link #bucket()} give copies. */
	private final ItemStack result;
	private final ItemStack bucket;
	private final float experience;
	private final int brewingTime;

	public BrewingRecipe(ResourceLocation id, String group, BrewingRecipeBookTab tab, List<Ingredient> ingredients, ItemStack result, ItemStack bucket,
			float experience, int brewingTime) {
		this.id = id;
		this.group = group;
		this.tab = tab;
		this.ingredients = List.copyOf(ingredients);
		this.result = result.copy();
		this.bucket = bucket.copy();
		this.experience = experience;
		this.brewingTime = brewingTime;
	}

	// ------------------------------------------------------------------ the recipe file

	/** Reads a recipe file (see the class comment); anything wrong with it throws, and vanilla logs the recipe as unreadable. */
	public static BrewingRecipe fromJson(ResourceLocation id, JsonObject json) {
		String group = GsonHelper.getAsString(json, "group", "");
		BrewingRecipeBookTab tab = BrewingRecipeBookTab.MISC;
		if (json.has("recipe_book_tab")) {
			String name = GsonHelper.getAsString(json, "recipe_book_tab");
			tab = Arrays.stream(BrewingRecipeBookTab.values()).filter(t -> t.getSerializedName().equals(name)).findFirst()
				.orElseThrow(() -> new JsonSyntaxException("Unknown recipe book tab '" + name + "'"));
		}
		JsonArray array = GsonHelper.getAsJsonArray(json, "ingredients");
		if (array.size() < 1 || array.size() > BrewKettleBlockEntity.INPUT_SLOTS) {
			throw new JsonSyntaxException("A brewing recipe has 1 to " + BrewKettleBlockEntity.INPUT_SLOTS + " ingredients, not " + array.size());
		}
		List<Ingredient> ingredients = new ArrayList<>(array.size());
		for (JsonElement element : array) {
			ingredients.add(ingredientFromJson(element));
		}
		ItemStack result = stackFromJson(GsonHelper.getAsJsonObject(json, "result"));
		ItemStack bucket = stackFromJson(GsonHelper.getAsJsonObject(json, "bucket"));
		float experience = GsonHelper.getAsFloat(json, "experience", 0.0F);
		int brewingTime = GsonHelper.getAsInt(json, "brewingtime", 200);
		return new BrewingRecipe(id, group, tab, ingredients, result, bucket, experience, brewingTime);
	}

	/**
	 * The recipe files' ingredients: an item id or {@code "#"} and a tag id, or a list of item ids (any of them); and
	 * this Minecraft's own form too ({@code {"item": ...}}, {@code {"tag": ...}}, a list of those).
	 */
	private static Ingredient ingredientFromJson(JsonElement json) {
		if (GsonHelper.isStringValue(json)) {
			return ingredientOf(json.getAsString());
		}
		if (json.isJsonArray() && json.getAsJsonArray().size() > 0 && GsonHelper.isStringValue(json.getAsJsonArray().get(0))) {
			List<ItemStack> items = new ArrayList<>();
			for (JsonElement element : json.getAsJsonArray()) {
				items.add(new ItemStack(itemOf(GsonHelper.convertToString(element, "item"))));
			}
			return Ingredient.of(items.stream());
		}
		return Ingredient.fromJson(json, false);
	}

	private static Ingredient ingredientOf(String id) {
		if (id.startsWith("#")) {
			return Ingredient.of(TagKey.create(Registries.ITEM, idOf(id.substring(1))));
		}
		return Ingredient.of(itemOf(id));
	}

	private static Item itemOf(String id) {
		Item item = BuiltInRegistries.ITEM.getOptional(idOf(id)).orElseThrow(() -> new JsonSyntaxException("Unknown item '" + id + "'"));
		if (item == Items.AIR) {
			throw new JsonSyntaxException("Item must not be minecraft:air");
		}
		return item;
	}

	private static ResourceLocation idOf(String id) {
		ResourceLocation key = ResourceLocation.tryParse(id);
		if (key == null) {
			throw new JsonSyntaxException("Not a valid id: '" + id + "'");
		}
		return key;
	}

	/** A stack as newer Minecraft's recipe files write it: {@code {"id": ..., "count": ...}} (count 1 if missing). */
	private static ItemStack stackFromJson(JsonObject json) {
		Item item = itemOf(GsonHelper.getAsString(json, "id"));
		int count = GsonHelper.getAsInt(json, "count", 1);
		ItemStack stack = new ItemStack(item, count);
		if (count < 1 || count > stack.getMaxStackSize()) {
			throw new JsonSyntaxException("Count must be 1 to " + stack.getMaxStackSize() + ", not " + count);
		}
		return stack;
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
	public boolean matches(Container input, Level level) {
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
	public int @Nullable [] consumption(Container input) {
		int slots = Math.min(BrewKettleBlockEntity.INPUT_SLOTS, input.getContainerSize());
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
	public ItemStack assemble(Container input, RegistryAccess registries) {
		return result.copy();
	}

	/** The six slots are one pool (see {@link #consumption}): any grid that holds the ingredients will do. */
	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= ingredients.size();
	}

	/** For the recipe book, which only reads it. */
	@Override
	public ItemStack getResultItem(RegistryAccess registries) {
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
	public ResourceLocation getId() {
		return id;
	}

	@Override
	public RecipeSerializer<BrewingRecipe> getSerializer() {
		return KettleRegistry.BREWING_SERIALIZER.get();
	}

	@Override
	public RecipeType<BrewingRecipe> getType() {
		return KettleRegistry.BREWING.get();
	}

	public static BrewingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
		String group = buffer.readUtf();
		BrewingRecipeBookTab tab = buffer.readEnum(BrewingRecipeBookTab.class);
		int count = buffer.readVarInt();
		List<Ingredient> ingredients = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			ingredients.add(Ingredient.fromNetwork(buffer));
		}
		ItemStack result = buffer.readItem();
		ItemStack bucket = buffer.readItem();
		float experience = buffer.readFloat();
		int brewingTime = buffer.readVarInt();
		return new BrewingRecipe(id, group, tab, ingredients, result, bucket, experience, brewingTime);
	}

	public static void toNetwork(FriendlyByteBuf buffer, BrewingRecipe recipe) {
		buffer.writeUtf(recipe.group);
		buffer.writeEnum(recipe.tab);
		buffer.writeVarInt(recipe.ingredients.size());
		for (Ingredient ingredient : recipe.ingredients) {
			ingredient.toNetwork(buffer);
		}
		buffer.writeItem(recipe.result);
		buffer.writeItem(recipe.bucket);
		buffer.writeFloat(recipe.experience);
		buffer.writeVarInt(recipe.brewingTime);
	}
}
