package jexxme.vineandbarrel.smoke;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The dedicated-server side of the smoke test ({@code -Dvineandbarrel.smoke=server}, Gradle: {@code runSmokeServer}):
 * says when it is ready, makes the joining smoke client an operator (it drives {@code /vbsmoke}), and stops once that
 * client has left again, writing its own {@code smoke/results.json} ({@code smoke-fd} in the FD run). Does nothing in any other run.
 */
final class SmokeServer
{
	private static final Logger LOGGER = LogManager.getLogger("vineandbarrel_smoke");
	/** Both server runs share one folder (and one eula.txt), so each writes its results to its own subfolder. */
	private static final Path OUT = FabricLoader.getInstance().getGameDir().resolve(System.getProperty("vineandbarrel.smokeOut", "smoke"));
	/** runSmokeRefusal: only refused clients come, and the server stops when told to (a {@code stop} file). */
	private static final boolean REFUSAL = Boolean.getBoolean("vineandbarrel.smokeExpectRefusal");
	private static boolean joined;
	private static boolean finished;
	/** How long the stop has waited for the world's generation to end (see {@link SmokeQuiet}). */
	private static int quietTicks;

	private SmokeServer() {
	}

	static void register() {
		if (!"server".equals(System.getProperty("vineandbarrel.smoke"))) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			// The same world settings the LAN host uses
			for (String command : new String[] { "gamerule doDaylightCycle false", "gamerule doMobSpawning false" }) {
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
			}
			write("server-ready", "ready");
			LOGGER.info("Smoke server ready");
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			joined = true;
			server.getPlayerList().op(handler.player.getGameProfile());
			LOGGER.info("Smoke client {} joined and is an operator", handler.player.getName().getString());
		});
		// Checked after the tick, not in the disconnect event: that can fire while the player is still on the list
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (finished) {
				return;
			}
			// The run is over, but the stop waits for the world's generation to end: see SmokeQuiet
			boolean over = REFUSAL ? Files.isRegularFile(OUT.resolve("stop")) : joined && server.getPlayerList().getPlayerCount() == 0;
			if (over && SmokeQuiet.generating(server) && ++quietTicks <= SmokeQuiet.LIMIT) {
				return;
			}
			if (REFUSAL) {
				// runSmokeRefusal: a client without Vine & Barrel must never get in; the Gradle task says when it gave up
				if (Files.isRegularFile(OUT.resolve("stop"))) {
					finished = true;
					finish(server, "refuses_a_client_without_vb", joined ? "a client without Vine & Barrel joined" : null);
				}
			} else if (joined && server.getPlayerList().getPlayerCount() == 0) {
				finished = true;
				finish(server, "a_client_joins_and_leaves_the_dedicated_server", null);
			}
		});
	}

	private static void finish(MinecraftServer server, String name, String problem) {
		JsonObject check = new JsonObject();
		check.addProperty("check", name);
		check.addProperty("mode", "server");
		check.addProperty("loader", "fabric");
		check.addProperty("farmersdelight", FabricLoader.getInstance().isModLoaded("farmersdelight"));
		check.addProperty("ok", problem == null);
		if (problem != null) {
			check.addProperty("problem", problem);
			LOGGER.warn("Smoke check {} FAILED: {}", name, problem);
		}
		JsonArray checks = new JsonArray();
		checks.add(check);
		JsonObject report = new JsonObject();
		report.addProperty("mode", "server");
		report.addProperty("completed", true);
		report.add("checks", checks);
		write("results.json", new GsonBuilder().setPrettyPrinting().create().toJson(report));
		LOGGER.info("Smoke test done; stopping the server");
		server.halt(false);
	}

	private static void write(String name, String text) {
		try {
			Files.createDirectories(OUT);
			Files.writeString(OUT.resolve(name), text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not write {}", OUT.resolve(name), e);
		}
	}
}
