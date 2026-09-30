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

/**
 * Phase 9: the CLI must list methods removed between two revisions,
 * without calling them a WARNING and without changing the selection.
 * Uses a throwaway repo in a temp directory (independent of the sample project).
 */
public class SelectTestsCliRemovedTest {

    private static final String SRC = "src/main/java/demo/Calculator.java";
    private static final String TEST = "src/test/java/demo/CalculatorTest.java";
    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    @TempDir
    Path repoDir;

    @Test
    public void shouldListRemovedMethodWithoutWarningOrSelection() throws Exception {
        String oldCommit;
        String newCommit;

        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(SRC,
                    "package demo;\n"
                  + "public class Calculator {\n"
                  + "    public int add(int a, int b) { return a + b; }\n"
                  + "    public int negate(int a) { return -a; }\n"
                  + "}\n");
            write(TEST,
                    "package demo;\n"
                  + "import org.junit.jupiter.api.Test;\n"
                  + "public class CalculatorTest {\n"
                  + "    @Test\n"
                  + "    public void testAdd() { new Calculator().add(1, 2); }\n"
                  + "}\n");
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1: add + negate + test for add");

            write(SRC,
                    "package demo;\n"
                  + "public class Calculator {\n"
                  + "    public int add(int a, int b) { return a + b; }\n"
                  + "}\n");
            git.add().addFilepattern(SRC).call();
            newCommit = commit(git, "V2: remove negate");
        }

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        int exitCode = SelectTestsCli.run(
                new String[] {repoDir.toString(), oldCommit, newCommit, SRC + "=" + TEST},
                out, err);
        String output = outBytes.toString(StandardCharsets.UTF_8);
        String errors = errBytes.toString(StandardCharsets.UTF_8);

        assertEquals(0, exitCode, "Unexpected error output: " + errors);
        assertTrue(output.contains("Removed methods (1):"),
                "Expected a removed-methods section. Output was:\n" + output);
        assertTrue(output.contains("- negate"),
                "Expected negate to be listed as removed. Output was:\n" + output);
        assertFalse(output.contains("WARNING"),
                "A removal alone must not print a WARNING. Output was:\n" + output);
        assertTrue(output.contains("none (no impacted tests found)"),
                "A removal alone must not change the selection. Output was:\n" + output);
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
