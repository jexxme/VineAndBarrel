package jexxme.vineandbarrel.mixin;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's own way to add an advancement trigger (private on this Minecraft, which has no trigger registry). */
@Mixin(CriteriaTriggers.class)
public interface CriteriaTriggersAccessor
{
	@Invoker("register")
	static <T extends CriterionTrigger<?>> T vineandbarrel$register(T trigger) {
		throw new AssertionError();
	}
}
