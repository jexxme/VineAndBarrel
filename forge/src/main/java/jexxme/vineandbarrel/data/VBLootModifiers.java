package jexxme.vineandbarrel.data;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.forge.AddTableLootModifier;
import jexxme.vineandbarrel.world.village.VillageType;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.minecraftforge.common.loot.LootTableIdCondition;

/**
 * Adds our village house tables to the vanilla ones, like FD's {@code add_loot_village_*} modifiers (Forge global
 * loot modifiers of our own type {@code vineandbarrel:add_table}, which Forge 47 has none of; Fabric adds the same
 * tables with {@code LootTableEvents.MODIFY}). Writes the list to {@code data/forge/loot_modifiers} too.
 */
public class VBLootModifiers extends GlobalLootModifierProvider
{
	public VBLootModifiers(PackOutput output) {
		super(output, VineAndBarrel.MODID);
	}

	@Override
	protected void start() {
		for (VillageType type : VillageType.values()) {
			add("add_loot_village_" + type.id() + "_house", new AddTableLootModifier(
				new LootItemCondition[]{LootTableIdCondition.builder(type.vanillaHouseLoot()).build()},
				type.houseLoot()));
		}
	}
}
