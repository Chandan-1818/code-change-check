package com.analysis.tool.experiment;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.analysis.tool.execution.TestRunResult;
import com.analysis.tool.experiment.FaultDetectionExperiment.ArmResult;
import com.analysis.tool.experiment.FaultDetectionExperiment.FaultOutcome;
import com.analysis.tool.experiment.FaultDetectionExperiment.Report;
import com.analysis.tool.experiment.FaultDetectionExperiment.Verdict;
import com.analysis.tool.experiment.FaultInjectionRunner.Fault;

/**
 * Unit tests for the G8 fault-detection classification and aggregation.
 *
 * These are deliberately repository-free and Maven-free: the experiment's
 * measured outcome must come from a real run, never from an expectation
 * encoded in a test. The tests here pin only the pure decision logic and the
 * deterministic report rendering.
 */
public class FaultDetectionExperimentTest {

    private static TestRunResult run(int total, int passed, int failed,
                                     boolean buildSucceeded, boolean compilationFailed) {
        return new TestRunResult(total, passed, failed, 0, 0L, buildSucceeded, compilationFailed);
    }

    private static Fault fault(String method) {
        return new FaultInjectionRunner.Fault(method, method + " -> broken",
                "src/main/java/com/sample/" + method.split("\\.")[0] + ".java",
                "find", "replace");
    }

    private static FaultOutcome outcome(String method, ArmResult manualOnly, ArmResult manualAndGenerated) {
        return new FaultOutcome(fault(method), "commit-" + method, "hash-" + method,
                manualOnly, manualAndGenerated);
    }

    @Test
    public void shouldDetectWhenAMutantCompilesAndATestFails() {
        Verdict verdict = FaultDetectionExperiment.classifyRun(
                run(10, 9, 1, true, false), Set.of("testAdd"));
        assertEquals(Verdict.DETECTED, verdict);
    }

    @Test
    public void shouldBeUndetectedWhenAMutantCompilesAndNoTestFails() {
        Verdict verdict = FaultDetectionExperiment.classifyRun(
                run(10, 10, 0, true, false), Set.of());
        assertEquals(Verdict.UNDETECTED, verdict);
    }

    @Test
    public void shouldBeInvalidWhenTheMutantDoesNotCompile() {
        Verdict verdict = FaultDetectionExperiment.classifyRun(
                run(0, 0, 0, false, true), Set.of());
        assertEquals(Verdict.INVALID, verdict);
    }

    @Test
    public void shouldDetectWhenTheSuiteExitsNonZeroBecauseATestFailed() {
        // Maven's exit code is non-zero for a killed mutant; that is a detection,
        // not a broken build. Only a compilation error or a run with no tests is
        // INVALID/INCONCLUSIVE.
        Verdict verdict = FaultDetectionExperiment.classifyRun(
                run(17, 16, 1, false, false), Set.of("testAdd"));
        assertEquals(Verdict.DETECTED, verdict);
    }

    @Test
    public void shouldBeInconclusiveWhenTheRunProducedNoTests() {
        Verdict verdict = FaultDetectionExperiment.classifyRun(
                run(0, 0, 0, false, false), Set.of());
        assertEquals(Verdict.INCONCLUSIVE, verdict);
    }

    @Test
    public void shouldBeInconclusiveWhenFailuresCannotBeAttributedToTests() {
        Verdict verdict = FaultDetectionExperiment.classifyRun(
                run(10, 9, 1, true, false), Set.of());
        assertEquals(Verdict.INCONCLUSIVE, verdict);
    }

    @Test
    public void shouldCountAdditionalDetectionOnlyWhenTheManualSuiteMissedTheFault() {
        FaultOutcome gain = outcome("Calculator.negate",
                new ArmResult(Verdict.UNDETECTED, Set.of(), "manual"),
                new ArmResult(Verdict.DETECTED, Set.of("gen_negate_01"), "combined"));
        assertTrue(gain.isGain());
        assertFalse(gain.isRegression());
    }

