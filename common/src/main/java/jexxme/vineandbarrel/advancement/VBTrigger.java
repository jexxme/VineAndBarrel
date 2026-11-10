package jexxme.vineandbarrel.advancement;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * A trigger with no conditions of its own (only the usual {@code player} predicate), like FD's cutting board trigger.
 * Each moment that has no vanilla trigger gets one: brewing ({@code vineandbarrel:brewed}), drinking together
 * ({@code vineandbarrel:cheers}) and sleeping Tipsy off ({@code vineandbarrel:slept_it_off}).
 */
public class VBTrigger extends SimpleCriterionTrigger<VBTrigger.TriggerInstance>
{
	private final ResourceLocation id;

	public VBTrigger(ResourceLocation id) {
		this.id = id;
	}

	@Override
	public ResourceLocation getId() {
		return id;
	}

	@Override
	protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
		return new TriggerInstance(id, player);
	}

	public void trigger(ServerPlayer player) {
		this.trigger(player, instance -> true);
	}

	public static TriggerInstance criterion(Supplier<VBTrigger> trigger) {
		return new TriggerInstance(trigger.get().getId(), ContextAwarePredicate.ANY);
	}

	public static class TriggerInstance extends AbstractCriterionTriggerInstance
	{
		public TriggerInstance(ResourceLocation id, ContextAwarePredicate player) {
			super(id, player);
		}
	}
}
