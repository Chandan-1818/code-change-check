package com.analysis.tool.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaParserAnalyzerTest {

    private static final String CALCULATOR_SOURCE =
            "package com.sample;\n" +
            "\n" +
            "public class Calculator {\n" +
            "\n" +
            "    public int add(int a, int b) {\n" +
            "        return a + b;\n" +
            "    }\n" +
            "\n" +
            "    public int subtract(int a, int b) {\n" +
            "        int result = a - b;\n" +
            "        return result;\n" +
            "    }\n" +
            "}\n";

    @Test
    public void shouldExtractMethodNamesFromCalculator() {
        JavaParserAnalyzer analyzer = new JavaParserAnalyzer();

        List<String> methodNames = analyzer.extractMethodNames(CALCULATOR_SOURCE);

        assertEquals(2, methodNames.size(),
                "Expected exactly two methods in Calculator");
        assertTrue(methodNames.contains("add"), "Expected 'add' method to be found");
        assertTrue(methodNames.contains("subtract"), "Expected 'subtract' method to be found");
    }
}
