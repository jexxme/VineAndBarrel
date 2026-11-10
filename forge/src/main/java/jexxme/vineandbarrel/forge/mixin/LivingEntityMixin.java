package jexxme.vineandbarrel.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.forge.VineAndBarrelForge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Marks the moment an entity finishes Farmer's Delight's milk bottle: only what the bottle itself does then is kept
 * away from Tipsy (see {@link VineAndBarrelForge#finishingMilkBottle}). Forge 47's {@code MobEffectEvent.Remove} does
 * not say what removes an effect (NeoForge 21.1's named the cure), and going by the item in use alone would also stop
 * our own lowering of Tipsy, and every other removal, while a stack of bottles is at the lips.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin
{
	@WrapOperation(method = "completeUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack vineandbarrel$finishingMilkBottle(ItemStack stack, Level level, LivingEntity entity, Operation<ItemStack> original) {
		if (!VineAndBarrelForge.isMilkBottle(stack)) {
			return original.call(stack, level, entity);
		}
		VineAndBarrelForge.finishingMilkBottle(1);
		try {
			return original.call(stack, level, entity);
		} finally {
			VineAndBarrelForge.finishingMilkBottle(-1);
		}
	}
}
