package com.analysis.tool.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the CLI's handling of changed methods that no test reaches.
 * An empty selection with untested changes must print a WARNING section
 * rather than "no impacted tests found", so it cannot be mistaken for
 * "nothing to run".
 *
 * Uses pinned sample-project commits:
 *   V4 = a3d0163, V5 = 268a165, V6 = cc0d769 (negate() added, no test).
 */
public class SelectTestsCliWarningTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final String CALCULATOR_PAIR =
            "src/main/java/com/sample/Calculator.java=src/test/java/com/sample/CalculatorTest.java";
    private static final String MULTIPLIER_PAIR =
            "src/main/java/com/sample/Multiplier.java=src/test/java/com/sample/MultiplierTest.java";

    private static final String VERSION_4_COMMIT = "a3d0163";
    private static final String VERSION_5_COMMIT = "268a165";
    private static final String VERSION_6_COMMIT = "cc0d769";

    private static class Result {
        int exitCode;
        String out;
        String err;
    }

    private Result runCli(String oldRevision, String newRevision) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        Result result = new Result();
        result.exitCode = SelectTestsCli.run(
                new String[] {SAMPLE_PROJECT_PATH, oldRevision, newRevision, CALCULATOR_PAIR, MULTIPLIER_PAIR},
                out, err);
        result.out = outBytes.toString(StandardCharsets.UTF_8);
        result.err = errBytes.toString(StandardCharsets.UTF_8);
        return result;
    }

    @Test
    public void shouldWarnAboutNegateAndNotClaimNothingWasImpacted() {
        Result result = runCli(VERSION_5_COMMIT, VERSION_6_COMMIT);

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("Selected tests : none"),
                "Expected the selection to be reported as none");
        assertTrue(result.out.contains("WARNING"),
                "Expected a WARNING section for the untested change");
        assertTrue(result.out.contains("negate"),
                "Expected negate to be listed as untested");
        assertFalse(result.out.contains("no impacted tests found"),
                "Must not report 'no impacted tests found' when a change is untested");
    }

    @Test
    public void shouldNotWarnWhenEveryChangedMethodIsCovered() {
        Result result = runCli(VERSION_4_COMMIT, VERSION_5_COMMIT);

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("testCompute"),
                "Expected testCompute to be selected");
        assertTrue(result.out.contains("testMultiply"),
                "Expected testMultiply to be selected");
        assertFalse(result.out.contains("WARNING"),
                "Expected no WARNING when every changed method is covered");
    }

    @Test
    public void shouldReportNoImpactedTestsAndNoWarningWhenNothingChanged() {
        Result result = runCli(VERSION_6_COMMIT, VERSION_6_COMMIT);

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("none (no impacted tests found)"),
                "Expected the plain 'no impacted tests found' message");
        assertFalse(result.out.contains("WARNING"),
                "Expected no WARNING when nothing changed");
    }
}
