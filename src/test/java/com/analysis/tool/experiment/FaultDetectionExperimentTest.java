package com.analysis.tool.experiment;

import java.util.Collections;
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
 * encoded in a test (requirement 10). The tests here pin only the pure
 * decision logic and the deterministic report rendering.
 */
public class FaultDetectionExperimentTest {

    private static TestRunResult run(int total, int passed, int failed, int errors,
                                     boolean buildSucceeded, boolean timedOut, boolean compilationFailed) {
        return new TestRunResult(total, passed, failed, errors, 0, 0L,
                buildSucceeded, timedOut, compilationFailed, Collections.emptyList());
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
                run(10, 9, 1, 0, true, false, false), Set.of("testAdd"));
        assertEquals(Verdict.DETECTED, verdict);
    }

    @Test
    public void shouldBeUndetectedWhenAMutantCompilesAndNoTestFails() {
        Verdict verdict = FaultDetectionExperiment.classifyRun(
                run(10, 10, 0, 0, true, false, false), Set.of());
        assertEquals(Verdict.UNDETECTED, verdict);
    }

    @Test
    public void shouldBeInvalidWhenTheMutantDoesNotCompile() {
        Verdict verdict = FaultDetectionExperiment.classifyRun(
                run(0, 0, 0, 0, false, false, true), Set.of());
        assertEquals(Verdict.INVALID, verdict);
    }

    @Test
    public void shouldBeInconclusiveWhenTheRunTimesOut() {
        Verdict verdict = FaultDetectionExperiment.classifyRun(
                run(0, 0, 0, 0, false, true, false), Set.of());
        assertEquals(Verdict.INCONCLUSIVE, verdict);
    }

    @Test
    public void shouldDetectWhenTheSuiteExitsNonZeroBecauseATestFailed() {
        // Maven's exit code is non-zero for a killed mutant; that is a detection,
        // not a broken build. Only a compilation error or a run with no tests is
        // INVALID/INCONCLUSIVE.
        Verdict verdict = FaultDetectionExperiment.classifyRun(
                run(17, 16, 1, 0, false, false, false), Set.of("testAdd"));
        assertEquals(Verdict.DETECTED, verdict);
    }

    @Test
    public void shouldBeInconclusiveWhenTheRunProducedNoTests() {
        Verdict verdict = FaultDetectionExperiment.classifyRun(
                run(0, 0, 0, 0, false, false, false), Set.of());
        assertEquals(Verdict.INCONCLUSIVE, verdict);
    }

    @Test
    public void shouldBeInconclusiveWhenFailuresCannotBeAttributedToTests() {
        Verdict verdict = FaultDetectionExperiment.classifyRun(
                run(10, 9, 1, 0, true, false, false), Set.of());
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
    public void shouldSeparateInvalidAndInconclusiveFromTheMutationScore() {
        Report report = new Report("7059f4c", "env", "baseline", List.of(
                outcome("A.add",
                        new ArmResult(Verdict.DETECTED, Set.of("t"), "manual"),
                        new ArmResult(Verdict.DETECTED, Set.of("t"), "combined")),
                outcome("B.negate",
                        new ArmResult(Verdict.UNDETECTED, Set.of(), "manual"),
                        new ArmResult(Verdict.DETECTED, Set.of("gen"), "combined")),
                outcome("C.broken",
                        ArmResult.invalid("does not compile"),
                        ArmResult.invalid("does not compile")),
                outcome("D.timeout",
                        new ArmResult(Verdict.INCONCLUSIVE, Set.of(), "timed out"),
                        new ArmResult(Verdict.INCONCLUSIVE, Set.of(), "timed out"))));

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
    }

    @Test
    public void shouldRenderBothArmsInTheMarkdownReport() {
        Report report = new Report("7059f4c", "java.version=17.0.1", "baseline", List.of(
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
    public void shouldRenderTheAggregateInTheJsonReport() {
        Report report = new Report("7059f4c", "env", "baseline", List.of(
                outcome("Calculator.negate",
                        new ArmResult(Verdict.UNDETECTED, Set.of(), "manual"),
                        new ArmResult(Verdict.DETECTED, Set.of("gen"), "combined"))));

        String json = report.toJson();
        assertTrue(json.contains("\"baseRevision\": \"7059f4c\""), json);
        assertTrue(json.contains("\"additionalDetection\": [\"Calculator.negate\"]"), json);
        assertTrue(json.contains("\"regressions\": []"), json);
        assertTrue(json.contains("\"mutantFileSha256\": \"hash-Calculator.negate\""), json);
        assertTrue(json.contains("\"mutationScore\": 1.000000"), json);
    }
}
