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
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class VBCreativeTabs
{
	public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VineAndBarrel.MODID);

	public static final Supplier<CreativeModeTab> VINE_AND_BARREL = TABS.register(VineAndBarrel.MODID, () -> Services.PLATFORM.creativeTabBuilder()
			.title(Component.translatable("itemGroup.vineandbarrel"))
			.icon(() -> new ItemStack(VBItems.RED_GRAPES.get()))
			.displayItems((parameters, output) -> tabOrder().forEach(output::accept))
			.build());

	private VBCreativeTabs() {
	}

	/**
	 * Everything in the tab, in FD's order: workstations, storage blocks, vessels, wild crops, crops and seeds (the Trellis
	 * next to the bushes that climb it), then what you make from them (bases, drinks, meals). Without Farmer's Delight,
	 * {@link #needsFarmersDelight()} is left out.
	 */
	public static List<Item> tabOrder() {
		List<Item> items = allInOrder();
		items.removeAll(hidden());
		return items;
	}

	/**
	 * Items only Farmer's Delight recipes make: the cooking pot meals, the Coq au Vin feast and its bowls, beer bread and
	 * melon wine. They exist anyway, so worlds stay stable.
	 */
	public static List<Item> needsFarmersDelight() {
		List<Item> items = new ArrayList<>(FDCompat.items());
		items.addAll(List.of(DrinkItems.MELON_WINE.get(), DrinkItems.AGED_MELON_WINE.get()));
		return items;
	}

	/** What the creative tab and JEI hide: {@link #needsFarmersDelight()} without Farmer's Delight, else nothing. */
	public static List<Item> hidden() {
		return FDCompat.isLoaded() ? List.of() : needsFarmersDelight();
	}

	private static List<Item> allInOrder() {
		List<Item> items = new ArrayList<>(KettleRegistry.allKettleItems());
		items.add(BarrelRegistry.AGING_BARREL_ITEM.get());
		items.addAll(List.of(VBItems.GRAPE_CRATE.get(), VBItems.WHITE_GRAPE_CRATE.get(), VBItems.HOPS_CRATE.get(), VBItems.BARLEY_BALE.get()));
		items.addAll(List.of(DrinkItems.MUG.get(), DrinkItems.WINE_GLASS.get()));
		items.addAll(List.of(VBItems.WILD_GRAPES.get(), VBItems.WILD_WHITE_GRAPES.get(), VBItems.WILD_HOPS.get(), VBItems.WILD_BARLEY.get()));
		items.addAll(List.of(VBItems.RED_GRAPES.get(), VBItems.WHITE_GRAPES.get(), VBItems.HOPS.get(), VBItems.TRELLIS.get(), VBItems.BARLEY.get(),
				VBItems.BARLEY_SEEDS.get()));
		items.addAll(BaseItems.all());
		items.addAll(DrinkItems.allDrinks());
		items.addAll(FDCompat.items());
		return items;
	}

	public static void register() {
		TABS.register();
	}
}
