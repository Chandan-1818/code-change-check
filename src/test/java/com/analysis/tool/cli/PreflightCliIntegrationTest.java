package com.analysis.tool.cli;

import com.analysis.tool.preflight.Preflight;
import com.analysis.tool.preflight.PreflightResult;
import com.analysis.tool.testsupport.SamplePaths;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H9: integration of the preflight check into {@code SelectTestsCli}. An
 * unsupported repository is refused before analysis (exit code 2, actionable
 * reason); a repository that can be analysed but is less reliable prints a
 * REDUCED preflight section; a fully supported repository prints neither.
 */
class PreflightCliIntegrationTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");
    private static final String SRC = "src/main/java/demo/Calculator.java";
    private static final String TEST = "src/test/java/demo/CalculatorTest.java";
    private static final String CALCULATOR_PAIR = SRC + "=" + TEST;

    private static final String JUNIT5_TEST =
            "package demo;\nimport org.junit.jupiter.api.Test;\n"
                    + "public class CalculatorTest { @Test public void testAdd() { new Calculator().add(1, 2); } }\n";

    @TempDir
    Path repoDir;

    private static class Result {
        int exitCode;
        String out;
        String err;
    }

    private Result runCli(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        Result result = new Result();
        result.exitCode = SelectTestsCli.run(args,
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));
        result.out = outBytes.toString(StandardCharsets.UTF_8);
        result.err = errBytes.toString(StandardCharsets.UTF_8);
        return result;
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = repoDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String commit(Git git, String message) throws Exception {
        return git.commit().setMessage(message).setAuthor(IDENT).setCommitter(IDENT)
                .setSign(false).call().getName();
    }

    @Test
    void nonGitDirectoryIsRefusedBeforeAnalysis() throws Exception {
        write(SRC, "package demo;\npublic class Calculator { public int add(int a, int b) { return a + b; } }\n");

        Result result = runCli(repoDir.toString(), "HEAD", "HEAD", CALCULATOR_PAIR);

        assertEquals(2, result.exitCode, result.err);
        assertTrue(result.err.contains("Analysis failed"), result.err);
        assertTrue(result.err.contains("not a Git repository"), result.err);
    }

    @Test
    void multiModuleRepositoryIsRefusedWithActionableReason() throws Exception {
        Files.createDirectories(repoDir.resolve(".git"));
        write("pom.xml", "<project><modules><module>a</module></modules></project>");

        Result result = runCli(repoDir.toString(), "HEAD", "HEAD");

        assertEquals(2, result.exitCode, result.err);
        assertTrue(result.err.contains("Analysis failed"), result.err);
        assertTrue(result.err.contains("multi-module"), result.err);
    }

    @Test
    void junit4RepositoryIsRefusedWithActionableReason() throws Exception {
        Files.createDirectories(repoDir.resolve(".git"));
        write("pom.xml", "<project><dependencies><dependency><groupId>junit</groupId>"
                + "<artifactId>junit</artifactId><version>4.13.2</version></dependency></dependencies></project>");

        Result result = runCli(repoDir.toString(), "HEAD", "HEAD");

        assertEquals(2, result.exitCode, result.err);
        assertTrue(result.err.contains("JUnit 4"), result.err);
    }

    @Test
    void supportedRepositoryRunsWithoutAnyPreflightNotice() throws Exception {
        String oldCommit;
        String newCommit;
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(SRC, "package demo;\npublic class Calculator { public int add(int a, int b) { return a + b; } }\n");
            write(TEST, JUNIT5_TEST);
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1");
            write(SRC, "package demo;\npublic class Calculator { public int add(int a, int b) { int r = a + b; return r; } }\n");
            git.add().addFilepattern(SRC).call();
            newCommit = commit(git, "V2");
        }

        Result result = runCli(repoDir.toString(), oldCommit, newCommit, CALCULATOR_PAIR);

        assertEquals(0, result.exitCode, result.err);
        assertFalse(result.out.contains("Preflight"), result.out);
    }

    @Test
    void repositoryWithAReductionTriggerRunsAndPrintsReducedPreflightNotice() throws Exception {
        String oldCommit;
        String newCommit;
        String springSource = "package demo;\nimport org.springframework.stereotype.Component;\n"
                + "@Component public class Calculator { public int add(int a, int b) { return a + b; } }\n";
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(SRC, springSource);
            write(TEST, JUNIT5_TEST);
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1");
            write(SRC, springSource.replace("return a + b;", "int r = a + b; return r;"));
            git.add().addFilepattern(SRC).call();
            newCommit = commit(git, "V2");
        }

        Result result = runCli(repoDir.toString(), oldCommit, newCommit, CALCULATOR_PAIR);

        assertEquals(0, result.exitCode, result.err);
        assertTrue(result.out.contains("Preflight      : REDUCED confidence"), result.out);
        assertTrue(result.out.contains("Spring indicators"), result.out);
    }

    @Test
    void sampleProjectIsClassifiedSupported() {
        PreflightResult result = Preflight.validate(SamplePaths.sampleProject());

        assertEquals(PreflightResult.Status.SUPPORTED, result.getStatus(), result.toString());
    }
}
