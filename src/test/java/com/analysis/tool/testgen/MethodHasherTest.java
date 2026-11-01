package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MethodHasherTest {

    private static String cls(String members) {
        return "package demo;\npublic class C {\n" + members + "\n}\n";
    }

    private static final String KEY = "C\tf(int)";

    @Test
    public void commentsAndWhitespaceDoNotChangeTheHash() {
        Map<String, String> a = new MethodHasher().hash(List.of(cls("public int f(int a) { return a; }")));
        Map<String, String> b = new MethodHasher().hash(List.of(
                cls("/** doc */\npublic int f(int a) {\n  // note\n  return   a;\n}")));
        assertEquals(a.get(KEY), b.get(KEY));
    }

    @Test
    public void bodyOrSignatureChangeChangesTheHash() {
        String base = new MethodHasher().hash(List.of(cls("public int f(int a) { return a; }"))).get(KEY);
        String body = new MethodHasher().hash(List.of(cls("public int f(int a) { return a + 1; }"))).get(KEY);
        String ret = new MethodHasher().hash(List.of(cls("public long f(int a) { return a; }"))).get(KEY);
        assertNotEquals(base, body);
        assertNotEquals(base, ret);
    }

    @Test
    public void overloadsGetSeparateKeys() {
        Map<String, String> hashes = new MethodHasher().hash(List.of(
                cls("public int f(int a) { return a; }\npublic int f(int a, int b) { return a + b; }")));
        assertTrue(hashes.containsKey("C\tf(int)"), hashes.keySet().toString());
        assertTrue(hashes.containsKey("C\tf(int,int)"), hashes.keySet().toString());
    }
}
