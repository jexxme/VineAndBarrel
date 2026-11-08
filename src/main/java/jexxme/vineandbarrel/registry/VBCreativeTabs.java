package jexxme.vineandbarrel.registry;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class VBCreativeTabs
{
	public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VineAndBarrel.MODID);

	public static final Supplier<CreativeModeTab> VINE_AND_BARREL = TABS.register(VineAndBarrel.MODID, () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.vineandbarrel"))
			.icon(() -> new ItemStack(VBItems.RED_GRAPES.get()))
			.displayItems((parameters, output) -> tabOrder().forEach(output::accept))
			.build());

	private VBCreativeTabs() {
	}

	/**
	 * Everything in the tab, in FD's order: workstations, storage blocks, vessels, wild crops, crops and seeds, then what
	 * you make from them (bases, drinks, meals).
	 */
	public static List<Item> tabOrder() {
		List<Item> items = new ArrayList<>(KettleRegistry.allKettleItems());
		items.add(BarrelRegistry.AGING_BARREL_ITEM.get());
		items.addAll(List.of(VBItems.GRAPE_CRATE.get(), VBItems.HOPS_CRATE.get(), VBItems.BARLEY_BALE.get()));
		items.addAll(List.of(DrinkItems.MUG.get(), DrinkItems.WINE_GLASS.get()));
		items.addAll(List.of(VBItems.WILD_GRAPES.get(), VBItems.WILD_HOPS.get(), VBItems.WILD_BARLEY.get()));
		items.addAll(List.of(VBItems.RED_GRAPES.get(), VBItems.HOPS.get(), VBItems.BARLEY.get(), VBItems.BARLEY_SEEDS.get()));
		items.addAll(BaseItems.all());
		items.addAll(DrinkItems.allDrinks());
		items.addAll(FDCompat.meals());
		return items;
	}

	public static void register(IEventBus modEventBus) {
		TABS.register(modEventBus);
	}
}
