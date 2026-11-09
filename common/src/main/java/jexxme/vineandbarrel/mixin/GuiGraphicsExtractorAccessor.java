package jexxme.vineandbarrel.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets a screen hand vanilla's GUI renderer its own 3D picture ("Watch it" scenes), the way vanilla's book and entity
 * pictures go in. NeoForge and Fabric each have their own method for it; this works the same on both.
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor
{
	@Accessor("guiRenderState")
	GuiRenderState vineandbarrel$guiRenderState();
}
