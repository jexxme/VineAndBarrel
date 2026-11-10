package jexxme.vineandbarrel.gametest;

import com.google.common.base.Stopwatch;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestReporter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * With {@code -Dvineandbarrel.testReport=<file>}, the GameTest run also writes a JUnit-style report of every test
 * (vanilla's own format), on both loaders; the log keeps its usual lines. The parity checklist
 * (scripts/multiloader/parity.py) is built from these reports.
 *
 * <p>On this Minecraft a test has a plain name ({@code kettle_boils}), on 26.x an id ({@code vineandbarrel:kettle_boils}).
 * The report names our tests by that id, so the reports of every Minecraft version name a test the same; that is the
 * one thing vanilla's {@code JUnitLikeTestReporter} can't do, so the report is written here, in the same shape.
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
		JUnitReport junit;
		try {
			junit = new JUnitReport(file);
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

	/** The name a test has in the report: our tests under their 26.x id, another mod's tests as they are. */
	static String reportName(String testName) {
		return VBGameTests.isOurs(testName) ? jexxme.vineandbarrel.VineAndBarrel.MODID + ":" + testName : testName;
	}

	/** Vanilla's {@code JUnitLikeTestReporter}, element for element, but with {@link #reportName} as the test's name. */
	private static final class JUnitReport implements TestReporter
	{
		private final Document document;
		private final Element testSuite;
		private final Stopwatch stopwatch;
		private final File destination;

		JUnitReport(File destination) throws ParserConfigurationException {
			this.destination = destination;
			this.document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			this.testSuite = this.document.createElement("testsuite");
			Element root = this.document.createElement("testsuite");
			root.appendChild(this.testSuite);
			this.document.appendChild(root);
			this.testSuite.setAttribute("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
			this.stopwatch = Stopwatch.createStarted();
		}

		private Element createTestCase(GameTestInfo testInfo) {
			Element testCase = this.document.createElement("testcase");
			testCase.setAttribute("name", reportName(testInfo.getTestName()));
			testCase.setAttribute("classname", testInfo.getStructureName());
			testCase.setAttribute("time", String.valueOf((double) testInfo.getRunTime() / 1000.0));
			this.testSuite.appendChild(testCase);
			return testCase;
		}

		@Override
		public synchronized void onTestFailed(GameTestInfo testInfo) {
			Throwable error = testInfo.getError();
			Element result = this.document.createElement(testInfo.isRequired() ? "failure" : "skipped");
			result.setAttribute("message", "(" + testInfo.getStructureBlockPos().toShortString() + ") " + (error == null ? "" : error.getMessage()));
			createTestCase(testInfo).appendChild(result);
		}

		@Override
		public synchronized void onTestSuccess(GameTestInfo testInfo) {
			createTestCase(testInfo);
		}

		@Override
		public synchronized void finish() {
			this.stopwatch.stop();
			this.testSuite.setAttribute("time", String.valueOf((double) this.stopwatch.elapsed(TimeUnit.MILLISECONDS) / 1000.0));
			try {
				TransformerFactory.newInstance().newTransformer().transform(new DOMSource(this.document), new StreamResult(this.destination));
			} catch (TransformerException e) {
				throw new Error("Couldn't save test report", e);
			}
		}
	}
}
