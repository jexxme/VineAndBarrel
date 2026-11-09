package jexxme.vineandbarrel.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.multiplayer.ClientAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** The player's own advancement progress as the client knows it, for the Brewer's path's ticks. */
@Mixin(ClientAdvancements.class)
public interface ClientAdvancementsAccessor
{
	@Accessor("progress")
	Map<AdvancementHolder, AdvancementProgress> vineandbarrel$progress();
}
