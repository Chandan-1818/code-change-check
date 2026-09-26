package com.analysis.tool.impact;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Full end-to-end integration test: proves that given two real Git revisions
 * of the sample project, ChangeImpactAnalyzer correctly identifies exactly
 * which test methods need to be re-run, using real Git history, real AST
 * parsing, and real method-call detection at every stage.
 */
public class ChangeImpactAnalyzerIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final String CALCULATOR_PATH =
            "src/main/java/com/sample/Calculator.java";

    private static final String CALCULATOR_TEST_PATH =
            "src/test/java/com/sample/CalculatorTest.java";

    @Test
    public void shouldSelectOnlyTestSubtractForRealCommitRange() throws Exception {
        ChangeImpactAnalyzer analyzer = new ChangeImpactAnalyzer();

        Set<String> impactedTests = analyzer.findImpactedTests(
                SAMPLE_PROJECT_PATH,
                "HEAD~1",
                "HEAD",
                CALCULATOR_PATH,
                CALCULATOR_TEST_PATH);

        assertTrue(impactedTests.contains("testSubtract"),
                "Expected testSubtract to be selected since subtract() was modified");
        assertFalse(impactedTests.contains("testAdd"),
                "Expected testAdd NOT to be selected since add() was unchanged");
        assertEquals(1, impactedTests.size(),
                "Expected exactly one impacted test for this commit range");
    }
}
