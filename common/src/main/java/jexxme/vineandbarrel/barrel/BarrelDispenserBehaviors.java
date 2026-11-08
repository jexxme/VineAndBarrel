package jexxme.vineandbarrel.barrel;

import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.tag.VBTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * B13: a dispenser facing an Aging Barrel taps a serving with an empty mug or wine glass, and pours in a bottle or bucket
 * of base (like dispensers filling bottles and buckets). Facing anything else, these items do what they did before.
 * A refused item stays in the dispenser and the dispenser clicks, like vanilla's failed dispenses.
 */
public final class BarrelDispenserBehaviors
{
	private BarrelDispenserBehaviors() {
	}

	/** Once, after every mod has registered its items (NeoForge: common setup; Fabric: when the first server starts). */
	public static void register() {
		wrap(DrinkItems.MUG.get(), true);
		wrap(DrinkItems.WINE_GLASS.get(), true);
		List<Item> bases = new ArrayList<>(BaseItems.all());
		// FD's drinks that ferment here (§5.2), only when Farmer's Delight is installed
		for (String fd : List.of("apple_cider", "melon_juice")) {
			BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(VBTags.FARMERS_DELIGHT, fd)).ifPresent(bases::add);
		}
		bases.forEach(base -> wrap(base, false));
	}

	private static void wrap(Item item, boolean vessel) {
		DispenseItemBehavior previous = DispenserBlock.DISPENSER_REGISTRY.getOrDefault(item, new DefaultDispenseItemBehavior());
		DispenseItemBehavior atBarrel = new OptionalDispenseItemBehavior()
		{
			@Override
			protected ItemStack execute(BlockSource source, ItemStack dispensed) {
				BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
				if (!(source.level().getBlockEntity(target) instanceof AgingBarrelBlockEntity barrel)) {
					setSuccess(false);
					return dispensed;
				}
				// The barrel works on a copy; consumeWithRemainder takes the item and keeps the result in the dispenser
				ItemStack result = vessel ? barrel.tap(dispensed.copy(), null) : barrel.pour(dispensed.copy(), null);
				setSuccess(result != null && (!vessel || !result.isEmpty()));
				if (!isSuccess()) {
					return dispensed;
				}
				source.level().gameEvent(null, vessel ? GameEvent.FLUID_PICKUP : GameEvent.FLUID_PLACE, target);
				return consumeWithRemainder(source, dispensed, result);
			}
		};
		DispenserBlock.registerBehavior(item, (source, dispensed) -> {
			BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
			return source.level().getBlockEntity(target) instanceof AgingBarrelBlockEntity
				? atBarrel.dispense(source, dispensed)
				: previous.dispense(source, dispensed);
		});
	}
}
