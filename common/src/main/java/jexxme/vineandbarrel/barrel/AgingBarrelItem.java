package jexxme.vineandbarrel.barrel;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.Block;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * An Aging Barrel that keeps its batch when broken (B12), like FD's cooking pot item and the Brew Kettle: a bar shows how
 * full it is, and the tooltip shows the drink, its servings and its stage. Its age stays put while it is an item.
 */
public class AgingBarrelItem extends BlockItem
{
	private static final int BAR_COLOR = ARGB.colorFromFloat(1.0F, 0.4F, 0.4F, 1.0F);

	public AgingBarrelItem(Block block, Properties properties) {
		super(block, properties);
	}

	public static BarrelContents contentsOf(ItemStack stack) {
		return stack.getOrDefault(BarrelRegistry.BARREL_CONTENTS.get(), BarrelContents.EMPTY);
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
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		BarrelContents contents = contentsOf(stack);
		if (!contents.isEmpty()) {
			String stage = contents.stageAt(contents.elapsed()).name().toLowerCase(java.util.Locale.ROOT);
			builder.accept(Component.translatable("tooltip.vineandbarrel.aging_barrel." + stage).withStyle(ChatFormatting.GRAY));
			if (contents.stageAt(contents.elapsed()) != BarrelStage.AGED) {
				builder.accept(Component.translatable("tooltip.vineandbarrel.aging_barrel.paused").withStyle(ChatFormatting.DARK_GRAY));
			}
		}
	}

	/** The drink shown in the barrel item's tooltip (rendered by the client's {@code BarrelTooltip}). */
	public record BarrelTooltipData(ItemStack drink, int servings) implements TooltipComponent
	{
	}
}
