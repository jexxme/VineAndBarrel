package jexxme.vineandbarrel.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Fox;

/** Hooks for mobs, called from each loader's entity events. */
public final class VBEntityEvents
{
	private VBEntityEvents() {
	}

	/** When an entity joins a level (server and client). */
	public static void onJoinLevel(Entity entity) {
		if (!entity.level().isClientSide() && entity instanceof Fox fox
				&& fox.goalSelector.getAvailableGoals().stream().noneMatch(wrapped -> wrapped.getGoal() instanceof FoxPickGrapesGoal)) {
			fox.goalSelector.addGoal(FoxPickGrapesGoal.PRIORITY, new FoxPickGrapesGoal(fox));
		}
	}
}
