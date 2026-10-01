package com.analysis.tool.experiment;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs ExperimentRunner over the real sample-project history V1..V8 using
 * pinned commit hashes (never HEAD-relative refs), and checks the rows
 * against selections already proven by the other integration tests.
 */
public class ExperimentRunnerTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final List<String> REVISIONS = Arrays.asList(
            "f2493c2",  // V1
            "9ed64fa",  // V2
            "7584402",  // V3
            "a3d0163",  // V4
            "268a165",  // V5
            "cc0d769",  // V6
            "bae01d1",  // V7
            "ebec3c0"); // V8

    private List<ExperimentRunner.Row> run() throws Exception {
        return new ExperimentRunner().run(SAMPLE_PROJECT_PATH, REVISIONS);
    }

    @Test
    public void shouldProduceOneRowPerConsecutiveRevisionPair() throws Exception {
        List<ExperimentRunner.Row> rows = run();

        assertEquals(7, rows.size(), "8 revisions should give 7 consecutive pairs");
        assertEquals("f2493c2", rows.get(0).getOldRevision());
        assertEquals("9ed64fa", rows.get(0).getNewRevision());
        assertEquals("ebec3c0", rows.get(6).getNewRevision());
    }

    @Test
    public void shouldMatchSelectionsProvenElsewhereForKnownRanges() throws Exception {
        List<ExperimentRunner.Row> rows = run();

        ExperimentRunner.Row v1ToV2 = rows.get(0);
        assertTrue(v1ToV2.getSelectedTests().contains("testSubtract"));
        assertFalse(v1ToV2.getSelectedTests().contains("testAdd"));

        assertEquals(Set.of("testCompute"), rows.get(2).getSelectedTests(), "V3 -> V4");

        assertEquals(Set.of("testCompute", "testMultiply"), rows.get(3).getSelectedTests(), "V4 -> V5");
        assertTrue(rows.get(3).getUntestedMethods().isEmpty(), "V4 -> V5 has no untested changes");

        assertTrue(rows.get(4).getSelectedTests().isEmpty(), "V5 -> V6 selects nothing");
        assertEquals(Set.of("negate"), rows.get(4).getUntestedMethods(), "V5 -> V6");

        assertEquals(Set.of("testPlaceOrder", "testSaveAndFindTotal"),
                rows.get(5).getSelectedTests(), "V6 -> V7");
        assertTrue(rows.get(5).getUntestedMethods().isEmpty(), "V6 -> V7 has no untested changes");

        assertEquals(Set.of("testPlaceOrder", "testSaveAndFindTotal"),
                rows.get(6).getSelectedTests(), "V7 -> V8");
    }

    @Test
    public void shouldRenderMarkdownAndCsv() throws Exception {
        List<ExperimentRunner.Row> rows = run();

        String markdown = ExperimentRunner.toMarkdown(rows);
        assertTrue(markdown.contains("| Range | File pairs | Selected tests |"));
        assertTrue(markdown.contains("a3d0163 -> 268a165"));
        assertTrue(markdown.contains("testCompute, testMultiply"));

        String csv = ExperimentRunner.toCsv(rows);
        assertTrue(csv.startsWith("oldRevision,newRevision,filePairs,selectedCount,"));
        assertEquals(8, csv.lines().count(), "1 header line + 7 data rows");
    }
}
