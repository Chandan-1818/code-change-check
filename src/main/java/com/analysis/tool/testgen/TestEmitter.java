package com.analysis.tool.testgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Phase 8B / tasks G5 and G6: turns recorded oracle outcomes into JUnit 5 source
 * and a manifest. Pure: no file or process access, no timestamps, so the same
 * input always gives byte-identical output.
 *
 * One test class per target class (rts.generated.&lt;Class&gt;GeneratedTest). Only
 * RETURNED and THREW outcomes become tests; every other outcome is reported in
 * the manifest (droppedCases / skipped), never hidden.
 *
 * Test names are gen_&lt;Class&gt;_&lt;method&gt;_&lt;nn&gt;, where nn counts ALL planned cases
 * of that class and method name (dropped ones included) so that dropping a case
 * does not rename the others.
 *
 * Manifest schema 2 adds methodBodyHash (per test), a "methods" list of the
 * supported methods with their hashes, per-test state, and a "retired" list.
 */
public class TestEmitter {

    public static final String GENERATOR_VERSION = "0.2.0";
    public static final int SCHEMA_VERSION = 2;
    public static final String TEST_PACKAGE = "rts.generated";
    public static final String TEST_DIRECTORY = "src/test/java/rts/generated/";

    public static final class Result {
        private final Map<String, String> files;
        private final String manifest;
        private final int generated;
        private final int dropped;
        private final int skipped;

        Result(Map<String, String> files, String manifest, int generated, int dropped, int skipped) {
            this.files = files;
            this.manifest = manifest;
            this.generated = generated;
            this.dropped = dropped;
            this.skipped = skipped;
        }

        /** Relative path (under the revision directory) to file content. */
        public Map<String, String> getFiles() { return files; }
        public String getManifest() { return manifest; }
        public int getGeneratedCount() { return generated; }
        public int getDroppedCount() { return dropped; }
        public int getSkippedCount() { return skipped; }
    }

    public Result emit(String project, String revision, List<DiscoveredMethod> discovered,
                       List<OracleCase> cases, List<OracleOutcome> outcomes) {
        return emit(project, revision, discovered, cases, outcomes,
                Collections.<String, String>emptyMap(), null, Collections.<String[]>emptyList());
    }

