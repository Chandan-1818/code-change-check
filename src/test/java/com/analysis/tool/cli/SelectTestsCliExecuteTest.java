package com.analysis.tool.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.analysis.tool.testsupport.PinnedSampleCheckout;

import java.nio.file.Path;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the CLI's optional --execute flag: real Maven subprocesses are
 * launched (full suite and selected tests) via TestRunner, so these are
 * noticeably slower than the rest of the CLI tests.
 *
 * Only reliable values are asserted (counts, build success, the exact
 * test-reduction percentage). Time reduction is deliberately not
 * asserted on either sign - see EvaluationMetricsIntegrationTest for why
 * this is unreliable on a suite this small.
 *
 * Uses pinned sample-project commits V4 = a3d0163, V5 = 268a165.
 */
public class SelectTestsCliExecuteTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final String VERSION_8_COMMIT = "ebec3c0";

    @TempDir
    Path tempDir;

    private static final String CALCULATOR_PAIR =
            "src/main/java/com/sample/Calculator.java=src/test/java/com/sample/CalculatorTest.java";
    private static final String MULTIPLIER_PAIR =
            "src/main/java/com/sample/Multiplier.java=src/test/java/com/sample/MultiplierTest.java";

    private static final String VERSION_4_COMMIT = "a3d0163";
    private static final String VERSION_5_COMMIT = "268a165";

    @Test
    public void shouldExecuteAndReportTestReduction() {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        int exitCode = SelectTestsCli.run(
                new String[] {PinnedSampleCheckout.cloneAt(SAMPLE_PROJECT_PATH, VERSION_8_COMMIT, tempDir),
                        VERSION_4_COMMIT, VERSION_5_COMMIT,
                        CALCULATOR_PAIR, MULTIPLIER_PAIR, "--execute"},
                out, err);

        String output = outBytes.toString(StandardCharsets.UTF_8);

        assertEquals(0, exitCode, "Unexpected error output: " + errBytes.toString(StandardCharsets.UTF_8));
        assertTrue(output.contains("Full suite     : 7 tests, 7 passed, 0 failed"),
                "Expected the full suite to report 7 passed, 0 failed. Actual output:\n" + output);
        assertTrue(output.contains("Selected tests : 2 tests, 2 passed, 0 failed"),
                "Expected the selected run to report 2 passed, 0 failed. Actual output:\n" + output);
        assertTrue(output.contains("Test reduction : 71.4%"),
                "Expected exactly 71.4% test reduction. Actual output:\n" + output);
        assertTrue(output.contains("build succeeded"),
                "Expected at least one 'build succeeded' line. Actual output:\n" + output);
    }

    @Test
    public void shouldSkipExecutionWhenNoTestsAreSelected() {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        int exitCode = SelectTestsCli.run(
                new String[] {SAMPLE_PROJECT_PATH, VERSION_5_COMMIT, VERSION_5_COMMIT,
                        CALCULATOR_PAIR, "--execute"},
                out, err);

        String output = outBytes.toString(StandardCharsets.UTF_8);

        assertEquals(0, exitCode, "Unexpected error output: " + errBytes.toString(StandardCharsets.UTF_8));
        assertTrue(output.contains("Execution skipped"),
                "Expected an execution-skipped message when nothing was selected. Actual output:\n" + output);
        assertFalse(output.contains("Full suite     :"),
                "Expected no full-suite execution line when execution is skipped. Actual output:\n" + output);
    }
}
