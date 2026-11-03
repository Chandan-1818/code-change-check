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
 *
 * <h2>H8 migration (GAP-14) - justified, not a workaround removal</h2>
 * Before H8, {@code --execute} ran Maven in whatever working tree was passed
 * as the repository path. The first test below passes a clone checked out at
 * <em>V8</em> (ebec3c0) while analysing the V4 -> V5 change, so the old
 * assertion "Full suite : 7 tests, 7 passed" was in fact measuring V8's test
 * tree, not the revision under analysis: exactly the stale-working-tree hazard
 * GAP-14 describes.
 *
 * H8 changed the contract: execution now happens in an isolated checkout of
 * the requested new revision (V5 here), so the numbers below are V5's real
 * suite. The V8 clone is still passed as the repository path on purpose - it
 * proves the analysis source and the executed revision are now decoupled, and
 * that the working-tree version can no longer decide the result. The
 * assertions were therefore updated to V5's actual suite (5 tests) and to the
 * new H8 summary labels, without weakening the original intent (7 -> 2 tests
 * has become 5 -> 2 tests; the reduction is still asserted exactly).
 */
public class SelectTestsCliExecuteTest {

    private static final String SAMPLE_PROJECT_PATH =
            com.analysis.tool.testsupport.SamplePaths.SAMPLE_PROJECT_PATH;

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
        assertTrue(output.contains("Target revision : " + VERSION_5_COMMIT),
                "The summary must name the revision that was actually executed. Actual output:\n" + output);
        assertTrue(output.contains("Full suite      : 5 tests, 5 passed, 0 failed, 0 errored, 0 skipped"),
                "Expected V5's full suite to report 5 passed, 0 failed, 0 errored, 0 skipped. Actual output:\n" + output);
        assertTrue(output.contains("Selected run    : 2 tests, 2 passed, 0 failed, 0 errored, 0 skipped"),
                "Expected the selected run to report 2 passed. Actual output:\n" + output);
        assertTrue(output.contains("Timed out       : no"),
                "A fast, healthy run must not report a timeout. Actual output:\n" + output);
        assertTrue(output.contains("Test reduction  : 60.0%"),
                "Expected exactly 60.0% test reduction (5 -> 2 tests). Actual output:\n" + output);
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
        assertFalse(output.contains("Full suite      :"),
                "Expected no full-suite execution line when execution is skipped. Actual output:\n" + output);
    }
}
