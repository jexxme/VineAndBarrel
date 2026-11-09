package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.world.village.VillageData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.VillagerTradeTags;
import net.minecraft.world.item.trading.VillagerTrade;

import java.util.concurrent.CompletableFuture;

/** Joins our trades to the vanilla trade pools (data-driven since 26.3), like FD's {@code VillagerTags}. */
public class VBTradeTags extends TagsProvider<VillagerTrade>
{
	public VBTradeTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
		super(output, Registries.VILLAGER_TRADE, lookupProvider, VineAndBarrel.MODID);
	}

	@Override
	protected void addTags(HolderLookup.Provider provider) {
		tag(VillagerTradeTags.FARMER_LEVEL_1).add(VillageData.FARMER_1_BARLEY_SEEDS).add(VillageData.FARMER_1_BARLEY_EMERALD);
		tag(VillagerTradeTags.FARMER_LEVEL_2).add(VillageData.FARMER_2_RED_GRAPES).add(VillageData.FARMER_2_HOPS).add(VillageData.FARMER_2_WHITE_GRAPES)
			.add(VillageData.FARMER_2_RED_GRAPES_EMERALD);
		tag(VillagerTradeTags.WANDERING_TRADER_COMMON).add(VillageData.WANDERING_TRADER_RED_GRAPES).add(VillageData.WANDERING_TRADER_HOPS).add(VillageData.WANDERING_TRADER_BARLEY_SEEDS)
			.add(VillageData.WANDERING_TRADER_WHITE_GRAPES);
	}
}
