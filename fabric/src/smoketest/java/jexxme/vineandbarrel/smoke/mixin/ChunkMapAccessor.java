package jexxme.vineandbarrel.smoke.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The chunks a level holds, for {@link jexxme.vineandbarrel.smoke.SmokeQuiet}. */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor
{
	@Accessor("updatingChunkMap")
	Long2ObjectLinkedOpenHashMap<ChunkHolder> vineandbarrel_smoke$updatingChunkMap();
}
