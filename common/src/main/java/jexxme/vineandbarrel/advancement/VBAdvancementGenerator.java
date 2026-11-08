package jexxme.vineandbarrel.advancement;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.tipsy.BlackoutEnding;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.criterion.ConsumeItemTrigger;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Our advancement tab (W2), built like FD's: a root that unlocks with any item, then a brewing branch and a tavern
 * branch. Icons reuse the item art. Vintner and Brewmaster ask for every wine and beer anyone can make, young and aged;
 * melon wine (FD only) is not required, so the tab can be finished without Farmer's Delight. Mead Hall and Cider House ask
 * for mead and hard cider, young and aged; hard cider is made from apple juice too, so both work without FD.
 */
public class VBAdvancementGenerator implements AdvancementSubProvider
{
	/** Every advancement of the tab, by name ({@code vineandbarrel:main/<name>}). */
	public static final List<String> ALL = List.of("root", "first_brew", "patience_pays", "vintner", "brewmaster", "mead_hall",
		"cider_house", "cellar_master", "on_tap", "cheers", "sleep_it_off", "where_am_i", "lost_weekend");

	public static Identifier id(String name) {
		return VineAndBarrel.id("main/" + name);
	}

	@Override
	public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> output) {
		HolderGetter<Item> items = registries.lookupOrThrow(Registries.ITEM);

		AdvancementHolder root = Advancement.Builder.advancement()
			.display(BarrelRegistry.AGING_BARREL_ITEM.get(), title("root"), description("root"),
				Identifier.withDefaultNamespace("block/spruce_planks"), AdvancementType.TASK, false, false, false)
			.addCriterion("any_item", InventoryChangeTrigger.TriggerInstance.hasItems(new ItemLike[]{}))
			.save(output, path("root"));

		// Brewing branch: the kettle, the barrel, then every drink
		AdvancementHolder firstBrew = child(root, KettleRegistry.kettleItem(), "first_brew", AdvancementType.TASK)
			.addCriterion("brewed", VBTrigger.criterion(VBAdvancements.BREWED))
			.save(output, path("first_brew"));

		AdvancementHolder patiencePays = child(firstBrew, DrinkItems.AGED_RED_WINE.get(), "patience_pays", AdvancementType.GOAL)
			.addCriterion("tapped_aged", TappedBarrelTrigger.criterion(Optional.of(true), Optional.empty(), Optional.of(true)))
			.save(output, path("patience_pays"));

		Advancement.Builder vintner = child(patiencePays, DrinkItems.AGED_SWEET_BERRY_WINE.get(), "vintner", AdvancementType.CHALLENGE);
		drank(vintner, items, DrinkItems.RED_WINE, DrinkItems.AGED_RED_WINE, DrinkItems.WHITE_WINE, DrinkItems.AGED_WHITE_WINE,
			DrinkItems.SWEET_BERRY_WINE, DrinkItems.AGED_SWEET_BERRY_WINE);
		vintner.rewards(AdvancementRewards.Builder.experience(100)).save(output, path("vintner"));

		Advancement.Builder brewmaster = child(patiencePays, DrinkItems.AGED_BARLEY_ALE.get(), "brewmaster", AdvancementType.CHALLENGE);
		drank(brewmaster, items, DrinkItems.BARLEY_ALE, DrinkItems.AGED_BARLEY_ALE, DrinkItems.WHEAT_BEER, DrinkItems.AGED_WHEAT_BEER);
		brewmaster.rewards(AdvancementRewards.Builder.experience(100)).save(output, path("brewmaster"));

		// Mead and our own cider, young and aged
		Advancement.Builder meadHall = child(patiencePays, DrinkItems.AGED_MEAD.get(), "mead_hall", AdvancementType.GOAL);
		drank(meadHall, items, DrinkItems.MEAD, DrinkItems.AGED_MEAD);
		meadHall.save(output, path("mead_hall"));

		Advancement.Builder ciderHouse = child(patiencePays, DrinkItems.AGED_HARD_CIDER.get(), "cider_house", AdvancementType.GOAL);
		drank(ciderHouse, items, DrinkItems.HARD_CIDER, DrinkItems.AGED_HARD_CIDER);
		ciderHouse.save(output, path("cider_house"));

		child(firstBrew, BarrelRegistry.AGING_BARREL_ITEM.get(), "cellar_master", AdvancementType.GOAL)
			.addCriterion("stacked_barrel", ItemUsedOnLocationTrigger.TriggerInstance.placedBlockWithProperties(
				BarrelRegistry.AGING_BARREL.get(), AgingBarrelBlock.STACKED, true))
			.save(output, path("cellar_master"));

		// Tavern branch: a village barrel, drinking together, the morning after
		AdvancementHolder onTap = child(root, DrinkItems.MUG.get(), "on_tap", AdvancementType.TASK)
			.addCriterion("tapped_village_barrel", TappedBarrelTrigger.criterion(Optional.empty(), Optional.of(true), Optional.empty()))
			.save(output, path("on_tap"));

		AdvancementHolder cheers = child(onTap, DrinkItems.WHEAT_BEER.get(), "cheers", AdvancementType.TASK)
			.addCriterion("cheers", VBTrigger.criterion(VBAdvancements.CHEERS))
			.save(output, path("cheers"));

		AdvancementHolder sleepItOff = child(cheers, Items.RED_BED, "sleep_it_off", AdvancementType.TASK)
			.addCriterion("slept_it_off", VBTrigger.criterion(VBAdvancements.SLEPT_IT_OFF))
			.save(output, path("sleep_it_off"));

		AdvancementHolder whereAmI = child(sleepItOff, Items.COMPASS, "where_am_i", AdvancementType.TASK)
			.addCriterion("blacked_out", VBTrigger.criterion(VBAdvancements.BLACKED_OUT))
			.save(output, path("where_am_i"));

		// Every way a blackout can end, once (waking up right where you fell doesn't count)
		Advancement.Builder lostWeekend = child(whereAmI, Items.CLOCK, "lost_weekend", AdvancementType.CHALLENGE);
		for (BlackoutEnding ending : BlackoutEnding.COLLECTABLE) {
			lostWeekend.addCriterion(ending.id(), BlackoutTrigger.criterion(Optional.of(ending)));
		}
		lostWeekend.rewards(AdvancementRewards.Builder.experience(100)).save(output, path("lost_weekend"));
	}

	@SafeVarargs
	private static void drank(Advancement.Builder builder, HolderGetter<Item> items, Supplier<? extends Item>... drinks) {
		for (Supplier<? extends Item> drink : drinks) {
			Item item = drink.get();
			builder.addCriterion(item.builtInRegistryHolder().key().identifier().getPath(), ConsumeItemTrigger.TriggerInstance.usedItem(items, item));
		}
	}

	/** Like FD: every one toasts, goals and challenges are also announced in chat. */
	private static Advancement.Builder child(AdvancementHolder parent, ItemLike icon, String name, AdvancementType frame) {
		return Advancement.Builder.advancement().parent(parent).display(icon.asItem(), title(name), description(name), null, frame, true,
			frame != AdvancementType.TASK, false);
	}

	private static String path(String name) {
		return id(name).toString();
	}

	private static Component title(String name) {
		return Component.translatable("advancements.vineandbarrel." + name + ".title");
	}

	private static Component description(String name) {
		return Component.translatable("advancements.vineandbarrel." + name + ".description");
	}
}
