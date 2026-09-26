package com.analysis.tool.impact;

import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.parser.JavaParserAnalyzer;
import com.analysis.tool.parser.MethodInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test: connects GitAnalyzer (Git history),
 * JavaParserAnalyzer (AST parsing), and MethodChangeDetector (comparison)
 * against the real sample project repository.
 *
 * This is the first fully-wired proof that the pipeline works on real
 * Git history and real parsed source, not hand-typed test strings.
 *
 * Uses pinned commit hashes (Version 1 -> Version 2) rather than
 * HEAD/HEAD~1, so this test remains correct even as new commits
 * (e.g. Version 3) are added to the sample project later.
 */
public class MethodChangeDetectorIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final String CALCULATOR_PATH =
            "src/main/java/com/sample/Calculator.java";

    private static final String VERSION_1_COMMIT = "f2493c2";
    private static final String VERSION_2_COMMIT = "9ed64fa";

    @Test
    public void shouldDetectSubtractModifiedAndAddUnchangedAcrossRealCommits() throws Exception {
        GitAnalyzer gitAnalyzer = new GitAnalyzer(SAMPLE_PROJECT_PATH);

        String oldSource = gitAnalyzer.getFileContentAtRevision(VERSION_1_COMMIT, CALCULATOR_PATH);
        String newSource = gitAnalyzer.getFileContentAtRevision(VERSION_2_COMMIT, CALCULATOR_PATH);

        gitAnalyzer.close();

        JavaParserAnalyzer parserAnalyzer = new JavaParserAnalyzer();
        List<MethodInfo> oldMethods = parserAnalyzer.extractMethods(oldSource);
        List<MethodInfo> newMethods = parserAnalyzer.extractMethods(newSource);

        MethodChangeDetector detector = new MethodChangeDetector();
        MethodChangeResult result = detector.detectChanges(oldMethods, newMethods);

        assertTrue(result.getModifiedMethods().contains("subtract"),
                "Expected 'subtract' to be detected as modified");
        assertTrue(result.getUnchangedMethods().contains("add"),
                "Expected 'add' to be detected as unchanged");
        assertEquals(0, result.getAddedMethods().size(),
                "Expected no methods to be added between these two revisions");
        assertEquals(0, result.getRemovedMethods().size(),
                "Expected no methods to be removed between these two revisions");
    }
}
