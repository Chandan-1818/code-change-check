package com.analysis.tool.execution;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for TestRunner: each test genuinely launches a nested
 * Maven build in the real sample project via ProcessBuilder, so these are
 * noticeably slower (a few seconds each) than the rest of the suite. This
 * is expected, not a defect - it is the actual mechanism being proven.
 */
public class TestRunnerIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    @Test
    public void shouldRunAllSevenTestsInTheFullSuite() throws Exception {
        TestRunner runner = new TestRunner();
        TestRunResult result = runner.runFullSuite(SAMPLE_PROJECT_PATH);

        assertTrue(result.isBuildSucceeded(), "Expected the full-suite build to succeed");
        assertEquals(7, result.getTotalTests(), "Expected all 7 sample-project tests to run");
        assertEquals(7, result.getPassed(), "Expected all 7 tests to pass");
        assertEquals(0, result.getFailed(), "Expected no failures");
        assertEquals(0, result.getSkipped(), "Expected no skipped tests");
    }

    @Test
    public void shouldRunOnlyTheSelectedTests() throws Exception {
        Set<String> selected = new HashSet<>();
        selected.add("CalculatorTest#testCompute");
        selected.add("MultiplierTest#testMultiply");

        TestRunner runner = new TestRunner();
        TestRunResult result = runner.runSelectedTests(SAMPLE_PROJECT_PATH, selected);

        assertTrue(result.isBuildSucceeded(), "Expected the selected-tests build to succeed");
        assertEquals(2, result.getTotalTests(), "Expected exactly the 2 selected tests to run");
        assertEquals(2, result.getPassed(), "Expected both selected tests to pass");
        assertEquals(0, result.getFailed(), "Expected no failures");
    }
}
