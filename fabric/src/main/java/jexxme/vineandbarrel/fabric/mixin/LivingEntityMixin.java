package jexxme.vineandbarrel.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.tipsy.TipsyEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A finished drink (what NeoForge's {@code LivingEntityUseItemEvent.Finish} is): sobering drinks lower Tipsy. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin
{
	@WrapOperation(method = "completeUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack vineandbarrel$finishUsingItem(ItemStack stack, Level level, LivingEntity entity, Operation<ItemStack> original) {
		ItemStack used = stack.copy();
		ItemStack result = original.call(stack, level, entity);
		TipsyEvents.onFinishUsingItem(entity, used);
		return result;
	}
}
