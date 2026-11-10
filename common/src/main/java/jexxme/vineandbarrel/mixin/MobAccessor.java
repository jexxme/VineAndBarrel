package jexxme.vineandbarrel.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A mob's goals (protected in vanilla): the fox learns to pick grapes ({@code VBEntityEvents}). */
@Mixin(Mob.class)
public interface MobAccessor
{
	@Accessor("goalSelector")
	GoalSelector vineandbarrel$goalSelector();
}
