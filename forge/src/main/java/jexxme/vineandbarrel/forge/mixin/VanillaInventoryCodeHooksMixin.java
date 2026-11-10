package jexxme.vineandbarrel.forge.mixin;

import jexxme.vineandbarrel.forge.transfer.ForgeItemAccess;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.VanillaInventoryCodeHooks;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Hoppers and droppers at a big kettle. On Forge 47 only a block entity has an item capability, and of a big kettle's
 * blocks only one has a block entity (the controller). Forge's hoppers and droppers look for the inventory next to them
 * here, so here the other kettle blocks answer with their kettle's inventory, as every kettle block did through
 * NeoForge 21.1's block capabilities. (A Forge class: nothing is remapped.)
 */
@Mixin(value = VanillaInventoryCodeHooks.class, remap = false)
abstract class VanillaInventoryCodeHooksMixin
{
	@Inject(method = "getItemHandler(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/core/Direction;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true, remap = false)
	private static void vineandbarrel$kettlePart(Level level, double x, double y, double z, Direction side,
		CallbackInfoReturnable<Optional<Pair<IItemHandler, Object>>> callback) {
		if (callback.getReturnValue().isPresent()) {
			return;
		}
		BrewKettleBlockEntity kettle = ForgeItemAccess.kettleBehindPart(level, BlockPos.containing(x, y, z));
		if (kettle != null) {
			callback.setReturnValue(Optional.of(ImmutablePair.<IItemHandler, Object>of(ForgeItemAccess.kettleView(kettle, side), kettle)));
		}
	}
}
