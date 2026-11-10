package jexxme.vineandbarrel.barrel;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * An Aging Barrel that keeps its batch when broken (B12), like FD's cooking pot item and the Brew Kettle: a bar shows how
 * full it is, and the tooltip shows the drink, its servings and its stage. Its age stays put while it is an item.
 * <p>
 * 1.20.1 has no data components: the batch and the "home brewed" mark are in the item's NBT, in vanilla's
 * {@code BlockEntityTag} under the block entity's own keys, so the loot table copies them straight from the block entity
 * ({@code copy_nbt}: {@code Batch} to {@code BlockEntityTag.Batch}, {@code HomeBrewed} to {@code BlockEntityTag.HomeBrewed};
 * the name with {@code copy_name}):
 * <pre>
 * {BlockEntityTag: {Batch: {base: {...}, young: {...}, aged: {...}, servings: 5, elapsed: 30000, ...}, HomeBrewed: 1b},
 *  display: {Name: '...'}}
 * </pre>
 * {@code Batch} is {@link BarrelContents#CODEC}; its {@code elapsed} is the batch's age.
 */
public class AgingBarrelItem extends BlockItem
{
	private static final int BAR_COLOR = Mth.color(0.4F, 0.4F, 1.0F);
	/** Vanilla's key for the block entity data a block item carries ({@link BlockItem#getBlockEntityData}). */
	private static final String BLOCK_ENTITY_TAG = "BlockEntityTag";
	/** The block entity's own save keys (see AgingBarrelBlockEntity). */
	static final String BATCH = "Batch";
	static final String HOME_BREWED = "HomeBrewed";

	public AgingBarrelItem(Block block, Properties properties) {
		super(block, properties);
	}

	/** The batch this barrel item holds, with its age as {@link BarrelContents#elapsed()} (26.x, 1.21.1: its barrel_contents component). */
	public static BarrelContents contentsOf(ItemStack stack) {
		CompoundTag data = getBlockEntityData(stack);
		Tag batch = data == null ? null : data.get(BATCH);
		return batch == null ? BarrelContents.EMPTY : BarrelContents.fromTag(batch,
			problem -> VineAndBarrel.LOGGER.warn("An Aging Barrel item's batch can't be read: {}", problem));
	}

	/** Puts a batch into a barrel item (an empty one takes the batch out, and the "home brewed" mark with it). */
	public static void setContents(ItemStack stack, BarrelContents contents) {
		Tag batch = contents.toTag();
		if (batch != null) {
			stack.getOrCreateTagElement(BLOCK_ENTITY_TAG).put(BATCH, batch);
			return;
		}
		CompoundTag data = getBlockEntityData(stack);
		if (data != null) {
			data.remove(BATCH);
			data.remove(HOME_BREWED);
			if (data.isEmpty()) {
				stack.removeTagKey(BLOCK_ENTITY_TAG);
			}
		}
	}

	/**
	 * Whether the barrel item's batch was poured in during play (not a tavern's stock from world generation), so tapping
	 * it aged still earns Patience Pays after the barrel was moved (B12; 26.x, 1.21.1: its home_brewed component).
	 */
	public static boolean isHomeBrewed(ItemStack stack) {
		CompoundTag data = getBlockEntityData(stack);
		return data != null && data.getBoolean(HOME_BREWED);
	}

	public static void setHomeBrewed(ItemStack stack, boolean homeBrewed) {
		if (homeBrewed) {
			stack.getOrCreateTagElement(BLOCK_ENTITY_TAG).putBoolean(HOME_BREWED, true);
			return;
		}
		CompoundTag data = getBlockEntityData(stack);
		if (data != null) {
			data.remove(HOME_BREWED);
			if (data.isEmpty()) {
				stack.removeTagKey(BLOCK_ENTITY_TAG);
			}
		}
	}

	/** B16: an empty barrel burns like a vanilla barrel, a full one never (it would burn the batch). */
	public static final int BURN_TIME = 300;

	/** Whether this stack is a barrel that must not burn (it holds a batch); the loaders' fuel hooks ask this. */
	public static boolean fuelBlocked(ItemStack stack) {
		return stack.getItem() instanceof AgingBarrelItem && !contentsOf(stack).isEmpty();
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return !contentsOf(stack).isEmpty();
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.min(1 + 12 * contentsOf(stack).servings() / AgingBarrelBlockEntity.CAPACITY, 13);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BAR_COLOR;
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		BarrelContents contents = contentsOf(stack);
		return Optional.of(new BarrelTooltipData(contents.isEmpty() ? ItemStack.EMPTY : contents.drinkAt(contents.elapsed()), contents.servings()));
	}

	/** The stage line under the drink: "Fermenting", "Young", "Aged" (aging pauses while it is an item). */
	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> builder, TooltipFlag flag) {
		BarrelContents contents = contentsOf(stack);
		if (!contents.isEmpty()) {
			String stage = contents.stageAt(contents.elapsed()).name().toLowerCase(java.util.Locale.ROOT);
			builder.add(Component.translatable("tooltip.vineandbarrel.aging_barrel." + stage).withStyle(ChatFormatting.GRAY));
			if (contents.stageAt(contents.elapsed()) != BarrelStage.AGED) {
				builder.add(Component.translatable("tooltip.vineandbarrel.aging_barrel.paused").withStyle(ChatFormatting.DARK_GRAY));
			}
		}
	}

	/** The drink shown in the barrel item's tooltip (rendered by the client's {@code BarrelTooltip}). */
	public record BarrelTooltipData(ItemStack drink, int servings) implements TooltipComponent
	{
	}
}
