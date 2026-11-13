package jexxme.vineandbarrel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import jexxme.vineandbarrel.platform.Services;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared rules of the grape and hop plants (one rule per concept, pillar 0), for the bush on the ground and for the vines
 * that climb a support above it (our Trellis, or an FD rope):
 * <ul>
 *   <li>4 ages like the sweet berry bush; it grows with the sweet berry bush's pace and light rule.</li>
 *   <li>From age 2 on, right-click picks the fruit through a harvest loot table and drops the plant back to
 *       age 1, exactly like the sweet berry bush (C1): half-ripe (age 2) gives fewer than ripe (age 3).</li>
 *   <li>Bone meal adds one age; on a ripe plant with a support above it makes the plant climb (FD tomato rule). Because
 *       the plants are bonemealable, FD's rich soil boosts them automatically.</li>
 *   <li>Ripe with a support above: it climbs onto it, up to {@value VineClimbing#MAX_HEIGHT} blocks tall (FD tomato rule).</li>
 * </ul>
 */
public abstract class ClimbingCropBlock extends BushBlock implements BonemealableBlock
{
	public static final int MAX_AGE = 3;
	/** The first age that can be picked: half-ripe, like the sweet berry bush at age 2. */
	public static final int HALF_RIPE_AGE = 2;
	public static final int PICKED_AGE = 1;
	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

	private final Supplier<? extends Item> produce;
	private final ResourceLocation harvestLoot;

	protected ClimbingCropBlock(Properties properties, Supplier<? extends Item> produce, ResourceLocation harvestLoot) {
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

	public ResourceLocation harvestLoot() {
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
	public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
		return new ItemStack(produce());
	}

	@Override
	public boolean isRandomlyTicking(BlockState state) {
		return true; // a ripe plant keeps ticking so it can climb a support placed above it later
	}

	@Override
	public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
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

	/**
	 * 1.20.1 has one hook for a right-click, called for each hand in turn: first what the stack in that hand does to the
	 * plant ({@link #useItemOn}), then, for the main hand only and when the stack had no business here, picking
	 * ({@link #useWithoutItem}): the order and the hands of the newer versions' two hooks.
	 */
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		InteractionResult withItem = useItemOn(player.getItemInHand(hand), state, level, pos, player, hand, hitResult);
		if (withItem != null) {
			return withItem;
		}
		return hand == InteractionHand.MAIN_HAND ? useWithoutItem(state, level, pos, player, hitResult) : InteractionResult.PASS;
	}

	/**
	 * What the stack in the hand does to the plant.
	 *
	 * @return the result when the stack was used here; {@link InteractionResult#PASS} (or {@link InteractionResult#FAIL})
	 * to leave the click to the item itself without picking; {@code null} when the stack has no business here: the plant
	 * is then picked
	 */
	protected @Nullable InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		// Like the sweet berry bush: bone meal goes to BoneMealItem while it can still do something here
		if (stack.is(Items.BONE_MEAL) && isValidBonemealTarget(level, pos, state, level.isClientSide())) {
			return InteractionResult.PASS;
		}
		return null;
	}

	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (canPick(state)) {
			if (level instanceof ServerLevel serverLevel) {
				// on the side the player picked from, just outside the block: the middle of a fence, wall, bars or
				// trellis is inside its collision, which would shove the fruit out and fling it away
				Direction side = hitResult.getDirection().getAxis().isHorizontal() ? hitResult.getDirection()
						: Direction.getNearest((float) (player.getX() - (pos.getX() + 0.5)), 0.0F, (float) (player.getZ() - (pos.getZ() + 0.5)));
				pick(serverLevel, pos, state, player, stack -> Block.popResourceFromFace(serverLevel, pos, side, stack));
			}
			return InteractionResult.sidedSuccess(level.isClientSide());
		}
		return InteractionResult.PASS;
	}

	/**
	 * Picks the fruit of a half-ripe or ripe plant: rolls the harvest loot table (it reads the age: fewer when half-ripe,
	 * like vanilla's {@code harvest/sweet_berry_bush}), plays the berry-picking sound and drops the plant back to age 1.
	 */
	public void pick(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity picker, Consumer<ItemStack> drops) {
		// 1.20.1 has no block_interact tables: the harvest tables are of the "block" kind, rolled with an empty tool
		LootParams.Builder params = new LootParams.Builder(level)
				.withParameter(LootContextParams.BLOCK_STATE, state)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
				.withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
				.withOptionalParameter(LootContextParams.BLOCK_ENTITY, level.getBlockEntity(pos))
				.withOptionalParameter(LootContextParams.THIS_ENTITY, picker);
		level.getServer().getLootData().getLootTable(harvestLoot)
				.getRandomItems(params.create(LootContextParamSets.BLOCK)).forEach(drops);
		level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
		BlockState picked = state.setValue(AGE, PICKED_AGE);
		level.setBlock(pos, picked, Block.UPDATE_CLIENTS);
		level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(picker, picked));
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean isClient) {
		if (state.getValue(AGE) < MAX_AGE) {
			return true;
		}
		if (VineClimbing.canClimb(level, pos, this)) {
			return true;
		}
		// FD tomato rule: bone meal on a grown plant passes on to the vine above it
		BlockPos above = pos.above();
		BlockState aboveState = level.getBlockState(above);
		return isVine(aboveState) && ((ClimbingCropBlock) aboveState.getBlock()).isValidBonemealTarget(level, above, aboveState, isClient);
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
		int age = state.getValue(AGE);
		if (age < MAX_AGE) {
			level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
		} else if (VineClimbing.canClimb(level, pos, this)) {
			VineClimbing.climb(level, pos, this);
		} else {
			BlockPos above = pos.above();
			BlockState aboveState = level.getBlockState(above);
			if (isVine(aboveState) && aboveState.getBlock() instanceof ClimbingCropBlock climber
					&& climber.isValidBonemealTarget(level, above, aboveState, false)) {
				climber.performBonemeal(level, random, above, aboveState);
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
