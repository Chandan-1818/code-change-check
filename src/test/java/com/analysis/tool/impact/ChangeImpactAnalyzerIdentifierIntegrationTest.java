package com.analysis.tool.impact;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that TestSelectionReport.getSelectedTestIdentifiers() returns
 * Class#method identifiers (the format Maven Surefire's -Dtest filter
 * accepts), not just plain method names, using real Git history from the
 * sample project (V4 = a3d0163, V5 = 268a165: only multiply() modified).
 */
public class ChangeImpactAnalyzerIdentifierIntegrationTest {

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

    @Test
    public void shouldReturnClassQualifiedIdentifiersForSelectedTests() throws Exception {
        TestSelectionReport report = new ChangeImpactAnalyzer().analyzeTransitively(
                SAMPLE_PROJECT_PATH, VERSION_4_COMMIT, VERSION_5_COMMIT, MAPPINGS);

        assertTrue(report.getSelectedTestIdentifiers().contains("CalculatorTest#testCompute"),
                "Expected CalculatorTest#testCompute in the qualified identifiers");
        assertTrue(report.getSelectedTestIdentifiers().contains("MultiplierTest#testMultiply"),
                "Expected MultiplierTest#testMultiply in the qualified identifiers");
        assertEquals(2, report.getSelectedTestIdentifiers().size(),
                "Expected exactly two qualified identifiers for the V4 to V5 range");
    }
}