    @Test
    public void shouldNotCountAFaultDetectedByBothSuitesAsAdditionalDetection() {
        FaultOutcome both = outcome("Calculator.add",
                new ArmResult(Verdict.DETECTED, Set.of("testAdd"), "manual"),
                new ArmResult(Verdict.DETECTED, Set.of("testAdd", "gen_add_01"), "combined"));
        assertFalse(both.isGain());
        assertFalse(both.isRegression());
    }

    @Test
    public void shouldNotCountAFaultUndetectedByBothSuitesAsAdditionalDetection() {
        FaultOutcome neither = outcome("CheckoutService.quote",
                new ArmResult(Verdict.UNDETECTED, Set.of(), "manual"),
                new ArmResult(Verdict.UNDETECTED, Set.of(), "combined"));
        assertFalse(neither.isGain());
        assertFalse(neither.isRegression());
    }

    @Test
    public void shouldFlagARegressionWhenTheManualSuiteKilledButTheCombinedSuiteDoesNot() {
        FaultOutcome regression = outcome("Multiplier.multiply",
                new ArmResult(Verdict.DETECTED, Set.of("testMultiply"), "manual"),
                new ArmResult(Verdict.UNDETECTED, Set.of(), "combined"));
        assertFalse(regression.isGain());
        assertTrue(regression.isRegression());
    }

    @Test
    public void shouldTreatAMutationWithNoMutantCommitAsSkipped() {
        FaultOutcome skipped = new FaultOutcome(fault("Gone.method"), null, null,
                ArmResult.invalid("not present exactly once"), ArmResult.invalid("not present exactly once"));
        assertTrue(skipped.isSkipped());
        assertFalse(skipped.isGain());
        assertFalse(skipped.isRegression());
    }

    @Test
    public void shouldSeparateInvalidAndInconclusiveFromTheMutationScore() {
        Report report = new Report("7059f4c", "env", "baseline", 12, 98, null, List.of(
                outcome("A.add",
                        new ArmResult(Verdict.DETECTED, Set.of("t"), "manual"),
                        new ArmResult(Verdict.DETECTED, Set.of("t"), "combined")),
                outcome("B.negate",
                        new ArmResult(Verdict.UNDETECTED, Set.of(), "manual"),
                        new ArmResult(Verdict.DETECTED, Set.of("gen"), "combined")),
                outcome("C.broken",
                        ArmResult.invalid("does not compile"),
                        ArmResult.invalid("does not compile")),
                outcome("D.noTests",
                        new ArmResult(Verdict.INCONCLUSIVE, Set.of(), "no tests ran"),
                        new ArmResult(Verdict.INCONCLUSIVE, Set.of(), "no tests ran"))));

        String summary = report.summary();
        assertTrue(summary.contains("Manual only         : DETECTED=1, UNDETECTED=1, INVALID=1, INCONCLUSIVE=1"),
                summary);
        assertTrue(summary.contains("Manual + generated  : DETECTED=2, UNDETECTED=0, INVALID=1, INCONCLUSIVE=1"),
                summary);
        assertTrue(summary.contains("mutation score=1/2=0.5000"), summary);
        assertTrue(summary.contains("mutation score=2/2=1.0000"), summary);
        assertTrue(summary.contains("Additional detection attributable to generated tests: 1 [B.negate]"), summary);
        assertEquals(1, report.getGains().size());
        assertEquals(0, report.getRegressions().size());
        assertEquals(4, report.getSupportedMutations());
        assertEquals(0, report.getSkippedMutations().size());
        assertEquals(0.5, report.getManualMutationScore(), 1e-9);
        assertEquals(1.0, report.getCombinedMutationScore(), 1e-9);
        assertEquals(50.0, report.getImprovementPercentagePoints(), 1e-9);
    }

