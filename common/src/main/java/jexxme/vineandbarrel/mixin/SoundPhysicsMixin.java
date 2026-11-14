package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import jexxme.vineandbarrel.tipsy.client.DrunkEars;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Woolly ears ({@link DrunkEars}) with Sound Physics Remastered: as a world sound plays (and again as it moves), that mod
 * sets a low-pass filter of its own on it (its muffling through walls, and its echoes), in place of the ears' one. The
 * ears' muffle goes into each of its filters instead, on top of its own. Only when that mod is there; wrapped, so other
 * mods' changes chain with it, and left out (no error) should a version of it have no such filters.
 */
@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
abstract class SoundPhysicsMixin
{
	@WrapOperation(method = "setEnvironment(IFFFFFFFFFF)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/EXTEfx;alFilterf(IIF)V"), require = 0)
	private static void vineandbarrel$woollyEars(int filter, int param, float value, Operation<Void> original, @Local(argsOnly = true) int source) {
		original.call(filter, param, DrunkEars.intoSoundPhysicsFilter(source, param, value));
	}
}
