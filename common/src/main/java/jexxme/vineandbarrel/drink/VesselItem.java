package jexxme.vineandbarrel.drink;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/** An empty mug or wine glass (DESIGN §5.3): tapped full at an Aging Barrel, and sneak-placed on a block like a drink (D1). */
public class VesselItem extends Item
{
	public VesselItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return context.isSecondaryUseActive() ? DrinkPlacing.place(context) : InteractionResult.PASS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> builder, TooltipFlag flag) {
		builder.add(DrinkPlacing.PLACEABLE_SNEAKING);
	}
}
