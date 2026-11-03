package com.analysis.tool.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3: a generated test that is selected for execution must really be copied
 * into the isolated target-revision checkout and run by Maven, and it must be
 * reported as executed only when Maven's Surefire reports confirm it ran.
 *
 * These tests launch real Maven builds against the sample project
 * (V4 = a3d0163 -&gt; V5 = 268a165), so they are slow. The sample repository is
 * only ever read: analysis and execution happen against a temporary clone
 * (H8/GAP-14), and both tests assert the original working tree is left
 * byte-for-byte unchanged.
 */
class SelectTestsCliGeneratedExecuteTest {

    private static final String SAMPLE_PROJECT_PATH =
            com.analysis.tool.testsupport.SamplePaths.SAMPLE_PROJECT_PATH;

    private static final String VERSION_4_COMMIT = "a3d0163";
    private static final String VERSION_5_COMMIT = "268a165";

    /** Calls the changed method (so it is selected) and compiles cleanly. */
    private static final String GOOD_GENERATED =
            "package rts.generated;\n"
          + "import org.junit.jupiter.api.Test;\n"
          + "import static org.junit.jupiter.api.Assertions.assertEquals;\n"
          + "import com.sample.Multiplier;\n"
          + "public class MultiplierGeneratedTest {\n"
          + "    @Test\n"
          + "    public void gen_multiply() {\n"
          + "        assertEquals(6, new Multiplier().multiply(2, 3));\n"
          + "    }\n"
          + "}\n";

    /**
     * Calls the changed method (so it is selected) but references a type that
     * does not exist, so javac refuses the build. This is the case where the
     * tool must never claim the generated test ran.
     */
    private static final String BROKEN_GENERATED =
            "package rts.generated;\n"
          + "import org.junit.jupiter.api.Test;\n"
          + "import com.sample.Multiplier;\n"
          + "public class BrokenGeneratedTest {\n"
          + "    @Test\n"
          + "    public void gen_multiply() {\n"
          + "        Multiplier multiplier = new Multiplier();\n"
          + "        multiplier.multiply(2, 3);\n"
          + "        MissingType doesNotCompile = null;\n"
          + "    }\n"
          + "}\n";

    @TempDir
    Path generatedRoot;

    private static final class Result {
        int exitCode;
        String out;
        String err;
    }

    private Result runCli(String... args) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        Result r = new Result();
        r.exitCode = SelectTestsCli.run(args,
                new PrintStream(o, true, StandardCharsets.UTF_8),
                new PrintStream(e, true, StandardCharsets.UTF_8));
        r.out = o.toString(StandardCharsets.UTF_8);
        r.err = e.toString(StandardCharsets.UTF_8);
        return r;
    }

    private Path writeGenerated(String fileName, String source) throws Exception {
        Path dir = generatedRoot.resolve("src/test/java/rts/generated");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), source, StandardCharsets.UTF_8);
        return generatedRoot;
    }

    @Test
    void shouldExecuteSelectedGeneratedTestsInTheIsolatedCheckout() throws Exception {
        Path generated = writeGenerated("MultiplierGeneratedTest.java", GOOD_GENERATED);
        Set<String> before = listSampleFiles();
        try {
            Result result = runCli("--repo=" + SAMPLE_PROJECT_PATH,
                    "--base=" + VERSION_4_COMMIT,
                    "--target=" + VERSION_5_COMMIT,
                    "--generated=" + generated,
                    "--execute");

            assertEquals(0, result.exitCode, result.err);
            assertTrue(result.out.contains(
                            "Included 1 generated test file(s) in the isolated checkout (original repository untouched)."),
                    "The generated test must be copied into the isolated checkout. Actual output:\n" + result.out);
            assertTrue(result.out.contains("MultiplierGeneratedTest#gen_multiply [generated]"),
                    "The selected generated test must carry the [generated] marker. Actual output:\n" + result.out);
            assertTrue(result.out.contains("Generated tests selected for execution (1):"), result.out);
            assertTrue(result.out.contains(
                            "MultiplierGeneratedTest#gen_multiply : EXECUTED (confirmed by Maven Surefire)"),
                    "A generated test that Maven really ran must be reported as executed. Actual output:\n" + result.out);
            assertTrue(result.out.contains("Generated tests executed: 1 / 1 confirmed by Maven"), result.out);
            assertTrue(result.out.contains("Execution        : ran successfully (1/1 generated confirmed)"),
                    "Final report must distinguish the confirmed generated tests. Actual output:\n" + result.out);
            assertTrue(result.out.contains("Selected tests   : 3 (2 existing, 1 generated)"),
                    "Final report must split existing from generated tests. Actual output:\n" + result.out);
        } finally {
            assertEquals(before, listSampleFiles(),
                    "The original sample repository must not be modified by execution");
        }
    }

    @Test
    void shouldNotReportGeneratedTestsAsExecutedWhenMavenCannotRunThem() throws Exception {
        Path generated = writeGenerated("BrokenGeneratedTest.java", BROKEN_GENERATED);
        Set<String> before = listSampleFiles();
        try {
            Result result = runCli("--repo=" + SAMPLE_PROJECT_PATH,
                    "--base=" + VERSION_4_COMMIT,
                    "--target=" + VERSION_5_COMMIT,
                    "--generated=" + generated,
                    "--execute");

            assertEquals(0, result.exitCode, result.err);
            assertTrue(result.out.contains("Generated tests selected for execution (1):"), result.out);
            assertTrue(result.out.contains(
                            "BrokenGeneratedTest#gen_multiply : NOT confirmed by Maven "
                                    + "(absent from Surefire results; not reported as executed)"),
                    "A generated test Maven could not run must never be reported as executed. Actual output:\n"
                            + result.out);
            assertTrue(result.out.contains("Generated tests executed: 0 / 1 confirmed by Maven"),
                    "No generated test was confirmed, so the count must be zero. Actual output:\n" + result.out);
            assertTrue(result.out.contains("Execution had failures; logs preserved at:"),
                    "A failed execution must preserve its logs. Actual output:\n" + result.out);
            assertTrue(result.out.contains("Execution        : ran with failures (0/1 generated confirmed)"),
                    result.out);
        } finally {
            assertEquals(before, listSampleFiles(),
                    "The original sample repository must not be modified by execution");
        }
    }

    /** Every path under the sample repository (excluding .git), for change detection. */
    private static Set<String> listSampleFiles() throws Exception {
        Path root = java.nio.file.Paths.get(SAMPLE_PROJECT_PATH);
        try (Stream<Path> walk = Files.walk(root)) {
            List<String> paths = walk
                    .filter(path -> !path.toString().contains(".git"))
                    .filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .collect(Collectors.toList());
            return new TreeSet<>(paths);
        }
    }
}
