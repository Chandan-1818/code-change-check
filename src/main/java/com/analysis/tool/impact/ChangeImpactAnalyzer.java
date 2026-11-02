package com.analysis.tool.impact;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.graph.DependencyGraph;
import com.analysis.tool.graph.DependencyGraphBuilder;
import com.analysis.tool.graph.MemberId;
import com.analysis.tool.graph.MemberKind;
import com.analysis.tool.graph.MethodNode;
import com.analysis.tool.parser.JavaParserAnalyzer;
import com.analysis.tool.parser.MethodInfo;
import com.analysis.tool.parser.SourceParseException;

/**
 * Orchestrates the full pipeline: given a source file's change between two
 * revisions, and its corresponding test file, determines which test methods
 * need to be re-run.
 *
 * Entry points:
 * - findImpactedTests(repoPath, oldRevision, newRevision, sourceFilePath, testFilePath)
 *   for a single file pair, using direct-call matching only.
 *
 * - findImpactedTests(repoPath, oldRevision, newRevision, fileMappings)
 *   for multiple file pairs, using direct-call matching only, with each
 *   pair processed independently.
 *
 * - analyzeTransitively(repoPath, oldRevision, newRevision, fileMappings)
 *   for multiple file pairs, building ONE combined dependency graph and
 *   using reverse BFS, so tests that reach a changed method through a
 *   chain of calls (possibly spanning several files) are also selected.
 *
 *   Returns a TestSelectionReport that also lists changed methods which
 *   no test reaches, and Class#method identifiers for execution (Phase 10).
 *
 * - findImpactedTestsTransitively(...) returns only the selected tests
 *   (plain names) from analyzeTransitively.
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
 * Known limitations of the transitive entry points: matching is by method
 * name only (callee classes are unresolved), plain-name results are two
 * classes with the same method name would collide (the Class#method
 * identifiers do not have this problem), and changes are detected in
 * source files only, not in test files.
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
            return findImpactedTestsForFile(
                    gitAnalyzer,
                    oldRevision,
                    newRevision,
                    sourceFilePath,
                    testFilePath);
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
     * pairs. Returns only the selected test names; see analyzeTransitively
     * for the fuller report.
     */
    public Set<String> findImpactedTestsTransitively(
            String repoPath,
            String oldRevision,
            String newRevision,
            List<FileMapping> fileMappings) throws Exception {

        return analyzeTransitively(
                repoPath,
                oldRevision,
                newRevision,
                fileMappings).getSelectedTests();
    }

    /**
     * Selects tests using one combined dependency graph across all file
     * pairs, and reports every added or modified method that no test
     * reaches.
     *
     * The reverse BFS runs once per changed method. A test is selected if it
     * reaches any changed method; a changed method that reaches no test
     * is reported as untested. A changed method that calls nothing and is
     * called by nothing never becomes a graph node, so it also reaches no
     * test and is reported as untested.
     */
    public TestSelectionReport analyzeTransitively(
            String repoPath,
            String oldRevision,
            String newRevision,
            List<FileMapping> fileMappings) throws Exception {

        return analyzeTransitively(repoPath, oldRevision, newRevision, fileMappings,
                Collections.<String>emptyList());
    }

    /**
     * Same as the 4-argument form, plus extra test sources that are not in the
     * repository (for example generated tests kept under generated-tests/).
     * Their @Test methods become test nodes in the combined graph, so a changed
     * method reached only by such a test is selected and not reported untested.
     *
     * Impact traversal is unlimited by default; see the 6-argument form to
     * configure a maximum depth.
     */
    public TestSelectionReport analyzeTransitively(
            String repoPath,
            String oldRevision,
            String newRevision,
            List<FileMapping> fileMappings,
            List<String> extraTestSources) throws Exception {

        return analyzeTransitively(repoPath, oldRevision, newRevision, fileMappings,
                extraTestSources, ImpactAnalyzer.UNLIMITED_DEPTH);
    }

    /**
     * Same as the 5-argument form, plus an explicit impact-depth limit.
     *
     * @param maxDepth {@link ImpactAnalyzer#UNLIMITED_DEPTH} (-1) for unlimited
     *                 traversal (the default), or a positive maximum number of
     *                 hops. When the limit excludes reachable methods, the
     *                 returned report is marked truncated.
     */
    public TestSelectionReport analyzeTransitively(
            String repoPath,
            String oldRevision,
            String newRevision,
            List<FileMapping> fileMappings,
            List<String> extraTestSources,
            int maxDepth) throws Exception {

        return analyzeTransitively(repoPath, oldRevision, newRevision, fileMappings,
                extraTestSources, maxDepth, Collections.<String>emptyList());
    }

    /**
     * Same as the 6-argument form, plus the list of files that actually changed
     * between the two revisions.
     *
     * A changed file that is not a Java source under {@code src/main/java} or
     * {@code src/test/java} (a build file such as pom.xml, a resource, or any
     * other non-Java file) cannot be analysed at member level: such a change can
     * alter behaviour without touching any member. When any is present the
     * report gets FALLBACK confidence and every discovered test is selected.
     *
     * @param changedFilePaths the file-level diff between the two revisions
     *                         (e.g. from GitAnalyzer.getChangedFiles); an empty
     *                         list means "no file-level information available".
     */
    public TestSelectionReport analyzeTransitively(
            String repoPath,
            String oldRevision,
            String newRevision,
            List<FileMapping> fileMappings,
            List<String> extraTestSources,
            int maxDepth,
            List<String> changedFilePaths) throws Exception {

        GitAnalyzer gitAnalyzer = new GitAnalyzer(repoPath);

        try {
            JavaParserAnalyzer parserAnalyzer = new JavaParserAnalyzer();
            MethodChangeDetector detector = new MethodChangeDetector();
            MemberChangeDetector memberDetector = new MemberChangeDetector();
            NonMethodChangeDetector nonMethodDetector = new NonMethodChangeDetector();
            ChangedFileClassifier fileClassifier = new ChangedFileClassifier();

            Set<String> buildOrResourceChanges =
                    fileClassifier.unanalysableChanges(changedFilePaths);

            Set<MemberId> changedMembers = new HashSet<>();
            Set<String> removedMethodNames = new HashSet<>();
            Set<String> expandedFiles = new java.util.TreeSet<>();
            Set<String> unparsedFiles = new java.util.TreeSet<>();
            // T3: member-level detail for the live workflow's display.
            Set<String> changedMemberLabels = new java.util.TreeSet<>();
            Set<String> impactedMethodLabels = new java.util.TreeSet<>();
            // PHASE 11: the impacted set split into directly reached callers and
            // callers reached only through a chain of calls.
            Set<String> directlyImpactedLabels = new java.util.TreeSet<>();
            Set<String> transitivelyImpactedLabels = new java.util.TreeSet<>();
            List<String> allSources = new ArrayList<>();

            TestSourceProvider testSourceProvider = new TestSourceProvider();
            List<String> testSources = new ArrayList<>();
            // H7: a test method added or modified between the two revisions is
            // selected unconditionally, even when the impact graph does not
            // reach it (the test itself is the change).
            Set<MethodNode> addedOrModifiedTestNodes = new LinkedHashSet<>();
            Set<String> testFilePaths = new LinkedHashSet<>();

            for (FileMapping mapping : fileMappings) {
                String oldSource;

                try {
                    oldSource = gitAnalyzer.getFileContentAtRevision(
                            oldRevision,
                            mapping.getSourceFilePath());
                } catch (IOException e) {
                    // File did not exist at the old revision (newly added).
                    oldSource = "";
                }

                String newSource;

                try {
                    newSource = gitAnalyzer.getFileContentAtRevision(
                            newRevision,
                            mapping.getSourceFilePath());
                } catch (IOException e) {
                    // File was deleted at the new revision.
                    newSource = "";
                }

                // H7: the mapped test file takes part in test discovery below,
                // alongside every other test file under src/test/java.
                testFilePaths.add(mapping.getTestFilePath());

                boolean sourceParsed = true;
                try {
                    List<MethodInfo> oldMembers = allMembers(parserAnalyzer, oldSource);
                    List<MethodInfo> newMembers = allMembers(parserAnalyzer, newSource);

                    MethodChangeResult changeResult = detector.detectChanges(
                            parserAnalyzer.extractMethods(oldSource),
                            parserAnalyzer.extractMethods(newSource));
                    removedMethodNames.addAll(changeResult.getRemovedMethods());

                    // Member-level (arity-aware) detection covering methods AND
                    // constructors, so a change to one overload does not select
                    // tests that only call a different-arity overload. Same-arity
                    // overloads still collapse (no type resolution); that can only
                    // over-select, never hide a change.
                    MemberChangeResult memberChanges =
                            memberDetector.detectMemberChanges(oldMembers, newMembers);
                    Set<MemberId> changed = new HashSet<>(memberChanges.getAddedMembers());
                    changed.addAll(memberChanges.getModifiedMembers());

                    Map<MemberId, Boolean> varargsById = new HashMap<>();
                    for (MethodInfo member : newMembers) {
                        varargsById.put(toMemberId(member), member.isVarargs());
                    }
                    for (MemberId member : changed) {
                        changedMembers.add(disableArityIfVarargs(member, varargsById));
                    }

                    boolean nonMethodChange =
                            nonMethodDetector.hasNonMethodChange(oldSource, newSource);

                    // A change outside every member body (field, constant, import,
                    // class header, or a MODIFIED constructor) still expands the
                    // whole file as a conservative superset. Added constructors are
                    // handled precisely above, since pure additions are not flagged
                    // here (a modified constructor is covered by both paths).
                    if (nonMethodChange) {
                        expandedFiles.add(mapping.getSourceFilePath());
                        for (MethodInfo member : newMembers) {
                            changedMembers.add(
                                    toMemberId(member).withArity(MemberId.UNKNOWN_ARITY));
                        }
                    } else if (changed.isEmpty()
                            && changeResult.getRemovedMethods().isEmpty()
                            && nonMethodDetector.differsStructurally(oldSource, newSource)) {
                        // The file changed structurally but no member-level or
                        // non-method change explained it (e.g. an added import or a
                        // new nested type). Treat it as an uncertainty trigger and
                        // expand the whole file rather than silently ignoring it.
                        expandedFiles.add(mapping.getSourceFilePath());
                        for (MethodInfo member : newMembers) {
                            changedMembers.add(
                                    toMemberId(member).withArity(MemberId.UNKNOWN_ARITY));
                        }
                    }
                } catch (SourceParseException e) {
                    // The source cannot be parsed, so its impact cannot be
                    // analysed; record it and fall back conservatively below.
                    sourceParsed = false;
                    unparsedFiles.add(mapping.getSourceFilePath());
                }

                if (sourceParsed) {
                    allSources.add(newSource);
                }
            }

            // T3: render the analysed changed members once, for display.
            for (MemberId member : changedMembers) {
                changedMemberLabels.add(member.getClassName() + "#" + member.getMemberName());
            }

            // H7: every test file under src/test/java at the new revision takes
            // part in the analysis, not only those paired by the Foo -> FooTest
            // convention. A non-conventionally named test file is associated
            // with production code through the call graph. Mapped test files
            // are included too, so an explicitly supplied pair is never dropped.
            try {
                for (String path : gitAnalyzer.listFilesAtRevision(newRevision)) {
                    if (TestSourceProvider.isTestSourceFile(path)) {
                        testFilePaths.add(path);
                    }
                }
            } catch (IOException e) {
                // No file listing available; fall back to the mapped test files.
            }

            for (String testFilePath : testFilePaths) {
                String newTestSource = contentOrEmpty(gitAnalyzer, newRevision, testFilePath);
                if (newTestSource.trim().isEmpty()) {
                    continue;
                }
                Set<MethodNode> testMethodsInFile = recordTestSource(
                        testSourceProvider, newTestSource, testFilePath,
                        testSources, allSources, unparsedFiles);
                if (!testMethodsInFile.isEmpty()) {
                    addedOrModifiedTestNodes.addAll(addedOrModifiedTestMethods(
                            parserAnalyzer, detector,
                            contentOrEmpty(gitAnalyzer, oldRevision, testFilePath),
                            newTestSource, testMethodsInFile));
                }
            }

            // Extra (generated) test sources are supplied by the caller and are
            // not part of the repository: they are discovered and can be
            // selected, but are never treated as "modified in the repository".
            if (extraTestSources != null) {
                for (String extraSource : extraTestSources) {
                    recordTestSource(testSourceProvider, extraSource,
                            "<generated test source>", testSources, allSources, unparsedFiles);
                }
            }

            TestSourceProvider.Discovery discovery = testSourceProvider.discover(testSources);
            Set<MethodNode> testNodes = discovery.getTestMethods();
            Map<MethodNode, Set<MethodNode>> lifecycleAttribution =
                    discovery.getLifecycleAttribution();

            SelectionConfidence confidence = SelectionConfidence.HIGH;
            if (!unparsedFiles.isEmpty() || !buildOrResourceChanges.isEmpty()) {
                confidence = SelectionConfidence.FALLBACK;
            } else if (!expandedFiles.isEmpty()) {
                confidence = SelectionConfidence.REDUCED;
            }

            Set<String> potentiallyBrokenTests =
                    potentiallyBrokenTests(testSources, removedMethodNames);

            if (confidence == SelectionConfidence.FALLBACK) {
                // A file could not be parsed, or a build/resource file changed in
                // a way member-level analysis cannot see, so the impact set is not
                // trustworthy. Select every discovered test (a superset of the
                // impacted tests) and report no method as untested, since
                // running the whole selection covers all of them.
                Set<String> allSelectedTests = new LinkedHashSet<>();
                Set<String> allSelectedIdentifiers = new LinkedHashSet<>();
                for (MethodNode testNode : testNodes) {
                    allSelectedTests.add(testNode.getMethodName());
                    allSelectedIdentifiers.add(discovery.identifierOf(testNode));
                }
                return new TestSelectionReport(
                        allSelectedTests,
                        allSelectedIdentifiers,
                        Collections.<String>emptySet(),
                        removedMethodNames, expandedFiles,
                        confidence, unparsedFiles, buildOrResourceChanges, false,
                        potentiallyBrokenTests,
                        Collections.<String, List<String>>emptyMap(),
                        changedMemberLabels,
                        Collections.<String>emptySet());
            }

            DependencyGraph graph =
                    new DependencyGraphBuilder().buildFromSources(allSources);

            ImpactAnalyzer impactAnalyzer = new ImpactAnalyzer();

            Set<String> selectedTests = new LinkedHashSet<>();
            Set<String> selectedTestIdentifiers = new LinkedHashSet<>();
            Set<String> untestedChangedMethods = new HashSet<>();
            Map<String, List<String>> explanations = new LinkedHashMap<>();
            boolean truncated = false;

            for (MemberId changedMember : changedMembers) {
                ImpactSet impactSet =
                        impactAnalyzer.analyzeMemberImpact(
                                graph,
                                Collections.singleton(changedMember),
                                maxDepth);

                truncated = truncated || impactSet.isTruncated();

                // T3: collect the impacted callers (excluding the test methods,
                // which are listed separately) for the display.
                for (MethodNode node : impactedNodes(impactSet)) {
                    if (!testNodes.contains(node)) {
                        impactedMethodLabels.add(node.getClassName() + "#" + node.getMethodName());
                    }
                }

                // PHASE 11: split the display set into direct and transitive.
                for (MethodNode node : impactSet.getDirectlyImpacted()) {
                    if (!testNodes.contains(node)) {
                        directlyImpactedLabels.add(node.getClassName() + "#" + node.getMethodName());
                    }
                }
                for (MethodNode node : impactSet.getTransitivelyImpacted().keySet()) {
                    if (!testNodes.contains(node)) {
                        transitivelyImpactedLabels.add(node.getClassName() + "#" + node.getMethodName());
                    }
                }

                Set<MethodNode> selectedForMember =
                        testNodesSelectedFor(impactSet, testNodes, lifecycleAttribution);

                if (selectedForMember.isEmpty()) {
                    // Only methods are reported untested; the report has always
                    // tracked methods, and a changed constructor is surfaced
                    // through the tests that construct the class.
                    if (changedMember.getKind() == MemberKind.METHOD) {
                        untestedChangedMethods.add(changedMember.getMemberName());
                    }
                } else {
                    for (MethodNode testNode : selectedForMember) {
                        selectedTests.add(testNode.getMethodName());
                        String identifier = discovery.identifierOf(testNode);
                        selectedTestIdentifiers.add(identifier);
                        explanations.putIfAbsent(identifier,
                                explanation(testNode, changedMember, impactSet, lifecycleAttribution));
                    }
                }
            }

            // H7: an added or modified test method is always selected, whether
            // or not the impact graph reaches it.
            for (MethodNode testNode : addedOrModifiedTestNodes) {
                selectedTests.add(testNode.getMethodName());
                selectedTestIdentifiers.add(discovery.identifierOf(testNode));
            }

            return new TestSelectionReport(
                    selectedTests,
                    selectedTestIdentifiers,
                    untestedChangedMethods,
                    removedMethodNames, expandedFiles,
                    confidence, unparsedFiles, buildOrResourceChanges, truncated,
                    potentiallyBrokenTests, explanations,
                    changedMemberLabels, impactedMethodLabels,
                    directlyImpactedLabels, transitivelyImpactedLabels);

        } finally {
            gitAnalyzer.close();
        }
    }

    /**
     * Extracts every declared member of a source file: methods first, then
     * constructors (name "&lt;init&gt;"). Used for member-level change
     * detection, so constructor changes participate in impact analysis.
     */
    private List<MethodInfo> allMembers(JavaParserAnalyzer parserAnalyzer, String source) {
        List<MethodInfo> members = new ArrayList<>(parserAnalyzer.extractMethods(source));
        members.addAll(parserAnalyzer.extractConstructors(source));
        return members;
    }

    /** Maps an extracted member to its identity (method or constructor). */
    private MemberId toMemberId(MethodInfo member) {
        if (MemberId.CONSTRUCTOR_NAME.equals(member.getName())) {
            return MemberId.forConstructor(member.getClassName(), member.getArity());
        }
        return MemberId.forMethod(member.getClassName(), member.getName(), member.getArity());
    }

    /**
     * Disables arity-based narrowing for a varargs member: a varargs call site
     * may supply any number of arguments, so the declared arity is not a safe
     * filter (conservative fallback, never under-selects).
     */
    private MemberId disableArityIfVarargs(MemberId member, Map<MemberId, Boolean> varargsById) {
        if (Boolean.TRUE.equals(varargsById.get(member))) {
            return member.withArity(MemberId.UNKNOWN_ARITY);
        }
        return member;
    }

    /**
     * Reads a file's content at a revision, returning an empty string when the
     * file does not exist there (newly added or deleted between revisions).
     */
    private static String contentOrEmpty(GitAnalyzer gitAnalyzer, String revision, String path) {
        try {
            return gitAnalyzer.getFileContentAtRevision(revision, path);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * H7: records one test source for discovery. Blank sources are ignored (a
     * deleted or absent test file). A source that cannot be parsed is reported
     * as unparsed (which forces FALLBACK confidence) and excluded.
     *
     * @return the test methods declared by this source (empty when unparsable)
     */
    private Set<MethodNode> recordTestSource(
            TestSourceProvider provider,
            String source,
            String pathLabel,
            List<String> testSources,
            List<String> allSources,
            Set<String> unparsedFiles) {

        if (source == null || source.trim().isEmpty()) {
            return Collections.emptySet();
        }

        TestSourceProvider.Discovery discovery;
        try {
            discovery = provider.discover(source);
        } catch (SourceParseException e) {
            unparsedFiles.add(pathLabel);
            return Collections.emptySet();
        }

        testSources.add(source);
        allSources.add(source);
        return discovery.getTestMethods();
    }

    /**
     * H7: the test methods of a test file that are added or modified between
     * the two revisions, restricted to methods actually annotated as tests (a
     * changed helper is not itself a test). The whole test source is compared,
     * so a non-conventionally named test file is handled like any other.
     *
     * @param newTestNodes the test methods already discovered in the new source
     */
    private Set<MethodNode> addedOrModifiedTestMethods(
            JavaParserAnalyzer parserAnalyzer,
            MethodChangeDetector detector,
            String oldTestSource,
            String newTestSource,
            Set<MethodNode> newTestNodes) {

        List<MethodInfo> oldMembers;
        List<MethodInfo> newMembers;
        try {
            oldMembers = parserAnalyzer.extractMethods(
                    oldTestSource == null ? "" : oldTestSource);
            newMembers = parserAnalyzer.extractMethods(newTestSource);
        } catch (SourceParseException e) {
            // The old revision cannot be compared reliably; treat every test as
            // changed so a comparison failure never narrows the selection.
            return newTestNodes;
        }

        MethodChangeResult changes = detector.detectChanges(oldMembers, newMembers);
        Set<String> changedNames = new HashSet<>(changes.getAddedMethods());
        changedNames.addAll(changes.getModifiedMethods());
        if (changedNames.isEmpty()) {
            return Collections.emptySet();
        }

        Set<MethodNode> result = new LinkedHashSet<>();
        for (MethodNode testNode : newTestNodes) {
            if (changedNames.contains(testNode.getMethodName())) {
                result.add(testNode);
            }
        }
        return result;
    }

    /**
     * H7: the tests selected for one changed member: tests the impact set
     * reaches, plus the tests attributed to a reached lifecycle method, so a
     * change reachable only through {@code @BeforeEach}/{@code @BeforeAll}
     * still selects the class's tests.
     */
    private Set<MethodNode> testNodesSelectedFor(
            ImpactSet impactSet,
            Set<MethodNode> testNodes,
            Map<MethodNode, Set<MethodNode>> lifecycleAttribution) {

        Set<MethodNode> selected = new LinkedHashSet<>();
        for (MethodNode node : impactedNodes(impactSet)) {
            if (testNodes.contains(node)) {
                selected.add(node);
            }
            Set<MethodNode> attributed = lifecycleAttribution.get(node);
            if (attributed != null) {
                for (MethodNode test : attributed) {
                    if (testNodes.contains(test)) {
                        selected.add(test);
                    }
                }
            }
        }
        return selected;
    }

    /** Every node in an impact set: changed, directly and transitively impacted. */
    private Set<MethodNode> impactedNodes(ImpactSet impactSet) {
        Set<MethodNode> nodes = new HashSet<>();
        nodes.addAll(impactSet.getChangedMethods());
        nodes.addAll(impactSet.getDirectlyImpacted());
        nodes.addAll(impactSet.getTransitivelyImpacted().keySet());
        return nodes;
    }

    /**
     * H7: the call chain explaining why a test was selected, nearest node
     * first, e.g. [CalculatorTest#testCompute, Calculator#compute]. When the
     * test was reached through a lifecycle method, the test is placed first
     * and the lifecycle method's own chain follows.
     *
     * The chain ends at the changed method. Because callee classes are
     * unresolved in the graph (see DependencyGraphBuilder), that last node's
     * class is usually "UNKNOWN"; it is rendered with the changed member's
     * real class and name so the explanation names the actual change.
     */
    private List<String> explanation(
            MethodNode testNode,
            MemberId changedMember,
            ImpactSet impactSet,
            Map<MethodNode, Set<MethodNode>> lifecycleAttribution) {

        MethodNode reached = testNode;
        if (!contained(impactSet, testNode)) {
            for (Map.Entry<MethodNode, Set<MethodNode>> entry : lifecycleAttribution.entrySet()) {
                if (entry.getValue().contains(testNode)) {
                    reached = entry.getKey();
                    break;
                }
            }
        }

        List<String> steps = new ArrayList<>();
        if (!reached.equals(testNode)) {
            steps.add(nameOf(testNode));
        }
        List<MethodNode> chain = impactSet.explanationChain(reached);
        for (int i = 0; i < chain.size(); i++) {
            MethodNode node = chain.get(i);
            boolean isEndOfChain = i == chain.size() - 1;
            if (isEndOfChain && "UNKNOWN".equals(node.getClassName())) {
                steps.add(changedMember.getClassName() + "#" + changedMember.getMemberName());
            } else {
                steps.add(nameOf(node));
            }
        }
        return steps;
    }

    private static boolean contained(ImpactSet impactSet, MethodNode node) {
        return impactSet.getChangedMethods().contains(node)
                || impactSet.getDirectlyImpacted().contains(node)
                || impactSet.getTransitivelyImpacted().containsKey(node);
    }

    /**
     * H7: tests that reference a method removed between the two revisions.
     * They cannot be selected through impact (the method no longer exists) but
     * may no longer compile, so they are reported separately instead of being
     * silently dropped.
     */
    private Set<String> potentiallyBrokenTests(
            List<String> testSources, Set<String> removedMethodNames) {

        if (removedMethodNames.isEmpty()) {
            return Collections.emptySet();
        }

        TestSourceProvider provider = new TestSourceProvider();
        Set<String> broken = new LinkedHashSet<>();
        for (String testSource : testSources) {
            for (MethodNode testNode
                    : provider.findTestMethodsCalling(testSource, removedMethodNames)) {
                broken.add(nameOf(testNode));
            }
        }
        return broken;
    }

    private static String nameOf(MethodNode node) {
        return node.getClassName() + "#" + node.getMethodName();
    }

    private Set<String> findImpactedTestsForFile(
            GitAnalyzer gitAnalyzer,
            String oldRevision,
            String newRevision,
            String sourceFilePath,
            String testFilePath) throws Exception {

        String oldSource;

        try {
            oldSource = gitAnalyzer.getFileContentAtRevision(
                    oldRevision,
                    sourceFilePath);
        } catch (IOException e) {
            // File did not exist at the old revision (e.g. it was newly added).
            // Treat as empty source so all methods are classified as "added".
            oldSource = "";
        }

        String newSource =
                gitAnalyzer.getFileContentAtRevision(
                        newRevision,
                        sourceFilePath);

        String testSource =
                gitAnalyzer.getFileContentAtRevision(
                        newRevision,
                        testFilePath);

        JavaParserAnalyzer parserAnalyzer =
                new JavaParserAnalyzer();

        List<MethodInfo> oldMethods =
                parserAnalyzer.extractMethods(oldSource);

        List<MethodInfo> newMethods =
                parserAnalyzer.extractMethods(newSource);

        MethodChangeDetector detector =
                new MethodChangeDetector();

        MethodChangeResult changeResult =
                detector.detectChanges(oldMethods, newMethods);

        TestImpactMapper testImpactMapper =
                new TestImpactMapper();

        Set<String> impactedTests = new HashSet<>();

        for (String changedMethod :
                changeResult.getModifiedMethods()) {

            impactedTests.addAll(
                    testImpactMapper.findTestsExercisingMethod(
                            testSource,
                            changedMethod));
        }

        for (String changedMethod :
                changeResult.getAddedMethods()) {

            impactedTests.addAll(
                    testImpactMapper.findTestsExercisingMethod(
                            testSource,
                            changedMethod));
        }

        return impactedTests;
    }
}