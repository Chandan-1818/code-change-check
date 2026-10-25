package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure logic tests: no Git, no files, no sample project. */
public class InputGeneratorTest {

    private final InputGenerator generator = new InputGenerator();

    @Test
    public void poolsAreFixed() {
        assertEquals(List.of("0", "1", "-1", "2", "2147483647"), InputGenerator.pool("int"));
        assertEquals(InputGenerator.pool("int"), InputGenerator.pool("Integer"));
        assertEquals(List.of("false", "true"), InputGenerator.pool("boolean"));
        assertTrue(InputGenerator.pool("String").contains("null"));
        assertFalse(InputGenerator.pool("short").contains("2147483647"));
    }

    @Test
    public void methodWithNoParametersGetsOneEmptyCase() {
        assertEquals(List.of(List.of()), generator.generate(Collections.emptyList()));
    }

    @Test
    public void smallProductIsEnumeratedInFixedOrder() {
        List<List<String>> cases = generator.generate(List.of("int", "boolean"));

        assertEquals(10, cases.size());
        assertEquals(List.of("0", "false"), cases.get(0));
        assertEquals(List.of("0", "true"), cases.get(1));
        assertEquals(List.of("1", "false"), cases.get(2));
        assertEquals(List.of("2147483647", "true"), cases.get(9));
    }

    @Test
    public void largeProductIsCappedSampledAndDeterministic() {
        List<String> types = List.of("int", "int", "int");

        List<List<String>> first = generator.generate(types);
        List<List<String>> second = new InputGenerator().generate(types);

        assertEquals(12, first.size());
        assertEquals(List.of("0", "0", "0"), first.get(0));
        assertEquals(12, new HashSet<>(first).size(), "cases must be distinct");
        assertEquals(first, second, "same input must give the same cases");
        for (List<String> testCase : first) {
            for (String value : testCase) {
                assertTrue(InputGenerator.pool("int").contains(value), value);
            }
        }
    }

    @Test
    public void capIsConfigurable() {
        assertEquals(3, generator.generate(List.of("int", "int"), 3).size());
    }

    @Test
    public void boxedTypesUseTheSamePoolsAsPrimitives() {
        assertEquals(generator.generate(List.of("int")), generator.generate(List.of("Integer")));
    }

    @Test
    public void unsupportedTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of("Object")));
    }

    @Test
    public void tooManyParametersAreRejected() {
        List<String> thirteen = Collections.nCopies(13, "int");
        assertThrows(IllegalArgumentException.class, () -> generator.generate(thirteen));
    }

    @Test
    public void constructorValuesAreNeverNull() {
        for (String type : List.of("int", "long", "short", "byte", "char", "boolean",
                "double", "float", "String")) {
            assertNotEquals("null", InputGenerator.safeConstructorValue(type), type);
        }
        assertEquals("\"\"", InputGenerator.safeConstructorValue("String"));
    }
}
