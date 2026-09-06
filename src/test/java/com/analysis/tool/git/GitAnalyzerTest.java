package com.analysis.tool.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
