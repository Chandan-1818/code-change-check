package com.analysis.tool.impact;

import com.analysis.tool.git.GitAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documents a known limitation of TestImpactMapper: it only detects
 * DIRECT method calls inside a test body, not transitive ones.
 *
 * testCompute() calls Calculator.compute(), which in turn calls
 * Multiplier.multiply() - a one-hop transitive relationship. Since
 * TestImpactMapper does not use the dependency graph, it fails to
 * recognize that testCompute() exercises multiply() at all.
 *
 * This is the exact gap that wiring ImpactAnalyzer's dependency graph
 * into test selection (a planned next step) is meant to close. This
 * test exists to concretely document the "before" state.
 */
public class TestImpactMapperGapIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final String CALCULATOR_TEST_PATH =
            "src/test/java/com/sample/CalculatorTest.java";

    private static final String VERSION_4_COMMIT = "a3d0163";

    @Test
    public void shouldFailToFindTestComputeAsExercisingMultiplyDueToMissingTransitiveReasoning() throws Exception {
        GitAnalyzer gitAnalyzer = new GitAnalyzer(SAMPLE_PROJECT_PATH);
        String testSource = gitAnalyzer.getFileContentAtRevision(VERSION_4_COMMIT, CALCULATOR_TEST_PATH);
        gitAnalyzer.close();

        TestImpactMapper mapper = new TestImpactMapper();
        Set<String> impactedTests = mapper.findTestsExercisingMethod(testSource, "multiply");

        // Documents the CURRENT (limited) behavior: testCompute is missed
        // because it only calls compute(), not multiply() directly.
        assertFalse(impactedTests.contains("testCompute"),
                "KNOWN LIMITATION: TestImpactMapper misses testCompute() because it " +
                "only calls compute(), not multiply() directly - no transitive reasoning yet");

        // Sanity check: it's not finding anything at all for "multiply",
        // confirming the mapper genuinely has zero visibility into this case,
        // not just an unrelated bug.
        assertTrue(impactedTests.isEmpty(),
                "Expected no tests to be found via direct-call matching alone for 'multiply'");
    }
}
