package com.analysis.tool.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests SelectTestsCli.run when no sourceFile=testFile pairs are given, so
 * the pairs are auto-discovered from the files present at either revision.
 *
 * Uses pinned sample-project commits (never HEAD-relative refs):
 *   V4 = a3d0163, V5 = 268a165, V6 = cc0d769, V7 = bae01d1, V8 = ebec3c0
 */
public class SelectTestsCliAutoDiscoverTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final String CALCULATOR_PAIR =
            "src/main/java/com/sample/Calculator.java=src/test/java/com/sample/CalculatorTest.java";
    private static final String MULTIPLIER_PAIR =
            "src/main/java/com/sample/Multiplier.java=src/test/java/com/sample/MultiplierTest.java";

    private static final String VERSION_4_COMMIT = "a3d0163";
    private static final String VERSION_5_COMMIT = "268a165";
    private static final String VERSION_6_COMMIT = "cc0d769";
    private static final String VERSION_7_COMMIT = "bae01d1";
    private static final String VERSION_8_COMMIT = "ebec3c0";

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
    public void shouldAutoDiscoverPairsAndSelectTransitiveTestsForV4ToV5() {
        Result result = runCli(SAMPLE_PROJECT_PATH, VERSION_4_COMMIT, VERSION_5_COMMIT);

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("File pairs     : 3 (auto-discovered)"),
                "Expected 3 auto-discovered pairs (App, Calculator, Multiplier). Actual output:\n" + result.out);
        assertTrue(result.out.contains("testCompute"),
                "Expected testCompute (selected transitively)");
        assertTrue(result.out.contains("testMultiply"),
                "Expected testMultiply (selected directly)");
        assertFalse(result.out.contains("testAdd"),
                "Expected testAdd NOT to be listed");
        assertFalse(result.out.contains("testSubtract"),
                "Expected testSubtract NOT to be listed");
    }

    @Test
    public void shouldStillReportUntestedMethodWhenPairsAreAutoDiscovered() {
        Result result = runCli(SAMPLE_PROJECT_PATH, VERSION_5_COMMIT, VERSION_6_COMMIT);

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("(auto-discovered)"),
                "Expected the auto-discovered marker");
        assertTrue(result.out.contains("WARNING"),
                "Expected the untested-method WARNING section. Actual output:\n" + result.out);
        assertTrue(result.out.contains("negate"),
                "Expected negate to be reported as untested");
    }

    @Test
    public void shouldAutoDiscoverOrderChainForV7ToV8() {
        Result result = runCli(SAMPLE_PROJECT_PATH, VERSION_7_COMMIT, VERSION_8_COMMIT);

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("File pairs     : 5 (auto-discovered)"),
                "Expected 5 auto-discovered pairs. Actual output:\n" + result.out);
        assertTrue(result.out.contains("testSaveAndFindTotal"),
                "Expected testSaveAndFindTotal (direct)");
        assertTrue(result.out.contains("testPlaceOrder"),
                "Expected testPlaceOrder (transitive via placeOrder -> save)");
        assertFalse(result.out.contains("testCompute"),
                "Expected Calculator tests NOT to be listed");
        assertFalse(result.out.contains("testMultiply"),
                "Expected Multiplier tests NOT to be listed");
    }

    @Test
    public void shouldNotAutoDiscoverWhenExplicitPairsAreGiven() {
        Result result = runCli(SAMPLE_PROJECT_PATH, VERSION_4_COMMIT, VERSION_5_COMMIT,
                CALCULATOR_PAIR, MULTIPLIER_PAIR);

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("File pairs     : 2"),
                "Expected exactly the 2 explicit pairs. Actual output:\n" + result.out);
        assertFalse(result.out.contains("auto-discovered"),
                "Explicit pairs must win: no auto-discovered marker expected");
    }
}
