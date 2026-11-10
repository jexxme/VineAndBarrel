package jexxme.vineandbarrel.fabric;

import jexxme.vineandbarrel.gametest.TestReports;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import net.minecraft.gametest.framework.GameTestRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Adds the common GameTests to vanilla's {@link GameTestRegistry} (Fabric's GameTest API only scans annotated classes
 * named as {@code fabric-gametest} entrypoints, and has no event like NeoForge's {@code RegisterGameTestsEvent}). The
 * test classes hand their registrations over while the mod is constructed; they run once everything is registered, at
 * the end of the mod's start, which is before Fabric's GameTest server ({@code -Dfabric-api.gametest}) or the
 * {@code /test} command reads the registry.
 */
public final class FabricGameTestRegistration
{
	/** The GameTest classes' registrations, until {@link #registerAll()} runs them. */
	private static final List<Consumer<GameTestRegistrar>> PENDING = new ArrayList<>();
	private static final GameTestRegistrar REGISTRAR = test -> GameTestRegistry.getAllTestFunctions().add(test);
	private static boolean registered;
	private static boolean any;

	private FabricGameTestRegistration() {
	}

	static synchronized void add(Consumer<GameTestRegistrar> tests) {
		any = true;
		if (registered) {
			tests.accept(REGISTRAR);
		} else {
			PENDING.add(tests);
		}
	}

	/** Once, at the end of the mod's start. */
	static synchronized void registerAll() {
		registered = true;
		PENDING.forEach(tests -> tests.accept(REGISTRAR));
		PENDING.clear();
	}

	/**
	 * As a server starts: Fabric's GameTest server replaces vanilla's global test reporter with its own just before it
	 * starts, after the mods were constructed, so our report ({@code -Dvineandbarrel.testReport}, {@link TestReports})
	 * is put back here. Without that property nothing changes and Fabric's own report is written.
	 */
	static synchronized void onServerStarting() {
		if (any && System.getProperty("fabric-api.gametest") != null) {
			TestReports.enable();
		}
	}
}
