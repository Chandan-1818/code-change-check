package com.analysis.tool.impact;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 9: reproduces the crash when a mapped source file is deleted
 * between two revisions. Uses a throwaway repo built in a temp directory,
 * so it does not depend on the sample project.
 */
class ChangeImpactAnalyzerDeletedFileIntegrationTest {

    private static final String SRC = "src/main/java/demo/Calculator.java";
    private static final String TEST = "src/test/java/demo/CalculatorTest.java";

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    @TempDir
    Path repoDir;

    @Test
    void deletedSourceFile_doesNotThrow() throws Exception {
        String oldCommit;
        String newCommit;

        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(SRC,
                    "package demo;\n"
                  + "public class Calculator {\n"
                  + "    public int add(int a, int b) { return a + b; }\n"
                  + "}\n");
            write(TEST,
                    "package demo;\n"
                  + "import org.junit.jupiter.api.Test;\n"
                  + "public class CalculatorTest {\n"
                  + "    @Test\n"
                  + "    public void testAdd() { new Calculator().add(1, 2); }\n"
                  + "}\n");
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1: Calculator + test");

            Files.delete(repoDir.resolve(SRC));
            git.rm().addFilepattern(SRC).call();
            newCommit = commit(git, "V2: delete Calculator");
        }

        List<FileMapping> mappings = Collections.singletonList(new FileMapping(SRC, TEST));

        assertDoesNotThrow(() ->
                new ChangeImpactAnalyzer().analyzeTransitively(
                        repoDir.toString(), oldCommit, newCommit, mappings));
    }

    @Test
    void deletedTestFile_doesNotThrow_andChangedMethodIsReportedUntested() throws Exception {
        String oldCommit;
        String newCommit;

        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(SRC,
                    "package demo;\n"
                  + "public class Calculator {\n"
                  + "    public int add(int a, int b) { return a + b; }\n"
                  + "}\n");
            write(TEST,
                    "package demo;\n"
                  + "import org.junit.jupiter.api.Test;\n"
                  + "public class CalculatorTest {\n"
                  + "    @Test\n"
                  + "    public void testAdd() { new Calculator().add(1, 2); }\n"
                  + "}\n");
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1: Calculator + test");

            write(SRC,
                    "package demo;\n"
                  + "public class Calculator {\n"
                  + "    public int add(int a, int b) { int r = a + b; return r; }\n"
                  + "}\n");
            Files.delete(repoDir.resolve(TEST));
            git.rm().addFilepattern(TEST).call();
            git.add().addFilepattern(SRC).call();
            newCommit = commit(git, "V2: modify add, delete test");
        }

        List<FileMapping> mappings = Collections.singletonList(new FileMapping(SRC, TEST));

        TestSelectionReport report = assertDoesNotThrow(() ->
                new ChangeImpactAnalyzer().analyzeTransitively(
                        repoDir.toString(), oldCommit, newCommit, mappings));

        assertTrue(report.getSelectedTests().isEmpty());
        assertTrue(report.getUntestedChangedMethods().contains("add"));
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
