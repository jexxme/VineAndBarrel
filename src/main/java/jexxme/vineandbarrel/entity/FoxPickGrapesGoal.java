package jexxme.vineandbarrel.entity;

import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.event.EventHooks;

/**
 * C5, "foxes love grapes": vanilla's {@code Fox.FoxEatBerriesGoal} for ripe grape bushes. Vanilla's goal hard-codes the
 * sweet berry bush, so foxes get this sibling goal at the same priority (added when they join the level), with the same
 * speed, search range, waiting time, yield and sound. Like in the fable, foxes only reach the grapes on the bush, never
 * the ones up on a rope.
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

	/** A ripe grape bush on the ground. */
	public static boolean canPick(LevelReader level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(VBBlocks.GRAPE_BUSH.get()) && ClimbingCropBlock.isRipe(state);
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
		if (fox.level() instanceof ServerLevel level && EventHooks.canEntityGrief(level, fox)) {
			BlockState state = level.getBlockState(blockPos);
			if (isValidTarget(level, blockPos)) {
				pickGrapes(level, blockPos, state);
			}
		}
	}

	/** Vanilla's {@code pickSweetBerries}: 2-3 from a ripe plant, one goes into the fox's mouth if it is empty. */
	public void pickGrapes(ServerLevel level, BlockPos pos, BlockState state) {
		int count = 1 + level.getRandom().nextInt(2) + 1;
		if (fox.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
			fox.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(VBItems.RED_GRAPES.get()));
			count--;
		}
		if (count > 0) {
			Block.popResource(level, pos, new ItemStack(VBItems.RED_GRAPES.get(), count));
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
