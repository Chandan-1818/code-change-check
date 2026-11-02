package com.analysis.tool.eval;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the confusion-matrix arithmetic, including the
 * zero-denominator cases that must stay explicitly undefined.
 */
public class PrecisionRecallTest {

    @Test
    public void countsTrueFalsePositivesAndFalseNegatives() {
        PrecisionRecall pr = PrecisionRecall.of(Set.of("a", "b", "c"), Set.of("a", "b", "d"));

        assertEquals(2, pr.getTruePositives());
        assertEquals(1, pr.getFalsePositives());
        assertEquals(1, pr.getFalseNegatives());
        assertEquals(3, pr.getSelectedCount());
        assertEquals(3, pr.getRequiredCount());
        assertEquals(2.0 / 3.0, pr.getPrecision().doubleValue(), 1e-9);
        assertEquals(2.0 / 3.0, pr.getRecall().doubleValue(), 1e-9);
    }

    @Test
    public void perfectSelectionHasFullPrecisionAndRecall() {
        PrecisionRecall pr = PrecisionRecall.of(Set.of("a", "b"), Set.of("a", "b"));

        assertEquals(2, pr.getTruePositives());
        assertEquals(0, pr.getFalsePositives());
        assertEquals(0, pr.getFalseNegatives());
        assertEquals(1.0, pr.getPrecision().doubleValue(), 1e-9);
        assertEquals(1.0, pr.getRecall().doubleValue(), 1e-9);
    }

    @Test
    public void precisionIsUndefinedWhenNothingIsSelected() {
        PrecisionRecall pr = PrecisionRecall.of(Set.of(), Set.of("a", "b"));

        assertEquals(0, pr.getTruePositives());
        assertEquals(0, pr.getFalsePositives());
        assertEquals(2, pr.getFalseNegatives());
        assertFalse(pr.isPrecisionDefined());
        assertNull(pr.getPrecision());
        assertEquals("n/a", pr.getPrecisionText());
        assertTrue(pr.isRecallDefined());
        assertEquals(0.0, pr.getRecall().doubleValue(), 1e-9);
    }

    @Test
    public void recallIsUndefinedWhenNothingIsRequired() {
        PrecisionRecall pr = PrecisionRecall.of(Set.of("a"), Set.of());

        assertEquals(0, pr.getTruePositives());
        assertEquals(1, pr.getFalsePositives());
        assertEquals(0, pr.getFalseNegatives());
        assertTrue(pr.isPrecisionDefined());
        assertEquals(0.0, pr.getPrecision().doubleValue(), 1e-9);
        assertFalse(pr.isRecallDefined());
        assertNull(pr.getRecall());
        assertEquals("n/a", pr.getRecallText());
    }

    @Test
    public void bothMetricsAreUndefinedWhenEverythingIsEmpty() {
        PrecisionRecall pr = PrecisionRecall.of(Set.of(), Set.of());

        assertFalse(pr.isPrecisionDefined());
        assertFalse(pr.isRecallDefined());
        assertNull(pr.getPrecision());
        assertNull(pr.getRecall());
        assertEquals("n/a", pr.getPrecisionText());
        assertEquals("n/a", pr.getRecallText());
    }

    @Test
    public void nullInputsAreTreatedAsEmptySets() {
        PrecisionRecall pr = PrecisionRecall.of(null, null);

        assertEquals(0, pr.getTruePositives());
        assertNull(pr.getPrecision());
        assertNull(pr.getRecall());
    }

    @Test
    public void countsFactoryBuildsAggregateValues() {
        PrecisionRecall pr = PrecisionRecall.ofCounts(4, 6, 2);

        assertEquals(4, pr.getTruePositives());
        assertEquals(6, pr.getFalsePositives());
        assertEquals(2, pr.getFalseNegatives());
        assertEquals(0.4, pr.getPrecision().doubleValue(), 1e-9);
        assertEquals(4.0 / 6.0, pr.getRecall().doubleValue(), 1e-9);
    }

    @Test
    public void countsFactoryRejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () -> PrecisionRecall.ofCounts(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PrecisionRecall.ofCounts(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> PrecisionRecall.ofCounts(0, 0, -1));
    }
}
