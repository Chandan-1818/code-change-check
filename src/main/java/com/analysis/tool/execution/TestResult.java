package com.analysis.tool.execution;

/**
 * A single test outcome read from a Surefire XML report (H8, GAP-16).
 *
 * The status distinguishes an assertion failure ({@link Status#FAILED}) from
 * an execution error ({@link Status#ERROR}), which Surefire reports through
 * different XML elements (&lt;failure&gt; versus &lt;error&gt;).
 */
public class TestResult {

    public enum Status { PASSED, FAILED, ERROR, SKIPPED }

    private final String className;
    private final String methodName;
    private final Status status;
    private final double timeSeconds;

    public TestResult(String className, String methodName, Status status, double timeSeconds) {
        this.className = className;
        this.methodName = methodName;
        this.status = status;
        this.timeSeconds = timeSeconds;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public Status getStatus() {
        return status;
    }

    public double getTimeSeconds() {
        return timeSeconds;
    }

    /** Identifier in the selector's own form (e.g. CalculatorTest#testCompute). */
    public String getDisplayName() {
        return className + "#" + methodName;
    }

    @Override
    public String toString() {
        return getDisplayName() + " [" + status + "]";
    }
}
