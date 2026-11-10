package jexxme.vineandbarrel.advancement;

import com.google.gson.JsonObject;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.tipsy.BlackoutEnding;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

import java.util.Optional;

/**
 * {@code vineandbarrel:woke_up}: a player woke up from a blackout. Optional condition {@code ending}: which ending it was
 * (the {@link BlackoutEnding} id, e.g. {@code in_the_hay}). Lost Weekend has one criterion per ending.
 */
public class BlackoutTrigger extends SimpleCriterionTrigger<BlackoutTrigger.TriggerInstance>
{
	public static final ResourceLocation ID = VineAndBarrel.id("woke_up");

	@Override
	public ResourceLocation getId() {
		return ID;
	}

	@Override
	protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
		return new TriggerInstance(player, json.has("ending") ? Optional.of(GsonHelper.getAsString(json, "ending")) : Optional.empty());
	}

	public void trigger(ServerPlayer player, BlackoutEnding ending) {
		this.trigger(player, instance -> instance.ending().map(id -> id.equals(ending.id())).orElse(true));
	}

	public static void award(ServerPlayer player, BlackoutEnding ending) {
		VBAdvancements.WOKE_UP.get().trigger(player, ending);
	}

	public static TriggerInstance criterion(Optional<BlackoutEnding> ending) {
		return new TriggerInstance(ContextAwarePredicate.ANY, ending.map(BlackoutEnding::id));
	}

	public static class TriggerInstance extends AbstractCriterionTriggerInstance
	{
		private final Optional<String> ending;

		public TriggerInstance(ContextAwarePredicate player, Optional<String> ending) {
			super(ID, player);
			this.ending = ending;
		}

		public Optional<String> ending() {
			return ending;
		}

		@Override
		public JsonObject serializeToJson(SerializationContext context) {
			JsonObject json = super.serializeToJson(context);
			ending.ifPresent(id -> json.addProperty("ending", id));
			return json;
		}
	}
}
