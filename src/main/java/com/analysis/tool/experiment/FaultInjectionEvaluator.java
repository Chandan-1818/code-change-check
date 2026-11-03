package com.analysis.tool.experiment;

import com.analysis.tool.eval.CaseResult;
import com.analysis.tool.eval.EvaluationReport;
import com.analysis.tool.eval.GroundTruthCase;
import com.analysis.tool.experiment.FaultInjectionRunner.Fault;
import com.analysis.tool.experiment.FaultInjectionRunner.FaultResult;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the fault-injection safety check into a ground-truth precision/recall
 * evaluation (H10, GAP-17).
 *
 * <p>{@link FaultInjectionRunner} already establishes a defensible ground
 * truth by execution: for each injected fault it commits a mutant revision,
 * runs the tool's selection between the base and the mutant, and runs the
 * full suite on the mutant to record which tests actually fail. This class
 * reuses that run without re-implementing it, then maps each result into the
 * {@code eval} model:
 *
 * <ul>
 *   <li>required (ground truth) = the tests that fail in the full suite at
 *       the mutant revision;</li>
 *   <li>selected = the tests the tool selected for base -&gt; mutant;</li>
 *   <li>the {@code eval} package then computes TP/FP/FN and precision/recall.</li>
 * </ul>
 *
 * <p>Recall is therefore exact against the fault-induced ground truth, while
 * precision is a lower bound (D-16).
 */
public class FaultInjectionEvaluator {

    /**
     * Runs the fault-injection experiment and returns the evaluation report.
     */
    public EvaluationReport evaluate(String repoPath, String baseRevision, List<Fault> faults)
            throws Exception {
        List<FaultResult> faultResults = new FaultInjectionRunner().run(repoPath, baseRevision, faults);

        List<CaseResult> caseResults = new ArrayList<>();
        for (FaultResult result : faultResults) {
            Fault fault = result.getFault();
            GroundTruthCase testCase = new GroundTruthCase(
                    "fault:" + fault.getMethod(),
                    baseRevision,
                    result.getMutantCommit(),
                    List.of(fault.getFile()),
                    List.of(fault.getMethod()),
                    result.getFailingTests(),
                    "fault injection (" + fault.getMutation()
                            + "); required tests = tests failing in the full suite at the mutant commit");
            caseResults.add(new CaseResult(testCase, result.getSelectedTests()));
        }
        return new EvaluationReport(caseResults);
    }

    /**
     * Command-line entry point.
     *
     * <pre>
     *   FaultInjectionEvaluator &lt;repoPath&gt; &lt;baseRevision&gt; [grown] [outputDir]
     * </pre>
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 4) {
            System.err.println("Usage: FaultInjectionEvaluator <repoPath> <baseRevision> [grown] [outputDir]");
            System.exit(1);
        }
        boolean grown = args.length >= 3 && "grown".equalsIgnoreCase(args[2]);
        List<Fault> faults = grown ? GrownSampleFaults.faults() : FaultInjectionRunner.sampleProjectFaults();
        String outputDir = args.length >= 4 ? args[3] : "experiments";

        EvaluationReport report = new FaultInjectionEvaluator().evaluate(args[0], args[1], faults);
        System.out.print(report.toMarkdown());
        report.write(Paths.get(outputDir));
        System.out.println("Wrote " + EvaluationReport.MARKDOWN_FILE + " and "
                + EvaluationReport.JSON_FILE + " to " + Path.of(outputDir).toAbsolutePath());
    }
}
