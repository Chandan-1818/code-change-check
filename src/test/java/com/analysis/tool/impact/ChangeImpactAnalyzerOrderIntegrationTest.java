package com.analysis.tool.impact;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of ChangeImpactAnalyzer.analyzeTransitively on the second,
 * independent chain in the sample project:
 * testPlaceOrder -> OrderService.placeOrder -> OrderRepository.save
 *
 * Uses pinned commit hashes (never HEAD-relative refs):
 *   V6 = cc0d769 (negate() added, no Order classes yet)
 *   V7 = bae01d1 (OrderRepository, OrderService and their tests added)
 *   V8 = ebec3c0 (OrderRepository.save() refactored)
 */
public class ChangeImpactAnalyzerOrderIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final List<FileMapping> MAPPINGS = Arrays.asList(
            new FileMapping(
                    "src/main/java/com/sample/Calculator.java",
                    "src/test/java/com/sample/CalculatorTest.java"),
            new FileMapping(
                    "src/main/java/com/sample/Multiplier.java",
                    "src/test/java/com/sample/MultiplierTest.java"),
            new FileMapping(
                    "src/main/java/com/sample/OrderRepository.java",
                    "src/test/java/com/sample/OrderRepositoryTest.java"),
            new FileMapping(
                    "src/main/java/com/sample/OrderService.java",
                    "src/test/java/com/sample/OrderServiceTest.java"));

    private static final String VERSION_6_COMMIT = "cc0d769";
    private static final String VERSION_7_COMMIT = "bae01d1";
    private static final String VERSION_8_COMMIT = "ebec3c0";

    private TestSelectionReport analyze(String oldRevision, String newRevision) throws Exception {
        return new ChangeImpactAnalyzer().analyzeTransitively(
                SAMPLE_PROJECT_PATH, oldRevision, newRevision, MAPPINGS);
    }

    @Test
    public void shouldSelectDirectAndTransitiveOrderTestsWhenOnlySaveIsModified() throws Exception {
        TestSelectionReport report = analyze(VERSION_7_COMMIT, VERSION_8_COMMIT);

        assertTrue(report.getSelectedTests().contains("testSaveAndFindTotal"),
                "Expected testSaveAndFindTotal to be selected (direct caller of save)");
        assertTrue(report.getSelectedTests().contains("testPlaceOrder"),
                "Expected testPlaceOrder to be selected transitively via placeOrder() -> save()");
        assertFalse(report.getSelectedTests().contains("testCompute"),
                "Expected Calculator tests NOT to be selected");
        assertFalse(report.getSelectedTests().contains("testMultiply"),
                "Expected Multiplier tests NOT to be selected");
        assertEquals(2, report.getSelectedTests().size(),
                "Expected exactly two selected tests for the V7 -> V8 range");
        assertTrue(report.getUntestedChangedMethods().isEmpty(),
                "Expected no untested changed methods when save() is covered");
    }

    @Test
    public void shouldSelectNothingWhenComparingRevisionToItself() throws Exception {
        TestSelectionReport report = analyze(VERSION_8_COMMIT, VERSION_8_COMMIT);

        assertTrue(report.getSelectedTests().isEmpty(),
                "Expected no selected tests when nothing changed");
        assertTrue(report.getUntestedChangedMethods().isEmpty(),
                "Expected no untested changed methods when nothing changed");
    }

    @Test
    public void shouldSelectBothOrderTestsWhenOrderClassesAreNewlyAdded() throws Exception {
        TestSelectionReport report = analyze(VERSION_6_COMMIT, VERSION_7_COMMIT);

        assertTrue(report.getSelectedTests().contains("testSaveAndFindTotal"),
                "Expected testSaveAndFindTotal to be selected for the newly added repository");
        assertTrue(report.getSelectedTests().contains("testPlaceOrder"),
                "Expected testPlaceOrder to be selected for the newly added service");
        assertEquals(2, report.getSelectedTests().size(),
                "Expected exactly two selected tests for the V6 -> V7 range");
        assertTrue(report.getUntestedChangedMethods().isEmpty(),
                "Expected no untested changed methods since every new method is covered");
    }
}
