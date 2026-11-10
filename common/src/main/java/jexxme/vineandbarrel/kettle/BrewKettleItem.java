package jexxme.vineandbarrel.kettle;

import jexxme.vineandbarrel.inventory.ItemSlots;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * A Brew Kettle that keeps its brew when broken (K6), exactly like FD's {@code CookingPotItem} (MIT): a bar shows how
 * full it is and the tooltip shows the brew and its servings.
 * <p>
 * On this Minecraft the brew is in the stack's NBT under {@link #CONTENTS_TAG} (newer Minecraft: the
 * {@code vineandbarrel:kettle_contents} component): the kettle block writes it into its drop and reads it when placed.
 */
public class BrewKettleItem extends BlockItem
{
	/** The stack NBT key of a kettle item's {@link KettleContents}: {@code {brew: <item stack>, bucket: <item stack>}}. */
	public static final String CONTENTS_TAG = "KettleContents";

	private static final int BAR_COLOR = Mth.color(0.4F, 0.4F, 1.0F);

	public BrewKettleItem(Block block, Properties properties) {
		super(block, properties);
	}

	/**
	 * The brew a kettle item holds. A kettle picked with its data in creative (Ctrl + pick block) has no contents tag but
	 * the whole block entity under vanilla's {@code BlockEntityTag}: its brew is read from there.
	 */
	public static KettleContents contentsOf(ItemStack stack) {
		KettleContents contents = storedContents(stack);
		if (contents != null) {
			return contents;
		}
		CompoundTag blockEntity = BlockItem.getBlockEntityData(stack);
		if (blockEntity != null && stack.getItem() instanceof BrewKettleItem) {
			ListTag stacks = blockEntity.getCompound("Inventory").getList(ItemSlots.VALUE_IO_KEY, Tag.TAG_COMPOUND);
			if (stacks.size() > BrewKettleBlockEntity.BREW_SLOT && !stacks.getCompound(BrewKettleBlockEntity.BREW_SLOT).isEmpty()) {
				ItemStack brew = ItemStack.of(stacks.getCompound(BrewKettleBlockEntity.BREW_SLOT));
				int servings = blockEntity.getInt("Servings");
				if (servings > 0 && !brew.isEmpty()) {
					brew.setCount(servings);
				}
				return brew.isEmpty() ? KettleContents.EMPTY : new KettleContents(brew, ItemStack.of(blockEntity.getCompound("BucketForm")));
			}
		}
		return KettleContents.EMPTY;
	}

	/** The contents written under {@link #CONTENTS_TAG}, or null if the stack has none. */
	static @org.jspecify.annotations.Nullable KettleContents storedContents(ItemStack stack) {
		CompoundTag tag = stack.getTagElement(CONTENTS_TAG);
		return tag == null ? null : KettleContents.load(tag);
	}

	/** Puts a brew into a kettle item (empty contents: takes it out). */
	public static void setContents(ItemStack stack, KettleContents contents) {
		if (contents.isEmpty()) {
			stack.removeTagKey(CONTENTS_TAG);
		} else {
			stack.addTagElement(CONTENTS_TAG, contents.save());
		}
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return contentsOf(stack).servings() > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.min(1 + 12 * contentsOf(stack).servings() / BrewKettleBlockEntity.MAX_SERVINGS, 13);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BAR_COLOR;
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return Optional.of(new KettleTooltipData(contentsOf(stack).brew()));
	}

	/** The brew shown in the kettle item's tooltip (rendered by the client's {@code KettleTooltip}). */
	public record KettleTooltipData(ItemStack brew) implements TooltipComponent
	{
	}
}
