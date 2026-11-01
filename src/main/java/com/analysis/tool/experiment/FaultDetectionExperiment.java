package com.analysis.tool.experiment;

import com.analysis.tool.cli.GenerateTestsCli;
import com.analysis.tool.execution.TestRunResult;
import com.analysis.tool.execution.TestRunner;
import com.analysis.tool.experiment.FaultInjectionRunner.Fault;
import com.analysis.tool.metrics.StaticCoverage;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G8 fault-detection experiment: the additional fault detection that the
 * generated suite contributes on top of the project's own manual suite.
 *
 * <p>{@link FaultInjectionRunner} answers a different question ("did the
 * selector select every test that fails?"). This class instead asks whether a
 * fault is <em>detected at all</em>, and by which suite. For every fault it
 * builds ONE mutant commit at the base revision, then runs two arms against
 * that same mutant:
 *
 * <ul>
 *   <li><b>Arm A - manual only.</b> The checkout's own suite.</li>
 *   <li><b>Arm B - manual + generated.</b> The same mutant with the generated
 *       suite copied into {@code src/test/java/rts/generated} of the temporary
 *       checkout only.</li>
 * </ul>
 *
 * <p>Because both arms share one working tree holding the mutant commit, the
 * source mutation is identical by construction; the SHA-256 of the mutated
 * file is recorded as the equivalence witness (commit ids carry timestamps,
 * so they are not comparable across runs). The pom, JDK and Maven command are
 * identical for both arms - the generated test sources are the only
 * difference.
 *
 * <p>Each arm is classified:
 * <ul>
 *   <li>{@code DETECTED} - the mutant compiled and at least one test failed.</li>
 *   <li>{@code UNDETECTED} - the mutant compiled and no test failed (survived).</li>
 *   <li>{@code INVALID} - no usable mutant: the mutation text was not unique, or
 *       the mutant did not compile (a stillborn mutant). Excluded from the
 *       mutation score.</li>
 *   <li>{@code INCONCLUSIVE} - no verdict could be obtained: the run reported no
 *       tests at all, or reported failures that could not be attributed to test
 *       names.</li>
 * </ul>
 *
 * <p>The mutation score of an arm is
 * {@code DETECTED / (DETECTED + UNDETECTED)} over its valid, conclusive
 * mutants. The additional detection attributable to the generated tests is the
 * set of faults that are {@code UNDETECTED} under Arm A and {@code DETECTED}
 * under Arm B.
 *
 * <p>The experiment never touches the target repository: everything runs in a
 * temporary clone that is deleted afterwards.
 */
public class FaultDetectionExperiment {

    public static final String MARKDOWN_FILE = "fault-detection-report.md";
    public static final String JSON_FILE = "fault-detection-results.json";

    /** Where the generator places emitted tests inside a revision directory. */
    public static final String GENERATED_SUBDIR = "src/test/java/rts/generated";

    private static final String NOT_MEASURED = "not measured";

    public enum Verdict { DETECTED, UNDETECTED, INVALID, INCONCLUSIVE }

    /** The outcome of one arm (one suite configuration) on one mutant. */
    public static final class ArmResult {

        private final Verdict verdict;
        private final Set<String> failingTests;
        private final String detail;

        public ArmResult(Verdict verdict, Set<String> failingTests, String detail) {
            this.verdict = verdict;
            this.failingTests = Collections.unmodifiableSet(new TreeSet<>(failingTests));
            this.detail = detail;
        }

        static ArmResult invalid(String detail) {
            return new ArmResult(Verdict.INVALID, Collections.emptySet(), detail);
        }

        public Verdict getVerdict() { return verdict; }
        public Set<String> getFailingTests() { return failingTests; }
        public String getDetail() { return detail; }
    }

    /** The two-arm outcome for a single injected fault. */
    public static final class FaultOutcome {

        private final Fault fault;
        private final String mutantCommit;
        private final String mutantFileSha256;
        private final ArmResult manualOnly;
        private final ArmResult manualAndGenerated;

        public FaultOutcome(Fault fault, String mutantCommit, String mutantFileSha256,
                            ArmResult manualOnly, ArmResult manualAndGenerated) {
            this.fault = fault;
            this.mutantCommit = mutantCommit;
            this.mutantFileSha256 = mutantFileSha256;
            this.manualOnly = manualOnly;
            this.manualAndGenerated = manualAndGenerated;
        }

        public Fault getFault() { return fault; }
        public String getMutantCommit() { return mutantCommit; }
        public String getMutantFileSha256() { return mutantFileSha256; }
        public ArmResult getManualOnly() { return manualOnly; }
        public ArmResult getManualAndGenerated() { return manualAndGenerated; }

        /** The mutation text did not occur exactly once, so no mutant was built. */
        public boolean isSkipped() {
            return mutantCommit == null;
        }

        /** Additional detection attributable to the generated tests. */
        public boolean isGain() {
            return manualOnly.getVerdict() == Verdict.UNDETECTED
                    && manualAndGenerated.getVerdict() == Verdict.DETECTED;
        }

        /** A fault the manual suite killed that the combined suite no longer kills. */
        public boolean isRegression() {
            return manualOnly.getVerdict() == Verdict.DETECTED
                    && manualAndGenerated.getVerdict() != Verdict.DETECTED;
        }
    }

    /**
     * Static reachability of the production methods, before and after the
     * generated suite is added. This is NOT runtime coverage: it is the same
     * reverse-reachability traversal the selector uses, so it can only say that
     * some test names a production method on a call path. It is reported under
     * that name and is never presented as line or branch coverage.
     */
    public static final class Coverage {

        private final int productionMethods;
        private final int manualCovered;
        private final int combinedCovered;
        private final Set<String> newlyCovered;

        public Coverage(int productionMethods, int manualCovered, int combinedCovered,
                        Set<String> newlyCovered) {
            this.productionMethods = productionMethods;
            this.manualCovered = manualCovered;
            this.combinedCovered = combinedCovered;
            this.newlyCovered = Collections.unmodifiableSet(new TreeSet<>(newlyCovered));
        }

        public int getProductionMethods() { return productionMethods; }
        public int getManualCovered() { return manualCovered; }
        public int getCombinedCovered() { return combinedCovered; }
        public Set<String> getNewlyCovered() { return newlyCovered; }

        public double getManualPercent() { return percent(manualCovered); }
        public double getCombinedPercent() { return percent(combinedCovered); }

        private double percent(int covered) {
            return productionMethods == 0 ? Double.NaN : 100.0 * covered / productionMethods;
        }
    }

    /** The whole experiment: environment, baseline, coverage and per-fault outcomes. */
    public static final class Report {

        private final String baseRevision;
        private final String environment;
        private final String baselineNote;
        private final int manualSuiteTests;
        private final int generatedSuiteTests;
        private final Coverage coverage;
        private final List<FaultOutcome> outcomes;

        public Report(String baseRevision, String environment, String baselineNote,
                      int manualSuiteTests, int generatedSuiteTests, Coverage coverage,
                      List<FaultOutcome> outcomes) {
            this.baseRevision = baseRevision;
            this.environment = environment;
            this.baselineNote = baselineNote;
            this.manualSuiteTests = manualSuiteTests;
            this.generatedSuiteTests = generatedSuiteTests;
            this.coverage = coverage;
            this.outcomes = List.copyOf(outcomes);
        }

        public String getBaseRevision() { return baseRevision; }
        public String getEnvironment() { return environment; }
        public String getBaselineNote() { return baselineNote; }
        public int getManualSuiteTests() { return manualSuiteTests; }
        public int getGeneratedSuiteTests() { return generatedSuiteTests; }
        public Coverage getCoverage() { return coverage; }
        public List<FaultOutcome> getOutcomes() { return outcomes; }

        public List<FaultOutcome> getGains() {
            List<FaultOutcome> gains = new ArrayList<>();
            for (FaultOutcome outcome : outcomes) {
                if (outcome.isGain()) {
                    gains.add(outcome);
                }
            }
            return gains;
        }

        public List<FaultOutcome> getRegressions() {
            List<FaultOutcome> regressions = new ArrayList<>();
            for (FaultOutcome outcome : outcomes) {
                if (outcome.isRegression()) {
                    regressions.add(outcome);
                }
            }
            return regressions;
        }

        /** Mutations that could not be injected at all, with the reason. */
        public List<FaultOutcome> getSkippedMutations() {
            List<FaultOutcome> skipped = new ArrayList<>();
            for (FaultOutcome outcome : outcomes) {
                if (outcome.isSkipped()) {
                    skipped.add(outcome);
                }
            }
            return skipped;
        }

        /** Mutations that were injected (a mutant commit was built). */
        public int getSupportedMutations() {
            return outcomes.size() - getSkippedMutations().size();
        }

        public List<FaultOutcome> getUndetectedInBoth() {
            List<FaultOutcome> both = new ArrayList<>();
            for (FaultOutcome outcome : outcomes) {
                if (outcome.getManualOnly().getVerdict() == Verdict.UNDETECTED
                        && outcome.getManualAndGenerated().getVerdict() == Verdict.UNDETECTED) {
                    both.add(outcome);
                }
            }
            return both;
        }

        public Double getManualMutationScore() {
            return mutationScore(outcomes, false);
        }

        public Double getCombinedMutationScore() {
            return mutationScore(outcomes, true);
        }

        /** Absolute gain of the combined arm over the manual arm, in percentage points. */
        public Double getImprovementPercentagePoints() {
            Double manual = getManualMutationScore();
            Double combined = getCombinedMutationScore();
            return manual == null || combined == null ? null : (combined - manual) * 100.0;
        }

        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("# G8 fault-detection report\n\n");
            sb.append("Base revision: ").append(baseRevision).append("\n\n");
            sb.append("Environment  : ").append(environment).append("\n\n");
            sb.append("Baseline     : ").append(baselineNote).append("\n\n");
            sb.append("## Suites\n\n");
            sb.append("Manual suite at the base revision : ").append(manualSuiteTests).append(" test(s)\n\n");
            sb.append("Generated suite                   : ").append(generatedSuiteTests).append(" test(s)\n\n");
            sb.append("## Coverage\n\n");
            if (coverage == null) {
                sb.append("Coverage: ").append(NOT_MEASURED).append(".\n\n");
            } else {
                sb.append("Kind: static reachability (reverse call-graph reachability, NOT runtime "
                        + "line/branch coverage).\n\n");
                sb.append("Production methods          : ").append(coverage.getProductionMethods()).append("\n\n");
                sb.append("Reached by the manual suite : ").append(coverage.getManualCovered())
                  .append(" (").append(percentText(coverage.getManualPercent())).append(")\n\n");
                sb.append("Reached with the generated suite: ").append(coverage.getCombinedCovered())
                  .append(" (").append(percentText(coverage.getCombinedPercent())).append(")\n\n");
                sb.append("Newly reached methods       : ").append(join(coverage.getNewlyCovered())).append("\n\n");
            }
            sb.append("## Mutation score\n\n");
            sb.append("Mutations          : ").append(outcomes.size()).append("\n\n");
            sb.append("Supported mutations: ").append(getSupportedMutations()).append("\n\n");
            sb.append("Skipped mutations  : ").append(getSkippedMutations().size()).append("\n\n");
            sb.append("Manual only        : ").append(countsLine(outcomes, false)).append("\n\n");
            sb.append("Manual + generated : ").append(countsLine(outcomes, true)).append("\n\n");
            sb.append("| Fault | Mutation | Manual only | Manual + generated | Additional detection |\n");
            sb.append("|---|---|---|---|---|\n");
            for (FaultOutcome outcome : outcomes) {
                sb.append("| ").append(outcome.getFault().getMethod())
                  .append(" | ").append(outcome.getFault().getMutation())
                  .append(" | ").append(render(outcome.getManualOnly()))
                  .append(" | ").append(render(outcome.getManualAndGenerated()))
                  .append(" | ").append(outcome.isGain() ? "yes" : "no")
                  .append(" |\n");
            }
            sb.append('\n');
            if (!getSkippedMutations().isEmpty()) {
                sb.append("## Skipped mutations\n\n");
                for (FaultOutcome outcome : getSkippedMutations()) {
                    sb.append("- ").append(outcome.getFault().getMethod()).append(": ")
                      .append(outcome.getManualOnly().getDetail()).append("\n");
                }
                sb.append('\n');
            }
            sb.append("Both arms of a fault run on the same mutant commit (identical mutated file). "
                    + "Mutant commit ids and the SHA-256 of the mutated file are recorded in the JSON. "
                    + "\"DETECTED\" lists the failing tests; a mutant that does not compile is INVALID "
                    + "(stillborn) and is excluded from the mutation score.\n");
            return sb.toString();
        }

        public String toJson() {
            Double manualScore = getManualMutationScore();
            Double combinedScore = getCombinedMutationScore();
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"baseRevision\": ").append(jsonString(baseRevision)).append(",\n");
            sb.append("  \"environment\": ").append(jsonString(environment)).append(",\n");
            sb.append("  \"baseline\": ").append(jsonString(baselineNote)).append(",\n");
            sb.append("  \"manualSuiteTests\": ").append(manualSuiteTests).append(",\n");
            sb.append("  \"generatedSuiteTests\": ").append(generatedSuiteTests).append(",\n");
            sb.append("  \"coverage\": ").append(coverageJson()).append(",\n");
            sb.append("  \"mutations\": ").append(outcomes.size()).append(",\n");
            sb.append("  \"supportedMutations\": ").append(getSupportedMutations()).append(",\n");
            sb.append("  \"skippedMutations\": ").append(nameArray(getSkippedMutations())).append(",\n");
            sb.append("  \"faults\": [\n");
            for (int i = 0; i < outcomes.size(); i++) {
                FaultOutcome outcome = outcomes.get(i);
                sb.append("    {\n");
                sb.append("      \"fault\": ").append(jsonString(outcome.getFault().getMethod())).append(",\n");
                sb.append("      \"mutation\": ").append(jsonString(outcome.getFault().getMutation())).append(",\n");
                sb.append("      \"mutantCommit\": ").append(jsonString(outcome.getMutantCommit())).append(",\n");
                sb.append("      \"mutantFileSha256\": ").append(jsonString(outcome.getMutantFileSha256())).append(",\n");
                sb.append("      \"manualOnly\": ").append(armJson(outcome.getManualOnly())).append(",\n");
                sb.append("      \"manualAndGenerated\": ").append(armJson(outcome.getManualAndGenerated())).append(",\n");
                sb.append("      \"additionalDetection\": ").append(outcome.isGain()).append(",\n");
                sb.append("      \"regression\": ").append(outcome.isRegression()).append("\n");
                sb.append("    }").append(i + 1 < outcomes.size() ? "," : "").append("\n");
            }
            sb.append("  ],\n");
            sb.append("  \"aggregate\": {\n");
            sb.append("    \"manualOnly\": ").append(armAggregateJson(outcomes, false, manualScore)).append(",\n");
            sb.append("    \"manualAndGenerated\": ").append(armAggregateJson(outcomes, true, combinedScore)).append(",\n");
            sb.append("    \"improvementPercentagePoints\": ").append(jsonNumber(getImprovementPercentagePoints())).append(",\n");
            sb.append("    \"additionalDetection\": ").append(nameArray(getGains())).append(",\n");
            sb.append("    \"regressions\": ").append(nameArray(getRegressions())).append(",\n");
            sb.append("    \"undetectedInBoth\": ").append(nameArray(getUndetectedInBoth())).append("\n");
            sb.append("  }\n");
            sb.append("}\n");
            return sb.toString();
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("G8 fault-detection summary\n");
            sb.append("Faults              : ").append(outcomes.size())
              .append(" (supported ").append(getSupportedMutations())
              .append(", skipped ").append(getSkippedMutations().size()).append(")\n");
            sb.append("Manual only         : ").append(countsLine(outcomes, false)).append("\n");
            sb.append("Manual + generated  : ").append(countsLine(outcomes, true)).append("\n");
            sb.append("Additional detection attributable to generated tests: ")
              .append(getGains().size()).append(" ").append(names(getGains())).append("\n");
            sb.append("No-regression check (detected by manual only, lost when generated tests are added): ")
              .append(getRegressions().size()).append(" ").append(names(getRegressions())).append("\n");
            sb.append("Undetected in both arms: ").append(getUndetectedInBoth().size())
              .append(" ").append(names(getUndetectedInBoth())).append("\n");
            if (coverage == null) {
                sb.append("Coverage            : ").append(NOT_MEASURED).append("\n");
            } else {
                sb.append("Static reachability : manual ").append(coverage.getManualCovered())
                  .append("/").append(coverage.getProductionMethods())
                  .append(" (").append(percentText(coverage.getManualPercent()))
                  .append("), manual+generated ").append(coverage.getCombinedCovered())
                  .append("/").append(coverage.getProductionMethods())
                  .append(" (").append(percentText(coverage.getCombinedPercent()))
                  .append("), newly reached ").append(coverage.getNewlyCovered().size()).append("\n");
            }
            return sb.toString();
        }

        /** Writes {@link #MARKDOWN_FILE} and {@link #JSON_FILE} into {@code directory}. */
        public void write(Path directory) throws IOException {
            if (directory == null) {
                throw new IllegalArgumentException("directory must not be null");
            }
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(MARKDOWN_FILE), toMarkdown(), StandardCharsets.UTF_8);
            Files.writeString(directory.resolve(JSON_FILE), toJson(), StandardCharsets.UTF_8);
        }

        private String countsLine(List<FaultOutcome> all, boolean combined) {
            Double score = combined ? getCombinedMutationScore() : getManualMutationScore();
            return counts(all, combined) + "; mutation score=" + scoreText(all, combined, score);
        }

        private String coverageJson() {
            if (coverage == null) {
                return "null";
            }
            return "{ \"kind\": \"static-reachability\""
                    + ", \"productionMethods\": " + coverage.getProductionMethods()
                    + ", \"manualCovered\": " + coverage.getManualCovered()
                    + ", \"manualPercent\": " + jsonNumber(nullable(coverage.getManualPercent()))
                    + ", \"combinedCovered\": " + coverage.getCombinedCovered()
                    + ", \"combinedPercent\": " + jsonNumber(nullable(coverage.getCombinedPercent()))
                    + ", \"newlyCovered\": " + stringArray(coverage.getNewlyCovered()) + " }";
        }

        private static Double nullable(double value) {
            return Double.isNaN(value) ? null : value;
        }
    }

    /**
     * Runs both arms for every fault. The generated suite must already exist at
     * {@code generatedDir/src/test/java/rts/generated}.
     */
    public Report run(String repoPath, String baseRevision, List<Fault> faults, Path generatedDir)
            throws Exception {
        Path generatedSources = generatedDir.resolve(GENERATED_SUBDIR);
        if (!Files.isDirectory(generatedSources)) {
            throw new IllegalArgumentException("No generated tests found at " + generatedSources);
        }

        Path workDir = Files.createTempDirectory("g8-fault-detection-");
        try (Git git = Git.cloneRepository()
                .setURI(new File(repoPath).getAbsoluteFile().toURI().toString())
                .setDirectory(workDir.toFile())
                .call()) {

            String environment = environment(workDir);

            git.checkout().setName(baseRevision).setForce(true).call();
            deleteRecursively(workDir.resolve("target"));
            deleteRecursively(workDir.resolve(GENERATED_SUBDIR));

            List<String> productionSources = readJavaSources(workDir.resolve("src/main/java"));
            List<String> manualTestSources = readJavaSourcesExcluding(
                    workDir.resolve("src/test/java"), workDir.resolve(GENERATED_SUBDIR));
            List<String> generatedTestSources = readJavaSources(generatedSources);

            Coverage coverage = measureCoverage(productionSources, manualTestSources, generatedTestSources);

            TestRunResult baseline = new TestRunner().runFullSuite(workDir.toString());
            if (!baseline.isBuildSucceeded() || baseline.isCompilationFailed() || baseline.getTotalTests() == 0) {
                throw new IllegalStateException("Baseline revision " + baseRevision
                        + " did not produce a passing build with tests, so a mutant that fails to compile "
                        + "could not be told apart from a broken environment: " + describe(baseline));
            }

            TestRunner runner = new TestRunner();
            PersonIdent ident = new PersonIdent("G8 Fault Injector", "g8@example.com");
            List<FaultOutcome> outcomes = new ArrayList<>();

            for (Fault fault : faults) {
                git.checkout().setName(baseRevision).setForce(true).call();
                deleteRecursively(workDir.resolve("target"));
                deleteRecursively(workDir.resolve(GENERATED_SUBDIR));

                Path file = workDir.resolve(fault.getFile());
                String original = Files.readString(file, StandardCharsets.UTF_8);
                int first = original.indexOf(fault.getFind());
                if (first < 0 || original.indexOf(fault.getFind(), first + 1) >= 0) {
                    String detail = "mutation text is not present exactly once in " + fault.getFile();
                    outcomes.add(new FaultOutcome(fault, null, null,
                            ArmResult.invalid(detail), ArmResult.invalid(detail)));
                    continue;
                }
                String mutated = original.replace(fault.getFind(), fault.getReplace());
                Files.writeString(file, mutated, StandardCharsets.UTF_8);

                git.add().addFilepattern(fault.getFile()).call();
                RevCommit mutant = git.commit()
                        .setMessage("G8 fault: " + fault.getMethod() + " (" + fault.getMutation() + ")")
                        .setAuthor(ident)
                        .setCommitter(ident)
                        .setSign(false)
                        .call();
                String mutantId = mutant.getName();
                String mutatedHash = sha256(mutated);

                ArmResult manualOnly = runArm(runner, workDir, false, generatedSources);
                ArmResult manualAndGenerated = runArm(runner, workDir, true, generatedSources);

                outcomes.add(new FaultOutcome(fault, mutantId, mutatedHash, manualOnly, manualAndGenerated));
            }

            return new Report(baseRevision, environment, describe(baseline),
                    baseline.getTotalTests(), countTestMethods(generatedTestSources), coverage, outcomes);
        } finally {
            deleteRecursively(workDir);
        }
    }

    private static ArmResult runArm(TestRunner runner, Path workDir, boolean withGeneratedTests,
                                    Path generatedSources) throws Exception {
        deleteRecursively(workDir.resolve("target"));
        deleteRecursively(workDir.resolve(GENERATED_SUBDIR));
        if (withGeneratedTests) {
            copyDirectory(generatedSources, workDir.resolve(GENERATED_SUBDIR));
        }
        TestRunResult run = runner.runFullSuite(workDir.toString());
        Set<String> failing = FaultInjectionRunner.readFailingTests(
                workDir.resolve("target").resolve("surefire-reports"));
        return new ArmResult(classifyRun(run, failing), failing, describe(run));
    }

    /**
     * Classifies one arm from its run result and the names of the failing tests.
     *
     * <p>A mutant is INVALID when it did not compile (stillborn) and
     * INCONCLUSIVE when the run produced no usable verdict. Note that
     * {@link TestRunResult#isBuildSucceeded()} follows Maven's exit code, which
     * is non-zero whenever any test fails; it is therefore NOT used as a gate
     * here - the compile signal is {@link TestRunResult#isCompilationFailed()}
     * and a run with no tests at all is INCONCLUSIVE.
     */
    static Verdict classifyRun(TestRunResult run, Set<String> failingTests) {
        if (run.isCompilationFailed()) {
            return Verdict.INVALID;
        }
        if (run.getTotalTests() == 0) {
            return Verdict.INCONCLUSIVE;
        }
        if (failingTests.isEmpty() && run.getFailed() > 0) {
            return Verdict.INCONCLUSIVE;
        }
        return failingTests.isEmpty() ? Verdict.UNDETECTED : Verdict.DETECTED;
    }

    private static String describe(TestRunResult run) {
        return "tests=" + run.getTotalTests()
                + ", passed=" + run.getPassed()
                + ", failed=" + run.getFailed()
                + ", skipped=" + run.getSkipped()
                + ", buildSucceeded=" + run.isBuildSucceeded()
                + ", compilationFailed=" + run.isCompilationFailed();
    }

    private static Coverage measureCoverage(List<String> productionSources, List<String> manualTestSources,
                                           List<String> generatedTestSources) {
        StaticCoverage staticCoverage = new StaticCoverage();
        StaticCoverage.Result manual = staticCoverage.compute(productionSources, manualTestSources);
        List<String> combinedTests = new ArrayList<>(manualTestSources);
        combinedTests.addAll(generatedTestSources);
        StaticCoverage.Result combined = staticCoverage.compute(productionSources, combinedTests);

        Set<String> newlyCovered = new TreeSet<>(combined.getCovered());
        newlyCovered.removeAll(manual.getCovered());
        return new Coverage(manual.getProduction().size(), manual.getCovered().size(),
                combined.getCovered().size(), newlyCovered);
    }

    private static int countTestMethods(List<String> sources) {
        int count = 0;
        for (String source : sources) {
            CompilationUnit unit = StaticJavaParser.parse(source);
            for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
                if (method.isAnnotationPresent("Test")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countVerdict(List<FaultOutcome> outcomes, boolean combined, Verdict verdict) {
        int count = 0;
        for (FaultOutcome outcome : outcomes) {
            ArmResult arm = combined ? outcome.getManualAndGenerated() : outcome.getManualOnly();
            if (arm.getVerdict() == verdict) {
                count++;
            }
        }
        return count;
    }

    private static Double mutationScore(List<FaultOutcome> outcomes, boolean combined) {
        int detected = countVerdict(outcomes, combined, Verdict.DETECTED);
        int valid = detected + countVerdict(outcomes, combined, Verdict.UNDETECTED);
        return valid == 0 ? null : (double) detected / valid;
    }

    private static String counts(List<FaultOutcome> outcomes, boolean combined) {
        return "DETECTED=" + countVerdict(outcomes, combined, Verdict.DETECTED)
                + ", UNDETECTED=" + countVerdict(outcomes, combined, Verdict.UNDETECTED)
                + ", INVALID=" + countVerdict(outcomes, combined, Verdict.INVALID)
                + ", INCONCLUSIVE=" + countVerdict(outcomes, combined, Verdict.INCONCLUSIVE);
    }

    private static String scoreText(List<FaultOutcome> outcomes, boolean combined, Double score) {
        int detected = countVerdict(outcomes, combined, Verdict.DETECTED);
        int valid = detected + countVerdict(outcomes, combined, Verdict.UNDETECTED);
        return score == null ? "n/a" : detected + "/" + valid + "="
                + String.format(Locale.ROOT, "%.4f", score);
    }

    private static String render(ArmResult arm) {
        switch (arm.getVerdict()) {
            case DETECTED:
                return "DETECTED (" + join(arm.getFailingTests()) + ")";
            case UNDETECTED:
                return "UNDETECTED";
            default:
                return arm.getVerdict() + " (" + arm.getDetail() + ")";
        }
    }

    private static String join(Set<String> values) {
        return values.isEmpty() ? "-" : String.join(", ", values);
    }

    private static String percentText(double percent) {
        return Double.isNaN(percent) ? "n/a" : String.format(Locale.ROOT, "%.2f%%", percent);
    }

    private static String names(List<FaultOutcome> outcomes) {
        if (outcomes.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < outcomes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(outcomes.get(i).getFault().getMethod());
        }
        return sb.append(']').toString();
    }

    private static String armJson(ArmResult arm) {
        return "{ \"verdict\": " + jsonString(arm.getVerdict().name())
                + ", \"failingTests\": " + stringArray(arm.getFailingTests())
                + ", \"detail\": " + jsonString(arm.getDetail()) + " }";
    }

    private static String armAggregateJson(List<FaultOutcome> outcomes, boolean combined, Double score) {
        return "{ \"detected\": " + countVerdict(outcomes, combined, Verdict.DETECTED)
                + ", \"undetected\": " + countVerdict(outcomes, combined, Verdict.UNDETECTED)
                + ", \"invalid\": " + countVerdict(outcomes, combined, Verdict.INVALID)
                + ", \"inconclusive\": " + countVerdict(outcomes, combined, Verdict.INCONCLUSIVE)
                + ", \"mutationScore\": " + jsonNumber(score) + " }";
    }

    private static String nameArray(List<FaultOutcome> outcomes) {
        Set<String> names = new TreeSet<>();
        for (FaultOutcome outcome : outcomes) {
            names.add(outcome.getFault().getMethod());
        }
        return stringArray(names);
    }

    private static String stringArray(Set<String> values) {
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (String value : values) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(jsonString(value));
        }
        return sb.append(']').toString();
    }

    private static String jsonNumber(Double value) {
        return value == null ? "null" : String.format(Locale.ROOT, "%.6f", value);
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unavailable";
        }
    }

    /** Records the JDK and Maven actually used, so both arms are provably one environment. */
    private static String environment(Path workDir) {
        String java = "java.version=" + System.getProperty("java.version")
                + ", java.vm=" + System.getProperty("java.vm.version")
                + ", os=" + System.getProperty("os.name") + " " + System.getProperty("os.version");
        String maven = firstLine(workDir, "mvn", "-v");
        return java + "; " + (maven.isEmpty() ? "mvn version unavailable" : maven);
    }

    private static String firstLine(Path workDir, String... command) {
        List<String> line = new ArrayList<>();
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            line.add("cmd");
            line.add("/c");
        }
        line.addAll(Arrays.asList(command));
        try {
            Process process = new ProcessBuilder(line)
                    .directory(workDir.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(60, TimeUnit.SECONDS);
            return output.lines().findFirst().orElse("").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> readJavaSources(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return Collections.emptyList();
        }
        List<String> sources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                sources.add(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return sources;
    }

    private static List<String> readJavaSourcesExcluding(Path root, Path excluded) throws IOException {
        if (!Files.isDirectory(root)) {
            return Collections.emptyList();
        }
        List<String> sources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(excluded))
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                sources.add(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return sources;
    }

    private static void copyDirectory(Path from, Path to) throws IOException {
        Files.createDirectories(to);
        try (Stream<Path> files = Files.list(from)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Files.copy(file, to.resolve(file.getFileName()));
            }
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                path.toFile().setWritable(true);
                path.toFile().delete();
            });
        } catch (IOException ignored) {
            // best-effort cleanup of a temp directory
        }
    }

    /**
     * Command-line entry point.
     *
     * <pre>
     *   FaultDetectionExperiment &lt;repoPath&gt; &lt;baseRevision&gt; [outDir] [project]
     * </pre>
     *
     * The generated suite is regenerated from the original, unmodified base
     * revision of the target repository into a temporary directory; nothing is
     * written into the target repository or into the tool's own test tree.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 4) {
            System.err.println("Usage: FaultDetectionExperiment <repoPath> <baseRevision> [outDir] [project]");
            System.exit(1);
        }
        String repoPath = args[0];
        String baseRevision = args[1];
        String outDir = args.length >= 3 ? args[2] : "experiments";
        String project = args.length >= 4 ? args[3] : new File(repoPath).getAbsoluteFile().getName();

        Path generatedRoot = Files.createTempDirectory("g8-generated-");
        PrintStream out = System.out;
        try {
            int generationExit = GenerateTestsCli.run(new String[]{
                    repoPath, baseRevision,
                    "--out=" + generatedRoot,
                    "--project=" + project}, out, System.err);
            if (generationExit != 0) {
                System.err.println("Generation failed with exit code " + generationExit + "; aborting");
                System.exit(generationExit);
            }
            Path generatedDir = generatedRoot.resolve(project).resolve(baseRevision);

            Report report = new FaultDetectionExperiment()
                    .run(repoPath, baseRevision, GrownSampleFaults.faults(), generatedDir);

            out.print(report.toMarkdown());
            out.println();
            out.print(report.summary());
            report.write(Paths.get(outDir));
            out.println("Wrote " + MARKDOWN_FILE + " and " + JSON_FILE
                    + " to " + Path.of(outDir).toAbsolutePath());
        } finally {
            deleteRecursively(generatedRoot);
        }
    }
}
