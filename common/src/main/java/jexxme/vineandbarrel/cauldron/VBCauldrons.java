package jexxme.vineandbarrel.cauldron;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.platform.registry.DeferredBlock;
import jexxme.vineandbarrel.platform.registry.DeferredItem;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;
import java.util.Map;

/**
 * Our bases in a cauldron, by water's rules (vanilla's {@code CauldronInteraction}):
 * <ul>
 * <li>A bucket of a base fills any cauldron, whatever was in it, exactly as vanilla's buckets pour over water, lava and
 * powder snow (and theirs pour over ours). Only a cauldron of the same base takes nothing, so topping one up never
 * throws servings away: bottles top it up.</li>
 * <li>A bottle goes into an empty cauldron or one of the same base that isn't full, one level each.</li>
 * <li>An empty bottle takes a level out; an empty bucket takes a full cauldron.</li>
 * </ul>
 * One hidden block per base, like vanilla's water, lava and powder snow cauldrons: no items, the vanilla cauldron is the
 * one you craft and place. Four levels, a bucket's four servings (see {@link BaseCauldronBlock}).
 */
public final class VBCauldrons
{
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VineAndBarrel.MODID);

	public static final DeferredBlock<BaseCauldronBlock> GRAPE_JUICE = cauldron("grape_juice", BaseItems.GRAPE_JUICE, BaseItems.GRAPE_JUICE_BUCKET);
	public static final DeferredBlock<BaseCauldronBlock> WHITE_GRAPE_JUICE = cauldron("white_grape_juice", BaseItems.WHITE_GRAPE_JUICE,
		BaseItems.WHITE_GRAPE_JUICE_BUCKET);
	public static final DeferredBlock<BaseCauldronBlock> SWEET_BERRY_JUICE = cauldron("sweet_berry_juice", BaseItems.SWEET_BERRY_JUICE,
		BaseItems.SWEET_BERRY_JUICE_BUCKET);
	public static final DeferredBlock<BaseCauldronBlock> APPLE_JUICE = cauldron("apple_juice", BaseItems.APPLE_JUICE, BaseItems.APPLE_JUICE_BUCKET);
	public static final DeferredBlock<BaseCauldronBlock> BARLEY_WORT = cauldron("barley_wort", BaseItems.BARLEY_WORT_BOTTLE, BaseItems.BARLEY_WORT_BUCKET);
	public static final DeferredBlock<BaseCauldronBlock> WHEAT_WORT = cauldron("wheat_wort", BaseItems.WHEAT_WORT_BOTTLE, BaseItems.WHEAT_WORT_BUCKET);
	public static final DeferredBlock<BaseCauldronBlock> HONEY_MUST = cauldron("honey_must", BaseItems.HONEY_MUST_BOTTLE, BaseItems.HONEY_MUST_BUCKET);

	private VBCauldrons() {
	}

	private static DeferredBlock<BaseCauldronBlock> cauldron(String base, DeferredItem<Item> bottle, DeferredItem<Item> bucket) {
		// Every property of the water cauldron (vanilla's ofLegacyCopy of the cauldron); the drop is the cauldron (loot table)
		String name = base + "_cauldron";
		return BLOCKS.registerBlock(name, p -> new BaseCauldronBlock(p, VineAndBarrel.MODID + ":" + name, bottle, bucket),
			() -> BlockBehaviour.Properties.ofLegacyCopy(Blocks.CAULDRON));
	}

	/** Every base's cauldron, in the bases' order (juices, then worts and must). */
	public static List<BaseCauldronBlock> all() {
		return List.of(GRAPE_JUICE.get(), WHITE_GRAPE_JUICE.get(), SWEET_BERRY_JUICE.get(), APPLE_JUICE.get(), BARLEY_WORT.get(), WHEAT_WORT.get(),
			HONEY_MUST.get());
	}

	/** Every state of every base's cauldron: a leatherworker's job site, like every vanilla cauldron's. */
	public static java.util.Set<BlockState> states() {
		java.util.Set<BlockState> states = new java.util.HashSet<>();
		all().forEach(cauldron -> states.addAll(cauldron.getStateDefinition().getPossibleStates()));
		return states;
	}

	/** The liquid colour of a cauldron's base, the same as in the kettle. */
	public static int color(BaseCauldronBlock cauldron) {
		return BaseItems.liquidColor(new ItemStack(cauldron.bottle()));
	}

	public static void register() {
		BLOCKS.register();
	}

	/**
	 * Adds every interaction, once, after registration (NeoForge: at common setup; Fabric: right after our registration),
	 * on both sides, so the client predicts the click as it does for water.
	 */
	public static void addInteractions() {
		List<Map<Item, CauldronInteraction>> vanilla = List.of(CauldronInteraction.EMPTY.map(), CauldronInteraction.WATER.map(), CauldronInteraction.LAVA.map(),
			CauldronInteraction.POWDER_SNOW.map());
		List<BaseCauldronBlock> ours = all();
		for (BaseCauldronBlock target : ours) {
			CauldronInteraction pourBucket = (state, level, pos, player, hand, held) -> state.is(target)
				? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
				: CauldronInteraction.emptyBucket(level, pos, player, hand, held, target.defaultBlockState().setValue(BaseCauldronBlock.LEVEL,
					BaseCauldronBlock.MAX_LEVEL), SoundEvents.BUCKET_EMPTY);
			// Its bucket pours over anything, like vanilla's buckets
			vanilla.forEach(interactions -> interactions.put(target.bucket(), pourBucket));
			ours.forEach(other -> other.interactions().map().put(target.bucket(), pourBucket));
			// Its bottles go into an empty cauldron or top it up
			CauldronInteraction pourBottle = (state, level, pos, player, hand, held) -> pourBottle(target, state, level, pos, player, hand, held);
			CauldronInteraction.EMPTY.map().put(target.bottle(), pourBottle);
			target.interactions().map().put(target.bottle(), pourBottle);
			// Taken out with an empty bottle (a level) or an empty bucket (only when full)
			target.interactions().map().put(Items.GLASS_BOTTLE, VBCauldrons::takeBottle);
			target.interactions().map().put(Items.BUCKET, (state, level, pos, player, hand, held) -> CauldronInteraction.fillBucket(state, level, pos,
				player, hand, held, new ItemStack(target.bucket()), target::isFull, SoundEvents.BUCKET_FILL));
			// Vanilla's buckets pour over ours, as over each other
			CauldronInteraction.addDefaultInteractions(target.interactions().map());
		}
	}

	private static ItemInteractionResult pourBottle(BaseCauldronBlock target, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, ItemStack held) {
		int next;
		if (state.is(Blocks.CAULDRON)) {
			next = BaseCauldronBlock.MIN_LEVEL;
		} else if (state.is(target) && !target.isFull(state)) {
			next = state.getValue(BaseCauldronBlock.LEVEL) + 1;
		} else {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!level.isClientSide()) {
			Item used = held.getItem();
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
			player.awardStat(Stats.USE_CAULDRON);
			player.awardStat(Stats.ITEM_USED.get(used));
			level.setBlockAndUpdate(pos, target.defaultBlockState().setValue(BaseCauldronBlock.LEVEL, next));
			level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide());
	}

	private static ItemInteractionResult takeBottle(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack held) {
		if (!(state.getBlock() instanceof BaseCauldronBlock cauldron)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!level.isClientSide()) {
			Item used = held.getItem();
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(cauldron.bottle())));
			player.awardStat(Stats.USE_CAULDRON);
			player.awardStat(Stats.ITEM_USED.get(used));
			BaseCauldronBlock.lowerFillLevel(state, level, pos);
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide());
	}
}
