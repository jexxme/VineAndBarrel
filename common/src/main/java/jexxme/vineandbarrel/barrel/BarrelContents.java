package jexxme.vineandbarrel.barrel;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One batch in an Aging Barrel: what was poured in, what it turns into and when, and how much is left. The barrel copies
 * its {@link AgingRecipe} into the batch at the first fill, so a batch keeps its course even if data packs change.
 * <p>
 * Also what a barrel broken with a batch inside carries as an item (B12; 1.20.1: in the item's NBT, see
 * {@link AgingBarrelItem}). There {@link #elapsed()} is the age of the
 * batch, which stays put while the barrel is an item: aging pauses until it is placed again. In a placed barrel the
 * age comes from the world's game time instead, so it keeps aging while the chunk is unloaded.
 *
 * @param base           the base in its bottle form (count 1); one bottle = 1 serving. Empty if its item no longer
 *                       exists (then there is just no undo)
 * @param bucket         the base in its bucket form (count 1, may be empty); one bucket = 4 servings
 * @param young          the young drink (count 1)
 * @param aged           the aged drink (count 1)
 * @param servings       servings left, 0 to {@link AgingBarrelBlockEntity#CAPACITY}
 * @param elapsed        ticks since the first fill (what the barrel item goes by; a placed barrel saves it too, for its drop)
 * @param fermentingTime ticks from the first fill until it is the young drink
 * @param agingTime      ticks from then until it is the aged drink
 */
public final class BarrelContents
{
	public static final BarrelContents EMPTY = new BarrelContents(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 0, 0L,
		AgingRecipe.DEFAULT_FERMENTING_TIME, AgingRecipe.DEFAULT_AGING_TIME);

	/**
	 * One stack of a saved batch. An item that no longer exists (its mod was removed, e.g. Farmer's Delight's apple cider
	 * as the base of hard cider) is read as empty with a warning, instead of failing the whole batch: the drinks are
	 * ours and still there, and the base is only needed again for undo while fermenting.
	 */
	private static final Codec<ItemStack> LENIENT_STACK = new Codec<>()
	{
		/**
		 * The shape 1.21.1 and 26.x write a stack in ({@code {id: "...", count: 1}}, an empty stack as {@code {}}), not
		 * 1.20.1's own ({@code id, Count, tag}): the taverns' structure files carry batches saved by 26.3, and a world's
		 * barrels keep one format across versions. A stack's NBT, which only 1.20.1 has, is kept under {@code tag}; 26.x's
		 * {@code components} can't be read here and are left out.
		 */
		@Override
		public <T> DataResult<Pair<ItemStack, T>> decode(DynamicOps<T> ops, T input) {
			Dynamic<T> saved = new Dynamic<>(ops, input);
			Optional<String> id = saved.get("id").asString().result();
			if (id.isEmpty()) {
				return DataResult.success(Pair.of(ItemStack.EMPTY, input));
			}
			ResourceLocation key = ResourceLocation.tryParse(id.get());
			Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
			if (item == null || item == Items.AIR) {
				VineAndBarrel.LOGGER.warn("An Aging Barrel batch names an item that no longer exists, ignoring it: {}", id.get());
				return DataResult.success(Pair.of(ItemStack.EMPTY, input));
			}
			// "Count" too: a batch whose stacks were rewritten in 1.20.1's own shape still reads
			int count = saved.get("count").asNumber().result().map(Number::intValue).orElseGet(() -> saved.get("Count").asInt(1));
			ItemStack stack = new ItemStack(item, count);
			saved.get("tag").result().map(tag -> tag.convert(NbtOps.INSTANCE).getValue()).ifPresent(tag -> {
				if (tag instanceof CompoundTag compound) {
					stack.setTag(compound.copy());
				}
			});
			return DataResult.success(Pair.of(stack, input));
		}

		@Override
		public <T> DataResult<T> encode(ItemStack stack, DynamicOps<T> ops, T prefix) {
			RecordBuilder<T> map = ops.mapBuilder();
			if (!stack.isEmpty()) {
				map.add("id", ops.createString(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
				map.add("count", ops.createInt(stack.getCount()));
				CompoundTag tag = stack.getTag();
				if (tag != null) {
					map.add("tag", NbtOps.INSTANCE.convertTo(ops, tag));
				}
			}
			return map.build(prefix);
		}
	};

	/** Placed barrels (block entity data) and barrel items (B12) both use this. */
	public static final Codec<BarrelContents> CODEC = RecordCodecBuilder.create(inst -> inst.group(
		LENIENT_STACK.optionalFieldOf("base", ItemStack.EMPTY).forGetter(c -> c.base),
		LENIENT_STACK.optionalFieldOf("bucket", ItemStack.EMPTY).forGetter(c -> c.bucket),
		LENIENT_STACK.optionalFieldOf("young", ItemStack.EMPTY).forGetter(c -> c.young),
		LENIENT_STACK.optionalFieldOf("aged", ItemStack.EMPTY).forGetter(c -> c.aged),
		Codec.intRange(0, AgingBarrelBlockEntity.CAPACITY).optionalFieldOf("servings", 0).forGetter(c -> c.servings),
		Codec.LONG.optionalFieldOf("elapsed", 0L).forGetter(c -> c.elapsed),
		Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("fermenting_time", AgingRecipe.DEFAULT_FERMENTING_TIME).forGetter(c -> c.fermentingTime),
		Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("aging_time", AgingRecipe.DEFAULT_AGING_TIME).forGetter(c -> c.agingTime)
	).apply(inst, BarrelContents::new));

	// No network form: 1.20.1 sends an item with its NBT, and the barrel item keeps its batch there (AgingBarrelItem)

	/** The batch as NBT, in {@link #CODEC}'s shape; null for an empty batch (nothing is saved then). */
	public @Nullable Tag toTag() {
		return isEmpty() ? null : CODEC.encodeStart(NbtOps.INSTANCE, this).result().orElse(null);
	}

	/** The batch saved in {@code tag} (empty if there is none); what can't be read is told to {@code onProblem}. */
	public static BarrelContents fromTag(@Nullable Tag tag, Consumer<String> onProblem) {
		return tag == null ? EMPTY : CODEC.parse(NbtOps.INSTANCE, tag).resultOrPartial(onProblem).orElse(EMPTY);
	}

	private final ItemStack base;
	private final ItemStack bucket;
	private final ItemStack young;
	private final ItemStack aged;
	private final int servings;
	private final long elapsed;
	private final int fermentingTime;
	private final int agingTime;
	private final int hashCode;

	/** A batch never changes once created (the block entity and its item form share it), so stacks are copied in and out. */
	public BarrelContents(ItemStack base, ItemStack bucket, ItemStack young, ItemStack aged, int servings, long elapsed, int fermentingTime, int agingTime) {
		this.base = base.copyWithCount(Math.min(base.getCount(), 1));
		this.bucket = bucket.copyWithCount(Math.min(bucket.getCount(), 1));
		this.young = young.copyWithCount(Math.min(young.getCount(), 1));
		this.aged = aged.copyWithCount(Math.min(aged.getCount(), 1));
		// A batch is its drink: without its base (removed with its mod) it still ferments and ages into the drink
		this.servings = this.young.isEmpty() && this.aged.isEmpty() ? 0 : servings;
		this.elapsed = Math.max(0L, elapsed);
		this.fermentingTime = fermentingTime;
		this.agingTime = agingTime;
		this.hashCode = Objects.hash(hashItemAndTag(this.base), hashItemAndTag(this.bucket),
			hashItemAndTag(this.young), hashItemAndTag(this.aged), this.servings, this.elapsed,
			fermentingTime, agingTime);
	}

	private static int hashItemAndTag(ItemStack stack) {
		return stack.isEmpty() ? 0 : 31 * stack.getItem().hashCode() + Objects.hashCode(stack.getTag());
	}

	public ItemStack base() {
		return base.copy();
	}

	public ItemStack bucket() {
		return bucket.copy();
	}

	public ItemStack young() {
		return young.copy();
	}

	public ItemStack aged() {
		return aged.copy();
	}

	public int servings() {
		return servings;
	}

	public long elapsed() {
		return elapsed;
	}

	public int fermentingTime() {
		return fermentingTime;
	}

	public int agingTime() {
		return agingTime;
	}

	public boolean isEmpty() {
		return servings <= 0;
	}

	public BarrelContents withServings(int newServings) {
		return newServings <= 0 ? EMPTY : new BarrelContents(base, bucket, young, aged, newServings, elapsed, fermentingTime, agingTime);
	}

	public BarrelContents withElapsed(long newElapsed) {
		return isEmpty() ? EMPTY : new BarrelContents(base, bucket, young, aged, servings, newElapsed, fermentingTime, agingTime);
	}

	/** The stage this batch is in after {@code age} ticks. */
	public BarrelStage stageAt(long age) {
		if (isEmpty()) {
			return BarrelStage.EMPTY;
		}
		if (age < fermentingTime) {
			return BarrelStage.FERMENTING;
		}
		return age < (long) fermentingTime + agingTime ? BarrelStage.YOUNG : BarrelStage.AGED;
	}

	/** Ticks left until the next stage, or 0 when it is aged (or empty). */
	public long ticksToNextStage(long age) {
		return switch (stageAt(age)) {
			case FERMENTING -> fermentingTime - age;
			case YOUNG -> (long) fermentingTime + agingTime - age;
			default -> 0L;
		};
	}

	/** What this batch is after {@code age} ticks: the base while fermenting, then the young drink, then the aged drink. */
	public ItemStack contentAt(long age) {
		return switch (stageAt(age)) {
			case FERMENTING -> base();
			case YOUNG -> young();
			case AGED -> aged();
			case EMPTY -> ItemStack.EMPTY;
		};
	}

	/** The drink this batch is or will be: the young one while fermenting. */
	public ItemStack drinkAt(long age) {
		return stageAt(age) == BarrelStage.AGED ? aged() : young();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof BarrelContents other && servings == other.servings && elapsed == other.elapsed
			&& fermentingTime == other.fermentingTime && agingTime == other.agingTime
			&& ItemStack.matches(base, other.base) && ItemStack.matches(bucket, other.bucket)
			&& ItemStack.matches(young, other.young) && ItemStack.matches(aged, other.aged);
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public String toString() {
		return "BarrelContents[" + servings + "x " + base + " -> " + young + " -> " + aged + ", elapsed " + elapsed + "]";
	}
}
