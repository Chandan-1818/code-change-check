package com.analysis.tool.impact;

import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.parser.JavaParserAnalyzer;
import com.analysis.tool.parser.MethodInfo;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the full pipeline: given a source file's change between two
 * revisions, and its corresponding test file, determines which test methods
 * need to be re-run.
 *
 * Two entry points are provided:
 * - findImpactedTests(repoPath, oldRevision, newRevision, sourceFilePath, testFilePath)
 *   for a single file pair (original scope).
 * - findImpactedTests(repoPath, oldRevision, newRevision, fileMappings)
 *   for multiple file pairs in one call, opening the Git repository once
 *   and unioning the impacted tests across all files.
 *
 * Considers only added and modified methods as impact triggers (a removed
 * method cannot be "called" by a test in the new version, so it is excluded
 * here; a test referencing a removed method would fail to compile, which is
 * a separate concern from impact selection).
 *
 * If the source file did not exist at the old revision (e.g. it is a newly
 * added file), its old content is treated as empty, so every method in the
 * new version is correctly classified as "added" rather than causing an
 * exception.
 */
public class ChangeImpactAnalyzer {

    public Set<String> findImpactedTests(
            String repoPath,
            String oldRevision,
            String newRevision,
            String sourceFilePath,
            String testFilePath) throws Exception {

        GitAnalyzer gitAnalyzer = new GitAnalyzer(repoPath);
        try {
            return findImpactedTestsForFile(gitAnalyzer, oldRevision, newRevision, sourceFilePath, testFilePath);
        } finally {
            gitAnalyzer.close();
        }
    }

    public Set<String> findImpactedTests(
            String repoPath,
            String oldRevision,
            String newRevision,
            List<FileMapping> fileMappings) throws Exception {

        GitAnalyzer gitAnalyzer = new GitAnalyzer(repoPath);
        Set<String> allImpactedTests = new HashSet<>();
        try {
            for (FileMapping mapping : fileMappings) {
                allImpactedTests.addAll(findImpactedTestsForFile(
                        gitAnalyzer,
                        oldRevision,
                        newRevision,
                        mapping.getSourceFilePath(),
                        mapping.getTestFilePath()));
            }
        } finally {
            gitAnalyzer.close();
        }
        return allImpactedTests;
    }

    private Set<String> findImpactedTestsForFile(
            GitAnalyzer gitAnalyzer,
            String oldRevision,
            String newRevision,
            String sourceFilePath,
            String testFilePath) throws Exception {

        String oldSource;
        try {
            oldSource = gitAnalyzer.getFileContentAtRevision(oldRevision, sourceFilePath);
        } catch (IOException e) {
            // File did not exist at the old revision (e.g. it was newly added).
            // Treat as empty source so all methods are classified as "added".
            oldSource = "";
        }

        String newSource = gitAnalyzer.getFileContentAtRevision(newRevision, sourceFilePath);
        String testSource = gitAnalyzer.getFileContentAtRevision(newRevision, testFilePath);

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
