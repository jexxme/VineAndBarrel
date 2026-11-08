package jexxme.vineandbarrel.kettle;

import net.minecraft.util.ARGB;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * A Brew Kettle that keeps its brew when broken (K6), exactly like FD's {@code CookingPotItem} (MIT): a bar shows how
 * full it is and the tooltip shows the brew and its servings.
 */
public class BrewKettleItem extends BlockItem
{
	private static final int BAR_COLOR = ARGB.colorFromFloat(1.0F, 0.4F, 0.4F, 1.0F);

	public BrewKettleItem(Block block, Properties properties) {
		super(block, properties);
	}

	public static KettleContents contentsOf(ItemStack stack) {
		return stack.getOrDefault(KettleRegistry.KETTLE_CONTENTS.get(), KettleContents.EMPTY);
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
