package jexxme.vineandbarrel.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

/**
 * {@code vineandbarrel:tapped_barrel}: a player tapped a drink from an Aging Barrel. Optional conditions: {@code aged}
 * (the serving was the aged drink, or not), {@code in_village} (the barrel stands in a village, e.g. a tavern's) and
 * {@code home_brewed} (the batch was poured in during play, not a tavern's stock from world generation).
 */
public class TappedBarrelTrigger extends SimpleCriterionTrigger<TappedBarrelTrigger.TriggerInstance>
{
	@Override
	public Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	public void trigger(ServerPlayer player, BlockPos barrel, boolean aged, boolean homeBrewed) {
		// a tavern's cellar counts: it lies under the tavern piece's box
		boolean inVillage = jexxme.vineandbarrel.world.village.VillageStructures.villageAt(player.level(), barrel).isValid();
		trigger(player, aged, inVillage, homeBrewed);
	}

	public void trigger(ServerPlayer player, boolean aged, boolean inVillage, boolean homeBrewed) {
		this.trigger(player, instance -> instance.matches(aged, inVillage, homeBrewed));
	}

	public static Criterion<TriggerInstance> criterion(Optional<Boolean> aged, Optional<Boolean> inVillage, Optional<Boolean> homeBrewed) {
		return VBAdvancements.TAPPED_BARREL.get().createCriterion(new TriggerInstance(Optional.empty(), aged, inVillage, homeBrewed));
	}

	public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<Boolean> aged, Optional<Boolean> inVillage,
								  Optional<Boolean> homeBrewed)
		implements SimpleCriterionTrigger.SimpleInstance
	{
		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
			Codec.BOOL.optionalFieldOf("aged").forGetter(TriggerInstance::aged),
			Codec.BOOL.optionalFieldOf("in_village").forGetter(TriggerInstance::inVillage),
			Codec.BOOL.optionalFieldOf("home_brewed").forGetter(TriggerInstance::homeBrewed)
		).apply(inst, TriggerInstance::new));

		public boolean matches(boolean wasAged, boolean wasInVillage, boolean wasHomeBrewed) {
			return aged.map(a -> a == wasAged).orElse(true) && inVillage.map(v -> v == wasInVillage).orElse(true)
				&& homeBrewed.map(h -> h == wasHomeBrewed).orElse(true);
		}
	}
}
