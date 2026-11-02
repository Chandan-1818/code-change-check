package com.analysis.tool.eval;

import java.util.Locale;
import java.util.Set;

/**
 * Confusion-matrix metrics for a ground-truth evaluation of regression test
 * selection.
 *
 * <ul>
 *   <li><b>TP</b> (true positive): a test that is both required (ground truth)
 *       and selected by the tool.</li>
 *   <li><b>FP</b> (false positive): a test that the tool selected but that is
 *       not required.</li>
 *   <li><b>FN</b> (false negative): a test that is required but was not
 *       selected.</li>
 * </ul>
 *
 * <pre>
 *   precision = TP / (TP + FP)
 *   recall    = TP / (TP + FN)
 * </pre>
 *
 * Zero denominators are handled explicitly. When nothing was selected the
 * precision is <em>undefined</em> (returned as {@code null} and rendered
 * {@code "n/a"}), and when nothing is required the recall is undefined. An
 * undefined metric is never silently turned into 100%, which would be
 * misleading on an empty selection.
 */
public final class PrecisionRecall {

    private final int truePositives;
    private final int falsePositives;
    private final int falseNegatives;

    private PrecisionRecall(int truePositives, int falsePositives, int falseNegatives) {
        this.truePositives = truePositives;
        this.falsePositives = falsePositives;
        this.falseNegatives = falseNegatives;
    }

    /**
     * Computes the confusion matrix of a selection against a required set.
     * {@code null} inputs are treated as empty sets so an incomplete case does
     * not crash the evaluation.
     */
    public static PrecisionRecall of(Set<String> selected, Set<String> required) {
        Set<String> selection = selected == null ? Set.of() : selected;
        Set<String> requirement = required == null ? Set.of() : required;

        int tp = 0;
        int fp = 0;
        for (String test : selection) {
            if (requirement.contains(test)) {
                tp++;
            } else {
                fp++;
            }
        }
        int fn = 0;
        for (String test : requirement) {
            if (!selection.contains(test)) {
                fn++;
            }
        }
        return new PrecisionRecall(tp, fp, fn);
    }

    /** Builds a metric value directly from already-summed counts (for aggregates). */
    public static PrecisionRecall ofCounts(int truePositives, int falsePositives, int falseNegatives) {
        if (truePositives < 0 || falsePositives < 0 || falseNegatives < 0) {
            throw new IllegalArgumentException("Confusion-matrix counts must not be negative");
        }
        return new PrecisionRecall(truePositives, falsePositives, falseNegatives);
    }

    public int getTruePositives() {
        return truePositives;
    }

    public int getFalsePositives() {
        return falsePositives;
    }

    public int getFalseNegatives() {
        return falseNegatives;
    }

    public int getSelectedCount() {
        return truePositives + falsePositives;
    }

    public int getRequiredCount() {
        return truePositives + falseNegatives;
    }

    public boolean isPrecisionDefined() {
        return getSelectedCount() > 0;
    }

    public boolean isRecallDefined() {
        return getRequiredCount() > 0;
    }

    /** Precision, or {@code null} when nothing was selected (undefined). */
    public Double getPrecision() {
        return isPrecisionDefined() ? (double) truePositives / getSelectedCount() : null;
    }

    /** Recall, or {@code null} when nothing is required (undefined). */
    public Double getRecall() {
        return isRecallDefined() ? (double) truePositives / getRequiredCount() : null;
    }

    public String getPrecisionText() {
        return text(getPrecision());
    }

    public String getRecallText() {
        return text(getRecall());
    }

    private static String text(Double value) {
        return value == null ? "n/a" : String.format(Locale.ROOT, "%.4f", value);
    }

    @Override
    public String toString() {
        return "PrecisionRecall{tp=" + truePositives + ", fp=" + falsePositives
                + ", fn=" + falseNegatives + ", precision=" + getPrecisionText()
                + ", recall=" + getRecallText() + "}";
    }
}
