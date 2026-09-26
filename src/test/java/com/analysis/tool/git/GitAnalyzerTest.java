package com.analysis.tool.git;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class GitAnalyzerTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final String CALCULATOR_PATH =
            "src/main/java/com/sample/Calculator.java";

    // Pinned commit hashes instead of HEAD/HEAD~1, so this test remains
    // correct even as new commits (e.g. Version 3) are added later.
    private static final String VERSION_1_COMMIT = "f2493c2";
    private static final String VERSION_2_COMMIT = "9ed64fa";

    @Test
    public void shouldOpenSampleProjectAndReadBranch() throws Exception {
        GitAnalyzer analyzer = new GitAnalyzer(SAMPLE_PROJECT_PATH);
        String branch = analyzer.getCurrentBranch();
        analyzer.close();

        assertEquals("master", branch);
    }

    @Test
    public void shouldDetectChangedFileBetweenVersion1AndVersion2() throws Exception {
        GitAnalyzer analyzer = new GitAnalyzer(SAMPLE_PROJECT_PATH);
        List<String> changedFiles = analyzer.getChangedFiles(VERSION_1_COMMIT, VERSION_2_COMMIT);
        analyzer.close();

        assertEquals(1, changedFiles.size(),
                "Expected exactly one changed file between Version 1 and Version 2");
        assertTrue(changedFiles.contains(CALCULATOR_PATH),
                "Expected Calculator.java to be the changed file");
    }

    @Test
    public void shouldReadDifferentFileContentAtDifferentRevisions() throws Exception {
        GitAnalyzer analyzer = new GitAnalyzer(SAMPLE_PROJECT_PATH);

        String oldContent = analyzer.getFileContentAtRevision(VERSION_1_COMMIT, CALCULATOR_PATH);
        String newContent = analyzer.getFileContentAtRevision(VERSION_2_COMMIT, CALCULATOR_PATH);

        analyzer.close();

        // Version 1 returned directly, no intermediate variable
        assertTrue(oldContent.contains("return a - b;"),
                "Expected Version 1 to return 'a - b' directly");
        assertFalse(oldContent.contains("int result"),
                "Expected Version 1 to NOT contain an intermediate 'result' variable");

        // Version 2 uses an intermediate variable
        assertTrue(newContent.contains("int result = a - b;"),
                "Expected Version 2 to declare an intermediate 'result' variable");
        assertTrue(newContent.contains("return result;"),
                "Expected Version 2 to return the 'result' variable");

        // The two versions must genuinely differ
        assertFalse(oldContent.equals(newContent),
                "Expected file content to differ between the two revisions");
    }
}
