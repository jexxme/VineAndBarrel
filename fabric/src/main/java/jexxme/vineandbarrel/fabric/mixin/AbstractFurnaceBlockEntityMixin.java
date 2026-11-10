package jexxme.vineandbarrel.fabric.mixin;

import jexxme.vineandbarrel.barrel.AgingBarrelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A barrel with a batch in it is no fuel (it would burn the batch); an empty one burns like a vanilla barrel (Fabric's
 * fuel registry, see VineAndBarrelFabric). The fuel registry knows items only, not their contents, so the furnace's two
 * questions about a stack are answered here. NeoForge: FurnaceFuelBurnTimeEvent.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin
{
	@Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
	private static void vineandbarrel$fullBarrelIsNoFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (AgingBarrelItem.fuelBlocked(stack)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getBurnDuration", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$fullBarrelBurnsNot(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (AgingBarrelItem.fuelBlocked(stack)) {
			cir.setReturnValue(0);
		}
	}
}
