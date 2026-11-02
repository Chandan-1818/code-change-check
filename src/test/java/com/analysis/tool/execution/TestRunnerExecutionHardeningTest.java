package com.analysis.tool.execution;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.analysis.tool.testsupport.PinnedSampleCheckout;
import com.analysis.tool.testsupport.SamplePaths;

/**
 * Integration tests for H8 execution hardening. Each test genuinely launches
 * a nested Maven build in a disposable clone of the sample project, so they
 * are slower (several seconds each) than the rest of the suite; that is the
 * mechanism under test, not a defect.
 *
 * Covered here:
 * <ul>
 *   <li>GAP-15 timeout: a run exceeding its budget is terminated, flagged and
 *       logged instead of hanging.</li>
 *   <li>Compilation failure: a broken source is reported as a compilation
 *       failure with zero tests, never as a set of failed tests.</li>
 *   <li>GAP-16 per-test results: individual assertion failures and execution
 *       errors are distinguished from each other and from passing tests.</li>
 * </ul>
 */
public class TestRunnerExecutionHardeningTest {

    private static final String VERSION_5_COMMIT = "268a165";

    @TempDir
    Path tempDir;

    @Test
    public void timesOutAndTerminatesInsteadOfHanging() throws Exception {
        String repoPath = PinnedSampleCheckout.cloneAt(
                SamplePaths.SAMPLE_PROJECT_PATH, VERSION_5_COMMIT, tempDir);

        // A 1 ms budget cannot be met by a real Maven build; the run must be
        // aborted and reported as a timeout rather than blocking the caller.
        TestRunResult result = new TestRunner(1L).runFullSuite(repoPath);

        assertTrue(result.isTimedOut(), "The run must be flagged as timed out");
        assertFalse(result.isBuildSucceeded(), "A timed-out run is never successful");
        assertEquals(0, result.getFailedOrErrors(), "A timeout is not a failed test");

        Path log = Paths.get(repoPath).resolve("target").resolve("maven-execution.log");
        assertTrue(Files.exists(log), "The Maven log must be preserved for a timed-out run");
        assertTrue(Files.readString(log, StandardCharsets.UTF_8).contains("timed out"),
                "The preserved log must record that the run timed out");
    }

    @Test
    public void compilationFailureIsNotReportedAsFailedTests() throws Exception {
        String repoPath = PinnedSampleCheckout.cloneAt(
                SamplePaths.SAMPLE_PROJECT_PATH, VERSION_5_COMMIT, tempDir);
        Path broken = Paths.get(repoPath).resolve("src/test/java/com/sample/BrokenByH8Test.java");
        Files.writeString(broken, "package com.sample;\npublic class BrokenByH8Test { this is not java }\n",
                StandardCharsets.UTF_8);

        TestRunResult result = new TestRunner().runFullSuite(repoPath);

        assertTrue(result.isCompilationFailed(), "The broken source must be detected as a compilation failure");
        assertFalse(result.isBuildSucceeded());
        assertEquals(0, result.getTotalTests(),
                "A compilation failure must not be reported as a collection of failed tests");
        assertEquals(0, result.getFailedOrErrors());
    }

    @Test
    public void distinguishesAssertionFailuresFromExecutionErrors() throws Exception {
        String repoPath = PinnedSampleCheckout.cloneAt(
                SamplePaths.SAMPLE_PROJECT_PATH, VERSION_5_COMMIT, tempDir);
        Path injected = Paths.get(repoPath).resolve("src/test/java/com/sample/H8InjectedTest.java");
        Files.writeString(injected,
                "package com.sample;\n"
                        + "import static org.junit.jupiter.api.Assertions.assertEquals;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class H8InjectedTest {\n"
                        + "  @Test public void assertionFailure() { assertEquals(1, 2); }\n"
                        + "  @Test public void throwingError() { throw new IllegalStateException(\"boom\"); }\n"
                        + "  @Test public void passes() { assertEquals(2, 2); }\n"
                        + "}\n",
                StandardCharsets.UTF_8);

        TestRunResult result = new TestRunner().runFullSuite(repoPath);

        assertFalse(result.isCompilationFailed(), "The injected test compiles; only its assertions fail");
        assertFalse(result.isBuildSucceeded(), "Maven reports a non-zero exit code when tests fail");
        assertTrue(result.hasPerTestResults(), "Surefire XML must yield per-test results");

        assertEquals(TestResult.Status.FAILED, statusOf(result, "com.sample.H8InjectedTest", "assertionFailure"),
                "An assertion failure must be reported as FAILED");
        assertEquals(TestResult.Status.ERROR, statusOf(result, "com.sample.H8InjectedTest", "throwingError"),
                "An execution error must be reported as ERROR, distinct from FAILED");
        assertEquals(TestResult.Status.PASSED, statusOf(result, "com.sample.H8InjectedTest", "passes"));

        assertEquals(1, result.getFailed(), "Exactly one assertion failure");
        assertEquals(1, result.getErrors(), "Exactly one execution error");
        assertEquals(2, result.getFailedOrErrors(), "Failures and errors are counted separately");
    }

    private static TestResult.Status statusOf(TestRunResult result, String className, String methodName) {
        for (TestResult testResult : result.getTestResults()) {
            if (testResult.getClassName().equals(className)
                    && testResult.getMethodName().equals(methodName)) {
                return testResult.getStatus();
            }
        }
        throw new AssertionError("No per-test result for " + className + "#" + methodName);
    }
}
