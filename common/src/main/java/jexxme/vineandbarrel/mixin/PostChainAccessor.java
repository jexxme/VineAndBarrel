package jexxme.vineandbarrel.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * A post chain's passes, so the drunk eyes ({@code tipsy.client.DrunkEyes}) can set their shader's uniforms each frame:
 * 1.20.1's chain has no way to set a uniform from outside (newer versions read it from a texture instead).
 */
@Mixin(PostChain.class)
public interface PostChainAccessor
{
	@Accessor("passes")
	List<PostPass> vineandbarrel$passes();
}
