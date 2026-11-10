package jexxme.vineandbarrel.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * The item frames that stand in for the taverns' shelves on this Minecraft (the build turns each shelf of a template
 * into one, marked {@code vineandbarrel_shelf}) get the block they hang in from where the template is placed. A template
 * can't know it, and vanilla logs an error for a frame whose saved block is far from its position.
 */
@Mixin(StructureTemplate.class)
public abstract class StructureEntityTileMixin
{
	@Inject(method = "createEntityIgnoreException", at = @At("HEAD"))
	private static void vineandbarrel$shelfFrameTile(ServerLevelAccessor level, CompoundTag tag, CallbackInfoReturnable<Optional<Entity>> cir) {
		if (tag.contains("vineandbarrel_shelf") && tag.contains("Pos", Tag.TAG_LIST)) {
			ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
			BlockPos tile = BlockPos.containing(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
			tag.putInt("TileX", tile.getX());
			tag.putInt("TileY", tile.getY());
			tag.putInt("TileZ", tile.getZ());
		}
	}
}
