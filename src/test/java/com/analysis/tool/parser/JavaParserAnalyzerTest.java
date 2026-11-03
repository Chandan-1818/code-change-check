package com.analysis.tool.parser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

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

    /**
     * The parser is configured for Java 17, so records, text blocks and
     * switch expressions must be accepted (GAP-12).
     */
    @Test
    public void shouldParseJava17LanguageFeatures() {
        String source =
                "package com.sample;\n" +
                "\n" +
                "public class Java17Features {\n" +
                "\n" +
                "    public record Point(int x, int y) {\n" +
                "    }\n" +
                "\n" +
                "    public String textBlock() {\n" +
                "        return \"\"\"\n" +
                "                hello\n" +
                "                \"\"\";\n" +
                "    }\n" +
                "\n" +
                "    public String switchExpression(int day) {\n" +
                "        return switch (day) {\n" +
                "            case 1 -> \"mon\";\n" +
                "            default -> \"other\";\n" +
                "        };\n" +
                "    }\n" +
                "}\n";

        JavaParserAnalyzer analyzer = new JavaParserAnalyzer();

        List<String> methodNames = analyzer.extractMethodNames(source);

        assertTrue(methodNames.contains("textBlock"),
                "Expected the text-block method to be extracted");
        assertTrue(methodNames.contains("switchExpression"),
                "Expected the switch-expression method to be extracted");
    }

    /**
     * Unparseable source must surface as the typed SourceParseException
     * (wrapping JavaParser's ParseProblemException), not crash the caller
     * with an unrelated runtime exception.
     */
    @Test
    public void shouldThrowTypedSourceParseExceptionOnUnparseableSource() {
        JavaParserAnalyzer analyzer = new JavaParserAnalyzer();

        SourceParseException exception = assertThrows(SourceParseException.class,
                () -> analyzer.extractMethodNames("public class Broken { this is not java"));

        assertNotNull(exception.getCause(),
                "Expected the typed exception to wrap the underlying parse problem");
    }
}
