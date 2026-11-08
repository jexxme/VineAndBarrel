package jexxme.vineandbarrel.smoke.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where an open screen's panel is, so the smoke test can hover what is drawn next to it (the effect list). */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor
{
	@Accessor("leftPos")
	int vineandbarrel_smoke$leftPos();

	@Accessor("topPos")
	int vineandbarrel_smoke$topPos();

	@Accessor("imageWidth")
	int vineandbarrel_smoke$imageWidth();
}
