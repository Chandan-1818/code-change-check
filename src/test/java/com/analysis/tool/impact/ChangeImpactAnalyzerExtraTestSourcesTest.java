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
 * A changed method with no manual test is untested; once a generated test
 * source is supplied it is covered, selected, and no longer reported untested.
 * Throwaway repo, portable, no JVM launched.
 */
class ChangeImpactAnalyzerExtraTestSourcesTest {

    private static final String SRC = "src/main/java/demo/Calculator.java";
    private static final String TEST = "src/test/java/demo/CalculatorTest.java";
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

    private String oldCommit;
    private String newCommit;

    private void buildHistory() throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(SRC, "package demo;\npublic class Calculator {\n"
                    + "    public int add(int a, int b) { return a + b; }\n}\n");
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1");

            write(SRC, "package demo;\npublic class Calculator {\n"
                    + "    public int add(int a, int b) { int r = a + b; return r; }\n}\n");
            git.add().addFilepattern(SRC).call();
            newCommit = commit(git, "V2");
        }
    }

    private final List<FileMapping> mappings = Collections.singletonList(new FileMapping(SRC, TEST));

    @Test
    void withoutExtraSourcesTheChangedMethodIsUntested() throws Exception {
        buildHistory();
        TestSelectionReport report = new ChangeImpactAnalyzer().analyzeTransitively(
                repoDir.toString(), oldCommit, newCommit, mappings);

        assertTrue(report.getSelectedTests().isEmpty());
        assertTrue(report.getUntestedChangedMethods().contains("add"));
    }

    @Test
    void generatedTestCoversTheMethodAndIsSelected() throws Exception {
        buildHistory();
        TestSelectionReport report = new ChangeImpactAnalyzer().analyzeTransitively(
                repoDir.toString(), oldCommit, newCommit, mappings, List.of(GENERATED));

        assertTrue(report.getUntestedChangedMethods().isEmpty(),
                "Untested: " + report.getUntestedChangedMethods());
        assertEquals(java.util.Set.of("gen_Calculator_add_01"), report.getSelectedTests());
        assertTrue(report.getSelectedTestIdentifiers()
                .contains("CalculatorGeneratedTest#gen_Calculator_add_01"));
    }

    @Test
    void nullOrEmptyExtraSourcesBehaveLikeTheFourArgumentForm() throws Exception {
        buildHistory();
        TestSelectionReport viaNull = new ChangeImpactAnalyzer().analyzeTransitively(
                repoDir.toString(), oldCommit, newCommit, mappings, null);
        assertTrue(viaNull.getUntestedChangedMethods().contains("add"));
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
