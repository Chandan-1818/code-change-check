package com.analysis.tool.impact;

import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.graph.DependencyGraph;
import com.analysis.tool.graph.DependencyGraphBuilder;
import com.analysis.tool.graph.MethodNode;
import com.analysis.tool.parser.JavaParserAnalyzer;
import com.analysis.tool.parser.MethodInfo;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the full pipeline: given a source file's change between two
 * revisions, and its corresponding test file, determines which test methods
 * need to be re-run.
 *
 * Three entry points are provided:
 * - findImpactedTests(repoPath, oldRevision, newRevision, sourceFilePath, testFilePath)
 *   for a single file pair, using direct-call matching only.
 * - findImpactedTests(repoPath, oldRevision, newRevision, fileMappings)
 *   for multiple file pairs, using direct-call matching only, with each
 *   pair processed independently.
 * - findImpactedTestsTransitively(repoPath, oldRevision, newRevision, fileMappings)
 *   for multiple file pairs, building ONE combined dependency graph and
 *   using reverse BFS, so tests that reach a changed method through a
 *   chain of calls (possibly spanning several files) are also selected.
 *
 * Considers only added and modified methods as impact triggers (a removed
 * method cannot be "called" by a test in the new version, so it is excluded
 * here; a test referencing a removed method would fail to compile, which is
 * a separate concern from impact selection).
 *
 * If a source file did not exist at the old revision (e.g. it is a newly
 * added file), its old content is treated as empty, so every method in the
 * new version is correctly classified as "added" rather than causing an
 * exception.
 *
 * Known limitations of findImpactedTestsTransitively: matching is by method
 * name only (callee classes are unresolved), results are plain test method
 * names (two test classes with the same test method name would collide),
 * and changes are detected in source files only, not in test files.
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

    /**
     * Selects tests using one combined dependency graph across all file
     * pairs, so a test is selected if it reaches a changed method directly
     * or through any chain of calls (up to the analyzer's depth cap).
     */
    public Set<String> findImpactedTestsTransitively(
            String repoPath,
            String oldRevision,
            String newRevision,
            List<FileMapping> fileMappings) throws Exception {

        GitAnalyzer gitAnalyzer = new GitAnalyzer(repoPath);
        try {
            JavaParserAnalyzer parserAnalyzer = new JavaParserAnalyzer();
            MethodChangeDetector detector = new MethodChangeDetector();

            Set<String> changedMethodNames = new HashSet<>();
            List<String> allSources = new ArrayList<>();
            Set<MethodNode> testNodes = new HashSet<>();

            for (FileMapping mapping : fileMappings) {
                String oldSource;
                try {
                    oldSource = gitAnalyzer.getFileContentAtRevision(oldRevision, mapping.getSourceFilePath());
                } catch (IOException e) {
                    // File did not exist at the old revision (newly added).
                    oldSource = "";
                }
                String newSource = gitAnalyzer.getFileContentAtRevision(newRevision, mapping.getSourceFilePath());
                String testSource = gitAnalyzer.getFileContentAtRevision(newRevision, mapping.getTestFilePath());

                MethodChangeResult changeResult = detector.detectChanges(
                        parserAnalyzer.extractMethods(oldSource),
                        parserAnalyzer.extractMethods(newSource));
                changedMethodNames.addAll(changeResult.getModifiedMethods());
                changedMethodNames.addAll(changeResult.getAddedMethods());

                allSources.add(newSource);
                allSources.add(testSource);
                collectTestNodes(testSource, testNodes);
            }

            DependencyGraph graph = new DependencyGraphBuilder().buildFromSources(allSources);
            ImpactSet impactSet = new ImpactAnalyzer().analyzeImpact(graph, changedMethodNames);

            Set<MethodNode> impactedNodes = new HashSet<>();
            impactedNodes.addAll(impactSet.getChangedMethods());
            impactedNodes.addAll(impactSet.getDirectlyImpacted());
            impactedNodes.addAll(impactSet.getTransitivelyImpacted().keySet());

            Set<String> impactedTests = new HashSet<>();
            for (MethodNode node : impactedNodes) {
                if (testNodes.contains(node)) {
                    impactedTests.add(node.getMethodName());
                }
            }
            return impactedTests;
        } finally {
            gitAnalyzer.close();
        }
    }

    /**
     * Records every method annotated with @Test in the given test source
     * as a MethodNode (enclosing class + method name).
     */
    private void collectTestNodes(String testSource, Set<MethodNode> testNodes) {
        CompilationUnit compilationUnit = StaticJavaParser.parse(testSource);
        for (ClassOrInterfaceDeclaration classDecl : compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
            for (MethodDeclaration method : classDecl.findAll(MethodDeclaration.class)) {
                if (method.isAnnotationPresent("Test")) {
                    testNodes.add(new MethodNode(classDecl.getNameAsString(), method.getNameAsString()));
                }
            }
        }
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
