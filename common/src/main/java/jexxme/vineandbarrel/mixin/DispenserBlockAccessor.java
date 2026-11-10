package jexxme.vineandbarrel.mixin;

import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * What a dispenser does with each item (private in vanilla): the barrel's dispenser behaviours wrap the one an item
 * already has ({@code BarrelDispenserBehaviors}).
 */
@Mixin(DispenserBlock.class)
public interface DispenserBlockAccessor
{
	@Accessor("DISPENSER_REGISTRY")
	static Map<Item, DispenseItemBehavior> vineandbarrel$registry() {
		throw new AssertionError();
	}
}