    @Test
    public void shouldCountSkippedMutationsSeparatelyFromSupportedOnes() {
        Report report = new Report("7059f4c", "env", "baseline", 12, 98, null, List.of(
                outcome("A.add",
                        new ArmResult(Verdict.DETECTED, Set.of("t"), "manual"),
                        new ArmResult(Verdict.DETECTED, Set.of("t"), "combined")),
                new FaultOutcome(fault("Z.removed"), null, null,
                        ArmResult.invalid("mutation text is not present exactly once"),
                        ArmResult.invalid("mutation text is not present exactly once"))));

        assertEquals(1, report.getSupportedMutations());
        assertEquals(1, report.getSkippedMutations().size());
        assertEquals("Z.removed", report.getSkippedMutations().get(0).getFault().getMethod());
        assertTrue(report.toMarkdown().contains("## Skipped mutations"), report.toMarkdown());
        assertTrue(report.toJson().contains("\"skippedMutations\": [\"Z.removed\"]"), report.toJson());
    }

    @Test
    public void shouldRenderBothArmsInTheMarkdownReport() {
        Report report = new Report("7059f4c", "java.version=17.0.1", "baseline", 12, 98, null, List.of(
                outcome("Calculator.negate",
                        new ArmResult(Verdict.UNDETECTED, Set.of(), "manual"),
                        new ArmResult(Verdict.DETECTED, Set.of("gen_Calculator_negate_01"), "combined"))));

        String markdown = report.toMarkdown();
        assertTrue(markdown.contains("| Fault | Mutation | Manual only | Manual + generated | Additional detection |"),
                markdown);
        assertTrue(markdown.contains("| Calculator.negate |"), markdown);
        assertTrue(markdown.contains("| UNDETECTED | DETECTED (gen_Calculator_negate_01) | yes |"), markdown);
        assertTrue(markdown.contains("Base revision: 7059f4c"), markdown);
    }

    @Test
    public void shouldReportCoverageAsStaticReachabilityAndNeverAsLineCoverage() {
        FaultDetectionExperiment.Coverage coverage = new FaultDetectionExperiment.Coverage(
                10, 7, 9, Set.of("PricingService.finalPrice", "Calculator.negate"));
        Report report = new Report("7059f4c", "env", "baseline", 12, 98, coverage, List.of(
                outcome("Calculator.negate",
                        new ArmResult(Verdict.UNDETECTED, Set.of(), "manual"),
                        new ArmResult(Verdict.DETECTED, Set.of("gen"), "combined"))));

        String markdown = report.toMarkdown();
        assertTrue(markdown.contains("Kind: static reachability"), markdown);
        assertTrue(markdown.contains("NOT runtime"), markdown);
        assertTrue(markdown.contains("Newly reached methods       : Calculator.negate, PricingService.finalPrice"),
                markdown);

        String json = report.toJson();
        assertTrue(json.contains("\"kind\": \"static-reachability\""), json);
        assertTrue(json.contains("\"productionMethods\": 10"), json);
        assertTrue(json.contains("\"manualCovered\": 7"), json);
        assertTrue(json.contains("\"combinedCovered\": 9"), json);
    }

    @Test
    public void shouldReportCoverageAsNotMeasuredWhenAbsent() {
        Report report = new Report("7059f4c", "env", "baseline", 12, 98, null, List.of());
        assertTrue(report.toMarkdown().contains("Coverage: not measured."), report.toMarkdown());
        assertTrue(report.toJson().contains("\"coverage\": null"), report.toJson());
    }

    @Test
    public void shouldRenderTheAggregateInTheJsonReport() {
        Report report = new Report("7059f4c", "env", "baseline", 12, 98, null, List.of(
                outcome("Calculator.negate",
                        new ArmResult(Verdict.UNDETECTED, Set.of(), "manual"),
                        new ArmResult(Verdict.DETECTED, Set.of("gen"), "combined"))));

        String json = report.toJson();
        assertTrue(json.contains("\"baseRevision\": \"7059f4c\""), json);
        assertTrue(json.contains("\"mutations\": 1"), json);
        assertTrue(json.contains("\"supportedMutations\": 1"), json);
        assertTrue(json.contains("\"additionalDetection\": [\"Calculator.negate\"]"), json);
        assertTrue(json.contains("\"regressions\": []"), json);
        assertTrue(json.contains("\"mutantFileSha256\": \"hash-Calculator.negate\""), json);
        assertTrue(json.contains("\"mutationScore\": 1.000000"), json);
        assertTrue(json.contains("\"improvementPercentagePoints\": 100.000000"), json);
    }
}
