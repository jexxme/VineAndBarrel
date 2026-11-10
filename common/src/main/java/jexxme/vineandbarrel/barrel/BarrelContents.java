package jexxme.vineandbarrel.barrel;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * One batch in an Aging Barrel: what was poured in, what it turns into and when, and how much is left. The barrel copies
 * its {@link AgingRecipe} into the batch at the first fill, so a batch keeps its course even if data packs change.
 * <p>
 * Also the item component of a barrel broken with a batch inside (B12). There {@link #elapsed()} is the age of the
 * batch, which stays put while the barrel is an item: aging pauses until it is placed again. In a placed barrel the
 * age comes from the world's game time instead, so it keeps aging while the chunk is unloaded.
 *
 * @param base           the base in its bottle form (count 1); one bottle = 1 serving. Empty if its item no longer
 *                       exists (then there is just no undo)
 * @param bucket         the base in its bucket form (count 1, may be empty); one bucket = 4 servings
 * @param young          the young drink (count 1)
 * @param aged           the aged drink (count 1)
 * @param servings       servings left, 0 to {@link AgingBarrelBlockEntity#CAPACITY}
 * @param elapsed        ticks since the first fill (item component only)
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
		@Override
		public <T> DataResult<Pair<ItemStack, T>> decode(DynamicOps<T> ops, T input) {
			DataResult<Pair<ItemStack, T>> result = ItemStack.OPTIONAL_CODEC.decode(ops, input);
			if (result.isError()) {
				VineAndBarrel.LOGGER.warn("An Aging Barrel batch names an item that no longer exists, ignoring it: {}",
					result.error().map(DataResult.Error::message).orElse("?"));
				return DataResult.success(Pair.of(ItemStack.EMPTY, input));
			}
			return result;
		}

		@Override
		public <T> DataResult<T> encode(ItemStack stack, DynamicOps<T> ops, T prefix) {
			return ItemStack.OPTIONAL_CODEC.encode(stack, ops, prefix);
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

	/** The same fields in the same order as on 26.x (written by hand: 1.21.1's StreamCodec.composite ends at six). */
	public static final StreamCodec<RegistryFriendlyByteBuf, BarrelContents> STREAM_CODEC = StreamCodec.of(
		(buf, c) -> {
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, c.base);
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, c.bucket);
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, c.young);
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, c.aged);
			ByteBufCodecs.VAR_INT.encode(buf, c.servings);
			ByteBufCodecs.VAR_LONG.encode(buf, c.elapsed);
			ByteBufCodecs.VAR_INT.encode(buf, c.fermentingTime);
			ByteBufCodecs.VAR_INT.encode(buf, c.agingTime);
		},
		buf -> new BarrelContents(
			ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
			ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
			ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
			ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.VAR_LONG.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf)));

	private final ItemStack base;
	private final ItemStack bucket;
	private final ItemStack young;
	private final ItemStack aged;
	private final int servings;
	private final long elapsed;
	private final int fermentingTime;
	private final int agingTime;
	private final int hashCode;

	/** Component values must never change once created (copies of an item share them), so stacks are copied in and out. */
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
		this.hashCode = Objects.hash(ItemStack.hashItemAndComponents(this.base), ItemStack.hashItemAndComponents(this.bucket),
			ItemStack.hashItemAndComponents(this.young), ItemStack.hashItemAndComponents(this.aged), this.servings, this.elapsed,
			fermentingTime, agingTime);
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
