package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.world.village.TavernKeeper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * A tavern keeper works at his kettle from beside the campfire it sits on. Vanilla's villager works at a job site whose
 * middle is within 1.73 blocks of his feet; a kettle on a campfire is a block higher than any job block, and from the
 * next block it is 1.8 to 2.2 away: he would never work, so never restock his trades. For him the reach is
 * {@link TavernKeeper#KETTLE_REACH}; every other villager works as before.
 */
@Mixin(WorkAtPoi.class)
public abstract class WorkAtPoiMixin
{
	@ModifyConstant(method = "checkExtraStartConditions", constant = @Constant(doubleValue = 1.73))
	private double vineandbarrel$keeperStarts(double reach, ServerLevel level, Villager body) {
		return TavernKeeper.isKeeper(body) ? TavernKeeper.KETTLE_REACH : reach;
	}

	@ModifyConstant(method = "canStillUse", constant = @Constant(doubleValue = 1.73))
	private double vineandbarrel$keeperWorks(double reach, ServerLevel level, Villager body, long timestamp) {
		return TavernKeeper.isKeeper(body) ? TavernKeeper.KETTLE_REACH : reach;
	}
}
