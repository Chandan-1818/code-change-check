package com.analysis.tool.experiment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests ExperimentCli.run directly (no separate JVM), checking each
 * documented exit code. Uses the real sample-project history with pinned
 * commit hashes (never HEAD-relative refs).
 */
public class ExperimentCliTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final String[] REVISIONS = {
            "f2493c2", "9ed64fa", "7584402", "a3d0163",
            "268a165", "cc0d769", "bae01d1", "ebec3c0"};

    @TempDir
    Path tempDir;

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

    private String[] argsWithRevisions(String... extra) {
        String[] args = new String[1 + REVISIONS.length + extra.length];
        args[0] = SAMPLE_PROJECT_PATH;
        System.arraycopy(REVISIONS, 0, args, 1, REVISIONS.length);
        System.arraycopy(extra, 0, args, 1 + REVISIONS.length, extra.length);
        return args;
    }

    @Test
    public void shouldPrintMarkdownTableForSampleHistory() {
        Result result = runCli(argsWithRevisions());

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("Revisions  : 8"),
                "Expected the revision count. Actual output:\n" + result.out);
        assertTrue(result.out.contains("a3d0163 -> 268a165"),
                "Expected the V4 -> V5 row. Actual output:\n" + result.out);
        assertTrue(result.out.contains("testCompute, testMultiply"),
                "Expected the V4 -> V5 selection. Actual output:\n" + result.out);
    }

    @Test
    public void shouldWriteCsvFileWhenRequested() throws Exception {
        Path csvFile = tempDir.resolve("results.csv");

        Result result = runCli(argsWithRevisions("--csv=" + csvFile));

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("CSV written to:"),
                "Expected a CSV confirmation line. Actual output:\n" + result.out);
        assertTrue(Files.exists(csvFile), "Expected the CSV file to exist");

        String csv = Files.readString(csvFile);
        assertTrue(csv.startsWith("oldRevision,newRevision,filePairs,selectedCount,"));
        assertEquals(8, csv.lines().count(), "1 header line + 7 data rows");
    }

    @Test
    public void shouldReturnExitCode1AndUsageWhenFewerThanTwoRevisions() {
        Result result = runCli(SAMPLE_PROJECT_PATH, "a3d0163");

        assertEquals(1, result.exitCode);
        assertTrue(result.err.contains("Usage"),
                "Expected a usage message on the error stream");
    }

    @Test
    public void shouldReturnExitCode2WhenRepositoryDoesNotExist() {
        Result result = runCli("D:/PROJECT/this-repository-does-not-exist", "a3d0163", "268a165");

        assertEquals(2, result.exitCode);
        assertTrue(result.err.contains("Experiment failed"),
                "Expected an 'Experiment failed' message on the error stream");
    }
}
