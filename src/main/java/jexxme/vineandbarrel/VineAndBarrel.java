package jexxme.vineandbarrel;

import jexxme.vineandbarrel.entity.VBEntityEvents;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.registry.VBCreativeTabs;
import jexxme.vineandbarrel.registry.VBItems;
import jexxme.vineandbarrel.world.AddFeaturesByFilterBiomeModifier;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(VineAndBarrel.MODID)
public class VineAndBarrel
{
	public static final String MODID = "vineandbarrel";
	public static final Logger LOGGER = LogManager.getLogger();

	public static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath(MODID, name);
	}

	public VineAndBarrel(IEventBus modEventBus, ModContainer modContainer) {
		VBBlocks.register(modEventBus);
		VBItems.register(modEventBus);
		VBCreativeTabs.register(modEventBus);
		AddFeaturesByFilterBiomeModifier.register(modEventBus);
		VBEntityEvents.register();
		jexxme.vineandbarrel.tipsy.TipsyRegistry.register(modEventBus);
		jexxme.vineandbarrel.tipsy.TipsyConfig.register(modContainer);
		jexxme.vineandbarrel.kettle.KettleRegistry.register(modEventBus);
		jexxme.vineandbarrel.barrel.BarrelRegistry.register(modEventBus);
		jexxme.vineandbarrel.compat.farmersdelight.FDCompat.register(modEventBus);
		jexxme.vineandbarrel.world.village.VillageStructures.register(modEventBus);
		jexxme.vineandbarrel.advancement.VBAdvancements.register(modEventBus);

		// GameTests only in the dev environment (or with -Dvineandbarrel.gametests=true), not in the released jar.
		// In the dev environment only, the wiki exporter (Gradle task runDocsExport) runs instead of the other tests; its
		// class isn't packed into the jar (see build.gradle).
		if (!FMLEnvironment.isProduction() && System.getProperty(jexxme.vineandbarrel.gametest.VBDocsExport.PROPERTY) != null) {
			jexxme.vineandbarrel.gametest.VBDocsExport.register(modEventBus);
		} else if (!FMLEnvironment.isProduction() || Boolean.getBoolean("vineandbarrel.gametests")) {
			jexxme.vineandbarrel.gametest.VBGameTests.register(modEventBus);
			jexxme.vineandbarrel.gametest.TipsyGameTests.register(modEventBus);
			jexxme.vineandbarrel.gametest.KettleGameTests.register(modEventBus);
			jexxme.vineandbarrel.gametest.BarrelGameTests.register(modEventBus);
			jexxme.vineandbarrel.gametest.FdCompatGameTests.register(modEventBus);
			jexxme.vineandbarrel.gametest.WorldGameTests.register(modEventBus);
			jexxme.vineandbarrel.gametest.AdvancementGameTests.register(modEventBus);
		}
	}
}
