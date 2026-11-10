package jexxme.vineandbarrel.compat.appleskin;

import jexxme.vineandbarrel.drink.DrinkItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * The food a drink really gives, for food previews (AppleSkin). Our drinks have no food of their own, but wheat beer
 * feeds through its Saturation buff: vanilla's Saturation feeds (level + 1) food and twice that in saturation every tick
 * it lasts, so young wheat beer (level I, 5 ticks) is 5 food and 10 saturation and aged (level II, 4 ticks) 8 and 16.
 * No AppleSkin classes here, so the GameTests check it with and without AppleSkin.
 */
public final class DrinkFood
{
	private DrinkFood() {
	}

	/** The drink's real food (its own plus what its Saturation buff feeds), or empty if that is just its own. */
	public static Optional<FoodProperties> of(ItemStack stack) {
		if (!(stack.getItem() instanceof DrinkItem drink)) {
			return Optional.empty();
		}
		MobEffectInstance buff = drink.buff();
		if (!buff.is(MobEffects.SATURATION)) {
			return Optional.empty();
		}
		int perTick = buff.getAmplifier() + 1;
		int nutrition = perTick * buff.getDuration();
		float saturation = FoodConstants.saturationByModifier(perTick, 1.0F) * buff.getDuration();
		FoodProperties own = stack.get(DataComponents.FOOD);
		// 1.21.1: the food component also carries the eating time, the vessel and the buff; they stay as they are
		return Optional.of(own == null ? new FoodProperties(nutrition, saturation, true, 1.6F, Optional.empty(), List.of())
			: new FoodProperties(own.nutrition() + nutrition, own.saturation() + saturation, own.canAlwaysEat(), own.eatSeconds(),
				own.usingConvertsTo(), own.effects()));
	}
}
