package com.analysis.tool.impact;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
 * confidence: how much trust to place in the selection (see SelectionConfidence).
 * unparsedFiles: files that could not be parsed; a non-empty set forces
 *   FALLBACK confidence and a conservative "select every discovered test".
 * buildOrResourceChanges: changed build/resource/other non-Java files that
 *   cannot be analysed at member level; a non-empty set also forces FALLBACK
 *   confidence and a conservative "select every discovered test".
 * truncated: true when a finite impact-depth limit stopped the traversal
 *   before the full reachable set was explored, so the selection is a
 *   partial view that must not be presented as complete.
 * potentiallyBrokenTests (H7): tests that call a method removed between the
 *   two revisions. A removed method cannot be selected through impact, so
 *   these tests are reported separately (they may no longer compile) without
 *   changing the impact-based selection.
 * explanations (H7): selected-test identifier -> the call chain that led to
 *   its selection, nearest node first and rendered as ClassName#methodName
 *   (e.g. [CalculatorTest#testCompute, Calculator#compute]), so every selected
 *   test can state why it was chosen.
 * changedMembers (T3): the members that were analysed as changed, rendered
 *   "Class#member" (a constructor as "Class#<init>"). This is what the
 *   member-level detector actually fed into impact analysis.
 * impactedMethods (T3): the methods the change reaches through the call
 *   graph, rendered "Class#method", excluding the test methods themselves.
 *   A callee whose declaring class could not be resolved (no Symbol Solver)
 *   is rendered with the "UNKNOWN" class marker.
 */
public class TestSelectionReport {

    private final Set<String> selectedTests;
    private final Set<String> selectedTestIdentifiers;
    private final Set<String> untestedChangedMethods;
    private final Set<String> removedMethods;
    private final Set<String> conservativelyExpandedFiles;
    private final SelectionConfidence confidence;
    private final Set<String> unparsedFiles;
    private final Set<String> buildOrResourceChanges;
    private final boolean truncated;
    private final Set<String> potentiallyBrokenTests;
    private final Map<String, List<String>> explanations;
    private final Set<String> changedMembers;
    private final Set<String> impactedMethods;

    public TestSelectionReport(Set<String> selectedTests,
                               Set<String> selectedTestIdentifiers,
                               Set<String> untestedChangedMethods) {
        this(selectedTests, selectedTestIdentifiers, untestedChangedMethods,
                Collections.<String>emptySet(), Collections.<String>emptySet(),
                SelectionConfidence.HIGH, Collections.<String>emptySet(), false);
    }

    public TestSelectionReport(Set<String> selectedTests,
                               Set<String> selectedTestIdentifiers,
                               Set<String> untestedChangedMethods,
                               Set<String> removedMethods) {
        this(selectedTests, selectedTestIdentifiers, untestedChangedMethods,
                removedMethods, Collections.<String>emptySet(),
                SelectionConfidence.HIGH, Collections.<String>emptySet(), false);
    }

    public TestSelectionReport(Set<String> selectedTests,
                               Set<String> selectedTestIdentifiers,
                               Set<String> untestedChangedMethods,
                               Set<String> removedMethods,
                               Set<String> conservativelyExpandedFiles) {
        this(selectedTests, selectedTestIdentifiers, untestedChangedMethods,
                removedMethods, conservativelyExpandedFiles,
                SelectionConfidence.HIGH, Collections.<String>emptySet(), false);
    }

    public TestSelectionReport(Set<String> selectedTests,
                               Set<String> selectedTestIdentifiers,
                               Set<String> untestedChangedMethods,
                               Set<String> removedMethods,
                               Set<String> conservativelyExpandedFiles,
                               SelectionConfidence confidence,
                               Set<String> unparsedFiles) {
        this(selectedTests, selectedTestIdentifiers, untestedChangedMethods,
                removedMethods, conservativelyExpandedFiles, confidence,
                unparsedFiles, false);
    }

    public TestSelectionReport(Set<String> selectedTests,
                               Set<String> selectedTestIdentifiers,
                               Set<String> untestedChangedMethods,
                               Set<String> removedMethods,
                               Set<String> conservativelyExpandedFiles,
                               SelectionConfidence confidence,
                               Set<String> unparsedFiles,
                               boolean truncated) {
        this(selectedTests, selectedTestIdentifiers, untestedChangedMethods,
                removedMethods, conservativelyExpandedFiles, confidence,
                unparsedFiles, Collections.<String>emptySet(), truncated);
    }

    public TestSelectionReport(Set<String> selectedTests,
                               Set<String> selectedTestIdentifiers,
                               Set<String> untestedChangedMethods,
                               Set<String> removedMethods,
                               Set<String> conservativelyExpandedFiles,
                               SelectionConfidence confidence,
                               Set<String> unparsedFiles,
                               Set<String> buildOrResourceChanges,
                               boolean truncated) {
        this(selectedTests, selectedTestIdentifiers, untestedChangedMethods,
                removedMethods, conservativelyExpandedFiles, confidence,
                unparsedFiles, buildOrResourceChanges, truncated,
                Collections.<String>emptySet(), Collections.<String, List<String>>emptyMap());
    }

    public TestSelectionReport(Set<String> selectedTests,
                               Set<String> selectedTestIdentifiers,
                               Set<String> untestedChangedMethods,
                               Set<String> removedMethods,
                               Set<String> conservativelyExpandedFiles,
                               SelectionConfidence confidence,
                               Set<String> unparsedFiles,
                               Set<String> buildOrResourceChanges,
                               boolean truncated,
                               Set<String> potentiallyBrokenTests,
                               Map<String, List<String>> explanations) {
        this(selectedTests, selectedTestIdentifiers, untestedChangedMethods,
                removedMethods, conservativelyExpandedFiles, confidence,
                unparsedFiles, buildOrResourceChanges, truncated,
                potentiallyBrokenTests, explanations,
                Collections.<String>emptySet(), Collections.<String>emptySet());
    }

    /**
     * Full form, adding the member-level detail the live workflow displays:
     * the changed members that were analysed, and the methods the change
     * reaches through the call graph.
     */
    public TestSelectionReport(Set<String> selectedTests,
                               Set<String> selectedTestIdentifiers,
                               Set<String> untestedChangedMethods,
                               Set<String> removedMethods,
                               Set<String> conservativelyExpandedFiles,
                               SelectionConfidence confidence,
                               Set<String> unparsedFiles,
                               Set<String> buildOrResourceChanges,
                               boolean truncated,
                               Set<String> potentiallyBrokenTests,
                               Map<String, List<String>> explanations,
                               Set<String> changedMembers,
                               Set<String> impactedMethods) {
        this.selectedTests = selectedTests;
        this.selectedTestIdentifiers = selectedTestIdentifiers;
        this.untestedChangedMethods = untestedChangedMethods;
        this.removedMethods = removedMethods;
        this.conservativelyExpandedFiles = conservativelyExpandedFiles;
        this.confidence = confidence;
        this.unparsedFiles = unparsedFiles;
        this.buildOrResourceChanges = buildOrResourceChanges;
        this.truncated = truncated;
        this.potentiallyBrokenTests = potentiallyBrokenTests;
        this.explanations = explanations;
        this.changedMembers = changedMembers;
        this.impactedMethods = impactedMethods;
    }

    public Set<String> getSelectedTests() { return selectedTests; }
    public Set<String> getSelectedTestIdentifiers() { return selectedTestIdentifiers; }
    public Set<String> getUntestedChangedMethods() { return untestedChangedMethods; }
    public Set<String> getRemovedMethods() { return removedMethods; }
    public Set<String> getConservativelyExpandedFiles() { return conservativelyExpandedFiles; }
    public SelectionConfidence getConfidence() { return confidence; }
    public Set<String> getUnparsedFiles() { return unparsedFiles; }
    public Set<String> getBuildOrResourceChanges() { return buildOrResourceChanges; }
    public boolean isTruncated() { return truncated; }
    public Set<String> getPotentiallyBrokenTests() { return potentiallyBrokenTests; }
    public Map<String, List<String>> getExplanations() { return explanations; }
    /** Changed members that were analysed, rendered "Class#member". */
    public Set<String> getChangedMembers() { return changedMembers; }
    /** Methods the change reaches through the call graph, rendered "Class#method". */
    public Set<String> getImpactedMethods() { return impactedMethods; }
}
