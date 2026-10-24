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
}
