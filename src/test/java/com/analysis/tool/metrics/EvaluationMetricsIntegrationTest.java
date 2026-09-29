package com.analysis.tool.metrics;

import com.analysis.tool.execution.TestRunResult;
import com.analysis.tool.execution.TestRunner;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test: runs the real sample project's full suite (5 tests)
 * and a real selected subset (2 tests: CalculatorTest#testCompute,
 * MultiplierTest#testMultiply), then computes EvaluationMetrics from the
 * two real TestRunResults.
 *
 * Like TestRunnerIntegrationTest, this launches real Maven subprocesses
 * and is noticeably slower than the rest of the suite.
 *
 * Known limitation (documented, not hidden): test reduction is asserted
 * exactly (60%), since it depends only on test counts. Time reduction is
 * NOT asserted here, because on a suite this small (5 vs 2 tests), Maven's
 * own JVM startup and initialization overhead (roughly constant per
 * invocation, on the order of 1-2 seconds) dominates the actual test
 * execution time, making the time-reduction percentage unreliable and
 * sometimes even negative - matching the roadmap's own stated caveat
 * (Phase 10: "Letting JVM/Maven startup overhead dominate short test
 * runs, making time comparisons misleading"). The observed value is
 * printed for visibility. A larger target project with real per-test
 * execution cost is needed before time reduction can be measured
 * reliably; averaging multiple runs (also roadmap-recommended) would
 * help but cannot fully remove this effect on a 5-test suite.
 */
public class EvaluationMetricsIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    @Test
    public void shouldComputeSixtyPercentTestReductionForRealV4ToV5Selection() throws Exception {
        TestRunner runner = new TestRunner();

        TestRunResult fullRun = runner.runFullSuite(SAMPLE_PROJECT_PATH);

        Set<String> selected = new HashSet<>();
        selected.add("CalculatorTest#testCompute");
        selected.add("MultiplierTest#testMultiply");
        TestRunResult selectedRun = runner.runSelectedTests(SAMPLE_PROJECT_PATH, selected);

        EvaluationMetrics metrics = EvaluationMetrics.compute(fullRun, selectedRun);

        assertEquals(5, fullRun.getTotalTests(), "Expected full suite to run 5 tests");
        assertEquals(2, selectedRun.getTotalTests(), "Expected selected run to run 2 tests");
        assertEquals(60.0, metrics.getTestReductionPercent(), 0.01,
                "Expected exactly 60% test reduction: (5-2)/5 * 100");

        System.out.println("Observed time reduction (informational only, not asserted): "
                + metrics.getTimeReductionPercent() + "% -- full=" + fullRun.getExecutionTimeMillis()
                + "ms, selected=" + selectedRun.getExecutionTimeMillis() + "ms");
    }
}
