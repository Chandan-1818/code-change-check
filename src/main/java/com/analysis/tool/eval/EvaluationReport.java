package com.analysis.tool.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The result of evaluating a whole ground-truth dataset: per-case metrics plus
 * the aggregate confusion matrix, rendered either as a human-readable
 * Markdown report or as a machine-readable JSON document.
 *
 * <p>The report is deterministic: cases are kept in dataset order and every
 * test set is sorted, so two renderings of the same report are byte-identical.
 *
 * <p>Precision is a <em>lower bound</em> (decision D-16). A selected test that
 * passes under the injected change may still be genuinely relevant, so the
 * reported precision understates the true value. Recall, by contrast, is exact
 * with respect to the fault-induced ground truth.
 */
public final class EvaluationReport {

    public static final String MARKDOWN_FILE = "ground-truth-report.md";
    public static final String JSON_FILE = "ground-truth-results.json";

    private final List<CaseResult> caseResults;
    private final PrecisionRecall aggregate;

    public EvaluationReport(List<CaseResult> caseResults) {
        if (caseResults == null) {
            throw new IllegalArgumentException("caseResults must not be null");
        }
        this.caseResults = List.copyOf(caseResults);
        int tp = 0;
        int fp = 0;
        int fn = 0;
        for (CaseResult result : this.caseResults) {
            tp += result.getMetrics().getTruePositives();
            fp += result.getMetrics().getFalsePositives();
            fn += result.getMetrics().getFalseNegatives();
        }
        this.aggregate = PrecisionRecall.ofCounts(tp, fp, fn);
    }

    public List<CaseResult> getCaseResults() {
        return caseResults;
    }

    public PrecisionRecall getAggregate() {
        return aggregate;
    }

    public int getCaseCount() {
        return caseResults.size();
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Ground-truth evaluation report\n\n");
        sb.append("Cases: ").append(caseResults.size()).append("\n\n");
        sb.append("| Case | Baseline | Changed | Selected | Required | TP | FP | FN | Precision | Recall |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (CaseResult result : caseResults) {
            GroundTruthCase testCase = result.getCase();
            PrecisionRecall metrics = result.getMetrics();
            sb.append("| ").append(testCase.getId())
              .append(" | ").append(testCase.getBaselineRevision())
              .append(" | ").append(testCase.getChangedRevision())
              .append(" | ").append(join(result.getSelectedTests()))
              .append(" | ").append(join(result.getRequiredTests()))
              .append(" | ").append(metrics.getTruePositives())
              .append(" | ").append(metrics.getFalsePositives())
              .append(" | ").append(metrics.getFalseNegatives())
              .append(" | ").append(metrics.getPrecisionText())
              .append(" | ").append(metrics.getRecallText())
              .append(" |\n");
        }
        sb.append('\n');
        sb.append("Aggregate: TP=").append(aggregate.getTruePositives())
          .append(", FP=").append(aggregate.getFalsePositives())
          .append(", FN=").append(aggregate.getFalseNegatives())
          .append("; precision=").append(aggregate.getPrecisionText())
          .append(", recall=").append(aggregate.getRecallText()).append(".\n\n");
        sb.append("Recall is exact with respect to the ground truth; precision is a lower bound "
                + "(a selected test that still passes may be relevant). \"n/a\" marks a metric with "
                + "a zero denominator (nothing selected, or nothing required).\n");
        return sb.toString();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"cases\": [\n");
        for (int i = 0; i < caseResults.size(); i++) {
            CaseResult result = caseResults.get(i);
            GroundTruthCase testCase = result.getCase();
            PrecisionRecall metrics = result.getMetrics();
            sb.append("    {\n");
            sb.append("      \"id\": ").append(jsonString(testCase.getId())).append(",\n");
            sb.append("      \"baselineRevision\": ").append(jsonString(testCase.getBaselineRevision())).append(",\n");
            sb.append("      \"changedRevision\": ").append(jsonString(testCase.getChangedRevision())).append(",\n");
            sb.append("      \"selectedTests\": ").append(jsonArray(result.getSelectedTests())).append(",\n");
            sb.append("      \"requiredTests\": ").append(jsonArray(result.getRequiredTests())).append(",\n");
            sb.append("      \"truePositives\": ").append(metrics.getTruePositives()).append(",\n");
            sb.append("      \"falsePositives\": ").append(metrics.getFalsePositives()).append(",\n");
            sb.append("      \"falseNegatives\": ").append(metrics.getFalseNegatives()).append(",\n");
            sb.append("      \"precision\": ").append(jsonNumber(metrics.getPrecision())).append(",\n");
            sb.append("      \"recall\": ").append(jsonNumber(metrics.getRecall())).append("\n");
            sb.append("    }").append(i + 1 < caseResults.size() ? "," : "").append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"aggregate\": {\n");
        sb.append("    \"cases\": ").append(caseResults.size()).append(",\n");
        sb.append("    \"truePositives\": ").append(aggregate.getTruePositives()).append(",\n");
        sb.append("    \"falsePositives\": ").append(aggregate.getFalsePositives()).append(",\n");
        sb.append("    \"falseNegatives\": ").append(aggregate.getFalseNegatives()).append(",\n");
        sb.append("    \"precision\": ").append(jsonNumber(aggregate.getPrecision())).append(",\n");
        sb.append("    \"recall\": ").append(jsonNumber(aggregate.getRecall())).append("\n");
        sb.append("  }\n");
        sb.append("}\n");
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

    private static String jsonArray(Set<String> values) {
        return values.stream().map(EvaluationReport::jsonString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String jsonNumber(Double value) {
        return value == null ? "null" : String.format(Locale.ROOT, "%.6f", value);
    }

    private static String jsonString(String value) {
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

    private static String join(Set<String> values) {
        return values.isEmpty() ? "-" : String.join(", ", values);
    }
}
