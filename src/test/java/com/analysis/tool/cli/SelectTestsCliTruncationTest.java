package com.analysis.tool.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the H6 CLI depth option and truncation reporting:
 * --max-depth=<n|-1> configures the impact traversal depth (default -1 =
 * unlimited), invalid values (0, other negatives, non-integers) are rejected
 * with exit 1, and a finite limit that excludes reachable methods prints an
 * explicit truncation warning so a partial selection is never presented as
 * complete.
 *
 * Uses pinned sample-project commits (V4 = a3d0163, V5 = 268a165).
 */
public class SelectTestsCliTruncationTest {

    private static final String SAMPLE_PROJECT_PATH =
            com.analysis.tool.testsupport.SamplePaths.SAMPLE_PROJECT_PATH;

    private static final String CALCULATOR_PAIR =
            "src/main/java/com/sample/Calculator.java=src/test/java/com/sample/CalculatorTest.java";
    private static final String MULTIPLIER_PAIR =
            "src/main/java/com/sample/Multiplier.java=src/test/java/com/sample/MultiplierTest.java";

    private static final String VERSION_4_COMMIT = "a3d0163";
    private static final String VERSION_5_COMMIT = "268a165";

    private static class Result {
        int exitCode;
        String out;
        String err;
    }

    private Result runCli(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        Result result = new Result();
        result.exitCode = SelectTestsCli.run(args, out, err);
        result.out = outBytes.toString(StandardCharsets.UTF_8);
        result.err = errBytes.toString(StandardCharsets.UTF_8);
        return result;
    }

    @Test
    public void shouldWarnWhenFiniteDepthLimitExcludesReachableTests() {
        Result result = runCli(SAMPLE_PROJECT_PATH, VERSION_4_COMMIT, VERSION_5_COMMIT,
                CALCULATOR_PAIR, MULTIPLIER_PAIR, "--max-depth=1");

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("impact analysis truncated at max depth 1"),
                "Expected an explicit truncation warning; output was: " + result.out);
        assertTrue(result.out.contains("Max depth      : 1"),
                "Expected the configured depth to be reported");
    }

    @Test
    public void shouldNotWarnAboutTruncationWithDefaultUnlimitedDepth() {
        Result result = runCli(SAMPLE_PROJECT_PATH, VERSION_4_COMMIT, VERSION_5_COMMIT,
                CALCULATOR_PAIR, MULTIPLIER_PAIR);

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("Max depth      : unlimited"),
                "Default depth must be reported as unlimited");
        assertFalse(result.out.contains("truncated"),
                "Unlimited traversal must never print a truncation warning; output was: " + result.out);
        assertTrue(result.out.contains("testCompute"),
                "Unlimited traversal must still select testCompute transitively");
    }

    @Test
    public void shouldRejectZeroDepthWithExitCode1() {
        Result result = runCli(SAMPLE_PROJECT_PATH, VERSION_4_COMMIT, VERSION_5_COMMIT,
                CALCULATOR_PAIR, "--max-depth=0");

        assertEquals(1, result.exitCode);
        assertTrue(result.err.contains("Invalid --max-depth"),
                "Zero must be rejected as an invalid configuration");
    }

    @Test
    public void shouldRejectNonIntegerDepthWithExitCode1() {
        Result result = runCli(SAMPLE_PROJECT_PATH, VERSION_4_COMMIT, VERSION_5_COMMIT,
                CALCULATOR_PAIR, "--max-depth=abc");

        assertEquals(1, result.exitCode);
        assertTrue(result.err.contains("Invalid --max-depth"),
                "A non-integer depth must be rejected");
    }
}
