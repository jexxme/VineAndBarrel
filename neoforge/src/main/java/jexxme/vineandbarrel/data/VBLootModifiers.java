package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.world.village.VillageType;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.common.loot.AddTableLootModifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootTableIdCondition;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Adds our village house tables to the vanilla ones, like FD's {@code add_loot_village_*} modifiers (NeoForge global
 * loot modifiers; Fabric adds the same tables with {@code LootTableEvents.MODIFY}).
 */
public class VBLootModifiers extends GlobalLootModifierProvider
{
	public VBLootModifiers(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries, VineAndBarrel.MODID);
	}

	@Override
	protected void start() {
		for (VillageType type : VillageType.values()) {
			add("add_loot_village_" + type.id() + "_house", new AddTableLootModifier(
				new LootItemCondition[]{LootTableIdCondition.builder(type.vanillaHouseLoot().identifier()).build()},
				IGlobalLootModifier.DEFAULT_PRIORITY, type.houseLoot()));
		}
	}
}
