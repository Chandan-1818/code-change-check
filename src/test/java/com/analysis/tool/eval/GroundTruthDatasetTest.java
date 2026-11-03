package com.analysis.tool.eval;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing, validation and loading tests for the ground-truth dataset,
 * including the invalid/incomplete-data cases that must be rejected with the
 * offending line number.
 */
public class GroundTruthDatasetTest {

    @Test
    public void parsesValidLinesInDatasetOrder() {
        GroundTruthDataset dataset = GroundTruthDataset.parse(List.of(
                "c1 | aaa1111 | bbb2222 | A.m;B.n | src/main/java/A.java | testA;testB | first",
                "c2 | ccc3333 | ddd4444 | C.k | src/main/java/C.java | testC"));

        assertEquals(2, dataset.size());
        assertEquals(List.of("c1", "c2"),
                List.of(dataset.getCases().get(0).getId(), dataset.getCases().get(1).getId()));

        GroundTruthCase first = dataset.getCases().get(0);
        assertEquals("aaa1111", first.getBaselineRevision());
        assertEquals("bbb2222", first.getChangedRevision());
        assertEquals(List.of("A.m", "B.n"), first.getChangedMethods());
        assertEquals(List.of("src/main/java/A.java"), first.getChangedFiles());
        assertEquals(Set.of("testA", "testB"), first.getRequiredTests());
        assertEquals("first", first.getNote());

        assertEquals("", dataset.getCases().get(1).getNote(), "note is optional");
    }

    @Test
    public void ignoresBlankLinesCommentsAndWhitespace() {
        GroundTruthDataset dataset = GroundTruthDataset.parse(List.of(
                "",
                "   ",
                "# a comment",
                "   # indented comment",
                "c1 | a | b | A.m |   F.java   |   testA ; testB   | note"));

        assertEquals(1, dataset.size());
        GroundTruthCase c = dataset.getCases().get(0);
        assertEquals("a", c.getBaselineRevision());
        assertEquals(List.of("F.java"), c.getChangedFiles());
        assertEquals(Set.of("testA", "testB"), c.getRequiredTests());
    }

    @Test
    public void allowsAnEmptyRequiredTestListMeaningNoTestDetectsTheChange() {
        GroundTruthDataset dataset = GroundTruthDataset.parse(List.of(
                "c1 | a | b | A.m | F.java | | no test detects this"));

        assertTrue(dataset.getCases().get(0).getRequiredTests().isEmpty());
    }

    @Test
    public void rejectsLinesWithTooFewFieldsAndReportsTheLineNumber() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> GroundTruthDataset.parse(List.of(
                        "c1 | a | b | A.m | F.java | testA",
                        "c2 | c | d")));

        assertTrue(ex.getMessage().contains("line 2"), ex.getMessage());
        assertTrue(ex.getMessage().contains("at least 6 fields"), ex.getMessage());
    }

    @Test
    public void rejectsBlankIdOrRevisionWithLineNumber() {
        IllegalArgumentException blankId = assertThrows(IllegalArgumentException.class,
                () -> GroundTruthDataset.parse(List.of("# ok", "  | a | b | A.m | F.java | t")));
        assertTrue(blankId.getMessage().contains("line 2"), blankId.getMessage());
        assertTrue(blankId.getMessage().contains("id"), blankId.getMessage());

        IllegalArgumentException blankRev = assertThrows(IllegalArgumentException.class,
                () -> GroundTruthDataset.parse(List.of("c1 |  | b | A.m | F.java | t")));
        assertTrue(blankRev.getMessage().contains("line 1"), blankRev.getMessage());
        assertTrue(blankRev.getMessage().contains("baselineRevision"), blankRev.getMessage());
    }

    @Test
    public void rejectsMissingChangedMethodOrFile() {
        IllegalArgumentException noMethod = assertThrows(IllegalArgumentException.class,
                () -> GroundTruthDataset.parse(List.of("c1 | a | b | | F.java | t")));
        assertTrue(noMethod.getMessage().contains("changedMethods"), noMethod.getMessage());

        IllegalArgumentException noFile = assertThrows(IllegalArgumentException.class,
                () -> GroundTruthDataset.parse(List.of("c1 | a | b | A.m | | t")));
        assertTrue(noFile.getMessage().contains("changedFiles"), noFile.getMessage());
    }

    @Test
    public void rejectsDuplicateCaseIds() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> GroundTruthDataset.parse(List.of(
                        "c1 | a | b | A.m | F.java | t",
                        "c1 | c | d | C.k | G.java | t")));

        assertTrue(ex.getMessage().contains("duplicate"), ex.getMessage());
        assertTrue(ex.getMessage().contains("c1"), ex.getMessage());
    }

    @Test
    public void rejectsNullAndEmptyCollections() {
        assertThrows(IllegalArgumentException.class, () -> GroundTruthDataset.parse(null));
        assertThrows(IllegalArgumentException.class, () -> new GroundTruthDataset(null));

        GroundTruthDataset empty = new GroundTruthDataset(List.of());
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.size());
    }

    @Test
    public void loadsAFileAndRejectsAMissingOne() throws Exception {
        Path file = Files.createTempFile("ground-truth-", ".txt");
        try {
            Files.writeString(file, String.join("\n",
                    "# header",
                    "c1 | aaa1111 | bbb2222 | A.m | F.java | testA | note",
                    ""), StandardCharsets.UTF_8);

            GroundTruthDataset dataset = GroundTruthDataset.load(file);
            assertEquals(1, dataset.size());
            assertEquals("c1", dataset.getCases().get(0).getId());
        } finally {
            Files.deleteIfExists(file);
        }

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> GroundTruthDataset.load(Path.of("does-not-exist-ground-truth.txt")));
        assertTrue(missing.getMessage().contains("not found"), missing.getMessage());
    }
}
