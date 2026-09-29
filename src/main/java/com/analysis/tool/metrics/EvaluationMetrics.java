package com.analysis.tool.metrics;

import com.analysis.tool.execution.TestRunResult;

/**
 * Computes evaluation metrics comparing a full test-suite run against a
 * selected-test run, per the roadmap's Phase 11 formulas:
 *
 *   Test Reduction %      = ((Total - Selected) / Total) x 100
 *   Execution Time Reduction % = ((FullTime - SelectedTime) / FullTime) x 100
 *
 * Precision and recall are NOT computed here: they require a "ground
 * truth" set of relevant tests (established via manual analysis or fault
 * injection, per the roadmap's Phase 18), which this project does not yet
 * have. Reporting them without a real ground truth would be misleading.
 */
public class EvaluationMetrics {

    private final double testReductionPercent;
    private final double timeReductionPercent;

    private EvaluationMetrics(double testReductionPercent, double timeReductionPercent) {
        this.testReductionPercent = testReductionPercent;
        this.timeReductionPercent = timeReductionPercent;
    }

    public static EvaluationMetrics compute(TestRunResult fullRun, TestRunResult selectedRun) {
        double testReduction = 0.0;
        if (fullRun.getTotalTests() > 0) {
            testReduction = ((double) (fullRun.getTotalTests() - selectedRun.getTotalTests())
                    / fullRun.getTotalTests()) * 100.0;
        }

        double timeReduction = 0.0;
        if (fullRun.getExecutionTimeMillis() > 0) {
            timeReduction = ((double) (fullRun.getExecutionTimeMillis() - selectedRun.getExecutionTimeMillis())
                    / fullRun.getExecutionTimeMillis()) * 100.0;
        }

        return new EvaluationMetrics(testReduction, timeReduction);
    }

    public double getTestReductionPercent() {
        return testReductionPercent;
    }

    public double getTimeReductionPercent() {
        return timeReductionPercent;
    }

    @Override
    public String toString() {
        return "EvaluationMetrics{testReductionPercent=" + testReductionPercent
                + ", timeReductionPercent=" + timeReductionPercent + "}";
    }
}
