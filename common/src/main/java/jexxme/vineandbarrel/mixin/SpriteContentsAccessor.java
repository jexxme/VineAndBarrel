package jexxme.vineandbarrel.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A texture's pixels, to tell whether it has half-transparent ones: the blocks with glass are drawn face by face in the
 * layer of each face's texture (VBRenderLayers), as 26.x does for every block.
 */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor
{
	@Accessor("originalImage")
	NativeImage vineandbarrel$originalImage();
}
