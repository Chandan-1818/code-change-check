package com.analysis.tool.eval;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The evaluated outcome of one {@link GroundTruthCase}: the tests the tool
 * actually selected, compared with the required (ground-truth) tests, plus
 * the resulting confusion matrix.
 */
public final class CaseResult {

    private final GroundTruthCase testCase;
    private final Set<String> selectedTests;
    private final PrecisionRecall metrics;

    public CaseResult(GroundTruthCase testCase, Set<String> selectedTests) {
        this.testCase = Objects.requireNonNull(testCase, "testCase must not be null");
        this.selectedTests = selectedTests == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new TreeSet<>(selectedTests));
        this.metrics = PrecisionRecall.of(this.selectedTests, testCase.getRequiredTests());
    }

    public GroundTruthCase getCase() {
        return testCase;
    }

    public Set<String> getSelectedTests() {
        return selectedTests;
    }

    public Set<String> getRequiredTests() {
        return testCase.getRequiredTests();
    }

    public PrecisionRecall getMetrics() {
        return metrics;
    }

    /** Required tests that were selected. */
    public Set<String> getTruePositiveTests() {
        Set<String> intersection = new TreeSet<>(selectedTests);
        intersection.retainAll(testCase.getRequiredTests());
        return Collections.unmodifiableSet(intersection);
    }

    /** Selected tests that are not required. */
    public Set<String> getFalsePositiveTests() {
        Set<String> difference = new TreeSet<>(selectedTests);
        difference.removeAll(testCase.getRequiredTests());
        return Collections.unmodifiableSet(difference);
    }

    /** Required tests that were not selected (a safety gap). */
    public Set<String> getFalseNegativeTests() {
        Set<String> difference = new TreeSet<>(testCase.getRequiredTests());
        difference.removeAll(selectedTests);
        return Collections.unmodifiableSet(difference);
    }

    @Override
    public String toString() {
        return "CaseResult{" + testCase.getId() + ", " + metrics + "}";
    }
}
