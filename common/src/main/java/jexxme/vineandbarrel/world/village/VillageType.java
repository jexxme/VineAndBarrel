package jexxme.vineandbarrel.world.village;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/** The five vanilla village types (DESIGN 8): their house pool, farm processor list and house chest loot. */
public enum VillageType
{
	PLAINS("plains", BuiltInLootTables.VILLAGE_PLAINS_HOUSE),
	DESERT("desert", BuiltInLootTables.VILLAGE_DESERT_HOUSE),
	SAVANNA("savanna", BuiltInLootTables.VILLAGE_SAVANNA_HOUSE),
	SNOWY("snowy", BuiltInLootTables.VILLAGE_SNOWY_HOUSE),
	TAIGA("taiga", BuiltInLootTables.VILLAGE_TAIGA_HOUSE);

	private final String name;
	private final ResourceLocation vanillaHouseLoot;

	VillageType(String name, ResourceLocation vanillaHouseLoot) {
		this.name = name;
		this.vanillaHouseLoot = vanillaHouseLoot;
	}

	public String id() {
		return name;
	}

	/** {@code minecraft:village/<type>/houses}, where every house and FD's compost pile live. */
	public ResourceLocation housesPool() {
		return new ResourceLocation("village/" + name + "/houses");
	}

	/** {@code minecraft:farm_<type>}, the processor list every farm of this village type is placed with. */
	public ResourceLocation farmProcessors() {
		return new ResourceLocation("farm_" + name);
	}

	/**
	 * {@code minecraft:zombie_<type>}, the processor list every piece of an abandoned (zombie) village of this type is placed
	 * with, its farms included: vanilla gives it the same farm rules as {@link #farmProcessors()}.
	 */
	public ResourceLocation zombieProcessors() {
		return new ResourceLocation("zombie_" + name);
	}

	/** Both lists a farm of this village type can be placed with: a living village's and an abandoned one's. */
	public java.util.List<ResourceLocation> farmProcessorLists() {
		return java.util.List.of(farmProcessors(), zombieProcessors());
	}

	/** The vanilla house chest of this village type. */
	public ResourceLocation vanillaHouseLoot() {
		return vanillaHouseLoot;
	}

	/** What we add to that chest: {@code vineandbarrel:chests/village/village_<type>_house}. */
	public ResourceLocation houseLoot() {
		return VineAndBarrel.id("chests/village/village_" + name + "_house");
	}

	/** The tavern's own chest: {@code vineandbarrel:chests/village/tavern_<type>}. */
	public ResourceLocation tavernLoot() {
		return VineAndBarrel.id("chests/village/tavern_" + name);
	}

	/** The chest hidden in the tavern's cellar (1.2): {@code vineandbarrel:chests/village/tavern_cellar_<type>}. */
	public ResourceLocation tavernCellarLoot() {
		return VineAndBarrel.id("chests/village/tavern_cellar_" + name);
	}

	/** The first tavern layout's template. */
	public ResourceLocation tavern() {
		return taverns().get(0);
	}

	/**
	 * The 0.9.0 tavern, {@code vineandbarrel:village/<type>/houses/<type>_tavern}: never chosen for a new village, but its
	 * template still ships, so a village laid out by 0.9.0 builds the rest of it in the chunks it hadn't generated yet, and
	 * it still counts as a tavern.
	 */
	public ResourceLocation legacyTavern() {
		return VineAndBarrel.id("village/" + name + "/houses/" + name + "_tavern");
	}

	/** Every tavern layout's template of this type (see {@link VillageStructures#TAVERNS}). */
	public java.util.List<ResourceLocation> taverns() {
		return VillageStructures.taverns(this).stream().map(VillageStructures.TavernLayout::id).toList();
	}
}
