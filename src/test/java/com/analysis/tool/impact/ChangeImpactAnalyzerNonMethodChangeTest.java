package com.analysis.tool.impact;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A change to a constant (outside every method body) must still select the
 * tests of the methods in that file; a comment-only change must select nothing.
 * Uses a throwaway repo, so it is portable (no D:/ paths).
 */
class ChangeImpactAnalyzerNonMethodChangeTest {

    private static final String SRC = "src/main/java/demo/Calc.java";
    private static final String TEST = "src/test/java/demo/CalcTest.java";
    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    @TempDir
    Path repoDir;

    private static String calc(String header) {
        return "package demo;\n"
             + "public class Calc {\n"
             + header + "\n"
             + "    public int f() { return K; }\n"
             + "    public int g() { return 2; }\n"
             + "}\n";
    }

    private static final String TEST_SOURCE =
            "package demo;\n"
          + "import org.junit.jupiter.api.Test;\n"
          + "public class CalcTest {\n"
          + "    @Test public void testG() { new Calc().g(); }\n"
          + "}\n";

    private TestSelectionReport analyze(String oldHeader, String newHeader) throws Exception {
        String oldCommit;
        String newCommit;
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(SRC, calc(oldHeader));
            write(TEST, TEST_SOURCE);
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1");

            write(SRC, calc(newHeader));
            git.add().addFilepattern(SRC).call();
            newCommit = commit(git, "V2");
        }
        List<FileMapping> mappings = Collections.singletonList(new FileMapping(SRC, TEST));
        return new ChangeImpactAnalyzer().analyzeTransitively(
                repoDir.toString(), oldCommit, newCommit, mappings);
    }

    @Test
    void constantChangeSelectsTestsOfMethodsInTheFile() throws Exception {
        TestSelectionReport report = analyze("    static final int K = 1;", "    static final int K = 2;");

        assertTrue(report.getConservativelyExpandedFiles().contains(SRC));
        assertTrue(report.getSelectedTests().contains("testG"),
                "Selected: " + report.getSelectedTests());
    }

    @Test
    void commentOnlyChangeSelectsNothing() throws Exception {
        TestSelectionReport report = analyze("    static final int K = 1;",
                "    // just a comment\n    static final int K = 1;");

        assertTrue(report.getConservativelyExpandedFiles().isEmpty());
        assertTrue(report.getSelectedTests().isEmpty(), "Selected: " + report.getSelectedTests());
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
}
