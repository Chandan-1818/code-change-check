package com.analysis.tool.impact;

import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.parser.JavaParserAnalyzer;
import com.analysis.tool.parser.MethodInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the full pipeline: given a source file's change between two
 * revisions, and its corresponding test file, determines which test methods
 * need to be re-run.
 *
 * Current scope: single source file + single test file per call.
 * Considers only added and modified methods as impact triggers (a removed
 * method cannot be "called" by a test in the new version, so it is excluded
 * here; a test referencing a removed method would fail to compile, which is
 * a separate concern from impact selection).
 */
public class ChangeImpactAnalyzer {

    public Set<String> findImpactedTests(
            String repoPath,
            String oldRevision,
            String newRevision,
            String sourceFilePath,
            String testFilePath) throws Exception {

        GitAnalyzer gitAnalyzer = new GitAnalyzer(repoPath);

        String oldSource = gitAnalyzer.getFileContentAtRevision(oldRevision, sourceFilePath);
        String newSource = gitAnalyzer.getFileContentAtRevision(newRevision, sourceFilePath);
        String testSource = gitAnalyzer.getFileContentAtRevision(newRevision, testFilePath);

        gitAnalyzer.close();

        JavaParserAnalyzer parserAnalyzer = new JavaParserAnalyzer();
        List<MethodInfo> oldMethods = parserAnalyzer.extractMethods(oldSource);
        List<MethodInfo> newMethods = parserAnalyzer.extractMethods(newSource);

        MethodChangeDetector detector = new MethodChangeDetector();
        MethodChangeResult changeResult = detector.detectChanges(oldMethods, newMethods);

        TestImpactMapper testImpactMapper = new TestImpactMapper();
        Set<String> impactedTests = new HashSet<>();

        for (String changedMethod : changeResult.getModifiedMethods()) {
            impactedTests.addAll(testImpactMapper.findTestsExercisingMethod(testSource, changedMethod));
        }
        for (String changedMethod : changeResult.getAddedMethods()) {
            impactedTests.addAll(testImpactMapper.findTestsExercisingMethod(testSource, changedMethod));
        }

        return impactedTests;
    }
}
