package com.analysis.tool.workflow;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.analysis.tool.execution.IsolatedRevisionCheckout;
import com.analysis.tool.execution.TestResult;
import com.analysis.tool.execution.TestRunResult;
import com.analysis.tool.execution.TestRunner;
import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.impact.ChangeImpactAnalyzer;
import com.analysis.tool.impact.ChangedFileClassifier;
import com.analysis.tool.impact.FileMapping;
import com.analysis.tool.impact.FileMappingDiscoverer;
import com.analysis.tool.impact.ImpactAnalyzer;
import com.analysis.tool.impact.TestSelectionReport;
import com.analysis.tool.impact.TestSourceProvider;
import com.analysis.tool.preflight.Preflight;
import com.analysis.tool.preflight.PreflightResult;
import com.analysis.tool.remote.RemoteRepository;
import com.analysis.tool.testgen.GeneratedTestWriter;
import com.analysis.tool.testgen.TestEmitter;
import com.analysis.tool.testgen.TestGenerator;

/**
 * The single orchestrator used by the interactive CLI, the remote workflow CLI
 * and (via them) the end-to-end demonstration.
 *
 * <p>It does not re-implement any engine: it drives the existing ones in the
 * order the tool documents -
 * {@link Preflight} &rarr; {@link GitAnalyzer} (file diff) &rarr;
 * {@link ChangedFileClassifier} (multi-language classification) &rarr;
 * {@link ChangeImpactAnalyzer} (member diff, dependency graph, reverse-BFS
 * impact, test selection) &rarr; {@link TestGenerator} (local JUnit 5
 * generation) &rarr; {@link IsolatedRevisionCheckout} + {@link TestRunner}
 * (isolated Maven execution and Surefire verification) - and collects a
 * structured {@link Result} from which the PHASE 11 terminal report is
 * rendered.
 *
 * <p>Only {@link Result#exitCode} and {@link Result#failureMessage} differ per
 * run: 1 = invalid revision, 2 = analysis/preflight failure, 3 = test execution
 * failure, 0 = success (including a run that reports warnings).
 */
public final class AnalysisWorkflow {

    /** Inputs of one workflow run. */
    public static final class Options {
        /** The Git repository to analyse (a local path or a cloned workspace). */
        public String repoPath;
        /** The older revision. */
        public String baseRevision;
        /** The newer revision. */
        public String targetRevision;
        /** Display name of the repository (project name or path). */
        public String repositoryLabel;
        /** Branch name, when known; may be null. */
        public String branch;
        /** Whether {@code --generate} was requested. */
        public boolean generate;
        /** Whether {@code --execute} was requested. */
        public boolean execute;
        /**
         * Explicit user confirmation to run build scripts/tests from this
         * repository (PHASE 4). Execution never happens without it.
         */
        public boolean executionConfirmed;
        /** An existing generated-test directory to include ({@code --generated}). */
        public String generatedDir;
        /** Where {@code --generate} writes its suite (default generated-tests). */
        public String outDir = "generated-tests";
        /** Impact traversal depth; -1 means unlimited. */
        public int maxDepth = ImpactAnalyzer.UNLIMITED_DEPTH;
        /** Timeout for each isolated Maven run. */
        public long timeoutMillis = TestRunner.DEFAULT_TIMEOUT_MILLIS;
    }

    /** Everything the terminal report needs, all computed from the real run. */
    public static final class Result {

        // REPOSITORY INFORMATION
        public String repositoryLabel = "";
        public String branch;
        public String baseRevision = "";
        public String targetRevision = "";

        // PROJECT DISCOVERY
        public boolean javaProjectDetected;
        public String buildSystem = "none";
        public String projectStructure = "unknown";
        public String testFramework = "none";
        public String compatibility = "UNKNOWN";
        public boolean mavenWrapperPresent;
        public final List<String> discoveryNotes = new ArrayList<>();

        // FILE CLASSIFICATION
        public Map<ChangedFileClassifier.FileCategory, Integer> fileCounts =
                new TreeMap<>();
        public int totalChangedFiles;

        // CHANGE DETECTION
        public List<GitAnalyzer.ChangedFile> diffEntries = new ArrayList<>();
        public int addedFiles;
        public int modifiedFiles;
        public int deletedFiles;
        public int changedClassCount;
        public Set<String> changedMembers = new TreeSet<>();
        public Set<String> impactedMethods = new TreeSet<>();
        public Set<String> directlyImpacted = new TreeSet<>();
        public Set<String> transitivelyImpacted = new TreeSet<>();
        public int unresolvedDependencies;

        // REGRESSION TEST SELECTION
        public int totalExistingTests;
        public int selectedExistingCount;
        public int selectedGeneratedCount;
        public int excludedCount;
        public double testReductionPercent;
        public Set<String> selectedIdentifiers = new TreeSet<>();
        public Set<String> generatedSelected = new TreeSet<>();
        public Set<String> untestedMethods = new TreeSet<>();
        public Set<String> removedMethods = new TreeSet<>();
        public Set<String> potentiallyBrokenTests = new TreeSet<>();
        public Set<String> unparsedFiles = new TreeSet<>();
        public Set<String> buildOrResourceChanges = new TreeSet<>();
        public String confidence = "UNKNOWN";
        public boolean truncated;

