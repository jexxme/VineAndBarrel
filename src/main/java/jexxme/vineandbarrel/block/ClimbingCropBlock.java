package jexxme.vineandbarrel.block;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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
import net.neoforged.neoforge.common.CommonHooks;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Shared rules of the grape and hop plants (one rule per concept, pillar 0), for the bush on the ground and for the vine
 * that climbs an FD rope above it:
 * <ul>
 *   <li>4 ages like the sweet berry bush; it grows with the sweet berry bush's pace and light rule.</li>
 *   <li>Ripe (age 3): right-click picks the fruit through a {@code block_interact} loot table and drops back to age 1,
 *       exactly like the sweet berry bush (C1).</li>
 *   <li>Bone meal adds one age; on a ripe plant with a rope above it makes the plant climb (FD tomato rule). Because the
 *       plants are bonemealable, FD's rich soil boosts them automatically.</li>
 *   <li>Ripe with an FD rope above: it climbs onto the rope, up to 3 blocks tall (FD tomato rule).</li>
 * </ul>
 */
public abstract class ClimbingCropBlock extends VegetationBlock implements BonemealableBlock
{
	public static final int MAX_AGE = 3;
	public static final int PICKED_AGE = 1;
	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

	private final Supplier<? extends Block> bush;
	private final Supplier<? extends Block> vine;
	private final Supplier<? extends Item> produce;
	private final ResourceKey<LootTable> harvestLoot;

	protected ClimbingCropBlock(Properties properties, Supplier<? extends Block> bush, Supplier<? extends Block> vine,
								Supplier<? extends Item> produce, ResourceKey<LootTable> harvestLoot) {
		super(properties);
		this.bush = bush;
		this.vine = vine;
		this.produce = produce;
		this.harvestLoot = harvestLoot;
		registerDefaultState(stateDefinition.any().setValue(AGE, 0));
	}

	/** 26.2: every vegetation block names a codec. Ours are never built from data, so it only ever gives back this block. */
	@Override
	protected MapCodec<? extends ClimbingCropBlock> codec() {
		return MapCodec.unit(this);
	}

	public Block bush() {
		return bush.get();
	}

	public Block vine() {
		return vine.get();
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

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(produce());
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true; // a ripe plant keeps ticking so it can climb a rope placed above it later
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!level.isAreaLoaded(pos, 1) || level.getRawBrightness(pos.above(), 0) < 9) {
			return;
		}
		int age = state.getValue(AGE);
		boolean climb = age == MAX_AGE && RopeClimbing.canClimb(level, pos, bush(), vine());
		if ((age < MAX_AGE || climb) && CommonHooks.canCropGrow(level, pos, state, random.nextInt(5) == 0)) {
			if (climb) {
				RopeClimbing.climb(level, pos, vine());
			} else {
				BlockState grown = state.setValue(AGE, age + 1);
				level.setBlock(pos, grown, Block.UPDATE_CLIENTS);
				level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(grown));
			}
			CommonHooks.fireCropGrowPost(level, pos, state);
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		// Like the sweet berry bush: bone meal goes to BoneMealItem while it can still do something here
		if (stack.is(Items.BONE_MEAL) && isValidBonemealTarget(level, pos, state)) {
			return InteractionResult.PASS;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (state.getValue(AGE) == MAX_AGE) {
			if (level instanceof ServerLevel serverLevel) {
				pick(serverLevel, pos, state, player, stack -> Block.popResource(serverLevel, pos, stack));
			}
			return InteractionResult.SUCCESS;
		}
		return super.useWithoutItem(state, level, pos, player, hitResult);
	}

	/**
	 * Picks the ripe fruit: rolls the harvest loot table, plays the berry-picking sound and drops the plant back to
	 * age 1. Used by players (right-click) and by foxes.
	 */
	public void pick(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity picker, java.util.function.Consumer<ItemStack> drops) {
		Block.dropFromBlockInteractLootTable(level, harvestLoot, state, level.getBlockEntity(pos), null, picker,
				(serverLevel, stack) -> drops.accept(stack));
		level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
		BlockState picked = state.setValue(AGE, PICKED_AGE);
		level.setBlock(pos, picked, Block.UPDATE_CLIENTS);
		level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(picker, picked));
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
		if (state.getValue(AGE) < MAX_AGE) {
			return true;
		}
		if (RopeClimbing.canClimb(level, pos, bush(), vine())) {
			return true;
		}
		// FD tomato rule: bone meal on a grown plant passes on to the vine above it
		BlockPos above = pos.above();
		BlockState aboveState = level.getBlockState(above);
		return aboveState.is(vine()) && ((ClimbingCropBlock) aboveState.getBlock()).isValidBonemealTarget(level, above, aboveState);
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
		} else if (RopeClimbing.canClimb(level, pos, bush(), vine())) {
			RopeClimbing.climb(level, pos, vine());
		} else {
			BlockPos above = pos.above();
			BlockState aboveState = level.getBlockState(above);
			if (aboveState.is(vine()) && aboveState.getBlock() instanceof ClimbingCropBlock climber
					&& climber.isValidBonemealTarget(level, above, aboveState)) {
				climber.performBonemeal(level, random, above, aboveState);
			}
		}
	}

	/**
	 * C2: what one bee pass does to this crop, the same as vanilla's bee does to a sweet berry bush: one age up until
	 * ripe. A ripe bush is left alone (bees don't make it climb a rope; that stays with growth and bone meal).
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

	/** Burns like the sweet berry bush (vanilla {@code FireBlock.bootStrap}: 60 / 100), as FD's plants do. */
	@Override
	public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
		return 60;
	}

	@Override
	public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
		return 100;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}
}
