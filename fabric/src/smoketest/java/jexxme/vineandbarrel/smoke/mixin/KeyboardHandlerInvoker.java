package jexxme.vineandbarrel.smoke.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A key press as the window delivers it (the smoke test presses the "Watch it" key over an item). */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerInvoker
{
	@Invoker("keyPress")
	void vineandbarrel_smoke$keyPress(long handle, int action, KeyEvent event);
}