        // AUTOMATIC TEST GENERATION
        public boolean generationRequested;
        public int eligibleMethods;
        public int generatedClasses;
        public int generatedMethods;
        public int generationFailures;
        public String generationSummary;

        // TEST EXECUTION
        public boolean executionRequested;
        public boolean executionRan;
        public String executionStatus = "NOT RUN";
        public int existingSelectedExecuted;
        public int generatedConfirmed;
        public int generatedNotConfirmed;
        public int passed;
        public int failed;
        public int errors;
        public int skipped;
        public boolean compilationFailed;
        public boolean timedOut;
        public boolean buildSucceeded = true;
        public String preservedLogsPath;

        // MESSAGES
        public final List<String> notes = new ArrayList<>();
        /** Set when the change set is outside the Java testing scope (PHASE 10). */
        public String javaScopeMessage;
        /** Non-null when the run stopped before producing an analysis. */
        public String failureMessage;
        /** 0 = success, 1 = invalid revision, 2 = analysis failure, 3 = execution failure. */
        public int exitCode;

        /** True when Java analysis/selection/generation actually ran. */
        public boolean javaProcessingRan() {
            return failureMessage == null && javaScopeMessage == null;
        }

        /** Renders the PHASE 11 terminal report. */
        public void render(PrintStream out) {
            out.println();
            out.println("================================================================");
            out.println(" CHANGE-SENSITIVE REGRESSION TESTING REPORT");
            out.println("================================================================");

            out.println();
            out.println("REPOSITORY INFORMATION");
            out.println();
            out.println("Repository: " + repositoryLabel);
            out.println("Branch: " + (branch == null || branch.isEmpty() ? "(unknown)" : branch));
            out.println();
            out.println("Base revision: " + baseRevision);
            out.println("Target revision: " + targetRevision);

            out.println();
            out.println("PROJECT DISCOVERY");
            out.println();
            out.println("Java project detected: " + (javaProjectDetected ? "YES" : "NO"));
            out.println("Build system: " + buildSystem);
            out.println("Project structure: " + projectStructure);
            out.println("Test framework: " + testFramework);
            out.println("Compatibility: " + compatibility);
            out.println("Maven wrapper: " + (mavenWrapperPresent ? "present" : "absent"));
            for (String note : discoveryNotes) {
                out.println("  - " + note);
            }

            out.println();
            out.println("FILE CLASSIFICATION");
            out.println();
            out.println("Total changed files: " + totalChangedFiles);
            out.println("Java production files: " + count(ChangedFileClassifier.FileCategory.JAVA_PRODUCTION));
            out.println("Java test files: " + count(ChangedFileClassifier.FileCategory.JAVA_TEST));
            out.println("Build/configuration files: "
                    + (count(ChangedFileClassifier.FileCategory.BUILD)
                    + count(ChangedFileClassifier.FileCategory.CONFIGURATION)));
            out.println("Other programming language files: "
                    + count(ChangedFileClassifier.FileCategory.OTHER_LANGUAGE));
            out.println("Documentation files: " + count(ChangedFileClassifier.FileCategory.DOCUMENTATION));
            out.println("Static assets: " + count(ChangedFileClassifier.FileCategory.STATIC_ASSET));
            out.println("Binary/unknown files: " + count(ChangedFileClassifier.FileCategory.BINARY_OR_UNKNOWN));

            out.println();
            out.println("CHANGE DETECTION");
            out.println();
            out.println("Added files: " + addedFiles);
            out.println("Modified files: " + modifiedFiles);
            out.println("Deleted files: " + deletedFiles);
            out.println();
            out.println("Changed classes: " + changedClassCount);
            out.println("Changed methods: " + changedMembers.size());
            for (String member : changedMembers) {
                out.println("  - " + member);
            }

            out.println();
            out.println("DEPENDENCY AND IMPACT ANALYSIS");
            out.println();
            out.println("Directly impacted methods: " + directlyImpacted.size());
            out.println("Transitively impacted methods: " + transitivelyImpacted.size());
            out.println("Unresolved dependencies: " + unresolvedDependencies);
            for (String method : directlyImpacted) {
                out.println("  - (direct) " + method);
            }
            for (String method : transitivelyImpacted) {
                out.println("  - (transitive) " + method);
            }

            out.println();
            out.println("REGRESSION TEST SELECTION");
            out.println();
            out.println("Total existing tests: " + totalExistingTests);
            out.println("Selected tests: " + (selectedExistingCount + selectedGeneratedCount)
                    + " (" + selectedExistingCount + " existing, " + selectedGeneratedCount + " generated)");
            out.println("Excluded tests: " + excludedCount);
            out.println("Test reduction: " + formatPercent(testReductionPercent) + "%");
            out.println("Confidence: " + confidence);
            for (String identifier : generatedSelected) {
                out.println("  - " + identifier + " [generated]");
            }
            for (String identifier : selectedIdentifiers) {
                if (!generatedSelected.contains(identifier)) {
                    out.println("  - " + identifier);
                }
            }

            if (!untestedMethods.isEmpty()) {
                out.println();
                out.println("WARNING - changed methods not reached by any test (" + untestedMethods.size() + "):");
                for (String method : untestedMethods) {
                    out.println("  - " + method);
                }
            }
            if (!removedMethods.isEmpty()) {
                out.println();
                out.println("Removed methods (" + removedMethods.size() + "):");
                for (String method : removedMethods) {
                    out.println("  - " + method);
                }
            }
            if (!potentiallyBrokenTests.isEmpty()) {
                out.println();
                out.println("Potentially broken tests (" + potentiallyBrokenTests.size()
                        + ") - reference a removed method:");
                for (String test : potentiallyBrokenTests) {
                    out.println("  - " + test);
                }
            }
            if (!unparsedFiles.isEmpty()) {
                out.println();
                out.println("Unparsed files (" + unparsedFiles.size() + "):");
                for (String file : unparsedFiles) {
                    out.println("  - " + file);
                }
            }
            if (!buildOrResourceChanges.isEmpty()) {
                out.println();
                out.println("Build/resource changes (" + buildOrResourceChanges.size()
                        + ") - not analysable at member level; the selection is conservative:");
                for (String file : buildOrResourceChanges) {
                    out.println("  - " + file);
                }
            }
            if (truncated) {
                out.println();
                out.println("WARNING - impact analysis was truncated at the requested depth; "
                        + "the selection may be incomplete.");
            }

            out.println();
            out.println("AUTOMATIC TEST GENERATION");
            out.println();
            out.println("Generated test classes: " + generatedClasses);
            out.println("Generated test methods: " + generatedMethods);
            out.println("Generation failures: " + generationFailures);
            if (generationSummary != null) {
                out.println("Detail: " + generationSummary);
            }

            out.println();
            out.println("TEST EXECUTION");
            out.println();
            out.println("Existing selected tests executed: " + existingSelectedExecuted);
            out.println("Generated tests executed: " + generatedConfirmed
                    + (selectedGeneratedCount > 0 ? " / " + selectedGeneratedCount + " selected" : ""));
            out.println();
            out.println("Passed: " + passed);
            out.println("Failed: " + failed);
            out.println("Errors: " + errors);
            out.println("Skipped: " + skipped);
            out.println("Not confirmed: " + generatedNotConfirmed);
            if (compilationFailed) {
                out.println("Compilation failed: YES");
            }
            if (timedOut) {
                out.println("Timed out: YES");
            }
            if (preservedLogsPath != null) {
                out.println("Logs preserved at: " + preservedLogsPath);
            }

            out.println();
            out.println("EXECUTION STATUS");
            out.println();
            out.println(executionStatus);

            for (String note : notes) {
                out.println();
                out.println("NOTE: " + note);
            }

            out.println();
            out.println("================================================================");
            out.println(" FINAL RESULT");
            out.println("================================================================");
            out.println();
            out.println(explanation());
        }

