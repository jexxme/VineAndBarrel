package jexxme.vineandbarrel.fabric.mixin;

import jexxme.vineandbarrel.fabric.FabricWildCrops;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * The world's data packs, as its biomes are loaded from them: wild crops read their biome modifier files from the same
 * resources ({@link FabricWildCrops}), so a data pack's replacement counts, as on NeoForge.
 */
@Mixin(RegistryDataLoader.class)
abstract class RegistryDataLoaderMixin
{
	@Inject(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/RegistryAccess;Ljava/util/List;)Lnet/minecraft/core/RegistryAccess$Frozen;",
		at = @At("HEAD"))
	private static void vineandbarrel$worldgenResources(ResourceManager resourceManager, RegistryAccess registryAccess, List<RegistryDataLoader.RegistryData<?>> registries,
		CallbackInfoReturnable<RegistryAccess.Frozen> callback) {
		for (RegistryDataLoader.RegistryData<?> data : registries) {
			if (data.key().equals(Registries.BIOME)) {
				FabricWildCrops.loadingWorldgen(resourceManager);
				return;
			}
		}
	}
}
