package jexxme.vineandbarrel.advancement;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.drink.DrankTogether;
import jexxme.vineandbarrel.mixin.CriteriaTriggersAccessor;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The advancement triggers for our tab (W2). The advancements themselves are data ({@link VBAdvancementGenerator});
 * whatever vanilla can already see (placing a block, drinking an item) uses vanilla's triggers.
 * <p>
 * This Minecraft has no trigger registry: a trigger carries its own id and goes into vanilla's own map
 * ({@link #registerTriggers()}).
 */
public final class VBAdvancements
{
	private static final List<CriterionTrigger<?>> TRIGGERS = new ArrayList<>();

	/** Took a finished brew out of a Brew Kettle (from its output slot, or with a bottle or bucket in hand). */
	public static final Supplier<VBTrigger> BREWED = simple("brewed");
	/** Drank together with someone (D2, {@link DrankTogether}). */
	public static final Supplier<VBTrigger> CHEERS = simple("cheers");
	/** Slept through the night while Tipsy (T2). */
	public static final Supplier<VBTrigger> SLEPT_IT_OFF = simple("slept_it_off");
	/** Woke up from a blackout, whichever ending it was (Where Am I?). */
	public static final Supplier<VBTrigger> BLACKED_OUT = simple("blacked_out");
	/** Woke up from a blackout, with the ending (Lost Weekend, one criterion per ending). */
	public static final Supplier<BlackoutTrigger> WOKE_UP = add(new BlackoutTrigger());
	/** Tapped a drink from an Aging Barrel. */
	public static final Supplier<TappedBarrelTrigger> TAPPED_BARREL = add(new TappedBarrelTrigger());

	private VBAdvancements() {
	}

	private static Supplier<VBTrigger> simple(String name) {
		return add(new VBTrigger(VineAndBarrel.id(name)));
	}

	private static <T extends CriterionTrigger<?>> Supplier<T> add(T trigger) {
		TRIGGERS.add(trigger);
		return () -> trigger;
	}

	public static void register() {
		DrankTogether.addListener(VBAdvancements::onCheers);
	}

	/**
	 * Enters the triggers into vanilla's map ({@code CriteriaTriggers.register}, private on this Minecraft). Once, on the
	 * main thread, before any world loads its advancements (from {@code VineAndBarrel.setup()}): the map is a plain one.
	 */
	public static void registerTriggers() {
		TRIGGERS.forEach(CriteriaTriggersAccessor::vineandbarrel$register);
	}

	private static void onCheers(DrankTogether event) {
		for (Player player : event.players()) {
			award(CHEERS, player);
		}
	}

	/** Fires {@code trigger} for this player if it is a server player (hand use, menus); does nothing otherwise. */
	public static void award(Supplier<VBTrigger> trigger, Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			trigger.get().trigger(serverPlayer);
		}
	}
}
