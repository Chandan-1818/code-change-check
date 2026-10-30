package com.analysis.tool.cli;

import com.analysis.tool.execution.*;
import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.impact.*;
import com.analysis.tool.metrics.EvaluationMetrics;

import java.io.PrintStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Command-line entry point for change-sensitive regression test selection.
 *
 * Usage:
 *   SelectTestsCli <repoPath> <oldRev> <newRev> [<sourceFile=testFile> ...]
 *       [--generated=<revisionDir>] [--execute]
 *
 * Each sourceFile=testFile argument pairs a production file with its test
 * file, both relative to the repository root. The tool compares the two
 * revisions and prints the test methods that should be re-run, including
 * tests that reach a changed method through a chain of calls. Changed
 * methods that no test reaches are listed separately as a warning, so an
 * empty selection is not mistaken for "nothing to run".
 *
 * --generated=<revisionDir> names a directory written by the test generator
 * (it must contain src/test/java/rts/generated/*.java). Those generated tests
 * take part in the analysis: a changed method reached only by a generated test
 * is selected and not reported as untested. A missing directory or one without
 * generated tests is an invalid argument (exit 1), never silently ignored.
 *
 * If the optional trailing --execute flag is given, the tool additionally
 * runs the full test suite and the selected tests as real Maven
 * subprocesses in the target repository, and prints a comparison
 * (counts, wall-clock time, and test reduction %). This is skipped, with
 * an explanation, if no tests were selected. Execution is off by default
 * because it is comparatively slow (launches real Maven builds).
 *
 * Exit codes: 0 = success (including runs that print warnings),
 * 1 = invalid arguments, 2 = analysis failed.
 *
 * If no sourceFile=testFile pairs are given, they are discovered from the
 * files present at either revision, using the naming convention Foo.java -> FooTest.java.
 */
public class SelectTestsCli {

    private static final String GENERATED_PREFIX = "--generated=";

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        String generatedDir = null;
        List<String> remaining = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith(GENERATED_PREFIX)) {
                generatedDir = arg.substring(GENERATED_PREFIX.length());
            } else {
                remaining.add(arg);
            }
        }
        String[] filteredArgs = remaining.toArray(new String[0]);

        boolean execute = filteredArgs.length > 0 && "--execute".equals(filteredArgs[filteredArgs.length - 1]);
        String[] positionalArgs = execute ? Arrays.copyOf(filteredArgs, filteredArgs.length - 1) : filteredArgs;

        if (positionalArgs.length < 3 || (generatedDir != null && generatedDir.isEmpty())) {
            err.println("Usage: SelectTestsCli <repoPath> <oldRev> <newRev> [<sourceFile=testFile> ...] "
                    + "[--generated=<revisionDir>] [--execute]");
            return 1;
        }

        String repoPath = positionalArgs[0];
        String oldRevision = positionalArgs[1];
        String newRevision = positionalArgs[2];

        List<FileMapping> mappings = new ArrayList<>();
        for (int i = 3; i < positionalArgs.length; i++) {
            int separator = positionalArgs[i].indexOf('=');
            if (separator <= 0 || separator == positionalArgs[i].length() - 1) {
                err.println("Invalid file pair (expected sourceFile=testFile): " + positionalArgs[i]);
                return 1;
            }
            mappings.add(new FileMapping(
                    positionalArgs[i].substring(0, separator),
                    positionalArgs[i].substring(separator + 1)));
        }

        List<String> generatedSources = new ArrayList<>();
        if (generatedDir != null) {
            Path generatedTests = Paths.get(generatedDir).resolve("src/test/java/rts/generated");
            if (!Files.isDirectory(generatedTests)) {
                err.println("Invalid --generated (no src/test/java/rts/generated under it): " + generatedDir);
                return 1;
            }
            try (Stream<Path> files = Files.list(generatedTests)) {
                List<Path> javaFiles = files.filter(p -> p.getFileName().toString().endsWith(".java"))
                        .sorted().collect(Collectors.toList());
                for (Path file : javaFiles) {
                    generatedSources.add(Files.readString(file, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                err.println("Could not read generated tests: " + e.getMessage());
                return 2;
            }
            if (generatedSources.isEmpty()) {
                err.println("Invalid --generated (no .java files in " + generatedTests + ")");
                return 1;
            }
        }

        try {
            boolean autoDiscovered = mappings.isEmpty();
            if (autoDiscovered) {
                mappings = discoverMappings(repoPath, oldRevision, newRevision);
            }

            TestSelectionReport report = new ChangeImpactAnalyzer()
                    .analyzeTransitively(repoPath, oldRevision, newRevision, mappings, generatedSources);

            Set<String> selectedTests = new TreeSet<>(report.getSelectedTests());
            Set<String> selectedTestIdentifiers = new TreeSet<>(report.getSelectedTestIdentifiers());
            Set<String> untestedMethods = new TreeSet<>(report.getUntestedChangedMethods());
            Set<String> removedMethods = new TreeSet<>(report.getRemovedMethods());

            out.println("Repository     : " + repoPath);
            out.println("Compared       : " + oldRevision + " -> " + newRevision);
            out.println("File pairs     : " + mappings.size() + (autoDiscovered ? " (auto-discovered)" : ""));
            if (generatedDir != null) {
                out.println("Generated tests: " + generatedSources.size() + " file(s) from " + generatedDir);
            }

            if (!selectedTests.isEmpty()) {
                out.println("Selected tests (" + selectedTests.size() + "):");
                for (String test : selectedTests) {
                    out.println("  - " + test);
                }
            } else if (untestedMethods.isEmpty()) {
                out.println("Selected tests : none (no impacted tests found)");
            } else {
                out.println("Selected tests : none");
            }

            if (!untestedMethods.isEmpty()) {
                out.println("WARNING - changed methods not reached by any test (" + untestedMethods.size() + "):");
                for (String method : untestedMethods) {
                    out.println("  - " + method);
                }
            }

            if (!removedMethods.isEmpty()) {
                out.println("Removed methods (" + removedMethods.size() + "):");
                for (String method : removedMethods) {
                    out.println("  - " + method);
                }
            }

            if (execute) {
                if (selectedTestIdentifiers.isEmpty()) {
                    out.println("Execution skipped: no tests were selected, so there is nothing to run.");
                } else {
                    out.println();
                    out.println("Executing full suite and selected tests (this launches real Maven builds)...");

                    TestRunner runner = new TestRunner();
                    TestRunResult fullRun = runner.runFullSuite(repoPath);
                    TestRunResult selectedRun = runner.runSelectedTests(repoPath, selectedTestIdentifiers);
                    EvaluationMetrics metrics = EvaluationMetrics.compute(fullRun, selectedRun);

                    out.println("Full suite     : " + fullRun.getTotalTests() + " tests, "
                            + fullRun.getPassed() + " passed, " + fullRun.getFailed() + " failed, "
                            + fullRun.getExecutionTimeMillis() + " ms, build "
                            + (fullRun.isBuildSucceeded() ? "succeeded" : "FAILED"));
                    out.println("Selected tests : " + selectedRun.getTotalTests() + " tests, "
                            + selectedRun.getPassed() + " passed, " + selectedRun.getFailed() + " failed, "
                            + selectedRun.getExecutionTimeMillis() + " ms, build "
                            + (selectedRun.isBuildSucceeded() ? "succeeded" : "FAILED"));
                    out.println("Test reduction : " + String.format("%.1f", metrics.getTestReductionPercent()) + "%");
                    out.println("Time reduction : " + String.format("%.1f", metrics.getTimeReductionPercent())
                            + "% (informational - unreliable on small suites due to Maven/JVM startup overhead)");
                }
            }

            return 0;
        } catch (Exception e) {
            err.println("Analysis failed: " + e.getMessage());
            return 2;
        }
    }

    private static List<FileMapping> discoverMappings(String repoPath, String oldRevision, String newRevision)
            throws IOException {
        GitAnalyzer git = new GitAnalyzer(repoPath);
        try {
            Set<String> allFiles = new TreeSet<>(git.listFilesAtRevision(oldRevision));
            allFiles.addAll(git.listFilesAtRevision(newRevision));
            return new FileMappingDiscoverer().discover(new ArrayList<>(allFiles));
        } finally {
            git.close();
        }
    }
}
