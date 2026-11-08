package jexxme.vineandbarrel.entity;

import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.registry.VBBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import jexxme.vineandbarrel.platform.Services;

/**
 * C5, "foxes love grapes": vanilla's {@code Fox.FoxEatBerriesGoal} for grape bushes with fruit (half-ripe or ripe, as
 * vanilla's goal takes a sweet berry bush from age 2), red and white. Vanilla's goal hard-codes the sweet berry bush, so
 * foxes get this sibling goal at the same priority (added when they join the level), with the same speed, search range,
 * waiting time, yield and sound. Like in the fable, foxes only reach the grapes on the bush, never
 * the ones up on a rope or a trellis.
 */
public class FoxPickGrapesGoal extends MoveToBlockGoal
{
	/** Vanilla: {@code goalSelector.addGoal(10, new FoxEatBerriesGoal(1.2F, 12, 1))}. */
	public static final int PRIORITY = 10;
	private static final int WAIT_TICKS = 40;

	private final Fox fox;
	protected int ticksWaited;

	public FoxPickGrapesGoal(Fox fox) {
		super(fox, 1.2F, 12, 1);
		this.fox = fox;
	}

	@Override
	public double acceptedDistance() {
		return 2.0;
	}

	@Override
	public boolean shouldRecalculatePath() {
		return tryTicks % 100 == 0;
	}

	@Override
	protected boolean isValidTarget(LevelReader level, BlockPos pos) {
		return canPick(level, pos);
	}

	/** A half-ripe or ripe grape bush on the ground, red or white. */
	public static boolean canPick(LevelReader level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return (state.is(VBBlocks.GRAPE_BUSH.get()) || state.is(VBBlocks.WHITE_GRAPE_BUSH.get())) && ClimbingCropBlock.canPick(state);
	}

	@Override
	public void tick() {
		if (isReachedTarget()) {
			if (ticksWaited >= WAIT_TICKS) {
				onReachedTarget();
			} else {
				ticksWaited++;
			}
		} else if (fox.getRandom().nextFloat() < 0.05F) {
			fox.playSound(SoundEvents.FOX_SNIFF, 1.0F, 1.0F);
		}
		super.tick();
	}

	protected void onReachedTarget() {
		if (fox.level() instanceof ServerLevel level && Services.PLATFORM.canEntityGrief(level, fox)) {
			BlockState state = level.getBlockState(blockPos);
			if (isValidTarget(level, blockPos)) {
				pickGrapes(level, blockPos, state);
			}
		}
	}

	/** Vanilla's {@code pickSweetBerries}: 1-2 from a half-ripe plant, 2-3 from a ripe one; one goes into an empty mouth. */
	public void pickGrapes(ServerLevel level, BlockPos pos, BlockState state) {
		int count = 1 + level.getRandom().nextInt(2) + (ClimbingCropBlock.isRipe(state) ? 1 : 0);
		Item grapes = ((ClimbingCropBlock) state.getBlock()).produce();
		if (fox.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
			fox.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(grapes));
			count--;
		}
		if (count > 0) {
			Block.popResource(level, pos, new ItemStack(grapes, count));
		}
		fox.playSound(SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.0F, 1.0F);
		level.setBlock(pos, state.setValue(ClimbingCropBlock.AGE, ClimbingCropBlock.PICKED_AGE), Block.UPDATE_CLIENTS);
		level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(fox));
	}

	@Override
	public boolean canUse() {
		return !fox.isSleeping() && super.canUse();
	}

	@Override
	public void start() {
		ticksWaited = 0;
		fox.setSitting(false);
		super.start();
	}
}
