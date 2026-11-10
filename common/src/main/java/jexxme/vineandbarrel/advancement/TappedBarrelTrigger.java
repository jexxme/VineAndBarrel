package jexxme.vineandbarrel.advancement;

import com.google.gson.JsonObject;
import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

import java.util.Optional;

/**
 * {@code vineandbarrel:tapped_barrel}: a player tapped a drink from an Aging Barrel. Optional conditions: {@code aged}
 * (the serving was the aged drink, or not), {@code in_village} (the barrel stands in a village, e.g. a tavern's) and
 * {@code home_brewed} (the batch was poured in during play, not a tavern's stock from world generation).
 */
public class TappedBarrelTrigger extends SimpleCriterionTrigger<TappedBarrelTrigger.TriggerInstance>
{
	public static final ResourceLocation ID = VineAndBarrel.id("tapped_barrel");

	@Override
	public ResourceLocation getId() {
		return ID;
	}

	@Override
	protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
		return new TriggerInstance(player, optionalBoolean(json, "aged"), optionalBoolean(json, "in_village"), optionalBoolean(json, "home_brewed"));
	}

	private static Optional<Boolean> optionalBoolean(JsonObject json, String key) {
		return json.has(key) ? Optional.of(GsonHelper.getAsBoolean(json, key)) : Optional.empty();
	}

	public void trigger(ServerPlayer player, BlockPos barrel, boolean aged, boolean homeBrewed) {
		// a tavern's cellar counts: it lies under the tavern piece's box
		boolean inVillage = jexxme.vineandbarrel.world.village.VillageStructures.villageAt(player.serverLevel(), barrel).isValid();
		trigger(player, aged, inVillage, homeBrewed);
	}

	public void trigger(ServerPlayer player, boolean aged, boolean inVillage, boolean homeBrewed) {
		this.trigger(player, instance -> instance.matches(aged, inVillage, homeBrewed));
	}

	public static TriggerInstance criterion(Optional<Boolean> aged, Optional<Boolean> inVillage, Optional<Boolean> homeBrewed) {
		return new TriggerInstance(ContextAwarePredicate.ANY, aged, inVillage, homeBrewed);
	}

	public static class TriggerInstance extends AbstractCriterionTriggerInstance
	{
		private final Optional<Boolean> aged;
		private final Optional<Boolean> inVillage;
		private final Optional<Boolean> homeBrewed;

		public TriggerInstance(ContextAwarePredicate player, Optional<Boolean> aged, Optional<Boolean> inVillage, Optional<Boolean> homeBrewed) {
			super(ID, player);
			this.aged = aged;
			this.inVillage = inVillage;
			this.homeBrewed = homeBrewed;
		}

		@Override
		public JsonObject serializeToJson(SerializationContext context) {
			JsonObject json = super.serializeToJson(context);
			aged.ifPresent(value -> json.addProperty("aged", value));
			inVillage.ifPresent(value -> json.addProperty("in_village", value));
			homeBrewed.ifPresent(value -> json.addProperty("home_brewed", value));
			return json;
		}

		public boolean matches(boolean wasAged, boolean wasInVillage, boolean wasHomeBrewed) {
			return aged.map(a -> a == wasAged).orElse(true) && inVillage.map(v -> v == wasInVillage).orElse(true)
				&& homeBrewed.map(h -> h == wasHomeBrewed).orElse(true);
		}
	}
}
