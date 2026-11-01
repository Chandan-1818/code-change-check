package com.analysis.tool.impact;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Full end-to-end integration test: proves that given two real Git revisions
 * of the sample project, ChangeImpactAnalyzer correctly identifies exactly
 * which test methods need to be re-run, using real Git history, real AST
 * parsing, and real method-call detection at every stage.
 *
 * Includes:
 * - a positive case (a method was actually modified)
 * - a negative case (no change at all)
 * - a multi-file case (one file unchanged, one file newly added)
 *
 * Uses pinned commit hashes rather than HEAD/HEAD~1, so this test remains
 * correct even as new commits are added to the sample project later.
 */
public class ChangeImpactAnalyzerIntegrationTest {

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

    private static final String VERSION_1_COMMIT = "f2493c2";
    private static final String VERSION_2_COMMIT = "9ed64fa";
    private static final String VERSION_3_COMMIT = "7584402";

    @Test
    public void shouldSelectOnlyTestSubtractForRealCommitRange() throws Exception {
        ChangeImpactAnalyzer analyzer = new ChangeImpactAnalyzer();

        Set<String> impactedTests = analyzer.findImpactedTests(
                SAMPLE_PROJECT_PATH,
                VERSION_1_COMMIT,
                VERSION_2_COMMIT,
                CALCULATOR_PATH,
                CALCULATOR_TEST_PATH);

        assertTrue(impactedTests.contains("testSubtract"),
                "Expected testSubtract to be selected since subtract() was modified");
        assertFalse(impactedTests.contains("testAdd"),
                "Expected testAdd NOT to be selected since add() was unchanged");
        assertEquals(1, impactedTests.size(),
                "Expected exactly one impacted test for this commit range");
    }

    @Test
    public void shouldSelectNoTestsWhenComparingRevisionToItself() throws Exception {
        ChangeImpactAnalyzer analyzer = new ChangeImpactAnalyzer();

        Set<String> impactedTests = analyzer.findImpactedTests(
                SAMPLE_PROJECT_PATH,
                VERSION_2_COMMIT,
                VERSION_2_COMMIT,
                CALCULATOR_PATH,
                CALCULATOR_TEST_PATH);

        assertTrue(impactedTests.isEmpty(),
                "Expected no impacted tests when comparing a revision to itself (no code change)");
    }

    @Test
    public void shouldSelectOnlyTestMultiplyWhenOnlyMultiplierIsAddedAcrossMultipleFiles() throws Exception {
        ChangeImpactAnalyzer analyzer = new ChangeImpactAnalyzer();

        Set<String> impactedTests = analyzer.findImpactedTests(
                SAMPLE_PROJECT_PATH,
                VERSION_2_COMMIT,
                VERSION_3_COMMIT,
                Arrays.asList(
                        new FileMapping(CALCULATOR_PATH, CALCULATOR_TEST_PATH),
                        new FileMapping(MULTIPLIER_PATH, MULTIPLIER_TEST_PATH)
                ));

        assertTrue(impactedTests.contains("testMultiply"),
                "Expected testMultiply to be selected since Multiplier.java was newly added");
        assertFalse(impactedTests.contains("testAdd"),
                "Expected testAdd NOT to be selected since Calculator.java was unchanged in this range");
        assertFalse(impactedTests.contains("testSubtract"),
                "Expected testSubtract NOT to be selected since Calculator.java was unchanged in this range");
        assertEquals(1, impactedTests.size(),
                "Expected exactly one impacted test across both files for this commit range");
    }
}
