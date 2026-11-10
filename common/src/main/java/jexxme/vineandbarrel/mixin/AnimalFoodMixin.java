package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import jexxme.vineandbarrel.entity.AnimalFoods;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * C3/C7: what an animal eats is a fixed set in its code on this Minecraft (1.20.5 and later: an item tag per animal).
 * Each {@code isFood} here also accepts that animal's tag, which our data files fill as on the newer versions (see
 * {@link AnimalFoods}): {@code Animal}'s own for cows, sheep and goats, and the ones chickens, horses and llamas
 * replace it with.
 */
@Mixin({Animal.class, Chicken.class, AbstractHorse.class, Llama.class})
public abstract class AnimalFoodMixin
{
	@ModifyReturnValue(method = "isFood", at = @At("RETURN"))
	private boolean vineandbarrel$foodByTag(boolean original, ItemStack stack) {
		return original || AnimalFoods.eats((Animal) (Object) this, stack);
	}
}
