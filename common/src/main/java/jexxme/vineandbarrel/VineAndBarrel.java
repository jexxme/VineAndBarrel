package jexxme.vineandbarrel;

import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBCreativeTabs;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The mod's common setup, the same on every loader. The loader's entry point ({@code VineAndBarrelNeoForge},
 * {@code VineAndBarrelFabric}) calls {@link #init()} first and then wires its events into the common classes.
 */
public final class VineAndBarrel
{
	public static final String MODID = "vineandbarrel";
	public static final Logger LOGGER = LogManager.getLogger();

	private VineAndBarrel() {
	}

	public static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath(MODID, name);
	}

	/** Once the loader has registered everything (NeoForge: common setup; Fabric: right after registration). */
	public static void setup() {
		jexxme.vineandbarrel.block.VBFlammability.register();
	}

	/** Hands every registry entry to the loader. */
	public static void init() {
		VBBlocks.register();
		VBItems.register();
		jexxme.vineandbarrel.registry.VBBlockEntities.register();
		VBCreativeTabs.register();
		jexxme.vineandbarrel.tipsy.TipsyRegistry.register();
		jexxme.vineandbarrel.kettle.KettleRegistry.register();
		jexxme.vineandbarrel.cauldron.VBCauldrons.register();
		jexxme.vineandbarrel.barrel.BarrelRegistry.register();
		jexxme.vineandbarrel.compat.farmersdelight.FDCompat.register();
		jexxme.vineandbarrel.compat.ModCompat.register();
		jexxme.vineandbarrel.world.village.VillageStructures.register();
		jexxme.vineandbarrel.world.village.TavernKeeper.register();
		jexxme.vineandbarrel.advancement.VBAdvancements.register();

		// GameTests only in the dev environment (or with -Dvineandbarrel.gametests=true), not in the released jar.
		// In the dev environment only, the wiki exporter (Gradle task runDocsExport) runs instead of the other tests; its
		// class isn't packed into the jar (see build-logic/vb-loader.gradle).
		boolean development = Services.PLATFORM.isDevelopment();
		if (development && System.getProperty(jexxme.vineandbarrel.gametest.VBDocsExport.PROPERTY) != null) {
			jexxme.vineandbarrel.gametest.VBDocsExport.register();
		} else if (development || Boolean.getBoolean("vineandbarrel.gametests")) {
			jexxme.vineandbarrel.gametest.TestReports.enable();
			jexxme.vineandbarrel.gametest.VBGameTests.register();
			jexxme.vineandbarrel.gametest.TipsyGameTests.register();
			jexxme.vineandbarrel.gametest.BlackoutGameTests.register();
			jexxme.vineandbarrel.gametest.KettleGameTests.register();
			jexxme.vineandbarrel.gametest.MultiKettleGameTests.register();
			jexxme.vineandbarrel.gametest.BarrelGameTests.register();
			jexxme.vineandbarrel.gametest.CauldronGameTests.register();
			jexxme.vineandbarrel.gametest.FdCompatGameTests.register();
			jexxme.vineandbarrel.gametest.WorldGameTests.register();
			jexxme.vineandbarrel.gametest.AdvancementGameTests.register();
			jexxme.vineandbarrel.gametest.ModCompatGameTests.register();
		}
	}
}
