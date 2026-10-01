package com.analysis.tool.cli;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 12: auto-discovery uses the union of files at the old and new
 * revisions. These tests use throwaway repos in a temp directory (no
 * dependence on the sample project) to cover the two cases the sample
 * history cannot: a source that exists only at the old revision (deleted)
 * and a source that exists only at the new revision (added).
 */
class SelectTestsCliAutoDiscoverTempRepoTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    private static final String CALC_SRC = "src/main/java/demo/Calculator.java";
    private static final String CALC_TEST = "src/test/java/demo/CalculatorTest.java";
    private static final String GREETER_SRC = "src/main/java/demo/Greeter.java";

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
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        Result result = new Result();
        result.exitCode = SelectTestsCli.run(args, out, err);
        result.out = outBytes.toString(StandardCharsets.UTF_8);
        result.err = errBytes.toString(StandardCharsets.UTF_8);
        return result;
    }

    @Test
    void sourceDeletedAtNewRevision_isStillDiscoveredAndItsMethodReportedRemoved() throws Exception {
        String oldCommit;
        String newCommit;

        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(CALC_SRC,
                    "package demo;\n"
                  + "public class Calculator {\n"
                  + "    public int add(int a, int b) { return a + b; }\n"
                  + "}\n");
            write(CALC_TEST,
                    "package demo;\n"
                  + "import org.junit.jupiter.api.Test;\n"
                  + "public class CalculatorTest {\n"
                  + "    @Test\n"
                  + "    public void testAdd() { new Calculator().add(1, 2); }\n"
                  + "}\n");
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1: Calculator + test");

            Files.delete(repoDir.resolve(CALC_SRC));
            git.rm().addFilepattern(CALC_SRC).call();
            newCommit = commit(git, "V2: delete Calculator");
        }

        Result result = runCli(repoDir.toString(), oldCommit, newCommit);

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("File pairs     : 1 (auto-discovered)"),
                "Expected the deleted source to be discovered from the old revision. Actual output:\n" + result.out);
        assertTrue(result.out.contains("Removed methods (1):"),
                "Expected a Removed methods section. Actual output:\n" + result.out);
        assertTrue(result.out.contains("  - add"),
                "Expected add to be listed as removed. Actual output:\n" + result.out);
    }

    @Test
    void sourceAddedAtNewRevision_isDiscoveredAndItsMethodReportedUntested() throws Exception {
        String oldCommit;
        String newCommit;

        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(CALC_SRC,
                    "package demo;\n"
                  + "public class Calculator {\n"
                  + "    public int add(int a, int b) { return a + b; }\n"
                  + "}\n");
            write(CALC_TEST,
                    "package demo;\n"
                  + "import org.junit.jupiter.api.Test;\n"
                  + "public class CalculatorTest {\n"
                  + "    @Test\n"
                  + "    public void testAdd() { new Calculator().add(1, 2); }\n"
                  + "}\n");
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1: Calculator + test");

            write(GREETER_SRC,
                    "package demo;\n"
                  + "public class Greeter {\n"
                  + "    public String greet(String name) { return \"Hello \" + name; }\n"
                  + "}\n");
            git.add().addFilepattern(GREETER_SRC).call();
            newCommit = commit(git, "V2: add Greeter with no test");
        }

        Result result = runCli(repoDir.toString(), oldCommit, newCommit);

        assertEquals(0, result.exitCode, "Unexpected error output: " + result.err);
        assertTrue(result.out.contains("File pairs     : 2 (auto-discovered)"),
                "Expected Calculator and the newly added Greeter. Actual output:\n" + result.out);
        assertTrue(result.out.contains("WARNING"),
                "Expected the untested-method WARNING. Actual output:\n" + result.out);
        assertTrue(result.out.contains("  - greet"),
                "Expected greet to be reported as untested. Actual output:\n" + result.out);
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = repoDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String commit(Git git, String message) throws Exception {
        return git.commit()
                .setMessage(message)
                .setAuthor(IDENT)
                .setCommitter(IDENT)
                .setSign(false)
                .call()
                .getName();
    }
}
