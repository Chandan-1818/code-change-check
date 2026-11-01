package com.analysis.tool.experiment;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests ExperimentCli.run with --execute: real Maven runs in a temporary
 * checkout of each revision. Uses pinned sample-project commits (never
 * HEAD-relative refs): V4 = a3d0163, V5 = 268a165, V6 = cc0d769.
 */
public class ExperimentCliExecuteTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

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
        result.exitCode = ExperimentCli.run(args, out, err);
        result.out = outBytes.toString(StandardCharsets.UTF_8);
        result.err = errBytes.toString(StandardCharsets.UTF_8);
        return result;
    }

    @Test
    public void shouldPrintExecutedTableWithReductionAtEachRevision() {
        Result result = runCli(SAMPLE_PROJECT_PATH, "a3d0163", "268a165", "cc0d769", "--execute");

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("Mode       : executed"),
                "Expected the executed-mode line. Actual output:\n" + result.out);
        assertTrue(result.out.contains("a3d0163 -> 268a165 | testCompute, testMultiply | 5 (5 passed) | 2 (2 passed) | 60.0% | OK"),
                "Expected the V4 -> V5 row with 60.0% at V5. Actual output:\n" + result.out);
        assertTrue(result.out.contains("268a165 -> cc0d769 | - | - | - | - | skipped (nothing selected)"),
                "Expected V5 -> V6 to be skipped. Actual output:\n" + result.out);
    }

    @Test
    public void shouldRejectCsvTogetherWithExecute() {
        Result result = runCli(SAMPLE_PROJECT_PATH, "a3d0163", "268a165",
                "--execute", "--csv=results.csv");

        assertEquals(1, result.exitCode);
        assertTrue(result.err.contains("--csv is not supported together with --execute"),
                "Expected an explanatory message. Actual error output:\n" + result.err);
    }
}
