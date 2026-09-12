package com.analysis.tool.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class JavaParserAnalyzerTest {

    private static final String CALCULATOR_VERSION_1 =
            "package com.sample;\n" +
            "\n" +
            "public class Calculator {\n" +
            "\n" +
            "    public int add(int a, int b) {\n" +
            "        return a + b;\n" +
            "    }\n" +
            "\n" +
            "    public int subtract(int a, int b) {\n" +
            "        return a - b;\n" +
            "    }\n" +
            "}\n";

    private static final String CALCULATOR_VERSION_2 =
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

        List<String> methodNames = analyzer.extractMethodNames(CALCULATOR_VERSION_1);

        assertEquals(2, methodNames.size(),
                "Expected exactly two methods in Calculator");
        assertTrue(methodNames.contains("add"), "Expected 'add' method to be found");
        assertTrue(methodNames.contains("subtract"), "Expected 'subtract' method to be found");
    }

    @Test
    public void shouldDetectDifferentBodyTextForModifiedMethod() {
        JavaParserAnalyzer analyzer = new JavaParserAnalyzer();

        List<MethodInfo> version1Methods = analyzer.extractMethods(CALCULATOR_VERSION_1);
        List<MethodInfo> version2Methods = analyzer.extractMethods(CALCULATOR_VERSION_2);

        MethodInfo subtractV1 = findMethodByName(version1Methods, "subtract");
        MethodInfo subtractV2 = findMethodByName(version2Methods, "subtract");

        assertNotEquals(subtractV1.getBodyText(), subtractV2.getBodyText(),
                "Expected subtract() body text to differ between Version 1 and Version 2");
    }

    @Test
    public void shouldDetectSameBodyTextForUnchangedMethod() {
        JavaParserAnalyzer analyzer = new JavaParserAnalyzer();

        List<MethodInfo> version1Methods = analyzer.extractMethods(CALCULATOR_VERSION_1);
        List<MethodInfo> version2Methods = analyzer.extractMethods(CALCULATOR_VERSION_2);

        MethodInfo addV1 = findMethodByName(version1Methods, "add");
        MethodInfo addV2 = findMethodByName(version2Methods, "add");

        assertEquals(addV1.getBodyText(), addV2.getBodyText(),
                "Expected add() body text to be identical between Version 1 and Version 2");
    }

    private MethodInfo findMethodByName(List<MethodInfo> methods, String name) {
        for (MethodInfo method : methods) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new IllegalArgumentException("No method named '" + name + "' found");
    }
}
