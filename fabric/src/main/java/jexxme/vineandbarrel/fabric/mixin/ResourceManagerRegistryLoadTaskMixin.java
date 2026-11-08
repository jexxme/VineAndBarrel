package jexxme.vineandbarrel.fabric.mixin;

import com.mojang.serialization.Lifecycle;
import jexxme.vineandbarrel.fabric.FabricWildCrops;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * The world's data packs, as its biomes are loaded from them: wild crops read their biome modifier files from the same
 * resources ({@link FabricWildCrops}), so a data pack's replacement counts, as on NeoForge.
 */
@Mixin(ResourceManagerRegistryLoadTask.class)
abstract class ResourceManagerRegistryLoadTaskMixin
{
	@Inject(method = "<init>", at = @At("TAIL"))
	private void vineandbarrel$worldgenResources(RegistryDataLoader.RegistryData<?> data, Lifecycle lifecycle, Map<ResourceKey<?>, Exception> loadingErrors,
												 ResourceManager resourceManager, CallbackInfo ci) {
		if (data.key().equals(Registries.BIOME)) {
			FabricWildCrops.loadingWorldgen(resourceManager);
		}
	}
}
