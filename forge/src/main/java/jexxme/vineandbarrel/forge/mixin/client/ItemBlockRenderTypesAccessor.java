package jexxme.vineandbarrel.forge.mixin.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Vanilla's own table of block render layers. Forge's {@code setRenderLayer} fills only Forge's table, which only
 * Forge's drawing reads; vanilla's {@code getMovingBlockRenderType} ("Watch it" scenes) reads this one.
 */
@Mixin(ItemBlockRenderTypes.class)
public interface ItemBlockRenderTypesAccessor
{
	@Accessor("TYPE_BY_BLOCK")
	static Map<Block, RenderType> vineandbarrel$typeByBlock() {
		throw new AssertionError();
	}
}
