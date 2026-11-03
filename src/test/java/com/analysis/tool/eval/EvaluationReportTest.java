package com.analysis.tool.eval;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Aggregation and report-rendering tests. The report must be deterministic
 * (two renderings byte-identical) so that repeated experiment runs produce the
 * same evidence, and zero-denominator metrics must render explicitly rather
 * than as a misleading 100%.
 */
public class EvaluationReportTest {

    private static GroundTruthCase caseWith(String id, Set<String> required) {
        return new GroundTruthCase(id, "base", "changed",
                List.of("src/main/java/" + id + ".java"), List.of(id + ".m"), required, "note");
    }

    private static EvaluationReport reportOf(CaseResult... results) {
        return new EvaluationReport(List.of(results));
    }

    @Test
    public void aggregatesTrueFalsePositivesAndFalseNegativesAcrossCases() {
        // case 1: selected {a,b,c}, required {a,b,d} -> tp=2, fp=1, fn=1
        CaseResult r1 = new CaseResult(caseWith("c1", Set.of("a", "b", "d")), Set.of("a", "b", "c"));
        // case 2: selected {x}, required {x} -> tp=1, fp=0, fn=0
        CaseResult r2 = new CaseResult(caseWith("c2", Set.of("x")), Set.of("x"));

        EvaluationReport report = reportOf(r1, r2);

        assertEquals(2, report.getCaseCount());
        assertEquals(3, report.getAggregate().getTruePositives());
        assertEquals(1, report.getAggregate().getFalsePositives());
        assertEquals(1, report.getAggregate().getFalseNegatives());
        assertEquals(3.0 / 4.0, report.getAggregate().getPrecision().doubleValue(), 1e-9);
        assertEquals(3.0 / 4.0, report.getAggregate().getRecall().doubleValue(), 1e-9);
    }

    @Test
    public void markdownListsEveryCaseAndTheAggregate() {
        CaseResult r1 = new CaseResult(caseWith("c1", Set.of("a", "b")), Set.of("a"));
        EvaluationReport report = reportOf(r1);

        String markdown = report.toMarkdown();

        assertTrue(markdown.contains("| Case | Baseline | Changed |"), markdown);
        assertTrue(markdown.contains("| c1 | base | changed |"), markdown);
        assertTrue(markdown.contains("TP=1"), markdown);
        assertTrue(markdown.contains("FP=0"), markdown);
        assertTrue(markdown.contains("FN=1"), markdown);
    }

    @Test
    public void markdownRendersUndefinedMetricsAsNa() {
        // nothing selected -> precision undefined, recall defined (0.0)
        CaseResult emptySelection = new CaseResult(caseWith("c1", Set.of("a")), Set.of());
        // nothing required -> recall undefined, precision defined (0.0)
        CaseResult emptyRequired = new CaseResult(caseWith("c2", Set.of()), Set.of("z"));

        EvaluationReport report = reportOf(emptySelection, emptyRequired);
        String markdown = report.toMarkdown();

        // per-case: c1 tp=0 fp=0 fn=1 -> precision n/a, recall 0.0000
        assertTrue(markdown.contains("| 0 | 0 | 1 | n/a | 0.0000 |"), markdown);
        // c2 tp=0 fp=1 fn=0 -> precision 0.0000, recall n/a
        assertTrue(markdown.contains("| 0 | 1 | 0 | 0.0000 | n/a |"), markdown);
        // aggregate: tp=0, fp=1, fn=1 -> precision 0.0, recall 0.0 (no misleading 100%)
        assertTrue(markdown.contains("precision=0.0000, recall=0.0000"), markdown);
        assertFalse(markdown.contains("precision=1.0000"), markdown);
    }

    @Test
    public void jsonEncodesNullForUndefinedMetrics() {
        CaseResult emptySelection = new CaseResult(caseWith("c1", Set.of("a")), Set.of());
        EvaluationReport report = reportOf(emptySelection);

        String json = report.toJson();

        assertTrue(json.contains("\"id\": \"c1\""), json);
        assertTrue(json.contains("\"selectedTests\": []"), json);
        assertTrue(json.contains("\"precision\": null"), json);
        assertTrue(json.contains("\"recall\": 0.000000"), json);
        assertTrue(json.contains("\"cases\": 1"), json);
    }

    @Test
    public void jsonAggregateIsNullWhenNothingSelected() {
        CaseResult emptySelection = new CaseResult(caseWith("c1", Set.of("a", "b")), Set.of());
        EvaluationReport report = reportOf(emptySelection);

        String json = report.toJson();
        int aggregateStart = json.indexOf("\"aggregate\"");
        assertTrue(aggregateStart > 0, json);
        assertTrue(json.substring(aggregateStart).contains("\"precision\": null"), json);
    }

    @Test
    public void renderingIsDeterministicAcrossRepeatedCalls() {
        CaseResult r1 = new CaseResult(caseWith("c1", Set.of("b", "a")), Set.of("c", "a"));
        CaseResult r2 = new CaseResult(caseWith("c2", Set.of("y")), Set.of("y", "x"));
        EvaluationReport report = reportOf(r1, r2);

        assertEquals(report.toMarkdown(), report.toMarkdown());
        assertEquals(report.toJson(), report.toJson());
    }

    @Test
    public void writesBothReportFilesIntoTheDirectory(@TempDir Path dir) throws Exception {
        CaseResult r1 = new CaseResult(caseWith("c1", Set.of("a", "b")), Set.of("a", "b"));
        EvaluationReport report = reportOf(r1);

        Path nested = dir.resolve("out");
        report.write(nested);

        Path markdown = nested.resolve(EvaluationReport.MARKDOWN_FILE);
        Path json = nested.resolve(EvaluationReport.JSON_FILE);
        assertTrue(Files.isRegularFile(markdown));
        assertTrue(Files.isRegularFile(json));
        assertEquals(report.toMarkdown(), Files.readString(markdown, StandardCharsets.UTF_8));
        assertEquals(report.toJson(), Files.readString(json, StandardCharsets.UTF_8));
    }

    @Test
    public void emptyReportHasUndefinedAggregateMetrics() {
        EvaluationReport report = new EvaluationReport(List.of());

        assertEquals(0, report.getCaseCount());
        assertFalse(report.getAggregate().isPrecisionDefined());
        assertFalse(report.getAggregate().isRecallDefined());
        assertTrue(report.toMarkdown().contains("precision=n/a, recall=n/a"), report.toMarkdown());
        assertTrue(report.toJson().contains("\"cases\": 0"), report.toJson());
    }
}
