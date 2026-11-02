package com.analysis.tool.execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the result-building logic that TestRunner applies after a
 * Maven run (H8): compilation-failure detection, console-summary fallback,
 * per-test precedence, timeout flagging and the timeout constructor contract.
 *
 * These call the package-private {@code parseResult} directly, so they are
 * fast and deterministic: no Maven build is started.
 */
public class TestRunnerParseResultTest {

    private final TestRunner runner = new TestRunner();

    @TempDir
    Path tempDir;

    @Test
    public void compilationFailureYieldsZeroTestsAndNoFailedTestRollUp() {
        String output = "[INFO] --- compiler:3.8.0:testCompile ---\n"
                + "[ERROR] COMPILATION ERROR :\n"
                + "[ERROR] /repo/src/test/java/com/sample/BrokenTest.java:[5,7] ';' expected\n"
                + "[INFO] BUILD FAILURE\n";

        TestRunResult result = runner.parseResult(tempDir.resolve("compile"), output, 1234L, false, false);

        assertTrue(result.isCompilationFailed(), "The compilation marker must set the flag");
        assertFalse(result.isBuildSucceeded(), "A compilation failure is never a successful build");
        assertEquals(0, result.getTotalTests(),
                "A compilation failure must not be reported as a collection of failed tests");
        assertEquals(0, result.getFailedOrErrors());
        assertFalse(result.hasPerTestResults());
    }

    @Test
    public void compilationFailureOverridesConsoleCounts() {
        String output = "Tests run: 3, Failures: 0, Errors: 0, Skipped: 0\n"
                + "[ERROR] COMPILATION ERROR :\n";

        TestRunResult result = runner.parseResult(tempDir.resolve("compile-console"), output, 10L, true, false);

        assertTrue(result.isCompilationFailed());
        assertFalse(result.isBuildSucceeded());
        assertEquals(0, result.getTotalTests());
    }

    @Test
    public void fallsBackToConsoleSummaryWhenNoReportsExist() {
        String output = "[INFO] Results:\n"
                + "[INFO] Tests run: 7, Failures: 1, Errors: 2, Skipped: 1\n"
                + "[INFO] BUILD FAILURE\n";

        TestRunResult result = runner.parseResult(tempDir.resolve("console"), output, 42L, false, false);

        assertFalse(result.hasPerTestResults(), "No XML means no per-test detail is claimed");
        assertEquals(7, result.getTotalTests());
        assertEquals(1, result.getFailed());
        assertEquals(2, result.getErrors());
        assertEquals(1, result.getSkipped());
        assertEquals(3, result.getPassed(), "Passed is the remainder: 7 - 1 - 2 - 1");
        assertEquals(42L, result.getExecutionTimeMillis());
    }

    @Test
    public void noReportsAndNoSummaryYieldsZeros() {
        TestRunResult result = runner.parseResult(tempDir.resolve("empty"), "[INFO] BUILD FAILURE\n", 5L, false, false);

        assertEquals(0, result.getTotalTests());
        assertEquals(0, result.getPassed());
        assertEquals(0, result.getFailedOrErrors());
        assertFalse(result.hasPerTestResults());
    }

    @Test
    public void perTestReportsTakePrecedenceOverConsoleSummary() throws IOException {
        Path reports = Files.createDirectories(tempDir.resolve("per-test").resolve("surefire-reports"));
        Files.writeString(reports.resolve("TEST-com.sample.CalculatorTest.xml"),
                "<?xml version=\"1.0\"?><testsuite>"
                        + "<testcase name=\"testAdd\" classname=\"com.sample.CalculatorTest\" time=\"0.0\"/>"
                        + "<testcase name=\"testSubtract\" classname=\"com.sample.CalculatorTest\" time=\"0.0\">"
                        + "<failure/></testcase>"
                        + "<testcase name=\"testDivide\" classname=\"com.sample.CalculatorTest\" time=\"0.0\">"
                        + "<error/></testcase>"
                        + "<testcase name=\"testSkipped\" classname=\"com.sample.CalculatorTest\" time=\"0.0\">"
                        + "<skipped/></testcase>"
                        + "</testsuite>",
                StandardCharsets.UTF_8);
        String misleadingConsole = "[INFO] Tests run: 999, Failures: 999, Errors: 999, Skipped: 999\n";

        TestRunResult result = runner.parseResult(reports, misleadingConsole, 10L, true, false);

        assertTrue(result.hasPerTestResults());
        assertEquals(4, result.getTotalTests(), "Counts come from the XML, not the console line");
        assertEquals(1, result.getPassed());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getErrors());
        assertEquals(1, result.getSkipped());
    }

    @Test
    public void timedOutRunIsFlaggedAndNeverSuccessful() {
        TestRunResult result = runner.parseResult(tempDir.resolve("timeout"), "", 5000L, false, true);

        assertTrue(result.isTimedOut(), "The timeout flag must survive into the result");
        assertFalse(result.isBuildSucceeded());
        assertEquals(0, result.getFailedOrErrors(), "A timeout is not a failed test");
    }

    @Test
    public void timeoutMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new TestRunner(0));
        assertThrows(IllegalArgumentException.class, () -> new TestRunner(-1));
        assertEquals(TestRunner.DEFAULT_TIMEOUT_MILLIS, new TestRunner().getTimeoutMillis());
        assertEquals(1234L, new TestRunner(1234L).getTimeoutMillis());
    }
}
