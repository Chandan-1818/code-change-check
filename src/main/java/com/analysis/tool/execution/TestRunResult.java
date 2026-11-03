package com.analysis.tool.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of running a Maven test invocation (full suite or a filtered
 * selection).
 *
 * H8 extends the result beyond the console summary line:
 * <ul>
 *   <li>{@code failed} is now assertion failures only; execution errors are
 *       reported separately through {@code errors} (GAP-16).</li>
 *   <li>{@code timedOut} marks a run that was terminated by the execution
 *       timeout, which is distinct from an ordinary test failure (GAP-15).</li>
 *   <li>{@code compilationFailed} marks a build that failed to compile, so a
 *       compilation error is never presented as a collection of failed tests.</li>
 *   <li>{@code testResults} carries the individual outcomes parsed from the
 *       Surefire XML reports; it is empty when those reports are unavailable,
 *       in which case no per-test detail is claimed.</li>
 * </ul>
 *
 * executionTimeMillis is wall-clock time measured around the process, not
 * Maven's own internal timing.
 */
public class TestRunResult {

    private final int totalTests;
    private final int passed;
    private final int failed;
    private final int errors;
    private final int skipped;
    private final long executionTimeMillis;
    private final boolean buildSucceeded;
    private final boolean timedOut;
    private final boolean compilationFailed;
    private final List<TestResult> testResults;

    /** Backward-compatible constructor: no explicit errors, timeout or per-test detail. */
    public TestRunResult(int totalTests, int passed, int failed, int skipped,
                          long executionTimeMillis, boolean buildSucceeded) {
        this(totalTests, passed, failed, 0, skipped, executionTimeMillis, buildSucceeded,
                false, false, Collections.emptyList());
    }

    public TestRunResult(int totalTests, int passed, int failed, int errors, int skipped,
                          long executionTimeMillis, boolean buildSucceeded,
                          boolean timedOut, boolean compilationFailed, List<TestResult> testResults) {
        this.totalTests = totalTests;
        this.passed = passed;
        this.failed = failed;
        this.errors = errors;
        this.skipped = skipped;
        this.executionTimeMillis = executionTimeMillis;
        this.buildSucceeded = buildSucceeded;
        this.timedOut = timedOut;
        this.compilationFailed = compilationFailed;
        this.testResults = testResults == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(testResults));
    }

    public int getTotalTests() {
        return totalTests;
    }

    public int getPassed() {
        return passed;
    }

    /** Assertion failures (Surefire &lt;failure&gt;), not execution errors. */
    public int getFailed() {
        return failed;
    }

    /** Execution errors (Surefire &lt;error&gt;), distinct from assertion failures. */
    public int getErrors() {
        return errors;
    }

    /** Failures and errors combined, for callers that only care that something broke. */
    public int getFailedOrErrors() {
        return failed + errors;
    }

    public int getSkipped() {
        return skipped;
    }

    public long getExecutionTimeMillis() {
        return executionTimeMillis;
    }

    public boolean isBuildSucceeded() {
        return buildSucceeded;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public boolean isCompilationFailed() {
        return compilationFailed;
    }

    public List<TestResult> getTestResults() {
        return testResults;
    }

    public boolean hasPerTestResults() {
        return !testResults.isEmpty();
    }

    @Override
    public String toString() {
        return "TestRunResult{totalTests=" + totalTests
                + ", passed=" + passed
                + ", failed=" + failed
                + ", errors=" + errors
                + ", skipped=" + skipped
                + ", executionTimeMillis=" + executionTimeMillis
                + ", buildSucceeded=" + buildSucceeded
                + ", timedOut=" + timedOut
                + ", compilationFailed=" + compilationFailed
                + ", perTestResults=" + testResults.size() + "}";
    }
}
