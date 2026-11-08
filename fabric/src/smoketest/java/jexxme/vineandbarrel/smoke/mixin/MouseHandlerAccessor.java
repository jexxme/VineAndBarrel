package jexxme.vineandbarrel.smoke.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The hidden window gets no mouse events: the smoke test puts the pointer where it wants to hover. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor
{
	@Accessor("xpos")
	void vineandbarrel_smoke$setXpos(double xpos);

	@Accessor("ypos")
	void vineandbarrel_smoke$setYpos(double ypos);
}
