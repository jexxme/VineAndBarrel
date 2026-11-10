package jexxme.vineandbarrel.kettle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * What a Brew Kettle item carries when it was broken with a brew inside (K6), like FD's cooking pot meal. On this
 * Minecraft it lives in the kettle item's NBT ({@link BrewKettleItem#contentsOf} / {@link BrewKettleItem#setContents};
 * newer Minecraft: a data component).
 *
 * @param brew   the finished brew in its bottle form; the count is the number of servings
 * @param bucket the same brew in its bucket form (one bucket = {@link BrewKettleBlockEntity#SERVINGS_PER_BUCKET} servings)
 */
public final class KettleContents
{
	public static final KettleContents EMPTY = new KettleContents(ItemStack.EMPTY, ItemStack.EMPTY);

	private static final String BREW = "brew";
	private static final String BUCKET = "bucket";

	private final ItemStack brew;
	private final ItemStack bucket;
	private final int hashCode;

	/** Values must never change once created (they are handed around like newer Minecraft's component values), so stacks are copied in and out. */
	public KettleContents(ItemStack brew, ItemStack bucket) {
		this.brew = brew.copy();
		this.bucket = bucket.copy();
		this.hashCode = 31 * hashItemAndTag(brew) + brew.getCount() * 7 + hashItemAndTag(bucket);
	}

	private static int hashItemAndTag(ItemStack stack) {
		return stack.isEmpty() ? 0 : 31 * stack.getItem().hashCode() + Objects.hashCode(stack.getTag());
	}

	/** What {@link #save} wrote: {@code brew} and {@code bucket}, each an item stack as this Minecraft saves it (missing: empty). */
	public static KettleContents load(CompoundTag tag) {
		ItemStack brew = tag.contains(BREW, net.minecraft.nbt.Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound(BREW)) : ItemStack.EMPTY;
		ItemStack bucket = tag.contains(BUCKET, net.minecraft.nbt.Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound(BUCKET)) : ItemStack.EMPTY;
		return brew.isEmpty() && bucket.isEmpty() ? EMPTY : new KettleContents(brew, bucket);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		if (!brew.isEmpty()) {
			tag.put(BREW, brew.save(new CompoundTag()));
		}
		if (!bucket.isEmpty()) {
			tag.put(BUCKET, bucket.save(new CompoundTag()));
		}
		return tag;
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
