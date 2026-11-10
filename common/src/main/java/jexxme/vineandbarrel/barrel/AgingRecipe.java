package jexxme.vineandbarrel.barrel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * {@code vineandbarrel:aging}: what a base becomes in the Aging Barrel (DESIGN §4.2). New drinks are pure data.
 * <pre>
 * {
 *   "type": "vineandbarrel:aging",
 *   "base": "vineandbarrel:grape_juice",            (one bottle = 1 serving)
 *   "bucket": "vineandbarrel:grape_juice_bucket",   (optional: one bucket = 4 servings)
 *   "young": "vineandbarrel:red_wine",              (after fermenting_time)
 *   "aged": "vineandbarrel:aged_red_wine",          (after fermenting_time + aging_time)
 *   "fermenting_time": 24000, "aging_time": 72000   (optional, in ticks: 1 day, then 3 more days)
 * }
 * </pre>
 * The drinks are served into their vessel (a mug or a wine glass: {@link AgingBarrelBlockEntity#vesselOf}); a recipe
 * whose drink has none is rejected once loaded. If two recipes share a base, the lowest id wins ({@link AgingRecipes}).
 */
public class AgingRecipe implements Recipe<Container>
{
	/** One in-game day. */
	public static final int DEFAULT_FERMENTING_TIME = 24000;
	/** Three more in-game days. */
	public static final int DEFAULT_AGING_TIME = 3 * 24000;

	private final ResourceLocation id;
	private final Holder<Item> base;
	private final Optional<Holder<Item>> bucket;
	/** Never changed or handed out: {@link #young()} and {@link #aged()} copy (26.x: an ItemStackTemplate). */
	private final ItemStack young;
	private final ItemStack aged;
	private final int fermentingTime;
	private final int agingTime;

	public AgingRecipe(ResourceLocation id, Holder<Item> base, Optional<Holder<Item>> bucket, ItemStack young, ItemStack aged, int fermentingTime, int agingTime) {
		this.id = id;
		this.base = base;
		this.bucket = bucket;
		this.young = young;
		this.aged = aged;
		this.fermentingTime = fermentingTime;
		this.agingTime = agingTime;
	}

	// ------------------------------------------------------------------ JSON and network (1.20.1: no codecs in serializers)

	/**
	 * Reads a recipe file, the same files as on 1.21.1 and 26.x: {@code base} and {@code bucket} are item ids; a drink is
	 * its id alone ({@code "vineandbarrel:red_wine"}) or {@code {"id": ...}}. 1.20.1 has no item components, so a drink
	 * with {@code "components"} is refused instead of quietly losing them.
	 */
	public static AgingRecipe fromJson(ResourceLocation id, JsonObject json) {
		Holder<Item> base = item(GsonHelper.getAsString(json, "base"), "base");
		Optional<Holder<Item>> bucket = json.has("bucket") ? Optional.of(item(GsonHelper.getAsString(json, "bucket"), "bucket")) : Optional.empty();
		ItemStack young = drink(json, "young");
		ItemStack aged = drink(json, "aged");
		return new AgingRecipe(id, base, bucket, young, aged, time(json, "fermenting_time", DEFAULT_FERMENTING_TIME), time(json, "aging_time", DEFAULT_AGING_TIME));
	}

	private static Holder<Item> item(String name, String key) {
		ResourceLocation itemId = ResourceLocation.tryParse(name);
		Item item = itemId == null ? null : BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
		if (item == null || item == Items.AIR) {
			throw new JsonSyntaxException("Unknown item '" + name + "' for " + key);
		}
		return item.builtInRegistryHolder();
	}

	private static ItemStack drink(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null) {
			throw new JsonSyntaxException("Missing " + key + ", expected to find an item");
		}
		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			if (object.has("components")) {
				throw new JsonSyntaxException(key + " has components, which this Minecraft (1.20.1) does not have");
			}
			return new ItemStack(item(GsonHelper.getAsString(object, "id"), key));
		}
		return new ItemStack(item(GsonHelper.convertToString(element, key), key));
	}

	private static int time(JsonObject json, String key, int fallback) {
		int ticks = GsonHelper.getAsInt(json, key, fallback);
		if (ticks < 1) {
			throw new JsonSyntaxException(key + " must be at least 1, found " + ticks);
		}
		return ticks;
	}

	public static class Serializer implements RecipeSerializer<AgingRecipe>
	{
		@Override
		public AgingRecipe fromJson(ResourceLocation id, JsonObject json) {
			return AgingRecipe.fromJson(id, json);
		}

		@Override
		public AgingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
			Holder<Item> base = holder(buf.readById(BuiltInRegistries.ITEM));
			Optional<Holder<Item>> bucket = buf.readOptional(b -> holder(b.readById(BuiltInRegistries.ITEM)));
			ItemStack young = buf.readItem();
			ItemStack aged = buf.readItem();
			int fermentingTime = buf.readVarInt();
			int agingTime = buf.readVarInt();
			return new AgingRecipe(id, base, bucket, young, aged, fermentingTime, agingTime);
		}

		private static Holder<Item> holder(Item item) {
			return (item == null ? Items.AIR : item).builtInRegistryHolder();
		}

		@Override
		public void toNetwork(FriendlyByteBuf buf, AgingRecipe recipe) {
			buf.writeId(BuiltInRegistries.ITEM, recipe.base.value());
			buf.writeOptional(recipe.bucket, (b, bucket) -> b.writeId(BuiltInRegistries.ITEM, bucket.value()));
			buf.writeItem(recipe.young);
			buf.writeItem(recipe.aged);
			buf.writeVarInt(recipe.fermentingTime);
			buf.writeVarInt(recipe.agingTime);
		}
	}

	@Override
	public ResourceLocation getId() {
		return id;
	}

	public Holder<Item> base() {
		return base;
	}

	public Optional<Holder<Item>> bucket() {
		return bucket;
	}

	public ItemStack young() {
		return young.copy();
	}

	public ItemStack aged() {
		return aged.copy();
	}

	public int fermentingTime() {
		return fermentingTime;
	}

	public int agingTime() {
		return agingTime;
	}

	/**
	 * Why this recipe can't work, if it can't: a drink is tapped into its vessel, so a drink without one could never be
	 * tapped. Checked once the recipes are loaded, see {@link AgingRecipes}.
	 */
	public Optional<String> problem() {
		for (ItemStack drink : List.of(young, aged)) {
			if (AgingBarrelBlockEntity.vesselOf(drink) == null) {
				return Optional.of("its drink " + BuiltInRegistries.ITEM.getKey(drink.getItem())
					+ " is none of our drinks and has no crafting remainder, so it names no vessel (mug, wine glass) to be tapped into");
			}
		}
		return Optional.empty();
	}

	/** How many servings one of these items pours in: a bottle of the base 1, a bucket of it 4, anything else 0. */
	public int servingsOf(ItemStack stack) {
		if (stack.is(base)) {
			return 1;
		}
		if (bucket.isPresent() && stack.is(bucket.get())) {
			return AgingBarrelBlockEntity.SERVINGS_PER_BUCKET;
		}
		return 0;
	}

	/** The batch this recipe starts in an empty barrel, with no servings yet. */
	public BarrelContents newBatch() {
		return new BarrelContents(new ItemStack(base), bucket.<ItemStack>map(ItemStack::new).orElse(ItemStack.EMPTY), young(), aged(), 0, 0L,
			fermentingTime, agingTime);
	}

	@Override
	public boolean matches(Container input, Level level) {
		return input.getContainerSize() > 0 && servingsOf(input.getItem(0)) > 0;
	}

	@Override
	public ItemStack assemble(Container input, RegistryAccess registries) {
		return young();
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return true;
	}

	@Override
	public ItemStack getResultItem(RegistryAccess registries) {
		return young();
	}

	/** Not a crafting-style recipe: never in the recipe book, never unlocked (like a special recipe). */
	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public boolean showNotification() {
		return false;
	}

	@Override
	public String getGroup() {
		return "";
	}

	@Override
	public RecipeSerializer<AgingRecipe> getSerializer() {
		return BarrelRegistry.AGING_SERIALIZER.get();
	}

	@Override
	public RecipeType<AgingRecipe> getType() {
		return BarrelRegistry.AGING.get();
	}
}
