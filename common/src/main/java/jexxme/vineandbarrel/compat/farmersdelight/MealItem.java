package jexxme.vineandbarrel.compat.farmersdelight;

import jexxme.vineandbarrel.drink.DrinkItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * A cooking pot meal (DESIGN §7). Eats like FD's bowl meals: food and bowl come from the item's food component, and with
 * Farmer's Delight installed it also gives FD's Nourishment, shown in the tooltip like FD shows it. Nourishment is
 * looked up by id, so without FD the meal is simply a good bowl of food.
 */
public class MealItem extends Item
{
	private final int nourishmentTicks;

	public MealItem(Properties properties, int nourishmentTicks) {
		super(properties);
		this.nourishmentTicks = nourishmentTicks;
	}

	public int nourishmentTicks() {
		return nourishmentTicks;
	}

	/** Exactly FD's {@code FoodValues.nourishment(duration)}: not ambient, no particles, no HUD icon. */
	public static MobEffectInstance nourishment(Holder<MobEffect> effect, int ticks) {
		return new MobEffectInstance(effect, ticks, 0, false, false);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!level.isClientSide()) {
			FDCompat.nourishment().ifPresent(effect -> entity.addEffect(nourishment(effect, nourishmentTicks)));
		}
		// The bowl comes back as on 26.x, also with a full inventory (see DrinkItem.Remainder)
		DrinkItem.Remainder bowl = DrinkItem.Remainder.before(stack, entity);
		return bowl.after(super.finishUsingItem(stack, level, entity), level, entity);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> builder, TooltipFlag flag) {
		Optional<Holder.Reference<MobEffect>> nourishment = FDCompat.nourishment();
		if (nourishment.isPresent()) {
			MobEffectInstance effect = new MobEffectInstance(nourishment.get(), nourishmentTicks, 0);
			builder.add(Component.translatable("potion.withDuration", Component.translatable(effect.getDescriptionId()),
				MobEffectUtil.formatDuration(effect, 1.0F, context.tickRate())).withStyle(ChatFormatting.BLUE));
		}
	}
}
