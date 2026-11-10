package jexxme.vineandbarrel.forge.mixin.client;

import jexxme.vineandbarrel.forge.client.ForgeClient;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiFunction;

/**
 * Keeps the sprite lookup the models of this reload are baked with. Forge 47's {@code ModelEvent.ModifyBakingResult}
 * does not hand it out (NeoForge 21.1's had {@code getTextureGetter()}), and the atlases themselves still hold the last
 * reload's sprites while models are baked; the trellis frame bakes its turned pieces with it ({@code TrellisFrameModel}).
 */
@Mixin(ModelBakery.class)
abstract class ModelBakeryMixin
{
	@Inject(method = "bakeModels", at = @At("HEAD"))
	private void vineandbarrel$keepSprites(BiFunction<ResourceLocation, Material, TextureAtlasSprite> sprites, CallbackInfo callback) {
		ForgeClient.bakingSprites((ModelBakery) (Object) this, sprites);
	}
}
