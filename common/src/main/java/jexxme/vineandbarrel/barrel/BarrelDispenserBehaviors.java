package jexxme.vineandbarrel.barrel;

import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.tag.VBTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
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

	/** Once, after every mod has registered its items (Forge: common setup; Fabric: when the first server starts). */
	public static void register() {
		wrap(DrinkItems.MUG.get(), true);
		wrap(DrinkItems.WINE_GLASS.get(), true);
		List<Item> bases = new ArrayList<>(BaseItems.all());
		// FD's drinks that ferment here (§5.2), only when Farmer's Delight is installed
		for (String fd : List.of("apple_cider", "melon_juice")) {
			BuiltInRegistries.ITEM.getOptional(new ResourceLocation(VBTags.FARMERS_DELIGHT, fd)).ifPresent(bases::add);
		}
		bases.forEach(base -> wrap(base, false));
	}

	private static void wrap(Item item, boolean vessel) {
		// 1.20.1 keeps the dispenser's behaviours private: the loader reads what was registered before
		DispenseItemBehavior registered = jexxme.vineandbarrel.mixin.DispenserBlockAccessor.vineandbarrel$registry().get(item);
		DispenseItemBehavior previous = registered != null ? registered : new DefaultDispenseItemBehavior();
		DispenseItemBehavior atBarrel = new OptionalDispenseItemBehavior()
		{
			private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();

			@Override
			protected ItemStack execute(BlockSource source, ItemStack dispensed) {
				BlockPos target = source.getPos().relative(source.getBlockState().getValue(DispenserBlock.FACING));
				if (!(source.getLevel().getBlockEntity(target) instanceof AgingBarrelBlockEntity barrel)) {
					setSuccess(false);
					return dispensed;
				}
				// The barrel works on a copy; consumeWithRemainder takes the item and keeps the result in the dispenser
				ItemStack result = vessel ? barrel.tap(dispensed.copy(), null) : barrel.pour(dispensed.copy(), null);
				setSuccess(result != null && (!vessel || !result.isEmpty()));
				if (!isSuccess()) {
					return dispensed;
				}
				source.getLevel().gameEvent(null, vessel ? GameEvent.FLUID_PICKUP : GameEvent.FLUID_PLACE, target);
				return consumeWithRemainder(source, dispensed, result);
			}

			/**
			 * 1.21's consumeWithRemainder, which 1.20.1 does not have: one item is used up; the result takes its slot if
			 * that was the last one, else it goes into the dispenser (onto a stack of the same, or into a free slot), or
			 * is thrown out if the dispenser is full.
			 */
			private ItemStack consumeWithRemainder(BlockSource source, ItemStack stack, ItemStack remainder) {
				stack.shrink(1);
				if (stack.isEmpty()) {
					return remainder;
				}
				DispenserBlockEntity dispenser = source.getEntity();
				ItemStack left = remainder.copy();
				for (int slot = 0; slot < dispenser.getContainerSize() && !left.isEmpty(); slot++) {
					ItemStack there = dispenser.getItem(slot);
					if (there.isEmpty() || ItemStack.isSameItemSameTags(left, there)) {
						int room = Math.min(left.getCount(), Math.min(dispenser.getMaxStackSize(), left.getMaxStackSize()) - there.getCount());
						if (room > 0) {
							if (there.isEmpty()) {
								dispenser.setItem(slot, left.split(room));
							} else {
								left.shrink(room);
								there.grow(room);
								dispenser.setChanged();
							}
						}
					}
				}
				if (!left.isEmpty()) {
					defaultDispenseItemBehavior.dispense(source, left);
				}
				return stack;
			}
		};
		DispenserBlock.registerBehavior(item, (source, dispensed) -> {
			BlockPos target = source.getPos().relative(source.getBlockState().getValue(DispenserBlock.FACING));
			return source.getLevel().getBlockEntity(target) instanceof AgingBarrelBlockEntity
				? atBarrel.dispense(source, dispensed)
				: previous.dispense(source, dispensed);
		});
	}
}