        private int count(ChangedFileClassifier.FileCategory category) {
            Integer value = fileCounts.get(category);
            return value == null ? 0 : value;
        }

        /** The concise, truthful explanation of the outcome. */
        public String explanation() {
            if (failureMessage != null) {
                return "The run stopped before completing: " + failureMessage;
            }
            if (javaScopeMessage != null) {
                return javaScopeMessage;
            }

            StringBuilder text = new StringBuilder();
            text.append("Analysis completed for ").append(totalChangedFiles)
                    .append(" changed file(s): ").append(changedMembers.size())
                    .append(" changed method(s) and ").append(impactedMethods.size())
                    .append(" impacted method(s) were identified, and ")
                    .append(selectedExistingCount).append(" of ").append(totalExistingTests)
                    .append(" existing test(s) were selected (")
                    .append(formatPercent(testReductionPercent)).append("% reduction, confidence ")
                    .append(confidence).append(").");

            if (generationRequested) {
                text.append(" ").append(generatedMethods).append(" test method(s) were generated locally.")
                        .append(generationFailures > 0 ? " " + generationFailures + " method(s) could not be generated." : "");
            }

            if (!executionRequested) {
                text.append(" Test execution was not requested.");
            } else if (!executionRan) {
                text.append(" Test execution did not run (").append(executionStatus).append(").");
            } else if ("SUCCESS".equals(executionStatus)) {
                text.append(" The selected tests were executed in an isolated checkout and all of them passed; ")
                        .append(generatedConfirmed).append(" generated test(s) were confirmed by Surefire.");
            } else if ("PARTIAL SUCCESS".equals(executionStatus)) {
                text.append(" Tests executed, but not every generated test was confirmed by Surefire.");
            } else {
                text.append(" Test execution FAILED (")
                        .append(compilationFailed ? "compilation failure" : failed + errors + " failing test(s)")
                        .append("); the analysis selection above is still valid.");
            }
            return text.toString();
        }
    }

