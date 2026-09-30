package com.analysis.tool.impact;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 12: source-to-test pairs are derived from file paths by naming
 * convention (Foo.java under src/main/java pairs with FooTest.java at the
 * same package path under src/test/java). Pure string logic, no Git needed.
 */
public class FileMappingDiscovererTest {

    private static final String CALC_SRC = "src/main/java/com/sample/Calculator.java";
    private static final String CALC_TEST = "src/test/java/com/sample/CalculatorTest.java";
    private static final String MULT_SRC = "src/main/java/com/sample/Multiplier.java";
    private static final String MULT_TEST = "src/test/java/com/sample/MultiplierTest.java";

    @Test
    public void shouldPairSourceWithConventionalTestPath() {
        List<FileMapping> mappings = new FileMappingDiscoverer()
                .discover(Arrays.asList(CALC_SRC, CALC_TEST, MULT_SRC, MULT_TEST));

        assertEquals(2, mappings.size(), "Mappings were: " + describe(mappings));
        assertEquals(new FileMapping(CALC_SRC, CALC_TEST), mappings.get(0));
        assertEquals(new FileMapping(MULT_SRC, MULT_TEST), mappings.get(1));
    }

    @Test
    public void shouldIgnoreTestFilesBuildFilesAndNonJavaSources() {
        List<FileMapping> mappings = new FileMappingDiscoverer().discover(Arrays.asList(
                "pom.xml",
                ".gitignore",
                CALC_TEST,
                "src/main/resources/app.properties",
                "src/main/java/com/sample/notes.txt"));

        assertTrue(mappings.isEmpty(), "Mappings were: " + describe(mappings));
    }

    @Test
    public void shouldStillPairSourceWhoseTestFileDoesNotExist() {
        List<FileMapping> mappings = new FileMappingDiscoverer()
                .discover(Collections.singletonList(CALC_SRC));

        assertEquals(1, mappings.size());
        assertEquals(new FileMapping(CALC_SRC, CALC_TEST), mappings.get(0));
    }

    private static String describe(List<FileMapping> mappings) {
        StringBuilder sb = new StringBuilder();
        for (FileMapping m : mappings) {
            sb.append(m.getSourceFilePath()).append('=').append(m.getTestFilePath()).append(' ');
        }
        return sb.toString();
    }
}
