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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** --generated option: throwaway repo, no sample project, no JVM launched. */
class SelectTestsCliGeneratedTest {

    private static final String SRC = "src/main/java/demo/Calculator.java";
    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    private static final String GENERATED =
            "package rts.generated;\n"
          + "import static org.junit.jupiter.api.Assertions.*;\n"
          + "import org.junit.jupiter.api.Test;\n"
          + "import demo.*;\n"
          + "public class CalculatorGeneratedTest {\n"
          + "    @Test\n"
          + "    public void gen_Calculator_add_01() throws Throwable {\n"
          + "        assertEquals(3, new Calculator().add(1, 2));\n"
          + "    }\n"
          + "}\n";

    @TempDir
    Path repoDir;
    @TempDir
    Path generatedRoot;

    private String oldCommit;
    private String newCommit;

    private static class Result {
        int exitCode;
        String out;
        String err;
    }

    private Result runCli(String... args) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        Result r = new Result();
        r.exitCode = SelectTestsCli.run(args,
                new PrintStream(o, true, StandardCharsets.UTF_8), new PrintStream(e, true, StandardCharsets.UTF_8));
        r.out = o.toString(StandardCharsets.UTF_8);
        r.err = e.toString(StandardCharsets.UTF_8);
        return r;
    }

    private void buildHistory() throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(repoDir.resolve(SRC), "package demo;\npublic class Calculator {\n"
                    + "    public int add(int a, int b) { return a + b; }\n}\n");
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1");

            write(repoDir.resolve(SRC), "package demo;\npublic class Calculator {\n"
                    + "    public int add(int a, int b) { int r = a + b; return r; }\n}\n");
            git.add().addFilepattern(SRC).call();
            newCommit = commit(git, "V2");
        }
    }

    @Test
    void withoutGeneratedOptionTheMethodIsUntested() throws Exception {
        buildHistory();
        Result r = runCli(repoDir.toString(), oldCommit, newCommit);

        assertEquals(0, r.exitCode, r.err);
        assertTrue(r.out.contains("WARNING"), r.out);
    }

    @Test
    void generatedTestsCoverTheMethodSoThereIsNoWarning() throws Exception {
        buildHistory();
        write(generatedRoot.resolve("src/test/java/rts/generated/CalculatorGeneratedTest.java"), GENERATED);

        Result r = runCli(repoDir.toString(), oldCommit, newCommit, "--generated=" + generatedRoot);

        assertEquals(0, r.exitCode, r.err);
        assertTrue(r.out.contains("Generated tests: 1 file(s)"), r.out);
        assertTrue(r.out.contains("gen_Calculator_add_01"), r.out);
        assertFalse(r.out.contains("WARNING"), r.out);
    }

    @Test
    void missingOrEmptyGeneratedDirectoryIsAnInvalidArgument() throws Exception {
        buildHistory();

        Result missing = runCli(repoDir.toString(), oldCommit, newCommit, "--generated=" + generatedRoot.resolve("nope"));
        assertEquals(1, missing.exitCode);
        assertTrue(missing.err.contains("Invalid --generated"), missing.err);

        Files.createDirectories(generatedRoot.resolve("src/test/java/rts/generated"));
        Result empty = runCli(repoDir.toString(), oldCommit, newCommit, "--generated=" + generatedRoot);
        assertEquals(1, empty.exitCode);
        assertTrue(empty.err.contains("no .java files"), empty.err);
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String commit(Git git, String message) throws Exception {
        return git.commit().setMessage(message).setAuthor(IDENT).setCommitter(IDENT)
                .setSign(false).call().getName();
    }
}
