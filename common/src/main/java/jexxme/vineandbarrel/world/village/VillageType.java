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

	/**
	 * {@code minecraft:zombie_<type>}, the processor list every piece of an abandoned (zombie) village of this type is placed
	 * with, its farms included: vanilla gives it the same farm rules as {@link #farmProcessors()}.
	 */
	public Identifier zombieProcessors() {
		return Identifier.withDefaultNamespace("zombie_" + name);
	}

	/** Both lists a farm of this village type can be placed with: a living village's and an abandoned one's. */
	public java.util.List<Identifier> farmProcessorLists() {
		return java.util.List.of(farmProcessors(), zombieProcessors());
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

	/** The chest hidden in the tavern's cellar (1.2): {@code vineandbarrel:chests/village/tavern_cellar_<type>}. */
	public ResourceKey<LootTable> tavernCellarLoot() {
		return ResourceKey.create(Registries.LOOT_TABLE, VineAndBarrel.id("chests/village/tavern_cellar_" + name));
	}

	/** The first tavern layout's template. */
	public Identifier tavern() {
		return taverns().getFirst();
	}

	/**
	 * The 0.9.0 tavern, {@code vineandbarrel:village/<type>/houses/<type>_tavern}: never chosen for a new village, but its
	 * template still ships, so a village laid out by 0.9.0 builds the rest of it in the chunks it hadn't generated yet, and
	 * it still counts as a tavern.
	 */
	public Identifier legacyTavern() {
		return VineAndBarrel.id("village/" + name + "/houses/" + name + "_tavern");
	}

	/** Every tavern layout's template of this type (see {@link VillageStructures#TAVERNS}). */
	public java.util.List<Identifier> taverns() {
		return VillageStructures.taverns(this).stream().map(VillageStructures.TavernLayout::id).toList();
	}
}
