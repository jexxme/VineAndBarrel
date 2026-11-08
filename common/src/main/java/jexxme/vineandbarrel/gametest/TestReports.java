package jexxme.vineandbarrel.gametest;

import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestReporter;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;

/**
 * With {@code -Dvineandbarrel.testReport=<file>}, the GameTest run also writes a JUnit-style report of every test
 * (vanilla's own format), on both loaders; the log keeps its usual lines. The parity checklist
 * (scripts/multiloader/parity.py) is built from these reports.
 */
public final class TestReports
{
	public static final String PROPERTY = "vineandbarrel.testReport";

	private TestReports() {
	}

	/** Early, before any test runs (it replaces vanilla's global reporter). */
	public static void enable() {
		String path = System.getProperty(PROPERTY);
		if (path == null || path.isEmpty()) {
			return;
		}
		File file = new File(path);
		File parent = file.getParentFile();
		if (parent != null) {
			parent.mkdirs();
		}
		JUnitLikeTestReporter junit;
		try {
			junit = new JUnitLikeTestReporter(file);
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException("Could not set up the test report " + file, e);
		}
		LogTestReporter log = new LogTestReporter();
		GlobalTestReporter.replaceWith(new TestReporter()
		{
			@Override
			public void onTestFailed(GameTestInfo testInfo) {
				log.onTestFailed(testInfo);
				junit.onTestFailed(testInfo);
			}

			@Override
			public void onTestSuccess(GameTestInfo testInfo) {
				log.onTestSuccess(testInfo);
				junit.onTestSuccess(testInfo);
			}

			@Override
			public void finish() {
				log.finish();
				junit.finish();
			}
		});
	}
}
