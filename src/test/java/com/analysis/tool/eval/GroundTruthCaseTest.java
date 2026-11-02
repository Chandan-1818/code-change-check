package com.analysis.tool.eval;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation and immutability tests for a single ground-truth case.
 */
public class GroundTruthCaseTest {

    private static GroundTruthCase validCase() {
        return new GroundTruthCase("c1", "aaa1111", "bbb2222",
                List.of("src/main/java/A.java"), List.of("A.m"),
                Set.of("t1", "t2"), "note");
    }

    @Test
    public void exposesEveryRecordedField() {
        GroundTruthCase c = validCase();

        assertEquals("c1", c.getId());
        assertEquals("aaa1111", c.getBaselineRevision());
        assertEquals("bbb2222", c.getChangedRevision());
        assertEquals(List.of("src/main/java/A.java"), c.getChangedFiles());
        assertEquals(List.of("A.m"), c.getChangedMethods());
        assertEquals(Set.of("t1", "t2"), c.getRequiredTests());
        assertEquals("note", c.getNote());
    }

    @Test
    public void requiredTestsAreSortedSoRenderingIsDeterministic() {
        GroundTruthCase c = new GroundTruthCase("c1", "a", "b",
                List.of("f.java"), List.of("A.m"),
                Set.of("t2", "t1", "t3"), "");

        assertEquals(List.of("t1", "t2", "t3"), new ArrayList<>(new TreeSet<>(c.getRequiredTests())));
    }

    @Test
    public void defensiveCopiesProtectAgainstLaterMutation() {
        List<String> files = new ArrayList<>(List.of("f.java"));
        List<String> methods = new ArrayList<>(List.of("A.m"));
        Set<String> required = new TreeSet<>(Set.of("t1"));
        GroundTruthCase c = new GroundTruthCase("c1", "a", "b", files, methods, required, "");

        files.add("g.java");
        methods.add("A.n");
        required.add("t2");

        assertEquals(List.of("f.java"), c.getChangedFiles());
        assertEquals(List.of("A.m"), c.getChangedMethods());
        assertEquals(Set.of("t1"), c.getRequiredTests());
        assertThrows(UnsupportedOperationException.class, () -> c.getChangedFiles().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> c.getRequiredTests().add("x"));
    }

    @Test
    public void blankIdOrRevisionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GroundTruthCase(
                " ", "a", "b", List.of("f"), List.of("m"), Set.of(), ""));
        assertThrows(IllegalArgumentException.class, () -> new GroundTruthCase(
                "id", null, "b", List.of("f"), List.of("m"), Set.of(), ""));
        assertThrows(IllegalArgumentException.class, () -> new GroundTruthCase(
                "id", "a", "", List.of("f"), List.of("m"), Set.of(), ""));
    }

    @Test
    public void emptyChangedFilesOrMethodsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GroundTruthCase(
                "id", "a", "b", List.of(), List.of("m"), Set.of(), ""));
        assertThrows(IllegalArgumentException.class, () -> new GroundTruthCase(
                "id", "a", "b", List.of("f"), List.of(), Set.of(), ""));
        assertThrows(IllegalArgumentException.class, () -> new GroundTruthCase(
                "id", "a", "b", null, List.of("m"), Set.of(), ""));
    }

    @Test
    public void nullRequiredTestsBecomeEmptyAndNullNoteBecomesEmpty() {
        GroundTruthCase c = new GroundTruthCase("id", "a", "b",
                List.of("f"), List.of("m"), null, null);

        assertTrue(c.getRequiredTests().isEmpty());
        assertEquals("", c.getNote());
    }
}