    private AnalysisWorkflow() {
    }

    private static final ChangedFileClassifier CLASSIFIER = new ChangedFileClassifier();

    public static Result analyze(Options options, PrintStream out) {
        Result result = new Result();
        result.repositoryLabel = options.repositoryLabel != null
                ? options.repositoryLabel
                : new File(options.repoPath).getAbsoluteFile().getName();
        result.branch = options.branch;
        result.baseRevision = options.baseRevision;
        result.targetRevision = options.targetRevision;
        result.generationRequested = options.generate;
        result.executionRequested = options.execute;

        // ---- Preflight / project discovery (H9, reused unchanged) ----------
        PreflightResult preflight = Preflight.validate(options.repoPath);
        result.compatibility = preflight.getSummary();
        discover(options.repoPath, preflight, result);

        if (preflight.isUnsupported()) {
            result.failureMessage = "preflight validation refused this repository";
            result.exitCode = 2;
            for (String reason : preflight.getRejections()) {
                result.discoveryNotes.add("refused: " + reason);
            }
            result.render(out);
            return result;
        }
        for (String reduction : preflight.getReductions()) {
            result.discoveryNotes.add("reduced confidence: " + reduction);
        }

        try {
            // ---- Revision validation --------------------------------------
            GitAnalyzer revisionCheck = new GitAnalyzer(options.repoPath);
            try {
                if (!revisionCheck.canResolve(options.baseRevision)) {
                    result.failureMessage = "invalid base revision (cannot be resolved in "
                            + options.repoPath + "): " + options.baseRevision;
                    result.exitCode = 1;
                    result.render(out);
                    return result;
                }
                if (!revisionCheck.canResolve(options.targetRevision)) {
                    result.failureMessage = "invalid target revision (cannot be resolved in "
                            + options.repoPath + "): " + options.targetRevision;
                    result.exitCode = 1;
                    result.render(out);
                    return result;
                }
            } finally {
                revisionCheck.close();
            }

            // ---- Change detection (file level) ---------------------------
            result.diffEntries = changedFileEntries(options.repoPath,
                    options.baseRevision, options.targetRevision);
            List<String> changedPaths = new ArrayList<>();
            for (GitAnalyzer.ChangedFile file : result.diffEntries) {
                changedPaths.add(file.getPath());
                switch (file.getChangeType()) {
                    case ADDED:
                        result.addedFiles++;
                        break;
                    case DELETED:
                        result.deletedFiles++;
                        break;
                    default:
                        result.modifiedFiles++;
                        break;
                }
            }
            result.totalChangedFiles = result.diffEntries.size();
            result.fileCounts = CLASSIFIER.countByCategory(changedPaths);

            // ---- PHASE 10: is there anything in the Java testing scope? ---
            boolean anyJavaChange = false;
            for (String path : changedPaths) {
                if (CLASSIFIER.isJavaChange(path)) {
                    anyJavaChange = true;
                    break;
                }
            }
            // A changed build file or resource cannot be analysed at member
            // level, but it can change behaviour in ways the graph cannot see,
            // so the pipeline is still run to fall back to the full suite
            // (PHASE 7/10) rather than being reported as "no Java changes".
            boolean anyBuildOrResourceChange = false;
            for (String path : changedPaths) {
                ChangedFileClassifier.Category category = CLASSIFIER.classify(path);
                if (category == ChangedFileClassifier.Category.BUILD
                        || category == ChangedFileClassifier.Category.RESOURCE) {
                    anyBuildOrResourceChange = true;
                    break;
                }
            }
            boolean repositoryHasJava = repositoryContainsJava(options.repoPath, options.targetRevision);

            if (!anyJavaChange && !anyBuildOrResourceChange) {
                if (result.totalChangedFiles == 0) {
                    result.javaScopeMessage = "No files changed between the two revisions, so there was "
                            + "nothing to analyse and no Java regression test was required.";
                } else if (!repositoryHasJava) {
                    result.javaScopeMessage = "No Java source files were found in this repository. "
                            + "Java regression test generation was not required. "
                            + "Other-language changes were detected but are outside the current testing scope.";
                } else {
                    result.javaScopeMessage = "No supported Java source changes were detected. "
                            + "Java regression test generation was not required. "
                            + "Other-language changes were detected but are outside the current testing scope.";
                }
                addMultiLanguageNotes(result);
                result.executionStatus = "NOT RUN";
                result.render(out);
                return result;
            }

            addMultiLanguageNotes(result);

            // ---- Build/configuration change -> conservative selection ------
            for (GitAnalyzer.ChangedFile file : result.diffEntries) {
                ChangedFileClassifier.FileCategory category = CLASSIFIER.categorize(file.getPath());
                if (category == ChangedFileClassifier.FileCategory.BUILD) {
                    result.notes.add("A build file changed (" + file.getPath() + "): the impact can extend "
                            + "beyond the changed methods, so the selection falls back to the full "
                            + "compatible test suite.");
                }
            }

            // ---- Generation (PHASE 8, reuses the existing local generator) --
            Map<String, String> generatedTestFiles = new TreeMap<>();
            if (options.generatedDir != null) {
                generatedTestFiles.putAll(readGeneratedTests(options.generatedDir));
            }
            if (options.generate) {
                generate(options, result, generatedTestFiles);
            } else {
                eligibleProductionMethods(result, generatedTestFiles);
            }

            List<String> generatedSources = new ArrayList<>(generatedTestFiles.values());

            // ---- Analysis (reuses the single existing pipeline) -----------
            List<FileMapping> mappings = discoverMappings(options.repoPath,
                    options.baseRevision, options.targetRevision);

            TestSelectionReport report = new ChangeImpactAnalyzer().analyzeTransitively(
                    options.repoPath, options.baseRevision, options.targetRevision,
                    mappings, generatedSources, options.maxDepth, changedPaths);

            result.changedMembers = new TreeSet<>(report.getChangedMembers());
            result.impactedMethods = new TreeSet<>(report.getImpactedMethods());
            result.directlyImpacted = new TreeSet<>(report.getDirectlyImpactedMethods());
            result.transitivelyImpacted = new TreeSet<>(report.getTransitivelyImpactedMethods());
            result.unresolvedDependencies = countUnresolved(result);
            result.changedClassCount = countChangedClasses(result.changedMembers);
            result.untestedMethods = new TreeSet<>(report.getUntestedChangedMethods());
            result.removedMethods = new TreeSet<>(report.getRemovedMethods());
            result.potentiallyBrokenTests = new TreeSet<>(report.getPotentiallyBrokenTests());
            result.unparsedFiles = new TreeSet<>(report.getUnparsedFiles());
            result.buildOrResourceChanges = new TreeSet<>(report.getBuildOrResourceChanges());
            result.confidence = report.getConfidence().name();
            result.truncated = report.isTruncated();

            Set<String> generatedClassNames = new TreeSet<>();
            for (String name : generatedTestFiles.keySet()) {
                generatedClassNames.add(stripJavaExtension(name));
            }

            result.selectedIdentifiers = new TreeSet<>(report.getSelectedTestIdentifiers());
            for (String identifier : result.selectedIdentifiers) {
                if (isGeneratedTest(identifier, generatedClassNames)) {
                    result.generatedSelected.add(identifier);
                }
            }
            result.selectedGeneratedCount = result.generatedSelected.size();
            result.selectedExistingCount = result.selectedIdentifiers.size() - result.selectedGeneratedCount;

            // ---- Selection metrics (PHASE 7 formula) ----------------------
            result.totalExistingTests = countExistingTestMethods(options.repoPath, options.targetRevision);
            result.excludedCount = Math.max(0, result.totalExistingTests - result.selectedExistingCount);
            result.testReductionPercent = result.totalExistingTests == 0
                    ? 0.0
                    : ((result.totalExistingTests - result.selectedExistingCount)
                    / (double) result.totalExistingTests) * 100.0;
            if (result.testReductionPercent < 0) {
                result.testReductionPercent = 0;
            }
            if (result.testReductionPercent > 100) {
                result.testReductionPercent = 100;
            }

            // ---- Isolated execution (PHASE 9) -----------------------------
            if (options.execute) {
                if (result.selectedIdentifiers.isEmpty()) {
                    result.executionStatus = "NOT RUN";
                    result.notes.add("Execution was skipped: no tests were selected, so there was nothing to run.");
                } else if (!options.executionConfirmed) {
                    result.executionStatus = "NOT RUN";
                    result.notes.add("Execution was skipped: running build scripts and tests in this repository "
                            + "requires explicit confirmation (pass --yes, or confirm in the interactive prompt).");
                } else {
                    runExecution(options, result, generatedTestFiles, generatedClassNames, out);
                }
            } else {
                result.executionStatus = "NOT RUN";
                result.notes.add("Test execution was not requested.");
            }

            // ---- Exit code ------------------------------------------------
            if ("FAILED".equals(result.executionStatus)) {
                result.exitCode = 3;
            } else {
                result.exitCode = 0;
            }
            result.render(out);
            return result;

        } catch (Exception e) {
            result.failureMessage = e.getMessage() == null ? e.toString() : e.getMessage();
            result.exitCode = 2;
            result.render(out);
            return result;
        }
    }

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------

