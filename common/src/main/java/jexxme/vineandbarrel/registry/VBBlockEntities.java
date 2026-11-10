package jexxme.vineandbarrel.registry;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.block.VineSupportBlockEntity;
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/** The crops' block entities: the block a vine on a fence, wall, chain or bars keeps inside. */
public final class VBBlockEntities
{
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VineAndBarrel.MODID);

	public static final Supplier<BlockEntityType<VineSupportBlockEntity>> VINE_SUPPORT = BLOCK_ENTITY_TYPES.register("vine_support",
			() -> Services.PLATFORM.blockEntityType(VineSupportBlockEntity::new,
					VBBlocks.GRAPE_VINE_ON_SUPPORT.get(), VBBlocks.WHITE_GRAPE_VINE_ON_SUPPORT.get(), VBBlocks.HOP_VINE_ON_SUPPORT.get()));

	private VBBlockEntities() {
	}

	public static void register() {
		BLOCK_ENTITY_TYPES.register();
	}
}
