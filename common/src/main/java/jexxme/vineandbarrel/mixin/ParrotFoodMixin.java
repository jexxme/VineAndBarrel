package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import jexxme.vineandbarrel.entity.AnimalFoods;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * C3: barley seeds tame a parrot like any seeds, as on the newer versions, where what tames a parrot is the item tag
 * {@code minecraft:parrot_food}. On this Minecraft it is a fixed set in the parrot's code, so the check also asks the tag
 * (see {@link AnimalFoods}).
 */
@Mixin(Parrot.class)
public abstract class ParrotFoodMixin
{
	@ModifyExpressionValue(method = "mobInteract", at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z", remap = false))
	private boolean vineandbarrel$tameFoodByTag(boolean tameFood, Player player, InteractionHand hand) {
		return tameFood || player.getItemInHand(hand).is(AnimalFoods.PARROT_FOOD);
	}
}
