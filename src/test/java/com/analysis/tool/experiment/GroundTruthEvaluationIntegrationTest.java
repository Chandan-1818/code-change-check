package com.analysis.tool.experiment;

import com.analysis.tool.eval.CaseResult;
import com.analysis.tool.eval.EvaluationReport;
import com.analysis.tool.experiment.FaultInjectionRunner.Fault;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end ground-truth evaluation on the real sample project.
 *
 * <p>Two independent checks:
 *
 * <ol>
 *   <li>{@link AnalysisTestSelector} (the production adapter) is exercised on a
 *       real commit pair whose selection is pinned by
 *       {@code LargerSampleHistoryTest}, proving the adapter returns the real
 *       tool's selection.</li>
 *   <li>{@link FaultInjectionEvaluator} is run on a small, representative
 *       subset of the grown-sample faults. The ground truth for each fault is
 *       established by execution: the tests that actually fail in the full
 *       suite at the mutant revision. The reported recall must therefore be
 *       exactly 1.0 — a required test that the tool failed to select would be a
 *       safety gap and would fail this test.</li>
 * </ol>
 */
public class GroundTruthEvaluationIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH =
            com.analysis.tool.testsupport.SamplePaths.SAMPLE_PROJECT_PATH;
    private static final String VERSION_16_COMMIT = "7059f4c";
    private static final String VERSION_13_COMMIT = "d85a3b7";
    private static final String VERSION_14_COMMIT = "efc30d0";

    private static final Set<String> REPRESENTATIVE_FAULTS =
            Set.of("Calculator.add", "Multiplier.multiply", "OrderService.placeOrder");

    @Test
    public void analysisTestSelectorReturnsTheDocumentedSelection() throws Exception {
        // V13 -> V14 is pinned in LargerSampleHistoryTest: the modified method
        // reaches pricing and checkout transitively.
        Set<String> selected = new AnalysisTestSelector()
                .selectTests(SAMPLE_PROJECT_PATH, VERSION_13_COMMIT, VERSION_14_COMMIT);

        assertEquals(Set.of("testCalculateTax", "testCalculateTaxHasMinimumOfOne",
                "testCalculateTaxOnZeroAmount", "testCheckout", "testCheckoutOutOfStock",
                "testFinalPrice", "testFinalPriceOnZeroAmount"), selected);
    }

    @Test
    public void evaluatesRealInjectedFaultsWithoutMissingAnyRequiredTest(@TempDir Path dir)
            throws Exception {
        List<Fault> subset = GrownSampleFaults.faults().stream()
                .filter(fault -> REPRESENTATIVE_FAULTS.contains(fault.getMethod()))
                .collect(Collectors.toList());
        assertEquals(REPRESENTATIVE_FAULTS.size(), subset.size(),
                "Every representative fault must exist in the grown-sample set");

        EvaluationReport report = new FaultInjectionEvaluator()
                .evaluate(SAMPLE_PROJECT_PATH, VERSION_16_COMMIT, subset);

        String markdown = report.toMarkdown();
        assertEquals(REPRESENTATIVE_FAULTS.size(), report.getCaseCount(), markdown);

        for (CaseResult result : report.getCaseResults()) {
            assertTrue(result.getFalseNegativeTests().isEmpty(),
                    "A required test was not selected for " + result.getCase().getId()
                            + ". Actual report:\n" + markdown);
        }
        assertTrue(report.getAggregate().getTruePositives() > 0,
                "At least one required test must have been selected. Actual report:\n" + markdown);
        assertEquals(0, report.getAggregate().getFalseNegatives(),
                "No required test may be missed. Actual report:\n" + markdown);
        assertEquals(1.0, report.getAggregate().getRecall().doubleValue(), 1e-9,
                "Recall must be exactly 1.0. Actual report:\n" + markdown);

        report.write(dir);
        Path markdownFile = dir.resolve(EvaluationReport.MARKDOWN_FILE);
        Path jsonFile = dir.resolve(EvaluationReport.JSON_FILE);
        assertTrue(Files.isRegularFile(markdownFile));
        assertTrue(Files.isRegularFile(jsonFile));
        assertEquals(report.toMarkdown(),
                Files.readString(markdownFile, StandardCharsets.UTF_8));
        assertEquals(report.toJson(), Files.readString(jsonFile, StandardCharsets.UTF_8));
    }
}
