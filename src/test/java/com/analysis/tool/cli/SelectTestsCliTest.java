package com.analysis.tool.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests SelectTestsCli.run directly (no separate JVM), capturing its
 * output streams, and checks each documented exit code:
 * 0 = success, 1 = invalid arguments, 2 = analysis failed.
 *
 * Uses pinned sample-project commits (V4 = a3d0163, V5 = 268a165).
 */
public class SelectTestsCliTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

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
    public void shouldReturnExitCode1AndUsageWhenTooFewArguments() {
        Result result = runCli(SAMPLE_PROJECT_PATH, VERSION_4_COMMIT);

        assertEquals(1, result.exitCode);
        assertTrue(result.err.contains("Usage"),
                "Expected a usage message on the error stream");
    }

    @Test
    public void shouldReturnExitCode1WhenFilePairIsMalformed() {
        Result result = runCli(SAMPLE_PROJECT_PATH, VERSION_4_COMMIT, VERSION_5_COMMIT,
                "src/main/java/com/sample/Calculator.java");

        assertEquals(1, result.exitCode);
        assertTrue(result.err.contains("Invalid file pair"),
                "Expected an 'Invalid file pair' message on the error stream");
    }

    @Test
    public void shouldReturnExitCode0AndListSelectedTestsForRealCommitRange() {
        Result result = runCli(SAMPLE_PROJECT_PATH, VERSION_4_COMMIT, VERSION_5_COMMIT,
                CALCULATOR_PAIR, MULTIPLIER_PAIR);

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("testCompute"),
                "Expected testCompute to be listed (selected transitively)");
        assertTrue(result.out.contains("testMultiply"),
                "Expected testMultiply to be listed (selected directly)");
        assertFalse(result.out.contains("testAdd"),
                "Expected testAdd NOT to be listed");
        assertFalse(result.out.contains("testSubtract"),
                "Expected testSubtract NOT to be listed");
    }

    @Test
    public void shouldReturnExitCode2WhenRepositoryDoesNotExist() {
        Result result = runCli("D:/PROJECT/this-repository-does-not-exist",
                VERSION_4_COMMIT, VERSION_5_COMMIT, CALCULATOR_PAIR);

        assertEquals(2, result.exitCode);
        assertTrue(result.err.contains("Analysis failed"),
                "Expected an 'Analysis failed' message on the error stream");
    }
}
