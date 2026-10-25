package com.analysis.tool.testgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/**
 * Phase 8B / task G3 (part 1): produces the argument lists used to call a
 * method under test, as Java source expressions.
 *
 * Deterministic by design (see docs/TEST_GENERATION_SPEC.md):
 * - each supported type has a fixed pool of boundary-style values;
 * - the cases are the cartesian product of the pools, enumerated in a fixed
 *   order (first parameter varies slowest);
 * - if the product exceeds the cap, case 0 (the first value of every pool) is
 *   always kept and the remaining cases are chosen with a Random seeded with
 *   SEED, then listed in ascending enumeration order. java.util.Random is
 *   specified exactly, so the same input gives the same output on any JVM.
 */
public class InputGenerator {

    public static final long SEED = 42L;
    public static final int DEFAULT_CASE_CAP = 12;
    static final int MAX_PARAMETERS = 12;

    /** The fixed value pool for a supported type, as Java source expressions. */
    public static List<String> pool(String type) {
        switch (primitiveName(type)) {
            case "int":
                return List.of("0", "1", "-1", "2", "2147483647");
            case "long":
                return List.of("0L", "1L", "-1L", "2L", "2147483647L");
            case "short":
                return List.of("(short) 0", "(short) 1", "(short) -1", "(short) 2");
            case "byte":
                return List.of("(byte) 0", "(byte) 1", "(byte) -1", "(byte) 2");
            case "char":
                return List.of("'a'", "'A'", "'0'");
            case "boolean":
                return List.of("false", "true");
            case "double":
                return List.of("0.0", "1.0", "-1.0", "2.5");
            case "float":
                return List.of("0.0f", "1.0f", "-1.0f", "2.5f");
            case "String":
                return List.of("null", "\"\"", "\"a\"", "\"hello world\"");
            default:
                throw new IllegalArgumentException("Unsupported type: " + type);
        }
    }

    /** First non-null pool value; used for constructor arguments when building receivers. */
    public static String safeConstructorValue(String type) {
        for (String value : pool(type)) {
            if (!"null".equals(value)) {
                return value;
            }
        }
        throw new IllegalStateException("No non-null value for " + type);
    }

    public List<List<String>> generate(List<String> parameterTypes) {
        return generate(parameterTypes, DEFAULT_CASE_CAP);
    }

    /**
     * Returns at most caseCap argument lists. A method with no parameters gets
     * exactly one (empty) case.
     *
     * @throws IllegalArgumentException for an unsupported type, a non-positive
     *         cap, or more than MAX_PARAMETERS parameters
     */
    public List<List<String>> generate(List<String> parameterTypes, int caseCap) {
        if (caseCap < 1) {
            throw new IllegalArgumentException("caseCap must be at least 1");
        }
        if (parameterTypes.size() > MAX_PARAMETERS) {
            throw new IllegalArgumentException("Too many parameters: " + parameterTypes.size());
        }

        List<List<String>> pools = new ArrayList<>();
        long total = 1;
        for (String type : parameterTypes) {
            List<String> pool = pool(type);
            pools.add(pool);
            total *= pool.size();
        }

        List<List<String>> cases = new ArrayList<>();
        for (long index : selectIndices(total, caseCap)) {
            cases.add(decode(index, pools));
        }
        return Collections.unmodifiableList(cases);
    }

    private static List<Long> selectIndices(long total, int caseCap) {
        List<Long> indices = new ArrayList<>();
        if (total <= caseCap) {
            for (long i = 0; i < total; i++) {
                indices.add(i);
            }
            return indices;
        }
        TreeSet<Long> chosen = new TreeSet<>();
        chosen.add(0L);
        Random random = new Random(SEED);
        while (chosen.size() < caseCap) {
            chosen.add((random.nextLong() & Long.MAX_VALUE) % total);
        }
        indices.addAll(chosen);
        return indices;
    }

    /** Mixed-radix decode: the last parameter varies fastest. */
    private static List<String> decode(long index, List<List<String>> pools) {
        String[] values = new String[pools.size()];
        long remaining = index;
        for (int i = pools.size() - 1; i >= 0; i--) {
            int radix = pools.get(i).size();
            values[i] = pools.get(i).get((int) (remaining % radix));
            remaining /= radix;
        }
        List<String> result = new ArrayList<>(values.length);
        Collections.addAll(result, values);
        return Collections.unmodifiableList(result);
    }

    private static String primitiveName(String type) {
        switch (type) {
            case "Integer": return "int";
            case "Long": return "long";
            case "Short": return "short";
            case "Byte": return "byte";
            case "Character": return "char";
            case "Boolean": return "boolean";
            case "Double": return "double";
            case "Float": return "float";
            default: return type;
        }
    }
}
