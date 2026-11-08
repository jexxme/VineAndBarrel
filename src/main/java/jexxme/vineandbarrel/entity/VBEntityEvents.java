package jexxme.vineandbarrel.entity;

import net.minecraft.world.entity.animal.fox.Fox;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Game-bus hooks for mobs. */
public final class VBEntityEvents
{
	private VBEntityEvents() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(VBEntityEvents::onJoinLevel);
	}

	private static void onJoinLevel(EntityJoinLevelEvent event) {
		if (!event.getLevel().isClientSide() && event.getEntity() instanceof Fox fox
				&& fox.goalSelector.getAvailableGoals().stream().noneMatch(wrapped -> wrapped.getGoal() instanceof FoxPickGrapesGoal)) {
			fox.goalSelector.addGoal(FoxPickGrapesGoal.PRIORITY, new FoxPickGrapesGoal(fox));
		}
	}
}