    private static void discover(String repoPath, PreflightResult preflight, Result result) {
        Path dir = Paths.get(repoPath);
        boolean mainJava = Files.isDirectory(dir.resolve("src/main/java"));
        boolean testJava = Files.isDirectory(dir.resolve("src/test/java"));
        boolean pom = Files.isRegularFile(dir.resolve("pom.xml"));
        boolean gradle = Files.isRegularFile(dir.resolve("build.gradle"))
                || Files.isRegularFile(dir.resolve("build.gradle.kts"));
        result.mavenWrapperPresent = Files.isRegularFile(dir.resolve("mvnw"))
                || Files.isRegularFile(dir.resolve("mvnw.cmd"));

        result.javaProjectDetected = mainJava || testJava || pom;

        if (pom) {
            result.buildSystem = "Maven";
        } else if (gradle) {
            result.buildSystem = "Gradle";
        } else {
            result.buildSystem = "none";
        }

        String pomText = readIfExists(dir.resolve("pom.xml"));
        result.projectStructure = pomText != null && pomText.contains("<modules")
                ? "Multi-module" : (pom ? "Single module" : "unknown");

        result.testFramework = detectTestFramework(pomText, dir);

        if (!result.javaProjectDetected) {
            result.discoveryNotes.add("no Java sources or Maven build found at the repository root");
        }
        if (result.buildSystem.equals("Gradle") && pom == false) {
            result.discoveryNotes.add("Gradle-only project: Maven/Surefire execution is not supported");
        }
        if (preflight != null && preflight.isUnsupported() == false && pom == false) {
            result.discoveryNotes.add("no pom.xml: the analysis runs, but Maven test execution needs a Maven project");
        }
    }

