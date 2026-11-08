package jexxme.vineandbarrel.kettle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
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
 */
public class BrewingRecipe implements Recipe<RecipeInput>
{
	public static final MapCodec<BrewingRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
		Codec.STRING.optionalFieldOf("group", "").forGetter(BrewingRecipe::group),
		BrewingRecipeBookTab.CODEC.optionalFieldOf("recipe_book_tab", BrewingRecipeBookTab.MISC).forGetter(BrewingRecipe::tab),
		Ingredient.CODEC.listOf(1, BrewKettleBlockEntity.INPUT_SLOTS).fieldOf("ingredients").forGetter(BrewingRecipe::ingredients),
		ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result),
		ItemStackTemplate.CODEC.fieldOf("bucket").forGetter(r -> r.bucket),
		Codec.FLOAT.optionalFieldOf("experience", 0.0F).forGetter(BrewingRecipe::experience),
		Codec.INT.optionalFieldOf("brewingtime", 200).forGetter(BrewingRecipe::brewingTime)
	).apply(inst, BrewingRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, BrewingRecipe> STREAM_CODEC = StreamCodec.of(BrewingRecipe::toNetwork, BrewingRecipe::fromNetwork);

	private final String group;
	private final BrewingRecipeBookTab tab;
	private final List<Ingredient> ingredients;
	private final ItemStackTemplate result;
	private final ItemStackTemplate bucket;
	private final float experience;
	private final int brewingTime;
	private @Nullable PlacementInfo placementInfo;

	public BrewingRecipe(String group, BrewingRecipeBookTab tab, List<Ingredient> ingredients, ItemStackTemplate result, ItemStackTemplate bucket, float experience, int brewingTime) {
		this.group = group;
		this.tab = tab;
		this.ingredients = List.copyOf(ingredients);
		this.result = result;
		this.bucket = bucket;
		this.experience = experience;
		this.brewingTime = brewingTime;
	}

	public BrewingRecipeBookTab tab() {
		return tab;
	}

	public List<Ingredient> ingredients() {
		return ingredients;
	}

	/** The brew in its bottle form (count = servings of one batch). */
	public ItemStack result() {
		return result.create();
	}

	/** The brew as one bucket: a whole batch. */
	public ItemStack bucket() {
		return bucket.create();
	}

	public int servings() {
		return result.count();
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
	public ItemStack assemble(RecipeInput input) {
		return result.create();
	}

	@Override
	public boolean showNotification() {
		return false;
	}

	@Override
	public String group() {
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

	@Override
	public PlacementInfo placementInfo() {
		// 26.3: recipes are decoded before tags are bound, so this must be computed lazily (as in FD)
		if (placementInfo == null) {
			placementInfo = PlacementInfo.create(ingredients);
		}
		return placementInfo;
	}

	@Override
	public List<RecipeDisplay> display() {
		return List.of(new BrewingRecipeDisplay(ingredients.stream().map(Ingredient::display).toList(),
			new SlotDisplay.ItemSlotDisplay(Items.GLASS_BOTTLE),
			new SlotDisplay.ItemStackSlotDisplay(result),
			new SlotDisplay.ItemSlotDisplay(KettleRegistry.kettleItem()),
			brewingTime, experience));
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return switch (tab) {
			case JUICES -> KettleRegistry.BREWING_JUICES.get();
			case WORTS -> KettleRegistry.BREWING_WORTS.get();
			case MISC -> KettleRegistry.BREWING_MISC.get();
		};
	}

	private static BrewingRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
		String group = buffer.readUtf();
		BrewingRecipeBookTab tab = buffer.readEnum(BrewingRecipeBookTab.class);
		int count = buffer.readVarInt();
		// 26.3: Ingredient.of() (empty) throws, so the list is never prefilled
		List<Ingredient> ingredients = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			ingredients.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
		}
		ItemStackTemplate result = ItemStackTemplate.STREAM_CODEC.decode(buffer);
		ItemStackTemplate bucket = ItemStackTemplate.STREAM_CODEC.decode(buffer);
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
		ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.result);
		ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.bucket);
		buffer.writeFloat(recipe.experience);
		buffer.writeVarInt(recipe.brewingTime);
	}
}
