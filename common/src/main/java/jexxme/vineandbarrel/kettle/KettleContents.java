package jexxme.vineandbarrel.kettle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * What a Brew Kettle item carries when it was broken with a brew inside (K6), like FD's cooking pot meal component.
 *
 * @param brew   the finished brew in its bottle form; the count is the number of servings
 * @param bucket the same brew in its bucket form (one bucket = {@link BrewKettleBlockEntity#SERVINGS_PER_BUCKET} servings)
 */
public final class KettleContents
{
	public static final KettleContents EMPTY = new KettleContents(ItemStack.EMPTY, ItemStack.EMPTY);

	public static final Codec<KettleContents> CODEC = RecordCodecBuilder.create(inst -> inst.group(
		ItemStack.OPTIONAL_CODEC.optionalFieldOf("brew", ItemStack.EMPTY).forGetter(c -> c.brew),
		ItemStack.OPTIONAL_CODEC.optionalFieldOf("bucket", ItemStack.EMPTY).forGetter(c -> c.bucket)
	).apply(inst, KettleContents::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, KettleContents> STREAM_CODEC = StreamCodec.composite(
		ItemStack.OPTIONAL_STREAM_CODEC, c -> c.brew,
		ItemStack.OPTIONAL_STREAM_CODEC, c -> c.bucket,
		KettleContents::new);

	private final ItemStack brew;
	private final ItemStack bucket;
	private final int hashCode;

	/** Component values must never change once created (copies of an item share them), so stacks are copied in and out. */
	public KettleContents(ItemStack brew, ItemStack bucket) {
		this.brew = brew.copy();
		this.bucket = bucket.copy();
		this.hashCode = 31 * ItemStack.hashItemAndComponents(brew) + brew.getCount() * 7 + ItemStack.hashItemAndComponents(bucket);
	}

	public ItemStack brew() {
		return brew.copy();
	}

	public ItemStack bucket() {
		return bucket.copy();
	}

	public int servings() {
		return brew.getCount();
	}

	public boolean isEmpty() {
		return brew.isEmpty();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof KettleContents other && ItemStack.matches(brew, other.brew) && ItemStack.matches(bucket, other.bucket);
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public String toString() {
		return "KettleContents[" + brew + ", " + bucket + "]";
	}
}
