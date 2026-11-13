package jexxme.vineandbarrel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import jexxme.vineandbarrel.platform.Services;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared rules of the grape and hop plants (one rule per concept, pillar 0), for the bush on the ground and for the vines
 * that climb a support above it (our Trellis, or an FD rope):
 * <ul>
 *   <li>4 ages like the sweet berry bush; it grows with the sweet berry bush's pace and light rule.</li>
 *   <li>From age 2 on, right-click picks the fruit through a {@code block_interact} loot table and drops the plant back to
 *       age 1, exactly like the sweet berry bush (C1): half-ripe (age 2) gives fewer than ripe (age 3).</li>
 *   <li>Bone meal adds one age; on a ripe plant with a support above it makes the plant climb (FD tomato rule). Because
 *       the plants are bonemealable, FD's rich soil boosts them automatically.</li>
 *   <li>Ripe with a support above: it climbs onto it, up to {@value VineClimbing#MAX_HEIGHT} blocks tall (FD tomato rule).</li>
 * </ul>
 */
public abstract class ClimbingCropBlock extends VegetationBlock implements BonemealableBlock
{
	public static final int MAX_AGE = 3;
	/** The first age that can be picked: half-ripe, like the sweet berry bush at age 2. */
	public static final int HALF_RIPE_AGE = 2;
	public static final int PICKED_AGE = 1;
	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

	private final Supplier<? extends Item> produce;
	private final ResourceKey<LootTable> harvestLoot;

	protected ClimbingCropBlock(Properties properties, Supplier<? extends Item> produce, ResourceKey<LootTable> harvestLoot) {
		super(properties);
		this.produce = produce;
		this.harvestLoot = harvestLoot;
		registerDefaultState(stateDefinition.any().setValue(AGE, 0));
	}

	/** The bush this plant grows from (the bush itself for a bush). */
	public abstract ClimbingBushBlock bushBlock();

	/** This plant's vine on an FD rope. */
	public Block ropeVine() {
		return bushBlock().ropeVineBlock();
	}

	/** This plant's vine on a Trellis. */
	public Block trellisVine() {
		return bushBlock().trellisVineBlock();
	}

	/** This plant's vine on a fence, wall, upright chain or bars. */
	public Block supportVine() {
		return bushBlock().supportVineBlock();
	}

	/** True for any vine of this plant (on a rope, a trellis or another support). */
	public boolean isVine(BlockState state) {
		return state.is(ropeVine()) || state.is(trellisVine()) || state.is(supportVine());
	}

	/** True for the bush or either vine of this plant: what a vine can grow on. */
	public boolean isPlant(BlockState state) {
		return state.is(bushBlock()) || isVine(state);
	}

	/**
	 * The vine that grows onto {@code support}: a vine on a rope for FD's ropes, a vine on a trellis (facing the same way)
	 * for our Trellis, a vine on a support for a fence, wall, upright chain or bars (which then keeps that block, see
	 * {@link VineOnSupportBlock#place}), nothing for anything else, or for a support under water (a vine doesn't grow there,
	 * like any crop).
	 */
	public @Nullable BlockState vineOn(BlockState support) {
		if (!support.getFluidState().isEmpty()) {
			return null;
		}
		if (VineClimbing.isRope(support)) {
			return ropeVine().defaultBlockState();
		}
		if (support.getBlock() instanceof TrellisBlock) {
			return trellisVine().defaultBlockState().setValue(TrellisBlock.AXIS, support.getValue(TrellisBlock.AXIS));
		}
		if (VineSupports.isMakeshift(support)) {
			return supportVine().defaultBlockState().setValue(VineOnSupportBlock.LOOK, VineSupports.lookOf(support));
		}
		return null;
	}

	public Item produce() {
		return produce.get();
	}

	public ResourceKey<LootTable> harvestLoot() {
		return harvestLoot;
	}

	public static boolean isRipe(BlockState state) {
		return state.getBlock() instanceof ClimbingCropBlock && state.getValue(AGE) == MAX_AGE;
	}

	/** Half-ripe or ripe: there is fruit to pick (the sweet berry bush's {@code age > 1}). */
	public static boolean canPick(BlockState state) {
		return state.getBlock() instanceof ClimbingCropBlock && state.getValue(AGE) >= HALF_RIPE_AGE;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(produce());
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true; // a ripe plant keeps ticking so it can climb a support placed above it later
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!level.hasChunksAt(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)) || level.getRawBrightness(pos.above(), 0) < 9) {
			return;
		}
		int age = state.getValue(AGE);
		boolean climb = age == MAX_AGE && VineClimbing.canClimb(level, pos, this);
		if ((age < MAX_AGE || climb) && Services.PLATFORM.canCropGrow(level, pos, state, random.nextInt(5) == 0)) {
			if (climb) {
				VineClimbing.climb(level, pos, this);
			} else {
				BlockState grown = state.setValue(AGE, age + 1);
				level.setBlock(pos, grown, Block.UPDATE_CLIENTS);
				level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(grown));
			}
			Services.PLATFORM.cropGrown(level, pos, state);
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		// Like the sweet berry bush: bone meal goes to BoneMealItem while it can still do something here
		if (stack.is(Items.BONE_MEAL) && isValidBonemealTarget(level, pos, state, BonemealSource.INTERACTION)) {
			return InteractionResult.PASS;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (canPick(state)) {
			if (level instanceof ServerLevel serverLevel) {
				// on the side the player picked from, just outside the block: the middle of a fence, wall, bars or
				// trellis is inside its collision, which would shove the fruit out and fling it away
				Direction side = hitResult.getDirection().getAxis().isHorizontal() ? hitResult.getDirection()
						: Direction.getApproximateNearest(player.getX() - (pos.getX() + 0.5), 0.0, player.getZ() - (pos.getZ() + 0.5));
				pick(serverLevel, pos, state, player, stack -> Block.popResourceFromFace(serverLevel, pos, side, stack));
			}
			return InteractionResult.SUCCESS;
		}
		return super.useWithoutItem(state, level, pos, player, hitResult);
	}

	/**
	 * Picks the fruit of a half-ripe or ripe plant: rolls the harvest loot table (it reads the age: fewer when half-ripe,
	 * like vanilla's {@code harvest/sweet_berry_bush}), plays the berry-picking sound and drops the plant back to age 1.
	 */
	public void pick(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity picker, Consumer<ItemStack> drops) {
		Block.dropFromBlockInteractLootTable(level, harvestLoot, pos, state, level.getBlockEntity(pos), null, picker,
				(serverLevel, stack) -> drops.accept(stack));
		level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
		BlockState picked = state.setValue(AGE, PICKED_AGE);
		level.setBlock(pos, picked, Block.UPDATE_CLIENTS);
		level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(picker, picked));
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		if (state.getValue(AGE) < MAX_AGE) {
			return true;
		}
		if (VineClimbing.canClimb(level, pos, this)) {
			return true;
		}
		// FD tomato rule: bone meal on a grown plant passes on to the vine above it
		BlockPos above = pos.above();
		BlockState aboveState = level.getBlockState(above);
		return isVine(aboveState) && ((ClimbingCropBlock) aboveState.getBlock()).isValidBonemealTarget(level, above, aboveState, source);
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		int age = state.getValue(AGE);
		if (age < MAX_AGE) {
			level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
		} else if (VineClimbing.canClimb(level, pos, this)) {
			VineClimbing.climb(level, pos, this);
		} else {
			BlockPos above = pos.above();
			BlockState aboveState = level.getBlockState(above);
			if (isVine(aboveState) && aboveState.getBlock() instanceof ClimbingCropBlock climber
					&& climber.isValidBonemealTarget(level, above, aboveState, source)) {
				climber.performBonemeal(level, random, above, aboveState, source);
			}
		}
	}

	/**
	 * C2: what one bee pass does to this crop, the same as vanilla's bee does to a sweet berry bush: one age up until
	 * ripe. A ripe bush is left alone (bees don't make it climb a support; that stays with growth and bone meal).
	 *
	 * @return the grown state, or {@code null} when there is nothing to grow
	 */
	public static @Nullable BlockState beeGrowState(BlockState state) {
		if (!(state.getBlock() instanceof ClimbingCropBlock)) {
			return null;
		}
		int age = state.getValue(AGE);
		return age < MAX_AGE ? state.setValue(AGE, age + 1) : null;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}
}
