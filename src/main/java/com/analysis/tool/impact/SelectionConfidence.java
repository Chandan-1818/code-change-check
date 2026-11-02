package com.analysis.tool.impact;

/**
 * How much trust to place in a selection produced by analyzeTransitively.
 *
 * HIGH     - every source and test file parsed, no conservative expansion was
 *            needed, so the selection is the precise impact set.
 * REDUCED  - analysis completed but a non-method change (field, import,
 *            constructor, class header...) forced every method of the file to
 *            be treated as changed, so the selection is a superset.
 * FALLBACK - at least one file could not be parsed, so the impact set cannot
 *            be trusted; every discovered test is selected instead.
 */
public enum SelectionConfidence {
    HIGH,
    REDUCED,
    FALLBACK
}
