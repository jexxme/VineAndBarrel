package jexxme.vineandbarrel.barrel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
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
 * The drinks are served into the vessel their use remainder names (a mug or a wine glass); a recipe whose drink has
 * none is rejected once loaded. If two recipes share a base, the lowest id wins ({@link AgingRecipes}).
 */
public class AgingRecipe implements Recipe<RecipeInput>
{
	/** One in-game day. */
	public static final int DEFAULT_FERMENTING_TIME = 24000;
	/** Three more in-game days. */
	public static final int DEFAULT_AGING_TIME = 3 * 24000;

	public static final MapCodec<AgingRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
		Item.CODEC.fieldOf("base").forGetter(AgingRecipe::base),
		Item.CODEC.optionalFieldOf("bucket").forGetter(AgingRecipe::bucket),
		ItemStackTemplate.CODEC.fieldOf("young").forGetter(r -> r.young),
		ItemStackTemplate.CODEC.fieldOf("aged").forGetter(r -> r.aged),
		Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("fermenting_time", DEFAULT_FERMENTING_TIME).forGetter(AgingRecipe::fermentingTime),
		Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("aging_time", DEFAULT_AGING_TIME).forGetter(AgingRecipe::agingTime)
	).apply(inst, AgingRecipe::new));


	public static final StreamCodec<RegistryFriendlyByteBuf, AgingRecipe> STREAM_CODEC = StreamCodec.composite(
		Item.STREAM_CODEC, AgingRecipe::base,
		ByteBufCodecs.optional(Item.STREAM_CODEC), AgingRecipe::bucket,
		ItemStackTemplate.STREAM_CODEC, r -> r.young,
		ItemStackTemplate.STREAM_CODEC, r -> r.aged,
		ByteBufCodecs.VAR_INT, AgingRecipe::fermentingTime,
		ByteBufCodecs.VAR_INT, AgingRecipe::agingTime,
		AgingRecipe::new);

	private final Holder<Item> base;
	private final Optional<Holder<Item>> bucket;
	private final ItemStackTemplate young;
	private final ItemStackTemplate aged;
	private final int fermentingTime;
	private final int agingTime;

	public AgingRecipe(Holder<Item> base, Optional<Holder<Item>> bucket, ItemStackTemplate young, ItemStackTemplate aged, int fermentingTime, int agingTime) {
		this.base = base;
		this.bucket = bucket;
		this.young = young;
		this.aged = aged;
		this.fermentingTime = fermentingTime;
		this.agingTime = agingTime;
	}

	public Holder<Item> base() {
		return base;
	}

	public Optional<Holder<Item>> bucket() {
		return bucket;
	}

	public ItemStack young() {
		return young.create();
	}

	public ItemStack aged() {
		return aged.create();
	}

	public int fermentingTime() {
		return fermentingTime;
	}

	public int agingTime() {
		return agingTime;
	}

	/**
	 * Why this recipe can't work, if it can't: a drink is tapped into the vessel its use remainder names, so a drink
	 * without one could never be tapped. Checked once the recipes are loaded (26.3 binds item components after recipe
	 * decoding), see {@link AgingRecipes}.
	 */
	public Optional<String> problem() {
		for (ItemStackTemplate drink : List.of(young, aged)) {
			if (drink.create().get(DataComponents.USE_REMAINDER) == null) {
				return Optional.of("its drink " + drink.item().getRegisteredName()
					+ " has no minecraft:use_remainder, so it names no vessel (mug, wine glass) to be tapped into");
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
		return new BarrelContents(new ItemStack(base), bucket.map(ItemStack::new).orElse(ItemStack.EMPTY), young(), aged(), 0, 0L,
			fermentingTime, agingTime);
	}

	@Override
	public boolean matches(RecipeInput input, Level level) {
		return input.size() > 0 && servingsOf(input.getItem(0)) > 0;
	}

	@Override
	public ItemStack assemble(RecipeInput input) {
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
	public String group() {
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

	@Override
	public PlacementInfo placementInfo() {
		return PlacementInfo.NOT_PLACEABLE;
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return BarrelRegistry.AGING_CATEGORY.get();
	}
}
