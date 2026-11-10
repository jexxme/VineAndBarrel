package jexxme.vineandbarrel.entity;

import jexxme.vineandbarrel.mixin.MobAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.animal.Fox;

/** Hooks for mobs, called from each loader's entity events. */
public final class VBEntityEvents
{
	private VBEntityEvents() {
	}

	/** When an entity joins a level (server and client). */
	public static void onJoinLevel(Entity entity) {
		if (!entity.level().isClientSide() && entity instanceof Fox fox) {
			GoalSelector goals = ((MobAccessor) fox).vineandbarrel$goalSelector();
			if (goals.getAvailableGoals().stream().noneMatch(wrapped -> wrapped.getGoal() instanceof FoxPickGrapesGoal)) {
				goals.addGoal(FoxPickGrapesGoal.PRIORITY, new FoxPickGrapesGoal(fox));
			}
		}
	}
}
