package jexxme.vineandbarrel.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * A trigger with no conditions of its own (only the usual {@code player} predicate), like FD's cutting board trigger.
 * Each moment that has no vanilla trigger gets one: brewing ({@code vineandbarrel:brewed}), drinking together
 * ({@code vineandbarrel:cheers}) and sleeping Tipsy off ({@code vineandbarrel:slept_it_off}).
 */
public class VBTrigger extends SimpleCriterionTrigger<VBTrigger.TriggerInstance>
{
	@Override
	public Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	public void trigger(ServerPlayer player) {
		this.trigger(player, instance -> true);
	}

	public static Criterion<TriggerInstance> criterion(Supplier<VBTrigger> trigger) {
		return trigger.get().createCriterion(new TriggerInstance(Optional.empty()));
	}

	public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance
	{
		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player)
		).apply(inst, TriggerInstance::new));
	}
}
