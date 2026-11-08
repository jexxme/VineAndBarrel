package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.kettle.KettleTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A campfire's smoke starts anywhere up to two blocks above it, so under a Brew Kettle it came out through the pot in
 * big puffs. With a kettle sitting on it (on its tray, or above a hopper) the campfire makes no smoke of its own; the
 * kettle's steam shows the heat instead. Covers the dousing puff too. A signal fire still sends up its column.
 */
@Mixin(CampfireBlock.class)
public abstract class CampfireUnderKettleMixin
{
	@Inject(at = @At("HEAD"), method = "makeParticles", cancellable = true)
	private static void vineandbarrel$noSmokeThroughKettle(Level level, BlockPos pos, boolean isSignalFire, boolean smoking, CallbackInfo ci) {
		if (KettleTags.hidesCampfireSmoke(level, pos, isSignalFire)) {
			ci.cancel();
		}
	}
}
