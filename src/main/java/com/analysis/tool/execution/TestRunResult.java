package com.analysis.tool.execution;

/**
 * Result of running a Maven test invocation (full suite or a filtered
 * selection), parsed from Surefire's console summary line
 * ("Tests run: X, Failures: Y, Errors: Z, Skipped: W").
 *
 * executionTimeMillis is wall-clock time measured around the process,
 * not Maven's own internal timing.
 */
public class TestRunResult {

    private final int totalTests;
    private final int passed;
    private final int failed;
    private final int skipped;
    private final long executionTimeMillis;
    private final boolean buildSucceeded;

    public TestRunResult(int totalTests, int passed, int failed, int skipped,
                          long executionTimeMillis, boolean buildSucceeded) {
        this.totalTests = totalTests;
        this.passed = passed;
        this.failed = failed;
        this.skipped = skipped;
        this.executionTimeMillis = executionTimeMillis;
        this.buildSucceeded = buildSucceeded;
    }

    public int getTotalTests() {
        return totalTests;
    }

    public int getPassed() {
        return passed;
    }

    public int getFailed() {
        return failed;
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

    @Override
    public String toString() {
        return "TestRunResult{totalTests=" + totalTests
                + ", passed=" + passed
                + ", failed=" + failed
                + ", skipped=" + skipped
                + ", executionTimeMillis=" + executionTimeMillis
                + ", buildSucceeded=" + buildSucceeded + "}";
    }
}
