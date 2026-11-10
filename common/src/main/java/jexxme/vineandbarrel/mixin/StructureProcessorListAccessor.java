package jexxme.vineandbarrel.mixin;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * A processor list's processors (private and final): {@code VillageStructures} puts our farm crop processor into the
 * village farm lists, exactly like Farmer's Delight.
 */
@Mixin(StructureProcessorList.class)
public interface StructureProcessorListAccessor
{
	@Mutable
	@Accessor("list")
	void vineandbarrel$setList(List<StructureProcessor> list);
}
