package com.analysis.tool.experiment;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-execution test: runs Maven in a temporary checkout of each revision,
 * so the figures are those of the suite that existed at that revision.
 * Uses pinned sample-project commits (never HEAD-relative refs):
 *   V4 = a3d0163, V5 = 268a165, V6 = cc0d769
 */
public class ExperimentExecutorTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    @Test
    public void shouldRunSuitesAtEachRevisionAndSkipEmptySelections() throws Exception {
        List<ExperimentExecutor.ExecutedRow> rows = new ExperimentExecutor().run(
                SAMPLE_PROJECT_PATH, Arrays.asList("a3d0163", "268a165", "cc0d769"));

        assertEquals(2, rows.size());

        ExperimentExecutor.ExecutedRow v4ToV5 = rows.get(0);
        assertTrue(v4ToV5.isExecuted());
        assertEquals(Set.of("testCompute", "testMultiply"), v4ToV5.getSelectedTests());
        assertEquals(5, v4ToV5.getFullTotal(), "The suite at V5 has 5 tests");
        assertEquals(5, v4ToV5.getFullPassed());
        assertEquals(2, v4ToV5.getSelectedTotal());
        assertEquals(2, v4ToV5.getSelectedPassed());
        assertEquals(60.0, v4ToV5.getTestReductionPercent(), 0.01,
                "(5-2)/5 = 60% at V5, not the V8 working-tree figure");
        assertTrue(v4ToV5.isHealthy());

        ExperimentExecutor.ExecutedRow v5ToV6 = rows.get(1);
        assertFalse(v5ToV6.isExecuted(), "V5 -> V6 selects nothing, so nothing is executed");
        assertTrue(v5ToV6.getSelectedTests().isEmpty());
    }

    @Test
    public void shouldRenderExecutedAndSkippedRowsAsMarkdown() {
        ExperimentExecutor.ExecutedRow executed = new ExperimentExecutor.ExecutedRow(
                "a3d0163", "268a165", Set.of("testCompute", "testMultiply"), true,
                5, 5, 0, 2, 2, 0, true, 60.0);
        ExperimentExecutor.ExecutedRow skipped = ExperimentExecutor.ExecutedRow.skipped(
                "268a165", "cc0d769", Collections.emptySet());

        String markdown = ExperimentExecutor.toMarkdown(Arrays.asList(executed, skipped));

        assertTrue(markdown.contains("| Range | Selected tests | Full suite | Selected run |"));
        assertTrue(markdown.contains("a3d0163 -> 268a165 | testCompute, testMultiply | 5 (5 passed) | 2 (2 passed) | 60.0% | OK"),
                "Actual markdown:\n" + markdown);
        assertTrue(markdown.contains("268a165 -> cc0d769 | - | - | - | - | skipped (nothing selected)"),
                "Actual markdown:\n" + markdown);
    }
}
