package jexxme.vineandbarrel.forge.mixin;

import jexxme.vineandbarrel.forge.ForgeCopper;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Honeycomb used the vanilla way (sneaking, or by anything that asks {@code getWaxed}) waxes a copper kettle
 * (NeoForge 21.1: the waxables data map). Vanilla's table is fixed; see {@link ForgeCopper}.
 */
@Mixin(HoneycombItem.class)
abstract class HoneycombItemMixin
{
	@Inject(method = "getWaxed", at = @At("HEAD"), cancellable = true)
	private static void vineandbarrel$waxedKettle(BlockState state, CallbackInfoReturnable<Optional<BlockState>> callback) {
		Block waxed = ForgeCopper.waxed(state.getBlock());
		if (waxed != null) {
			callback.setReturnValue(Optional.of(waxed.withPropertiesOf(state)));
		}
	}
}
