package com.analysis.tool.testgen;

import java.util.List;

/** Phase 8B / G6: the keys that identify a target method and a single case. */
final class MethodKey {

    private MethodKey() {
    }

    static String of(String className, String signature) {
        return className + "\t" + signature;
    }

    static String ofCase(String className, String signature, List<String> inputs) {
        return of(className, signature) + "\t" + String.join("\u0001", inputs);
    }

    static String signature(OracleCase c) {
        return c.getMethodName() + "(" + String.join(",", c.getParameterTypes()) + ")";
    }
}
