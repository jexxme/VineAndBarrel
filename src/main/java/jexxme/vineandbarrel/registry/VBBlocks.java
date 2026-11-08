package jexxme.vineandbarrel.registry;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.block.BarleyBaleBlock;
import jexxme.vineandbarrel.block.BarleyBlock;
import jexxme.vineandbarrel.block.ClimbingBushBlock;
import jexxme.vineandbarrel.block.VineOnRopeBlock;
import jexxme.vineandbarrel.block.WildCropBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VBBlocks
{
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VineAndBarrel.MODID);

	/** Right-click harvest of a ripe plant, a block_interact table like vanilla's harvest/sweet_berry_bush. */
	public static final ResourceKey<LootTable> HARVEST_GRAPES = ResourceKey.create(Registries.LOOT_TABLE, VineAndBarrel.id("harvest/grapes"));
	public static final ResourceKey<LootTable> HARVEST_HOPS = ResourceKey.create(Registries.LOOT_TABLE, VineAndBarrel.id("harvest/hops"));

	// ------------------------------------------------------------------ crops
	public static final DeferredBlock<ClimbingBushBlock> GRAPE_BUSH = BLOCKS.registerBlock("grape_bush",
			p -> new ClimbingBushBlock(p, VBBlocks.GRAPE_VINE_ON_ROPE, VBItems.RED_GRAPES, HARVEST_GRAPES), VBBlocks::bush);
	public static final DeferredBlock<ClimbingBushBlock> HOP_BUSH = BLOCKS.registerBlock("hop_bush",
			p -> new ClimbingBushBlock(p, VBBlocks.HOP_VINE_ON_ROPE, VBItems.HOPS, HARVEST_HOPS), VBBlocks::bush);
	public static final DeferredBlock<VineOnRopeBlock> GRAPE_VINE_ON_ROPE = BLOCKS.registerBlock("grape_vine_on_rope",
			p -> new VineOnRopeBlock(p, VBBlocks.GRAPE_BUSH, VBItems.RED_GRAPES, HARVEST_GRAPES), VBBlocks::vineOnRope);
	public static final DeferredBlock<VineOnRopeBlock> HOP_VINE_ON_ROPE = BLOCKS.registerBlock("hop_vine_on_rope",
			p -> new VineOnRopeBlock(p, VBBlocks.HOP_BUSH, VBItems.HOPS, HARVEST_HOPS), VBBlocks::vineOnRope);
	public static final DeferredBlock<BarleyBlock> BARLEY = BLOCKS.registerBlock("barley",
			BarleyBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT));

	// ------------------------------------------------------------------ wild crops (FD's WildCropBlock and its stew-effect style)
	public static final DeferredBlock<WildCropBlock> WILD_GRAPES = BLOCKS.registerBlock("wild_grapes",
			p -> new WildCropBlock(MobEffects.REGENERATION, 6, p), () -> BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS));
	public static final DeferredBlock<WildCropBlock> WILD_HOPS = BLOCKS.registerBlock("wild_hops",
			p -> new WildCropBlock(MobEffects.NAUSEA, 8, p), () -> BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS));
	public static final DeferredBlock<WildCropBlock> WILD_BARLEY = BLOCKS.registerBlock("wild_barley",
			p -> new WildCropBlock(MobEffects.SATURATION, 0.35F, p), () -> BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS));

	// ------------------------------------------------------------------ crop storage (FD crates, vanilla hay bale)
	public static final DeferredBlock<Block> GRAPE_CRATE = BLOCKS.registerBlock("grape_crate", Block::new, VBBlocks::crate);
	public static final DeferredBlock<Block> HOPS_CRATE = BLOCKS.registerBlock("hops_crate", Block::new, VBBlocks::crate);
	/** A hay bale in every way: axis placement, 80% less fall damage, hoe-mineable, flammable, feeds horses and llamas. */
	public static final DeferredBlock<BarleyBaleBlock> BARLEY_BALE = BLOCKS.registerBlock("barley_bale",
			BarleyBaleBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.HAY_BLOCK));

	private VBBlocks() {
	}

	/** The sweet berry bush's properties (no collision, berry-bush sounds, popped by pistons). */
	private static BlockBehaviour.Properties bush() {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.SWEET_BERRY_BUSH);
	}

	/**
	 * A bush on a rope: like FD's tomatoes on rope, pistons move it whole (rope included) instead of popping it, so the
	 * embedded rope is never lost under a piston head.
	 */
	private static BlockBehaviour.Properties vineOnRope() {
		return bush().pushReaction(PushReaction.NORMAL);
	}

	/** FD's crate properties: oak planks, strength 2/3, wood sounds. */
	private static BlockBehaviour.Properties crate() {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(2.0F, 3.0F).sound(SoundType.WOOD);
	}

	public static void register(IEventBus modEventBus) {
		BLOCKS.register(modEventBus);
	}
}
