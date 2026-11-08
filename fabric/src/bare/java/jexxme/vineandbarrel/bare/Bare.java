package jexxme.vineandbarrel.bare;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The cross-version smoke tests' other side, without Vine & Barrel: {@code -Dvineandbarrel.bare=server} is a Fabric server
 * with only Fabric API that a client with Vine & Barrel joins ({@code runSmokeForeign}); {@code -Dvineandbarrel.bare=refused}
 * is a client with neither Vine & Barrel nor Fabric API, so vanilla on the wire, that a server with Vine & Barrel must
 * refuse with a reason ({@code runSmokeRefusal}). Each writes {@code results.json} to its {@code smoke} folder (or
 * {@code vineandbarrel.smokeOut}). Plain mixins only: it runs where Fabric API is missing too.
 */
public final class Bare
{
	public static final Logger LOGGER = LogManager.getLogger("vineandbarrel_bare");
	public static final String MODE = System.getProperty("vineandbarrel.bare", "off");
	private static final Path OUT = Path.of(System.getProperty("vineandbarrel.smokeOut", "smoke")).toAbsolutePath();

	private Bare() {
	}

	public static void write(String name, String text) {
		try {
			Files.createDirectories(OUT);
			Files.writeString(OUT.resolve(name), text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not write {}", OUT.resolve(name), e);
		}
	}

	/** One check's result, as the other smoke tests write theirs. */
	public static void report(String check, String problem) {
		JsonObject result = new JsonObject();
		result.addProperty("check", check);
		result.addProperty("mode", MODE);
		result.addProperty("loader", "fabric");
		result.addProperty("ok", problem == null);
		if (problem != null) {
			result.addProperty("problem", problem);
			LOGGER.warn("Smoke check {} FAILED: {}", check, problem);
		} else {
			LOGGER.info("Smoke check {} passed", check);
		}
		JsonArray checks = new JsonArray();
		checks.add(result);
		JsonObject report = new JsonObject();
		report.addProperty("mode", MODE);
		report.addProperty("completed", true);
		report.add("checks", checks);
		write("results.json", new GsonBuilder().setPrettyPrinting().create().toJson(report));
	}
}
