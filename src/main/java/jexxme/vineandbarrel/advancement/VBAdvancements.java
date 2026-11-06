package jexxme.vineandbarrel.advancement;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.drink.CheersEvent;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * The advancement triggers for our tab (W2). The advancements themselves are data ({@link VBAdvancementGenerator});
 * whatever vanilla can already see (placing a block, drinking an item) uses vanilla's triggers.
 */
public final class VBAdvancements
{
	public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, VineAndBarrel.MODID);

	/** Took a finished brew out of a Brew Kettle (from its output slot, or with a bottle or bucket in hand). */
	public static final Supplier<VBTrigger> BREWED = TRIGGERS.register("brewed", VBTrigger::new);
	/** Drank together with someone (D2, {@link CheersEvent}). */
	public static final Supplier<VBTrigger> CHEERS = TRIGGERS.register("cheers", VBTrigger::new);
	/** Slept through the night while Tipsy (T2). */
	public static final Supplier<VBTrigger> SLEPT_IT_OFF = TRIGGERS.register("slept_it_off", VBTrigger::new);
	public static final Supplier<VBTrigger> BLACKED_OUT = TRIGGERS.register("blacked_out", VBTrigger::new);
	/** Tapped a drink from an Aging Barrel. */
	public static final Supplier<TappedBarrelTrigger> TAPPED_BARREL = TRIGGERS.register("tapped_barrel", TappedBarrelTrigger::new);

	private VBAdvancements() {
	}

	public static void register(IEventBus modEventBus) {
		TRIGGERS.register(modEventBus);
		NeoForge.EVENT_BUS.addListener(CheersEvent.class, VBAdvancements::onCheers);
	}

	private static void onCheers(CheersEvent event) {
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
