package jexxme.vineandbarrel.compat.appleskin;

import jexxme.vineandbarrel.drink.DrinkItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

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
		if (buff.getEffect() != MobEffects.SATURATION) {
			return Optional.empty();
		}
		int perTick = buff.getAmplifier() + 1;
		int nutrition = perTick * buff.getDuration();
		// Vanilla's saturation of a food: nutrition x modifier x 2 (Saturation feeds with modifier 1)
		float saturation = perTick * 1.0F * 2.0F * buff.getDuration();
		FoodProperties own = stack.getItem().getFoodProperties();
		// 1.20.1: a food says its saturation as a modifier of its nutrition, so the sum is turned back into one
		FoodProperties.Builder food = new FoodProperties.Builder();
		if (own == null) {
			return Optional.of(food.nutrition(nutrition).saturationMod(saturation / (nutrition * 2.0F)).alwaysEat().build());
		}
		int totalNutrition = own.getNutrition() + nutrition;
		float totalSaturation = own.getNutrition() * own.getSaturationModifier() * 2.0F + saturation;
		food.nutrition(totalNutrition).saturationMod(totalNutrition == 0 ? 0.0F : totalSaturation / (totalNutrition * 2.0F));
		if (own.canAlwaysEat()) {
			food.alwaysEat();
		}
		if (own.isFastFood()) {
			food.fast();
		}
		if (own.isMeat()) {
			food.meat();
		}
		for (Pair<MobEffectInstance, Float> effect : own.getEffects()) {
			food.effect(effect.getFirst(), effect.getSecond());
		}
		return Optional.of(food.build());
	}
}
