package jexxme.vineandbarrel.mixin;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * A village pool's pieces: the weighted list it is saved as and the flat list it draws from (both private and final).
 * {@code VillageStructures} adds our taverns to the vanilla house pools, exactly like Farmer's Delight.
 */
@Mixin(StructureTemplatePool.class)
public interface StructureTemplatePoolAccessor
{
	@Accessor("rawTemplates")
	List<Pair<StructurePoolElement, Integer>> vineandbarrel$rawTemplates();

	@Mutable
	@Accessor("rawTemplates")
	void vineandbarrel$setRawTemplates(List<Pair<StructurePoolElement, Integer>> rawTemplates);

	@Accessor("templates")
	ObjectArrayList<StructurePoolElement> vineandbarrel$templates();
}
