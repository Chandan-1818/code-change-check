package com.analysis.tool.impact;

import java.util.Set;

/**
 * Result of a test-selection run.
 *
 * selectedTests: test methods that reach at least one changed method,
 * directly or through a chain of calls.
 *
 * untestedChangedMethods: added or modified methods that no test reaches.
 * Reported explicitly so that an empty selection is not mistaken for
 * "nothing to run" when it actually means "this change has no coverage".
 *
 * Matching is by method name only (see DependencyGraphBuilder), so a test
 * that calls a different method with the same name can hide a coverage gap.
 */
public class TestSelectionReport {

    private final Set<String> selectedTests;
    private final Set<String> untestedChangedMethods;

    public TestSelectionReport(Set<String> selectedTests, Set<String> untestedChangedMethods) {
        this.selectedTests = selectedTests;
        this.untestedChangedMethods = untestedChangedMethods;
    }

    public Set<String> getSelectedTests() {
        return selectedTests;
    }

    public Set<String> getUntestedChangedMethods() {
        return untestedChangedMethods;
    }
}
