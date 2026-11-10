package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * C3/C7: horses and llamas accept barley and the Barley Bale through the vanilla food tags, but their
 * {@code handleEating} hard-codes what each food does (wheat heals 2, a hay bale heals 10 to 20 and ages foals up).
 * So {@code handleEating} sees barley as wheat and the bale as a hay bale: the same effect. The caller
 * ({@code fedFood}) still uses up the real stack.
 */
@Mixin({AbstractHorse.class, Llama.class})
public abstract class HorseFoodMixin
{
	@ModifyVariable(at = @At("HEAD"), method = "handleEating", argsOnly = true)
	private ItemStack vineandbarrel$eatLikeVanilla(ItemStack itemStack) {
		if (itemStack.is(VBItems.BARLEY.get())) {
			return new ItemStack(Items.WHEAT, itemStack.getCount());
		}
		if (itemStack.is(VBItems.BARLEY_BALE.get())) {
			return new ItemStack(Items.HAY_BLOCK, itemStack.getCount());
		}
		return itemStack;
	}
}
