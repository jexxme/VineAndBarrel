package jexxme.vineandbarrel.registry;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.block.BarleyBaleBlock;
import jexxme.vineandbarrel.block.BarleyBlock;
import jexxme.vineandbarrel.block.ClimbingBushBlock;
import jexxme.vineandbarrel.block.TrellisBlock;
import jexxme.vineandbarrel.block.VineOnRopeBlock;
import jexxme.vineandbarrel.block.VineOnTrellisBlock;
import jexxme.vineandbarrel.block.WildCropBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootTable;
import jexxme.vineandbarrel.platform.registry.DeferredBlock;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

public final class VBBlocks
{
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VineAndBarrel.MODID);

	/** Right-click harvest of a ripe plant, a block_interact table like vanilla's harvest/sweet_berry_bush. */
	public static final ResourceKey<LootTable> HARVEST_GRAPES = ResourceKey.create(Registries.LOOT_TABLE, VineAndBarrel.id("harvest/grapes"));
	public static final ResourceKey<LootTable> HARVEST_HOPS = ResourceKey.create(Registries.LOOT_TABLE, VineAndBarrel.id("harvest/hops"));
	public static final ResourceKey<LootTable> HARVEST_WHITE_GRAPES = ResourceKey.create(Registries.LOOT_TABLE, VineAndBarrel.id("harvest/white_grapes"));

	// ------------------------------------------------------------------ crops
	public static final DeferredBlock<ClimbingBushBlock> GRAPE_BUSH = BLOCKS.registerBlock("grape_bush",
			p -> new ClimbingBushBlock(p, VBBlocks.GRAPE_VINE_ON_ROPE, VBBlocks.GRAPE_VINE_ON_TRELLIS, VBItems.RED_GRAPES, HARVEST_GRAPES), VBBlocks::bush);
	public static final DeferredBlock<ClimbingBushBlock> HOP_BUSH = BLOCKS.registerBlock("hop_bush",
			p -> new ClimbingBushBlock(p, VBBlocks.HOP_VINE_ON_ROPE, VBBlocks.HOP_VINE_ON_TRELLIS, VBItems.HOPS, HARVEST_HOPS), VBBlocks::bush);
	public static final DeferredBlock<VineOnRopeBlock> GRAPE_VINE_ON_ROPE = BLOCKS.registerBlock("grape_vine_on_rope",
			p -> new VineOnRopeBlock(p, VBBlocks.GRAPE_BUSH, VBItems.RED_GRAPES, HARVEST_GRAPES), VBBlocks::vineOnRope);
	public static final DeferredBlock<VineOnRopeBlock> HOP_VINE_ON_ROPE = BLOCKS.registerBlock("hop_vine_on_rope",
			p -> new VineOnRopeBlock(p, VBBlocks.HOP_BUSH, VBItems.HOPS, HARVEST_HOPS), VBBlocks::vineOnRope);
	public static final DeferredBlock<ClimbingBushBlock> WHITE_GRAPE_BUSH = BLOCKS.registerBlock("white_grape_bush",
			p -> new ClimbingBushBlock(p, VBBlocks.WHITE_GRAPE_VINE_ON_ROPE, VBBlocks.WHITE_GRAPE_VINE_ON_TRELLIS, VBItems.WHITE_GRAPES, HARVEST_WHITE_GRAPES),
			VBBlocks::bush);
	public static final DeferredBlock<VineOnRopeBlock> WHITE_GRAPE_VINE_ON_ROPE = BLOCKS.registerBlock("white_grape_vine_on_rope",
			p -> new VineOnRopeBlock(p, VBBlocks.WHITE_GRAPE_BUSH, VBItems.WHITE_GRAPES, HARVEST_WHITE_GRAPES), VBBlocks::vineOnRope);

	// ------------------------------------------------------------------ the Trellis, and the vines grown onto it
	public static final DeferredBlock<TrellisBlock> TRELLIS = BLOCKS.registerBlock("trellis", TrellisBlock::new, VBBlocks::trellis);
	public static final DeferredBlock<VineOnTrellisBlock> GRAPE_VINE_ON_TRELLIS = BLOCKS.registerBlock("grape_vine_on_trellis",
			p -> new VineOnTrellisBlock(p, VBBlocks.GRAPE_BUSH, VBBlocks.TRELLIS, VBItems.RED_GRAPES, HARVEST_GRAPES), VBBlocks::vineOnTrellis);
	public static final DeferredBlock<VineOnTrellisBlock> WHITE_GRAPE_VINE_ON_TRELLIS = BLOCKS.registerBlock("white_grape_vine_on_trellis",
			p -> new VineOnTrellisBlock(p, VBBlocks.WHITE_GRAPE_BUSH, VBBlocks.TRELLIS, VBItems.WHITE_GRAPES, HARVEST_WHITE_GRAPES), VBBlocks::vineOnTrellis);
	public static final DeferredBlock<VineOnTrellisBlock> HOP_VINE_ON_TRELLIS = BLOCKS.registerBlock("hop_vine_on_trellis",
			p -> new VineOnTrellisBlock(p, VBBlocks.HOP_BUSH, VBBlocks.TRELLIS, VBItems.HOPS, HARVEST_HOPS), VBBlocks::vineOnTrellis);

	public static final DeferredBlock<BarleyBlock> BARLEY = BLOCKS.registerBlock("barley",
			BarleyBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT));

	// ------------------------------------------------------------------ wild crops (FD's WildCropBlock and its stew-effect style)
	public static final DeferredBlock<WildCropBlock> WILD_GRAPES = BLOCKS.registerBlock("wild_grapes",
			p -> new WildCropBlock(MobEffects.REGENERATION, 6, p), () -> BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS));
	public static final DeferredBlock<WildCropBlock> WILD_HOPS = BLOCKS.registerBlock("wild_hops",
			p -> new WildCropBlock(MobEffects.NAUSEA, 8, p), () -> BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS));
	public static final DeferredBlock<WildCropBlock> WILD_WHITE_GRAPES = BLOCKS.registerBlock("wild_white_grapes",
			p -> new WildCropBlock(MobEffects.NIGHT_VISION, 5, p), () -> BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS));
	public static final DeferredBlock<WildCropBlock> WILD_BARLEY = BLOCKS.registerBlock("wild_barley",
			p -> new WildCropBlock(MobEffects.SATURATION, 0.35F, p), () -> BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS));

	// ------------------------------------------------------------------ crop storage (FD crates, vanilla hay bale)
	public static final DeferredBlock<Block> GRAPE_CRATE = BLOCKS.registerBlock("grape_crate", Block::new, VBBlocks::crate);
	public static final DeferredBlock<Block> WHITE_GRAPE_CRATE = BLOCKS.registerBlock("white_grape_crate", Block::new, VBBlocks::crate);
	public static final DeferredBlock<Block> HOPS_CRATE = BLOCKS.registerBlock("hops_crate", Block::new, VBBlocks::crate);
	/** A hay bale in every way: axis placement, 80% less fall damage, hoe-mineable, flammable, feeds horses and llamas. */
	public static final DeferredBlock<BarleyBaleBlock> BARLEY_BALE = BLOCKS.registerBlock("barley_bale",
			BarleyBaleBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.HAY_BLOCK));

	private VBBlocks() {
	}

	/**
	 * The sweet berry bush's properties (no collision, berry-bush sounds), but pistons move it whole like its vines instead
	 * of popping it, so a trellis in it is never lost under a piston head (pushed off its soil it breaks and leaves the
	 * trellis).
	 */
	private static BlockBehaviour.Properties bush() {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.SWEET_BERRY_BUSH).pushReaction(PushReaction.NORMAL);
	}

	/**
	 * A bush on a rope: like FD's tomatoes on rope, pistons move it whole (rope included) instead of popping it, so the
	 * embedded rope is never lost under a piston head.
	 */
	private static BlockBehaviour.Properties vineOnRope() {
		return bush().pushReaction(PushReaction.NORMAL);
	}

	/**
	 * The Trellis: sticks like a ladder (strength, ladder sounds, burns with lava), a thin panel that light and water pass
	 * through. Not climbable: a vine is at most three blocks tall, all within reach from the ground.
	 */
	private static BlockBehaviour.Properties trellis() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(0.4F)
				.sound(SoundType.LADDER).noOcclusion().ignitedByLava();
	}

	/**
	 * A vine grown onto a trellis: a bush (berry-bush sounds, random ticks) with the trellis's panel inside, so it breaks
	 * like the trellis and you bump into the panel, not the leaves. Pistons move it whole, like a vine on a rope.
	 */
	private static BlockBehaviour.Properties vineOnTrellis() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.4F).randomTicks().sound(SoundType.SWEET_BERRY_BUSH)
				.noOcclusion().ignitedByLava().pushReaction(PushReaction.NORMAL);
	}

	/** FD's crate properties: oak planks, strength 2/3, wood sounds. */
	private static BlockBehaviour.Properties crate() {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(2.0F, 3.0F).sound(SoundType.WOOD);
	}

	public static void register() {
		BLOCKS.register();
	}
}
