package com.analysis.tool.git;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GitAnalyzerTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

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
        List<String> changedFiles = analyzer.getChangedFiles("HEAD~1", "HEAD");
        analyzer.close();

        assertEquals(1, changedFiles.size(),
                "Expected exactly one changed file between Version 1 and Version 2");
        assertTrue(changedFiles.contains("src/main/java/com/sample/Calculator.java"),
                "Expected Calculator.java to be the changed file");
    }
}
