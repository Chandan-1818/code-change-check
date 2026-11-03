package com.analysis.tool.preflight;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H9: the structured preflight outcome derives its status from the reasons it
 * carries, so a caller can never report SUPPORTED while holding a rejection.
 */
class PreflightResultTest {

    @Test
    void supportedWhenThereAreNoRejectionsAndNoReductions() {
        PreflightResult result = new PreflightResult(List.of(), List.of());

        assertEquals(PreflightResult.Status.SUPPORTED, result.getStatus());
        assertEquals("SUPPORTED", result.getSummary());
        assertFalse(result.isUnsupported());
        assertFalse(result.isReduced());
    }

    @Test
    void reducedWhenOnlyReductionsArePresent() {
        PreflightResult result = new PreflightResult(List.of(), List.of("Spring indicators ..."));

        assertEquals(PreflightResult.Status.REDUCED, result.getStatus());
        assertTrue(result.isReduced());
        assertFalse(result.isUnsupported());
        assertEquals("REDUCED (1 reason(s))", result.getSummary());
    }

    @Test
    void unsupportedWhenARejectionIsPresentEvenWithReductions() {
        PreflightResult result =
                new PreflightResult(List.of("not a Git repository"), List.of("Spring indicators ..."));

        assertEquals(PreflightResult.Status.UNSUPPORTED, result.getStatus());
        assertTrue(result.isUnsupported());
        assertFalse(result.isReduced());
        assertEquals("UNSUPPORTED (1 reason(s))", result.getSummary());
    }

    @Test
    void copiesItsReasonsSoTheCallerCannotMutateThem() {
        List<String> rejections = new ArrayList<>();
        rejections.add("r1");

        PreflightResult result = new PreflightResult(rejections, List.of("d1"));
        rejections.add("r2");

        assertEquals(List.of("r1"), result.getRejections());
        assertThrows(UnsupportedOperationException.class, () -> result.getRejections().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> result.getReductions().add("x"));
    }
}
