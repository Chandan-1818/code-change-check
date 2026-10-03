package com.analysis.tool.experiment;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Selection-only experiment over the grown sample history V8..V16, using
 * pinned commit hashes (never HEAD-relative refs):
 *   V8  = ebec3c0   V9  = 7b79d9c   V10 = 52db174   V11 = 2a36954
 *   V12 = bee1bf5   V13 = d85a3b7   V14 = efc30d0   V15 = 3337a0b
 *   V16 = 7059f4c
 */
public class LargerSampleHistoryTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final List<String> REVISIONS = Arrays.asList(
            "ebec3c0", "7b79d9c", "52db174", "2a36954", "bee1bf5",
            "d85a3b7", "efc30d0", "3337a0b", "7059f4c");

    private List<ExperimentRunner.Row> run() throws Exception {
        return new ExperimentRunner().run(SAMPLE_PROJECT_PATH, REVISIONS);
    }

    @Test
    public void shouldSelectExpectedTestsAndReportUntestedAndRemovedMethods() throws Exception {
        List<ExperimentRunner.Row> rows = run();

        assertEquals(8, rows.size(), "9 revisions should give 8 consecutive pairs");

        assertEquals(Set.of("testCalculateTax", "testCalculateTaxOnZeroAmount"),
                rows.get(0).getSelectedTests(), "V8 -> V9");
        assertEquals(Set.of("testApplyDiscount", "testApplyDiscountOnZeroAmount"),
                rows.get(1).getSelectedTests(), "V9 -> V10");
        assertEquals(Set.of("testFinalPrice", "testFinalPriceOnZeroAmount"),
                rows.get(2).getSelectedTests(), "V10 -> V11");
        assertEquals(Set.of("testRestockAndAvailable", "testSetAndGetStock"),
                rows.get(3).getSelectedTests(), "V11 -> V12");
        assertEquals(Set.of("testCheckout", "testCheckoutOutOfStock"),
                rows.get(4).getSelectedTests(), "V12 -> V13");

        assertEquals(Set.of("testCalculateTax", "testCalculateTaxHasMinimumOfOne",
                        "testCalculateTaxOnZeroAmount", "testCheckout", "testCheckoutOutOfStock",
                        "testFinalPrice", "testFinalPriceOnZeroAmount"),
                rows.get(5).getSelectedTests(),
                "V13 -> V14: the modified method reaches pricing and checkout transitively");

        assertTrue(rows.get(6).getSelectedTests().isEmpty(), "V14 -> V15 selects nothing");
        assertEquals(Set.of("quote"), rows.get(6).getUntestedMethods(), "V14 -> V15");

        assertTrue(rows.get(7).getSelectedTests().isEmpty(), "V15 -> V16 selects nothing");
        assertEquals(Set.of("compute"), rows.get(7).getRemovedMethods(), "V15 -> V16");

        for (int i = 0; i < rows.size(); i++) {
            if (i != 6) {
                assertEquals(Collections.emptySet(), rows.get(i).getUntestedMethods(),
                        "Only V14 -> V15 has an untested change; row " + i);
            }
            if (i != 7) {
                assertEquals(Collections.emptySet(), rows.get(i).getRemovedMethods(),
                        "Only V15 -> V16 removes a method; row " + i);
            }
        }
    }

    @Test
    public void shouldRenderTheModifiedMethodRowInMarkdown() throws Exception {
        String markdown = ExperimentRunner.toMarkdown(run());

        assertTrue(markdown.contains("d85a3b7 -> efc30d0"), "Actual markdown:\n" + markdown);
        assertTrue(markdown.contains("| quote |"), "Actual markdown:\n" + markdown);
        assertTrue(markdown.contains("| compute |"), "Actual markdown:\n" + markdown);
    }
}
