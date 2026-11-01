package com.analysis.tool.execution;

/**
 * Result of running a Maven test invocation (full suite or a filtered
 * selection), parsed from Surefire's console summary line
 * ("Tests run: X, Failures: Y, Errors: Z, Skipped: W").
 *
 * executionTimeMillis is wall-clock time measured around the process,
 * not Maven's own internal timing.
 *
 * Maven's exit code is non-zero whenever a test fails, so "build failed" and
 * "the mutation was killed" cannot be told apart from the exit code alone.
 * compilationFailed reports the one build failure that changes the meaning of
 * the run: a mutant that did not compile never executed any test, so its
 * zero-failure summary must not be read as "the suite survived the mutation".
 */
public class TestRunResult {

    private final int totalTests;
    private final int passed;
    private final int failed;
    private final int skipped;
    private final long executionTimeMillis;
    private final boolean buildSucceeded;
    private final boolean compilationFailed;

    /** Backward-compatible constructor: no compilation-failure signal. */
    public TestRunResult(int totalTests, int passed, int failed, int skipped,
                          long executionTimeMillis, boolean buildSucceeded) {
        this(totalTests, passed, failed, skipped, executionTimeMillis, buildSucceeded, false);
    }

    public TestRunResult(int totalTests, int passed, int failed, int skipped,
                          long executionTimeMillis, boolean buildSucceeded,
                          boolean compilationFailed) {
        this.totalTests = totalTests;
        this.passed = passed;
        this.failed = failed;
        this.skipped = skipped;
        this.executionTimeMillis = executionTimeMillis;
        this.buildSucceeded = buildSucceeded;
        this.compilationFailed = compilationFailed;
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

    /**
     * True when the build failed to compile, so no test could run. Such a run
     * carries no information about whether the suite survives the mutation.
     */
    public boolean isCompilationFailed() {
        return compilationFailed;
    }

    @Override
    public String toString() {
        return "TestRunResult{totalTests=" + totalTests
                + ", passed=" + passed
                + ", failed=" + failed
                + ", skipped=" + skipped
                + ", executionTimeMillis=" + executionTimeMillis
                + ", buildSucceeded=" + buildSucceeded
                + ", compilationFailed=" + compilationFailed + "}";
    }
}