    private static String detectTestFramework(String pomText, Path dir) {
        boolean jupiter = false;
        boolean junit4 = false;
        boolean testng = false;
        if (pomText != null) {
            jupiter = pomText.contains("junit-jupiter");
            junit4 = pomText.contains("<artifactId>junit</artifactId>") || pomText.contains("junit-vintage");
            testng = pomText.contains("testng");
        }
        if (!jupiter) {
            Set<String> testImports = new TreeSet<>();
            for (Path path : listFiles(dir, "src/test/java")) {
                String text = readIfExists(path);
                if (text == null) {
                    continue;
                }
                if (text.contains("org.junit.jupiter")) {
                    testImports.add("jupiter");
                    jupiter = true;
                }
                if (text.contains("org.junit.Test")) {
                    junit4 = true;
                }
                if (text.contains("org.testng")) {
                    testng = true;
                }
            }
            if (testImports.isEmpty() && !jupiter && !junit4 && !testng) {
                return "none";
            }
        }
        if (jupiter) {
            return "JUnit 5";
        }
        if (junit4) {
            return "JUnit 4";
        }
        if (testng) {
            return "TestNG";
        }
        return "none";
    }

    private static boolean repositoryContainsJava(String repoPath, String revision) {
        try {
            GitAnalyzer git = new GitAnalyzer(repoPath);
            try {
                for (String path : git.listFilesAtRevision(revision)) {
                    if (path.endsWith(".java")) {
                        return true;
                    }
                }
                return false;
            } finally {
                git.close();
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static void addMultiLanguageNotes(Result result) {
        int otherLanguages = count(result, ChangedFileClassifier.FileCategory.OTHER_LANGUAGE);
        if (otherLanguages > 0) {
            result.notes.add(otherLanguages + " other-language file(s) were detected but are outside the "
                    + "current testing scope (this system is Java-focused).");
        }
        int assets = count(result, ChangedFileClassifier.FileCategory.STATIC_ASSET)
                + count(result, ChangedFileClassifier.FileCategory.BINARY_OR_UNKNOWN);
        if (assets > 0) {
            result.notes.add(assets + " static-asset/binary file(s) changed; they are never parsed as Java "
                    + "and no Java test is generated for them.");
        }
    }

    private static int count(Result result, ChangedFileClassifier.FileCategory category) {
        Integer value = result.fileCounts.get(category);
        return value == null ? 0 : value;
    }

    // ------------------------------------------------------------------
    // Generation
    // ------------------------------------------------------------------

    private static void generate(Options options, Result result, Map<String, String> generatedTestFiles) {
        try {
            Path outRoot = Paths.get(options.outDir);
            String projectName = RemoteRepository.sanitizeProjectName(result.repositoryLabel);
            List<String> productionSources =
                    com.analysis.tool.cli.GenerateTestsCli.readProductionSources(
                            options.repoPath, options.targetRevision);
            if (productionSources.isEmpty()) {
                result.generationSummary = "no .java files under src/main/java at " + options.targetRevision;
                result.notes.add("Automatic test generation was skipped: " + result.generationSummary);
                return;
            }
            eligibleProductionMethods(result, generatedTestFiles);

            TestEmitter.Result genResult = new TestGenerator()
                    .generate(productionSources, projectName, options.targetRevision);
            Path written = new GeneratedTestWriter().write(outRoot, projectName, options.targetRevision, genResult);
            for (Map.Entry<String, String> entry : genResult.getFiles().entrySet()) {
                generatedTestFiles.put(fileName(entry.getKey()), entry.getValue());
            }
            result.generatedClasses = genResult.getFiles().size();
            result.generatedMethods = genResult.getGeneratedCount();
            result.generationFailures = genResult.getSkippedCount() + genResult.getDroppedCount();
            result.generationSummary = genResult.getGeneratedCount() + " generated test method(s) in "
                    + genResult.getFiles().size() + " class(es) from " + productionSources.size()
                    + " production file(s), written to " + written
                    + " (never inside the analysed repository)";
        } catch (Exception e) {
            result.generationSummary = "generation failed: " + e.getMessage();
            result.notes.add("Automatic test generation failed: " + e.getMessage());
        }
    }

    /**
     * PHASE 8 "eligible production methods": the production methods the change
     * touched, either directly (changed) or through the call graph (impacted).
     * Computed from the real analysis, never hardcoded.
     */
    private static void eligibleProductionMethods(Result result, Map<String, String> generatedTestFiles) {
        Set<String> eligible = new TreeSet<>(result.changedMembers);
        eligible.addAll(result.impactedMethods);
        result.eligibleMethods = eligible.size();
    }

    private static int countChangedClasses(Set<String> changedMembers) {
        Set<String> classes = new TreeSet<>();
        for (String member : changedMembers) {
            int hash = member.indexOf('#');
            classes.add(hash < 0 ? member : member.substring(0, hash));
        }
        return classes.size();
    }

    private static int countUnresolved(Result result) {
        int unresolved = 0;
        for (String method : result.impactedMethods) {
            int hash = method.indexOf('#');
            String className = hash < 0 ? method : method.substring(0, hash);
            if ("UNKNOWN".equals(className)) {
                unresolved++;
            }
        }
        return unresolved;
    }

    private static Map<String, String> readGeneratedTests(String generatedDir) {
        Map<String, String> files = new TreeMap<>();
        Path dir = Paths.get(generatedDir).resolve("src/test/java/rts/generated");
        if (!Files.isDirectory(dir)) {
            return files;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path file : (Iterable<Path>) stream
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()::iterator) {
                files.put(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // An unreadable generated directory simply contributes no extra tests.
        }
        return files;
    }

    // ------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------

    private static void runExecution(Options options, Result result,
                                     Map<String, String> generatedTestFiles,
                                     Set<String> generatedClassNames, PrintStream out) throws Exception {

        out.println();
        out.println("Executing in an isolated checkout of " + options.targetRevision
                + " (this launches real Maven builds; the analysed repository is never modified)...");

        try (IsolatedRevisionCheckout checkout =
                     IsolatedRevisionCheckout.open(options.repoPath, options.targetRevision)) {

            if (!generatedTestFiles.isEmpty()) {
                Path destination = checkout.getDirectory().resolve("src/test/java/rts/generated");
                Files.createDirectories(destination);
                for (Map.Entry<String, String> entry : generatedTestFiles.entrySet()) {
                    Files.writeString(destination.resolve(entry.getKey()), entry.getValue(),
                            StandardCharsets.UTF_8);
                }
                out.println("Included " + generatedTestFiles.size()
                        + " generated test file(s) in the isolated checkout (original repository untouched).");
            }

            TestRunner runner = new TestRunner(options.timeoutMillis);
            TestRunResult fullRun = runner.runFullSuite(checkout.getPath());
            TestRunResult selectedRun = runner.runSelectedTests(checkout.getPath(), result.selectedIdentifiers);
            result.executionRan = true;

            result.passed = selectedRun.getPassed();
            result.failed = selectedRun.getFailed();
            result.errors = selectedRun.getErrors();
            result.skipped = selectedRun.getSkipped();
            result.compilationFailed = selectedRun.isCompilationFailed() || fullRun.isCompilationFailed();
            result.timedOut = selectedRun.isTimedOut() || fullRun.isTimedOut();
            result.buildSucceeded = fullRun.isBuildSucceeded() && selectedRun.isBuildSucceeded();

            List<TestResult> results = selectedRun.getTestResults();
            boolean perTest = selectedRun.hasPerTestResults();
            for (String identifier : result.selectedIdentifiers) {
                if (isGeneratedTest(identifier, generatedClassNames)) {
                    if (perTest && wasExecuted(identifier, results)) {
                        result.generatedConfirmed++;
                    } else {
                        result.generatedNotConfirmed++;
                    }
                } else if (perTest && wasExecuted(identifier, results)) {
                    result.existingSelectedExecuted++;
                }
            }

            out.println("Full suite      : " + describe(fullRun));
            out.println("Selected run    : " + describe(selectedRun));
            for (String identifier : result.generatedSelected) {
                if (perTest && wasExecuted(identifier, results)) {
                    out.println("  - " + identifier + " : EXECUTED (confirmed by Maven Surefire)");
                } else {
                    out.println("  - " + identifier
                            + " : NOT confirmed by Maven (not reported as executed)");
                }
            }
            out.println("Generated tests executed: " + result.generatedConfirmed + " / "
                    + result.selectedGeneratedCount + " confirmed by Maven");

            boolean executionFailed = !result.buildSucceeded || result.timedOut
                    || result.compilationFailed
                    || fullRun.getFailedOrErrors() > 0 || selectedRun.getFailedOrErrors() > 0;

            if (executionFailed) {
                result.executionStatus = "FAILED";
                Path preserved = preserveLogs(checkout.getDirectory());
                result.preservedLogsPath = preserved == null ? null : preserved.toString();
                out.println("Execution had failures; logs preserved at: " + result.preservedLogsPath);
            } else if (result.generatedNotConfirmed > 0) {
                result.executionStatus = "PARTIAL SUCCESS";
            } else {
                result.executionStatus = "SUCCESS";
            }
        }
    }

    private static boolean isGeneratedTest(String identifier, Set<String> generatedClassNames) {
        if (generatedClassNames.isEmpty()) {
            return false;
        }
        return generatedClassNames.contains(simpleClassName(identifier));
    }

    private static boolean wasExecuted(String identifier, List<TestResult> results) {
        String className = simpleClassName(identifier);
        int hash = identifier.indexOf('#');
        String method = hash < 0 ? "" : identifier.substring(hash + 1);
        for (TestResult result : results) {
            String resultClass = result.getClassName();
            int dot = resultClass.lastIndexOf('.');
            if (dot >= 0) {
                resultClass = resultClass.substring(dot + 1);
            }
            if (resultClass.equals(className) && result.getMethodName().equals(method)) {
                return true;
            }
        }
        return false;
    }

    private static String simpleClassName(String identifier) {
        int hash = identifier.indexOf('#');
        String className = hash < 0 ? identifier : identifier.substring(0, hash);
        int dollar = className.indexOf('$');
        if (dollar >= 0) {
            className = className.substring(0, dollar);
        }
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    private static String describe(TestRunResult run) {
        return run.getTotalTests() + " tests, " + run.getPassed() + " passed, "
                + run.getFailed() + " failed, " + run.getErrors() + " errored, "
                + run.getSkipped() + " skipped, " + run.getExecutionTimeMillis() + " ms, build "
                + (run.isBuildSucceeded() ? "succeeded" : "FAILED")
                + ", compile " + (run.isCompilationFailed() ? "FAILED" : "ok");
    }

    private static Path preserveLogs(Path checkoutDir) {
        try {
            Path logsDir = Files.createTempDirectory("rts-execution-logs-");
            Path log = checkoutDir.resolve("target").resolve("maven-execution.log");
            if (Files.exists(log)) {
                Files.copy(log, logsDir.resolve("maven-execution.log"));
            }
            Path reports = checkoutDir.resolve("target").resolve("surefire-reports");
            if (Files.isDirectory(reports)) {
                Path destination = Files.createDirectories(logsDir.resolve("surefire-reports"));
                try (Stream<Path> files = Files.list(reports)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        if (Files.isRegularFile(file)) {
                            Files.copy(file, destination.resolve(file.getFileName()));
                        }
                    }
                }
            }
            return logsDir;
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int countExistingTestMethods(String repoPath, String revision) throws IOException {
        TestSourceProvider provider = new TestSourceProvider();
        Set<String> identifiers = new TreeSet<>();
        GitAnalyzer git = new GitAnalyzer(repoPath);
        try {
            for (String path : git.listFilesAtRevision(revision)) {
                if (!TestSourceProvider.isTestSourceFile(path)) {
                    continue;
                }
                String source;
                try {
                    source = git.getFileContentAtRevision(revision, path);
                } catch (IOException e) {
                    continue;
                }
                try {
                    identifiers.addAll(provider.discover(source).getIdentifiers().values());
                } catch (RuntimeException e) {
                    // An unparsable test file is reported by the analysis itself;
                    // it simply cannot be counted here.
                }
            }
        } finally {
            git.close();
        }
        return identifiers.size();
    }

    private static List<FileMapping> discoverMappings(String repoPath, String base, String target)
            throws IOException {
        GitAnalyzer git = new GitAnalyzer(repoPath);
        try {
            Set<String> allFiles = new TreeSet<>(git.listFilesAtRevision(base));
            allFiles.addAll(git.listFilesAtRevision(target));
            return new FileMappingDiscoverer().discover(new ArrayList<>(allFiles));
        } finally {
            git.close();
        }
    }

    private static List<GitAnalyzer.ChangedFile> changedFileEntries(String repoPath, String base, String target)
            throws IOException {
        GitAnalyzer git = new GitAnalyzer(repoPath);
        try {
            return git.getChangedFileEntries(base, target);
        } finally {
            git.close();
        }
    }

    private static List<Path> listFiles(Path root, String relative) {
        Path dir = root.resolve(relative);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String readIfExists(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String stripJavaExtension(String fileName) {
        return fileName.endsWith(".java")
                ? fileName.substring(0, fileName.length() - ".java".length())
                : fileName;
    }

    private static String fileName(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash < 0 ? relativePath : relativePath.substring(slash + 1);
    }

    private static String formatPercent(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