    /**
     * @param hashes  MethodKey to body hash; a missing key is written as an empty hash
     * @param states  one state per case (ACTIVE, BEHAVIOUR_CHANGED), or null for all ACTIVE
     * @param retired entries {testMethod, retiredAtRevision}
     */
    public Result emit(String project, String revision, List<DiscoveredMethod> discovered,
                       List<OracleCase> cases, List<OracleOutcome> outcomes,
                       Map<String, String> hashes, List<String> states, List<String[]> retired) {
        if (cases.size() != outcomes.size()) {
            throw new IllegalArgumentException("cases (" + cases.size() + ") and outcomes ("
                    + outcomes.size() + ") differ in length");
        }
        if (states != null && states.size() != cases.size()) {
            throw new IllegalArgumentException("states and cases differ in length");
        }

        TreeSet<String> packages = new TreeSet<>();
        for (DiscoveredMethod method : discovered) {
            if (!method.getPackageName().isEmpty()) {
                packages.add(method.getPackageName());
            }
        }

        Map<String, List<Integer>> byClass = new TreeMap<>();
        for (int i = 0; i < cases.size(); i++) {
            byClass.computeIfAbsent(cases.get(i).getClassName(), k -> new ArrayList<>()).add(i);
        }

        Map<String, Integer> counters = new HashMap<>();
        Map<String, Integer> totalPerSignature = new TreeMap<>();
        Map<String, Integer> usablePerSignature = new TreeMap<>();
        Map<String, String> firstBadStatus = new HashMap<>();
        List<String> testEntries = new ArrayList<>();
        List<String> droppedEntries = new ArrayList<>();
        Map<String, String> files = new TreeMap<>();

        for (Map.Entry<String, List<Integer>> entry : byClass.entrySet()) {
            String className = entry.getKey();
            StringBuilder body = new StringBuilder();
            int testsInClass = 0;

            for (int index : entry.getValue()) {
                OracleCase c = cases.get(index);
                OracleOutcome o = outcomes.get(index);
                String signature = MethodKey.signature(c);
                String signatureKey = MethodKey.of(className, signature);
                int number = counters.merge(className + "." + c.getMethodName(), 1, Integer::sum);
                String testName = String.format("gen_%s_%s_%02d", className, c.getMethodName(), number);

                totalPerSignature.merge(signatureKey, 1, Integer::sum);
                if (!o.isUsable()) {
                    firstBadStatus.putIfAbsent(signatureKey, o.getStatus().name());
                    droppedEntries.add("    {\"targetClass\": " + q(className)
                            + ", \"targetSignature\": " + q(signature)
                            + ", \"inputs\": " + array(c.getArgumentExpressions())
                            + ", \"status\": " + q(o.getStatus().name())
                            + ", \"detail\": " + q(o.getDetail()) + "}");
                    continue;
                }
                usablePerSignature.merge(signatureKey, 1, Integer::sum);
                testsInClass++;

                body.append("    @Test\n");
                body.append("    public void ").append(testName).append("() throws Throwable {\n");
                body.append("        ").append(assertion(c, o)).append('\n');
                body.append("    }\n\n");

                boolean threw = o.getStatus() == OracleOutcome.Status.THREW;
                String state = states == null ? "ACTIVE" : states.get(index);
                testEntries.add("    {\"testClass\": " + q(TEST_PACKAGE + "." + className + "GeneratedTest")
                        + ", \"testMethod\": " + q(testName)
                        + ", \"targetClass\": " + q(className)
                        + ", \"targetSignature\": " + q(signature)
                        + ", \"inputs\": " + array(c.getArgumentExpressions())
                        + ", \"expected\": {\"kind\": " + q(threw ? "EXCEPTION" : "RETURN")
                        + ", \"value\": " + q(o.getDetail()) + "}"
                        + ", \"methodBodyHash\": " + q(hashes.getOrDefault(signatureKey, ""))
                        + ", \"strength\": \"ASSERTED\", \"state\": " + q(state) + "}");
            }

            if (testsInClass > 0) {
                StringBuilder source = new StringBuilder();
                source.append("package ").append(TEST_PACKAGE).append(";\n\n");
                source.append("import static org.junit.jupiter.api.Assertions.*;\n\n");
                source.append("import org.junit.jupiter.api.Test;\n");
                for (String pkg : packages) {
                    source.append("import ").append(pkg).append(".*;\n");
                }
                source.append("\n/** Generated characterization tests for ").append(className)
                      .append(". Do not edit; regenerate instead. */\n");
                source.append("public class ").append(className).append("GeneratedTest {\n\n");
                source.append(body);
                source.append("}\n");
                files.put(TEST_DIRECTORY + className + "GeneratedTest.java", source.toString());
            }
        }

        Map<String, String> methodEntries = new TreeMap<>();
        List<String> skippedEntries = new ArrayList<>();
        for (DiscoveredMethod method : discovered) {
            if (method.isSupported()) {
                String key = MethodKey.of(method.getClassName(), method.getSignature());
                methodEntries.put(key, "    {\"targetClass\": " + q(method.getClassName())
                        + ", \"targetSignature\": " + q(method.getSignature())
                        + ", \"methodBodyHash\": " + q(hashes.getOrDefault(key, "")) + "}");
            } else {
                skippedEntries.add("    {\"targetClass\": " + q(method.getClassName())
                        + ", \"targetSignature\": " + q(method.getSignature())
                        + ", \"reason\": \"NOT_SUPPORTED\", \"detail\": " + q(method.getReason()) + "}");
            }
        }
        for (Map.Entry<String, Integer> entry : totalPerSignature.entrySet()) {
            if (usablePerSignature.getOrDefault(entry.getKey(), 0) == 0) {
                String[] parts = entry.getKey().split("\t", 2);
                skippedEntries.add("    {\"targetClass\": " + q(parts[0])
                        + ", \"targetSignature\": " + q(parts[1])
                        + ", \"reason\": " + q(firstBadStatus.get(entry.getKey()))
                        + ", \"detail\": \"no usable case\"}");
            }
        }

        List<String> retiredEntries = new ArrayList<>();
        for (String[] r : retired) {
            retiredEntries.add("    {\"testMethod\": " + q(r[0]) + ", \"retiredAtRevision\": " + q(r[1]) + "}");
        }

        StringBuilder manifest = new StringBuilder();
        manifest.append("{\n");
        manifest.append("  \"schemaVersion\": ").append(SCHEMA_VERSION).append(",\n");
        manifest.append("  \"generatorVersion\": ").append(q(GENERATOR_VERSION)).append(",\n");
        manifest.append("  \"seed\": ").append(InputGenerator.SEED).append(",\n");
        manifest.append("  \"caseCap\": ").append(InputGenerator.DEFAULT_CASE_CAP).append(",\n");
        manifest.append("  \"project\": ").append(q(project)).append(",\n");
        manifest.append("  \"revision\": ").append(q(revision)).append(",\n");
        manifest.append("  \"tests\": ").append(list(testEntries)).append(",\n");
        manifest.append("  \"methods\": ").append(list(new ArrayList<>(methodEntries.values()))).append(",\n");
        manifest.append("  \"skipped\": ").append(list(skippedEntries)).append(",\n");
        manifest.append("  \"droppedCases\": ").append(list(droppedEntries)).append(",\n");
        manifest.append("  \"retired\": ").append(list(retiredEntries)).append("\n");
        manifest.append("}\n");

        return new Result(files, manifest.toString(), testEntries.size(), droppedEntries.size(),
                skippedEntries.size());
    }

    private static String assertion(OracleCase c, OracleOutcome o) {
        String call = (c.isStatic() ? c.getClassName() : c.getReceiverExpression())
                + "." + c.getMethodName() + "(" + c.renderArguments() + ")";

        if (o.getStatus() == OracleOutcome.Status.THREW) {
            return "assertThrows(" + o.getDetail().replace('$', '.') + ".class, () -> " + call + ");";
        }

        String literal = o.getDetail();
        if ("null".equals(literal)) {
            return "assertNull(" + call + ");";
        }
        if ("true".equals(literal)) {
            return "assertTrue(" + call + ");";
        }
        if ("false".equals(literal)) {
            return "assertFalse(" + call + ");";
        }
        if (literal.startsWith("\"") || literal.startsWith("'")) {
            return "assertEquals(" + literal + ", " + call + ");";
        }
        if (literal.startsWith("Float.") || literal.endsWith("f")) {
            return "assertEquals(" + literal + ", (float) (" + call + "), 0.0f);";
        }
        if (literal.startsWith("Double.") || literal.contains(".")) {
            return "assertEquals(" + literal + ", (double) (" + call + "), 0.0);";
        }
        return "assertEquals(" + literal + ", " + call + ");";
    }

    private static String list(List<String> entries) {
        if (entries.isEmpty()) {
            return "[]";
        }
        return "[\n" + String.join(",\n", entries) + "\n  ]";
    }

    private static String array(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(q(items.get(i)));
        }
        return sb.append(']').toString();
    }

    /** JSON string literal, ASCII only. */
    static String q(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : (value == null ? "" : value).toCharArray()) {
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 32 || ch > 126) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.append('"').toString();
    }
}
