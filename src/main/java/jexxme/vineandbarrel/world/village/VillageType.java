package jexxme.vineandbarrel.world.village;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/** The five vanilla village types (DESIGN 8): their house pool, farm processor list and house chest loot. */
public enum VillageType
{
	PLAINS("plains", BuiltInLootTables.VILLAGE_PLAINS_HOUSE),
	DESERT("desert", BuiltInLootTables.VILLAGE_DESERT_HOUSE),
	SAVANNA("savanna", BuiltInLootTables.VILLAGE_SAVANNA_HOUSE),
	SNOWY("snowy", BuiltInLootTables.VILLAGE_SNOWY_HOUSE),
	TAIGA("taiga", BuiltInLootTables.VILLAGE_TAIGA_HOUSE);

	private final String name;
	private final ResourceKey<LootTable> vanillaHouseLoot;

	VillageType(String name, ResourceKey<LootTable> vanillaHouseLoot) {
		this.name = name;
		this.vanillaHouseLoot = vanillaHouseLoot;
	}

	public String id() {
		return name;
	}

	/** {@code minecraft:village/<type>/houses}, where every house and FD's compost pile live. */
	public Identifier housesPool() {
		return Identifier.withDefaultNamespace("village/" + name + "/houses");
	}

	/** {@code minecraft:farm_<type>}, the processor list every farm of this village type is placed with. */
	public Identifier farmProcessors() {
		return Identifier.withDefaultNamespace("farm_" + name);
	}

	/** The vanilla house chest of this village type. */
	public ResourceKey<LootTable> vanillaHouseLoot() {
		return vanillaHouseLoot;
	}

	/** What we add to that chest: {@code vineandbarrel:chests/village/village_<type>_house}. */
	public ResourceKey<LootTable> houseLoot() {
		return ResourceKey.create(Registries.LOOT_TABLE, VineAndBarrel.id("chests/village/village_" + name + "_house"));
	}

	/** The tavern's own chest: {@code vineandbarrel:chests/village/tavern_<type>}. */
	public ResourceKey<LootTable> tavernLoot() {
		return ResourceKey.create(Registries.LOOT_TABLE, VineAndBarrel.id("chests/village/tavern_" + name));
	}

	/** The tavern template: {@code vineandbarrel:village/<type>/houses/<type>_tavern}. */
	public Identifier tavern() {
		return VineAndBarrel.id("village/" + name + "/houses/" + name + "_tavern");
	}
}
