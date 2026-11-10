package jexxme.vineandbarrel.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.tipsy.TipsyEvents;
import jexxme.vineandbarrel.tipsy.TipsyRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A finished drink (what Forge's {@code LivingEntityUseItemEvent.Finish} is): sobering drinks lower Tipsy.
 * <p>
 * And Farmer's Delight's milk bottle leaves Tipsy alone: it removes one random effect that a milk bucket cures (every
 * effect, by Porting Lib's default), straight through {@code removeEffect}, with no event and (in Refabricated for this
 * Minecraft) no tag to keep an effect out. Tipsy isn't its to clear: the bottle lowers it by one level
 * ({@code #vineandbarrel:sobering_drinks}, right after). So while that item finishes, removing Tipsy does nothing, which
 * is what cancelling NeoForge's removal event does there. Farmer's Delight is only named by the item's id.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin
{
	@Unique
	private static final ResourceLocation VINEANDBARREL$FD_MILK_BOTTLE = FDCompat.id("milk_bottle");
	/** True while Farmer's Delight's milk bottle runs its own {@code finishUsingItem} for this entity. */
	@Unique
	private boolean vineandbarrel$finishingMilkBottle;

	@WrapOperation(method = "completeUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack vineandbarrel$finishUsingItem(ItemStack stack, Level level, LivingEntity entity, Operation<ItemStack> original) {
		ItemStack used = stack.copy();
		ItemStack result;
		vineandbarrel$finishingMilkBottle = BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(VINEANDBARREL$FD_MILK_BOTTLE);
		try {
			result = original.call(stack, level, entity);
		} finally {
			vineandbarrel$finishingMilkBottle = false;
		}
		TipsyEvents.onFinishUsingItem(entity, used);
		return result;
	}

	@Inject(method = "removeEffect", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$milkBottleKeepsTipsy(MobEffect effect, CallbackInfoReturnable<Boolean> callback) {
		if (vineandbarrel$finishingMilkBottle && effect == TipsyRegistry.TIPSY.get()) {
			callback.setReturnValue(false);
		}
	}
}
