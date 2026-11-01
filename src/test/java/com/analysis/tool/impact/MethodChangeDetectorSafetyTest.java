package com.analysis.tool.impact;

import com.analysis.tool.parser.JavaParserAnalyzer;
import com.analysis.tool.parser.MethodInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure string tests: no Git, no sample project, portable. */
public class MethodChangeDetectorSafetyTest {

    private static String cls(String members) {
        return "package demo;\npublic class C {\n" + members + "\n}\n";
    }

    private MethodChangeResult detect(String oldSrc, String newSrc) {
        JavaParserAnalyzer p = new JavaParserAnalyzer();
        List<MethodInfo> o = p.extractMethods(oldSrc);
        List<MethodInfo> n = p.extractMethods(newSrc);
        return new MethodChangeDetector().detectChanges(o, n);
    }

    @Test
    public void changeToOneOverloadIsNotHiddenByTheOther() {
        String oldSrc = cls("int calc(int a) { return a; }\nint calc(int a, int b) { return a + b; }");
        String newSrc = cls("int calc(int a) { return a + 1; }\nint calc(int a, int b) { return a + b; }");
        assertTrue(detect(oldSrc, newSrc).getModifiedMethods().contains("calc"));

        String oldSrc2 = cls("int calc(int a, int b) { return a + b; }\nint calc(int a) { return a; }");
        String newSrc2 = cls("int calc(int a, int b) { return a + b; }\nint calc(int a) { return a + 1; }");
        assertTrue(detect(oldSrc2, newSrc2).getModifiedMethods().contains("calc"));
    }

    @Test
    public void reorderingOverloadsIsNotAChange() {
        String a = cls("int calc(int a) { return a; }\nint calc(int a, int b) { return a + b; }");
        String b = cls("int calc(int a, int b) { return a + b; }\nint calc(int a) { return a; }");
        assertTrue(detect(a, b).getModifiedMethods().isEmpty());
    }

    @Test
    public void signatureOnlyChangeIsDetected() {
        String oldSrc = cls("public int f() { return 1; }");
        String newSrc = cls("public long f() { return 1; }");
        assertTrue(detect(oldSrc, newSrc).getModifiedMethods().contains("f"));
    }

    @Test
    public void commentAndWhitespaceOnlyChangeIsNotAChange() {
        String oldSrc = cls("int f() { return 1; }");
        String newSrc = cls("/** doc */\nint f() {\n    // note\n    return   1;\n}");
        MethodChangeResult r = detect(oldSrc, newSrc);
        assertTrue(r.getModifiedMethods().isEmpty(), "Modified: " + r.getModifiedMethods());
        assertEquals(List.of("f"), r.getUnchangedMethods());
    }
}
