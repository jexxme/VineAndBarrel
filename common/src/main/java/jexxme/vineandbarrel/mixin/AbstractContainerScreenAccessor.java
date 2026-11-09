package jexxme.vineandbarrel.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The slot under the mouse, so the "Watch it" key opens the scene of the item there. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor
{
	@Accessor("hoveredSlot")
	@Nullable Slot vineandbarrel$hoveredSlot();
}
