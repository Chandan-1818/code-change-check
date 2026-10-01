package com.analysis.tool.impact;

import com.analysis.tool.git.GitAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * End-to-end integration test: connects GitAnalyzer (real Git history)
 * with TestImpactMapper (real AST-based method-call detection) against
 * the real sample project's CalculatorTest.java.
 */
public class TestImpactMapperIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final String CALCULATOR_TEST_PATH =
            "src/test/java/com/sample/CalculatorTest.java";

    private static final String VERSION_8_COMMIT = "ebec3c0";

    @Test
    public void shouldMapSubtractToTestSubtractOnly() throws Exception {
        GitAnalyzer gitAnalyzer = new GitAnalyzer(SAMPLE_PROJECT_PATH);
        String testSource = gitAnalyzer.getFileContentAtRevision(VERSION_8_COMMIT, CALCULATOR_TEST_PATH);
        gitAnalyzer.close();

        TestImpactMapper mapper = new TestImpactMapper();
        Set<String> impactedTests = mapper.findTestsExercisingMethod(testSource, "subtract");

        assertTrue(impactedTests.contains("testSubtract"),
                "Expected testSubtract to be identified as exercising subtract()");
        assertFalse(impactedTests.contains("testAdd"),
                "Expected testAdd NOT to be identified as exercising subtract()");
    }

    @Test
    public void shouldMapAddToTestAddOnly() throws Exception {
        GitAnalyzer gitAnalyzer = new GitAnalyzer(SAMPLE_PROJECT_PATH);
        String testSource = gitAnalyzer.getFileContentAtRevision(VERSION_8_COMMIT, CALCULATOR_TEST_PATH);
        gitAnalyzer.close();

        TestImpactMapper mapper = new TestImpactMapper();
        Set<String> impactedTests = mapper.findTestsExercisingMethod(testSource, "add");

        assertTrue(impactedTests.contains("testAdd"),
                "Expected testAdd to be identified as exercising add()");
        assertFalse(impactedTests.contains("testSubtract"),
                "Expected testSubtract NOT to be identified as exercising add()");
    }
}
