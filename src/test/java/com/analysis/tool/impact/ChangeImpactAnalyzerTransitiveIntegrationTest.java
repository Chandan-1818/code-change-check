package com.analysis.tool.impact;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of ChangeImpactAnalyzer.findImpactedTestsTransitively:
 * real Git history, real AST parsing, one combined dependency graph across
 * multiple source/test file pairs, and reverse-BFS test selection.
 *
 * Uses pinned commit hashes (never HEAD-relative refs) so results stay
 * stable as the sample project gains new commits.
 */
public class ChangeImpactAnalyzerTransitiveIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final String CALCULATOR_PATH =
            "src/main/java/com/sample/Calculator.java";
    private static final String CALCULATOR_TEST_PATH =
            "src/test/java/com/sample/CalculatorTest.java";
    private static final String MULTIPLIER_PATH =
            "src/main/java/com/sample/Multiplier.java";
    private static final String MULTIPLIER_TEST_PATH =
            "src/test/java/com/sample/MultiplierTest.java";

    private static final String VERSION_3_COMMIT = "7584402";
    private static final String VERSION_4_COMMIT = "a3d0163";
    private static final String VERSION_5_COMMIT = "268a165";

    private static final List<FileMapping> MAPPINGS = Arrays.asList(
            new FileMapping(CALCULATOR_PATH, CALCULATOR_TEST_PATH),
            new FileMapping(MULTIPLIER_PATH, MULTIPLIER_TEST_PATH));

    @Test
    public void shouldSelectDirectAndTransitiveTestsWhenOnlyMultiplyIsModified() throws Exception {
        ChangeImpactAnalyzer analyzer = new ChangeImpactAnalyzer();

        Set<String> impactedTests = analyzer.findImpactedTestsTransitively(
                SAMPLE_PROJECT_PATH, VERSION_4_COMMIT, VERSION_5_COMMIT, MAPPINGS);

        assertTrue(impactedTests.contains("testMultiply"),
                "Expected testMultiply (direct caller of multiply) to be selected");
        assertTrue(impactedTests.contains("testCompute"),
                "Expected testCompute to be selected transitively via compute() -> multiply()");
        assertFalse(impactedTests.contains("testAdd"),
                "Expected testAdd NOT to be selected");
        assertFalse(impactedTests.contains("testSubtract"),
                "Expected testSubtract NOT to be selected");
        assertEquals(2, impactedTests.size(),
                "Expected exactly two impacted tests for the V4 -> V5 range");
    }

    @Test
    public void shouldSelectOnlyTestComputeWhenComputeIsAdded() throws Exception {
        ChangeImpactAnalyzer analyzer = new ChangeImpactAnalyzer();

        Set<String> impactedTests = analyzer.findImpactedTestsTransitively(
                SAMPLE_PROJECT_PATH, VERSION_3_COMMIT, VERSION_4_COMMIT, MAPPINGS);

        assertTrue(impactedTests.contains("testCompute"),
                "Expected testCompute to be selected since compute() was added");
        assertEquals(1, impactedTests.size(),
                "Expected exactly one impacted test for the V3 -> V4 range");
    }

    @Test
    public void shouldSelectNothingWhenComparingRevisionToItself() throws Exception {
        ChangeImpactAnalyzer analyzer = new ChangeImpactAnalyzer();

        Set<String> impactedTests = analyzer.findImpactedTestsTransitively(
                SAMPLE_PROJECT_PATH, VERSION_5_COMMIT, VERSION_5_COMMIT, MAPPINGS);

        assertTrue(impactedTests.isEmpty(),
                "Expected no impacted tests when nothing changed");
    }
}
