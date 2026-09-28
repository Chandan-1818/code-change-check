package com.analysis.tool.impact;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of ChangeImpactAnalyzer.analyzeTransitively and the
 * untested-method report, using real Git history from the sample project.
 *
 * Uses pinned commit hashes (never HEAD-relative refs):
 *   V4 = a3d0163 (compute() added)
 *   V5 = 268a165 (multiply() refactored)
 *   V6 = cc0d769 (negate() added, no test)
 */
public class ChangeImpactAnalyzerReportIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final List<FileMapping> MAPPINGS = Arrays.asList(
            new FileMapping(
                    "src/main/java/com/sample/Calculator.java",
                    "src/test/java/com/sample/CalculatorTest.java"),
            new FileMapping(
                    "src/main/java/com/sample/Multiplier.java",
                    "src/test/java/com/sample/MultiplierTest.java"));

    private static final String VERSION_4_COMMIT = "a3d0163";
    private static final String VERSION_5_COMMIT = "268a165";
    private static final String VERSION_6_COMMIT = "cc0d769";

    private TestSelectionReport analyze(String oldRevision, String newRevision) throws Exception {
        return new ChangeImpactAnalyzer().analyzeTransitively(
                SAMPLE_PROJECT_PATH, oldRevision, newRevision, MAPPINGS);
    }

    @Test
    public void shouldReportNegateAsUntestedAndSelectNoTestsWhenOnlyNegateIsAdded() throws Exception {
        TestSelectionReport report = analyze(VERSION_5_COMMIT, VERSION_6_COMMIT);

        assertTrue(report.getSelectedTests().isEmpty(),
                "Expected no tests to be selected, since no test reaches negate()");
        assertTrue(report.getUntestedChangedMethods().contains("negate"),
                "Expected negate to be reported as an untested changed method");
        assertEquals(1, report.getUntestedChangedMethods().size(),
                "Expected negate to be the only untested changed method");
    }

    @Test
    public void shouldReportNothingUntestedWhenEveryChangedMethodIsCovered() throws Exception {
        TestSelectionReport report = analyze(VERSION_4_COMMIT, VERSION_5_COMMIT);

        assertTrue(report.getSelectedTests().contains("testMultiply"),
                "Expected testMultiply to be selected (direct)");
        assertTrue(report.getSelectedTests().contains("testCompute"),
                "Expected testCompute to be selected (transitive via compute())");
        assertEquals(2, report.getSelectedTests().size(),
                "Expected exactly two selected tests");
        assertTrue(report.getUntestedChangedMethods().isEmpty(),
                "Expected no untested changed methods when multiply() is covered");
    }

    @Test
    public void shouldReportCoveredAndUncoveredChangesTogether() throws Exception {
        TestSelectionReport report = analyze(VERSION_4_COMMIT, VERSION_6_COMMIT);

        assertTrue(report.getSelectedTests().contains("testMultiply"),
                "Expected testMultiply to be selected");
        assertTrue(report.getSelectedTests().contains("testCompute"),
                "Expected testCompute to be selected");
        assertEquals(2, report.getSelectedTests().size(),
                "Expected exactly two selected tests");
        assertTrue(report.getUntestedChangedMethods().contains("negate"),
                "Expected negate to be reported as untested");
        assertEquals(1, report.getUntestedChangedMethods().size(),
                "Expected negate to be the only untested changed method");
    }

    @Test
    public void shouldReportNothingWhenComparingRevisionToItself() throws Exception {
        TestSelectionReport report = analyze(VERSION_6_COMMIT, VERSION_6_COMMIT);

        assertTrue(report.getSelectedTests().isEmpty(),
                "Expected no selected tests when nothing changed");
        assertTrue(report.getUntestedChangedMethods().isEmpty(),
                "Expected no untested changed methods when nothing changed");
    }
}
