package jexxme.vineandbarrel.fabric.mixin;

import jexxme.vineandbarrel.fabric.FabricGameTestRegistration;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** The GameTests join the test registries a server loads from its data, just before they freeze (dev only). */
@Mixin(RegistryLoadTask.class)
abstract class RegistryLoadTaskMixin
{
	@Shadow
	@Final
	private WritableRegistry<?> registry;

	@Inject(method = "freezeRegistry", at = @At("HEAD"))
	private void vineandbarrel$addGameTests(Map<ResourceKey<?>, Exception> loadingErrors, CallbackInfoReturnable<Boolean> callback) {
		if ((Object) this instanceof ResourceManagerRegistryLoadTask<?>) {
			FabricGameTestRegistration.beforeFreeze(registry);
		}
	}
}
