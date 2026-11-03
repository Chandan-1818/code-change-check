package com.analysis.tool.impact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NonMethodChangeDetectorTest {

    private final NonMethodChangeDetector detector = new NonMethodChangeDetector();

    private static String cls(String members) {
        return "package demo;\npublic class C {\n" + members + "\n}\n";
    }

    // ---- changes or removals of existing things: flagged ----

    @Test
    public void changedConstantIsFlagged() {
        assertTrue(detector.hasNonMethodChange(
                cls("static final int D = 10;\nint f() { return D; }"),
                cls("static final int D = 20;\nint f() { return D; }")));
    }

    @Test
    public void removedFieldIsFlagged() {
        assertTrue(detector.hasNonMethodChange(
                cls("int x = 1;\nint f() { return 1; }"),
                cls("int f() { return 1; }")));
    }

    @Test
    public void removedImportIsFlagged() {
        assertTrue(detector.hasNonMethodChange(
                "package demo;\nimport java.util.List;\npublic class C { }\n",
                "package demo;\npublic class C { }\n"));
    }

    @Test
    public void classAnnotationAddedIsFlagged() {
        assertTrue(detector.hasNonMethodChange(
                "package demo;\npublic class C { }\n",
                "package demo;\n@Deprecated\npublic class C { }\n"));
    }

    @Test
    public void constructorBodyChangeIsFlagged() {
        assertTrue(detector.hasNonMethodChange(
                cls("int x;\nC() { x = 1; }"),
                cls("int x;\nC() { x = 2; }")));
    }

    // ---- pure additions, method changes, comments: NOT flagged ----

    @Test
    public void addedFieldIsNotFlagged() {
        assertFalse(detector.hasNonMethodChange(
                cls("int f() { return 1; }"),
                cls("int extra = 5;\nint f() { return 1; }")));
    }

    @Test
    public void addedImportIsNotFlagged() {
        assertFalse(detector.hasNonMethodChange(
                "package demo;\npublic class C { }\n",
                "package demo;\nimport java.util.List;\npublic class C { }\n"));
    }

    @Test
    public void addedOrRemovedMethodIsNotFlagged() {
        assertFalse(detector.hasNonMethodChange(
                cls("int f() { return 1; }"),
                cls("int f() { return 1; }\nint g() { return 2; }")));
        assertFalse(detector.hasNonMethodChange(
                cls("int f() { return 1; }\nint g() { return 2; }"),
                cls("int f() { return 1; }")));
    }

    @Test
    public void methodBodyOnlyChangeIsNotFlagged() {
        assertFalse(detector.hasNonMethodChange(
                cls("int f() { return 1; }"),
                cls("int f() { return 2; }")));
    }

    @Test
    public void commentOnlyChangeIsNotFlagged() {
        assertFalse(detector.hasNonMethodChange(
                cls("int x = 1;\nint f() { return x; }"),
                cls("// new comment\nint x = 1;\nint f() { return x; }")));
    }

    @Test
    public void addedOrDeletedFileIsNotFlagged() {
        assertFalse(detector.hasNonMethodChange("", cls("int f() { return 1; }")));
        assertFalse(detector.hasNonMethodChange(cls("int f() { return 1; }"), ""));
    }

    // ---- class-level declarations: records and annotation types (H4) ----

    @Test
    public void recordComponentChangeIsFlagged() {
        assertTrue(detector.hasNonMethodChange(
                "package demo;\npublic record Point(int x, int y) { }\n",
                "package demo;\npublic record Point(int x, int y, int z) { }\n"));
    }

    @Test
    public void recordHeaderChangeIsFlagged() {
        assertTrue(detector.hasNonMethodChange(
                "package demo;\npublic record Range(int lo, int hi) { }\n",
                "package demo;\npublic record Range(long lo, long hi) { }\n"));
    }

    @Test
    public void annotationTypeChangeIsFlagged() {
        assertTrue(detector.hasNonMethodChange(
                "package demo;\npublic @interface Flag { int value(); }\n",
                "package demo;\npublic @interface Flag { String value(); }\n"));
    }

    @Test
    public void recordBodyOnlyChangeIsNotFlagged() {
        assertFalse(detector.hasNonMethodChange(
                "package demo;\npublic record Point(int x) {\n    int doubled() { return x * 2; }\n}\n",
                "package demo;\npublic record Point(int x) {\n    int doubled() { return x + x; }\n}\n"));
    }

    // ---- structural difference (uncertainty trigger) ----

    @Test
    public void addedImportDiffersStructurallyButIsNotANonMethodChange() {
        String oldSrc = "package demo;\npublic class C { int f() { return 1; } }\n";
        String newSrc = "package demo;\nimport java.util.List;\npublic class C { int f() { return 1; } }\n";

        assertFalse(detector.hasNonMethodChange(oldSrc, newSrc),
                "a pure import addition is not a non-method change");
        assertTrue(detector.differsStructurally(oldSrc, newSrc),
                "but the file did change, so it must not be silently ignored");
    }

    @Test
    public void commentAndWhitespaceOnlyChangeDoesNotDifferStructurally() {
        String oldSrc = "package demo;\npublic class C { int f() { return 1; } }\n";
        String newSrc = "package demo;\n\n// a comment\npublic class C {\n    int f() { return   1; }\n}\n";

        assertFalse(detector.differsStructurally(oldSrc, newSrc));
    }

    @Test
    public void blankSidesDoNotDifferStructurally() {
        assertFalse(detector.differsStructurally("", cls("int f() { return 1; }")));
        assertFalse(detector.differsStructurally(cls("int f() { return 1; }"), ""));
    }
}
