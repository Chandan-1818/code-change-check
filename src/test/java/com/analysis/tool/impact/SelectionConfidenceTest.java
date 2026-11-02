package com.analysis.tool.impact;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the three confidence levels reported by analyzeTransitively
 * (GAP-12):
 *   HIGH     - a plain method-body change on parseable sources;
 *   REDUCED  - a non-method change forces the whole file to be treated
 *              as changed;
 *   FALLBACK - an unparseable source must not crash the analysis; every
 *              discovered test is selected instead.
 *
 * Uses a throwaway repo built in a temp directory, so it is portable.
 */
class SelectionConfidenceTest {

    private static final String SRC = "src/main/java/demo/Calc.java";
    private static final String TEST = "src/test/java/demo/CalcTest.java";
    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    private static final String TEST_SOURCE =
            "package demo;\n"
          + "import org.junit.jupiter.api.Test;\n"
          + "public class CalcTest {\n"
          + "    @Test public void testG() { new Calc().g(); }\n"
          + "}\n";

    @TempDir
    Path repoDir;

    private TestSelectionReport analyze(String oldSource, String newSource) throws Exception {
        String oldCommit;
        String newCommit;
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(SRC, oldSource);
            write(TEST, TEST_SOURCE);
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1");

            write(SRC, newSource);
            git.add().addFilepattern(SRC).call();
            newCommit = commit(git, "V2");
        }
        List<FileMapping> mappings = Collections.singletonList(new FileMapping(SRC, TEST));
        return new ChangeImpactAnalyzer().analyzeTransitively(
                repoDir.toString(), oldCommit, newCommit, mappings);
    }

    @Test
    void bodyOnlyChangeIsHighConfidence() throws Exception {
        TestSelectionReport report = analyze(
                "package demo;\npublic class Calc {\n"
                        + "    public int g() { return 2; }\n}\n",
                "package demo;\npublic class Calc {\n"
                        + "    public int g() { return 3; }\n}\n");

        assertEquals(SelectionConfidence.HIGH, report.getConfidence());
        assertTrue(report.getUnparsedFiles().isEmpty());
        assertTrue(report.getSelectedTests().contains("testG"),
                "Selected: " + report.getSelectedTests());
    }

    @Test
    void nonMethodChangeIsReducedConfidence() throws Exception {
        TestSelectionReport report = analyze(
                "package demo;\npublic class Calc {\n"
                        + "    static final int K = 1;\n"
                        + "    public int g() { return K; }\n}\n",
                "package demo;\npublic class Calc {\n"
                        + "    static final int K = 2;\n"
                        + "    public int g() { return K; }\n}\n");

        assertEquals(SelectionConfidence.REDUCED, report.getConfidence());
        assertTrue(report.getConservativelyExpandedFiles().contains(SRC));
    }

    @Test
    void unparseableSourceFallsBackWithoutCrashing() throws Exception {
        TestSelectionReport report = analyze(
                "package demo;\npublic class Calc {\n"
                        + "    public int g() { return 2; }\n}\n",
                "package demo;\npublic class Calc {\n"
                        + "    public int g() { this is not java\n}\n");

        assertEquals(SelectionConfidence.FALLBACK, report.getConfidence());
        assertTrue(report.getUnparsedFiles().contains(SRC),
                "Unparsed: " + report.getUnparsedFiles());
        assertTrue(report.getSelectedTests().contains("testG"),
                "Fallback must select every discovered test: " + report.getSelectedTests());
        assertTrue(report.getUntestedChangedMethods().isEmpty());
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
