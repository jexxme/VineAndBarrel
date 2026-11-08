package jexxme.vineandbarrel.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jexxme.vineandbarrel.tipsy.BlackoutEnding;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

/**
 * {@code vineandbarrel:woke_up}: a player woke up from a blackout. Optional condition {@code ending}: which ending it was
 * (the {@link BlackoutEnding} id, e.g. {@code in_the_hay}). Lost Weekend has one criterion per ending.
 */
public class BlackoutTrigger extends SimpleCriterionTrigger<BlackoutTrigger.TriggerInstance>
{
	@Override
	public Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	public void trigger(ServerPlayer player, BlackoutEnding ending) {
		this.trigger(player, instance -> instance.ending().map(id -> id.equals(ending.id())).orElse(true));
	}

	public static void award(ServerPlayer player, BlackoutEnding ending) {
		VBAdvancements.WOKE_UP.get().trigger(player, ending);
	}

	public static Criterion<TriggerInstance> criterion(Optional<BlackoutEnding> ending) {
		return VBAdvancements.WOKE_UP.get().createCriterion(new TriggerInstance(Optional.empty(), ending.map(BlackoutEnding::id)));
	}

	public record TriggerInstance(Optional<Holder<LootItemCondition>> player, Optional<String> ending)
		implements SimpleCriterionTrigger.SimpleInstance
	{
		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
			Codec.STRING.optionalFieldOf("ending").forGetter(TriggerInstance::ending)
		).apply(inst, TriggerInstance::new));
	}
}
