package com.analysis.tool.impact;

import java.util.Collections;
import java.util.Set;

/**
 * Result of a test-selection run.
 *
 * selectedTests: test methods reaching a changed method (plain names).
 * selectedTestIdentifiers: the same tests as "ClassName#methodName" (Surefire -Dtest format).
 * untestedChangedMethods: added/modified methods that no test reaches.
 * removedMethods: methods present in the old revision only.
 * conservativelyExpandedFiles: files where a change outside any method body
 *   (field, constant, import, class annotation...) was detected, so ALL of
 *   the file's methods were treated as changed.
 */
public class TestSelectionReport {

    private final Set<String> selectedTests;
    private final Set<String> selectedTestIdentifiers;
    private final Set<String> untestedChangedMethods;
    private final Set<String> removedMethods;
    private final Set<String> conservativelyExpandedFiles;

    public TestSelectionReport(Set<String> selectedTests,
                               Set<String> selectedTestIdentifiers,
                               Set<String> untestedChangedMethods) {
        this(selectedTests, selectedTestIdentifiers, untestedChangedMethods,
                Collections.<String>emptySet(), Collections.<String>emptySet());
    }

    public TestSelectionReport(Set<String> selectedTests,
                               Set<String> selectedTestIdentifiers,
                               Set<String> untestedChangedMethods,
                               Set<String> removedMethods) {
        this(selectedTests, selectedTestIdentifiers, untestedChangedMethods,
                removedMethods, Collections.<String>emptySet());
    }

    public TestSelectionReport(Set<String> selectedTests,
                               Set<String> selectedTestIdentifiers,
                               Set<String> untestedChangedMethods,
                               Set<String> removedMethods,
                               Set<String> conservativelyExpandedFiles) {
        this.selectedTests = selectedTests;
        this.selectedTestIdentifiers = selectedTestIdentifiers;
        this.untestedChangedMethods = untestedChangedMethods;
        this.removedMethods = removedMethods;
        this.conservativelyExpandedFiles = conservativelyExpandedFiles;
    }

    public Set<String> getSelectedTests() { return selectedTests; }
    public Set<String> getSelectedTestIdentifiers() { return selectedTestIdentifiers; }
    public Set<String> getUntestedChangedMethods() { return untestedChangedMethods; }
    public Set<String> getRemovedMethods() { return removedMethods; }
    public Set<String> getConservativelyExpandedFiles() { return conservativelyExpandedFiles; }
}
