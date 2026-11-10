package jexxme.vineandbarrel.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The template of a village piece (26.x: {@code SinglePoolElement#getTemplateLocation}; protected on this Minecraft), so
 * {@code VillageStructures} can tell our taverns among a pool's and a village's pieces.
 */
@Mixin(SinglePoolElement.class)
public interface SinglePoolElementAccessor
{
	@Accessor("template")
	Either<ResourceLocation, StructureTemplate> vineandbarrel$template();
}
