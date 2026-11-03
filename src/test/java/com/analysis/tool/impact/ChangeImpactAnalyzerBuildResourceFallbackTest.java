package com.analysis.tool.impact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.analysis.tool.git.GitAnalyzer;

/**
 * H4: a changed build or resource file cannot be analysed at member level, so
 * the run must return FALLBACK confidence and select every discovered test.
 * A changed Java source that no detector explained must be treated as an
 * uncertainty trigger instead of being silently ignored.
 *
 * Uses a throwaway repo built in a temp directory, so it is portable.
 */
class ChangeImpactAnalyzerBuildResourceFallbackTest {

    private static final String SRC = "src/main/java/demo/Calc.java";
    private static final String TEST = "src/test/java/demo/CalcTest.java";
    private static final String POM = "pom.xml";
    private static final String RESOURCE = "src/main/resources/application.properties";
    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    private static final String SRC_V1 =
            "package demo;\npublic class Calc {\n    public int g() { return 2; }\n}\n";
    private static final String SRC_IMPORT_ONLY_V2 =
            "package demo;\nimport java.util.List;\n"
                    + "public class Calc {\n    public int g() { return 2; }\n}\n";
    private static final String POM_V1 = "<project><version>1</version></project>\n";
    private static final String RESOURCE_V1 = "key=one\n";

    private static final String TEST_SOURCE =
            "package demo;\n"
          + "import org.junit.jupiter.api.Test;\n"
          + "public class CalcTest {\n"
          + "    @Test public void testG() { new Calc().g(); }\n"
          + "}\n";

    @TempDir
    Path repoDir;

    private TestSelectionReport analyze(String srcAtV2, String pomAtV2, String resourceAtV2)
            throws Exception {
        String oldCommit;
        String newCommit;
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(SRC, SRC_V1);
            write(TEST, TEST_SOURCE);
            write(POM, POM_V1);
            write(RESOURCE, RESOURCE_V1);
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1");

            write(SRC, srcAtV2);
            write(POM, pomAtV2);
            write(RESOURCE, resourceAtV2);
            git.add().addFilepattern(".").call();
            newCommit = commit(git, "V2");
        }

        List<FileMapping> mappings = Collections.singletonList(new FileMapping(SRC, TEST));
        List<String> changed;
        GitAnalyzer gitAnalyzer = new GitAnalyzer(repoDir.toString());
        try {
            changed = gitAnalyzer.getChangedFiles(oldCommit, newCommit);
        } finally {
            gitAnalyzer.close();
        }

        return new ChangeImpactAnalyzer().analyzeTransitively(
                repoDir.toString(), oldCommit, newCommit, mappings,
                Collections.<String>emptyList(), ImpactAnalyzer.UNLIMITED_DEPTH, changed);
    }

    @Test
    void pomChangeFallsBackAndSelectsEveryDiscoveredTest() throws Exception {
        TestSelectionReport report =
                analyze(SRC_V1, "<project><version>2</version></project>\n", RESOURCE_V1);

        assertEquals(SelectionConfidence.FALLBACK, report.getConfidence());
        assertTrue(report.getBuildOrResourceChanges().contains(POM),
                "Build changes: " + report.getBuildOrResourceChanges());
        assertTrue(report.getSelectedTests().contains("testG"),
                "Fallback must select every discovered test: " + report.getSelectedTests());
        assertTrue(report.getUntestedChangedMethods().isEmpty());
    }

    @Test
    void resourceChangeFallsBackAndSelectsEveryDiscoveredTest() throws Exception {
        TestSelectionReport report = analyze(SRC_V1, POM_V1, "key=two\n");

        assertEquals(SelectionConfidence.FALLBACK, report.getConfidence());
        assertTrue(report.getBuildOrResourceChanges().contains(RESOURCE),
                "Build/resource changes: " + report.getBuildOrResourceChanges());
        assertTrue(report.getSelectedTests().contains("testG"),
                "Fallback must select every discovered test: " + report.getSelectedTests());
    }

    @Test
    void sourceOnlyChangeIsNotABuildOrResourceFallback() throws Exception {
        TestSelectionReport report = analyze(SRC_IMPORT_ONLY_V2, POM_V1, RESOURCE_V1);

        assertTrue(report.getBuildOrResourceChanges().isEmpty(),
                "Build/resource changes: " + report.getBuildOrResourceChanges());
    }

    @Test
    void javaFileChangeWithNoMemberChangeIsNotSilentlyIgnored() throws Exception {
        TestSelectionReport report = analyze(SRC_IMPORT_ONLY_V2, POM_V1, RESOURCE_V1);

        assertEquals(SelectionConfidence.REDUCED, report.getConfidence(),
                "An unexplained file change must reduce confidence, not be ignored");
        assertTrue(report.getConservativelyExpandedFiles().contains(SRC),
                "Expanded files: " + report.getConservativelyExpandedFiles());
        assertTrue(report.getSelectedTests().contains("testG"),
                "The file's tests must still be selected: " + report.getSelectedTests());
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
