package jexxme.vineandbarrel.fabric.mixin;

import jexxme.vineandbarrel.barrel.AgingBarrelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2: a barrel with a batch in it is no fuel (it would burn the batch); an empty one burns like a vanilla barrel
 * (Fabric's fuel registry, see VineAndBarrelFabric). NeoForge: FurnaceFuelBurnTimeEvent. 26.3: the cooking_fuel component.
 */
@Mixin(FuelValues.class)
public abstract class FuelValuesMixin
{
	@Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$fullBarrelIsNoFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (AgingBarrelItem.fuelBlocked(stack)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "burnDuration", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$fullBarrelBurnsNot(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (AgingBarrelItem.fuelBlocked(stack)) {
			cir.setReturnValue(0);
		}
	}
}
