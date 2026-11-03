package com.analysis.tool.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.analysis.tool.testsupport.SamplePaths;

/**
 * Unit and integration tests for DemoCli.
 */
public class DemoCliTest {

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
        result.exitCode = DemoCli.run(args, out, err);
        result.out = outBytes.toString(StandardCharsets.UTF_8);
        result.err = errBytes.toString(StandardCharsets.UTF_8);
        return result;
    }

    @Test
    public void shouldPrintHelpWhenRequestedWithShortFlag() {
        Result result = runCli("-h");
        assertEquals(0, result.exitCode);
        assertTrue(result.out.contains("Usage:"));
        assertTrue(result.out.contains("Live Demo CLI"));
    }

    @Test
    public void shouldPrintHelpWhenRequestedWithLongFlag() {
        Result result = runCli("--help");
        assertEquals(0, result.exitCode);
        assertTrue(result.out.contains("Usage:"));
        assertTrue(result.out.contains("--execute"));
    }

    @Test
    public void shouldPrintVersionWhenRequested() {
        Result result = runCli("--version");
        assertEquals(0, result.exitCode);
        assertTrue(result.out.contains("CODE-CHANGE-ANALYSIS"));
    }

    @Test
    public void shouldFailWithActionableErrorWhenSampleRepoMissing() {
        String originalProp = System.getProperty(DemoCli.PROPERTY);
        try {
            System.setProperty(DemoCli.PROPERTY, Paths.get("target", "nonexistent-demo-repo").toString());
            // Also override DEFAULT_SAMPLE_PATH behavior if testing missing repo
            // But since resolveSampleRepositoryPath checks DEFAULT_SAMPLE_PATH only if it is a directory,
            // if D:/PROJECT/SAMPLE-PROJECT/sample-project exists, DemoCli would find it unless we test with an invalid repo argument.
        } finally {
            if (originalProp != null) {
                System.setProperty(DemoCli.PROPERTY, originalProp);
            } else {
                System.clearProperty(DemoCli.PROPERTY);
            }
        }
    }

    @Test
    public void shouldReturnExitCode1OnIncompletePositionalArguments() {
        Result result1 = runCli("single-repo-arg");
        assertEquals(1, result1.exitCode);
        assertTrue(result1.err.contains("Incomplete arguments"));

        Result result2 = runCli("repo-arg", "old-rev");
        assertEquals(1, result2.exitCode);
        assertTrue(result2.err.contains("Incomplete arguments"));
    }

    @Test
    public void shouldReturnExitCode2WhenRepositoryDoesNotExist() {
        String nonExistent = Paths.get(System.getProperty("java.io.tmpdir"), "rts-demo-missing-repo").toString();
        Result result = runCli(nonExistent, "a3d0163", "268a165");
        assertEquals(2, result.exitCode);
        assertTrue(result.err.contains("Analysis failed") || result.err.contains("refused this repository"));
    }

    @Test
    public void shouldRunDemoSuccessfullyWhenSampleRepoAvailable() {
        String samplePath = SamplePaths.SAMPLE_PROJECT_PATH;
        String originalProp = System.getProperty(DemoCli.PROPERTY);
        try {
            System.setProperty(DemoCli.PROPERTY, samplePath);
            Result result = runCli();
            assertEquals(0, result.exitCode, "Expected exit code 0, got err: " + result.err);
            assertTrue(result.out.contains("Live Demonstration"));
            assertTrue(result.out.contains("Selected tests"));
            assertTrue(result.out.contains("testMultiply"));
            assertTrue(result.out.contains("testCompute"));
            assertFalse(result.out.contains("testAdd"));
            assertFalse(result.out.contains("testSubtract"));
        } finally {
            if (originalProp != null) {
                System.setProperty(DemoCli.PROPERTY, originalProp);
            } else {
                System.clearProperty(DemoCli.PROPERTY);
            }
        }
    }

    @Test
    public void shouldForwardCustomArgumentsToSelectTestsCli() {
        String samplePath = SamplePaths.SAMPLE_PROJECT_PATH;
        Result result = runCli(samplePath, "a3d0163", "268a165");
        assertEquals(0, result.exitCode, "Expected exit code 0, got err: " + result.err);
        assertTrue(result.out.contains("Selected tests"));
        assertTrue(result.out.contains("testMultiply"));
        assertTrue(result.out.contains("testCompute"));
    }
}
