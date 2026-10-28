package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end for G2-G5 on inline sources (no sample project): discover, plan,
 * record in separate JVMs, emit, write, and check the generated source compiles
 * against the target and JUnit. Slow-ish (starts JVMs); it does not execute the
 * generated tests, which is G7.
 */
public class TestGeneratorTest {

    private static final String CALCULATOR =
            "package demo;\n"
          + "public class Calculator {\n"
          + "    public int add(int a, int b) { return a + b; }\n"
          + "    public int divide(int a, int b) { return a / b; }\n"
          + "    public boolean isPositive(int a) { return a > 0; }\n"
          + "    public double half(double a) { return a / 2; }\n"
          + "    public static String greet(String n) { return \"Hi \" + n; }\n"
          + "    public void reset() { }\n"
          + "}\n";

    private static final String FILE = "src/test/java/rts/generated/CalculatorGeneratedTest.java";

    @TempDir
    Path tempDir;

    @Test
    public void generatesDeterministicCompilableSuiteForProjectWithoutTests() throws Exception {
        List<String> sources = List.of(CALCULATOR);

        TestEmitter.Result first = new TestGenerator().generate(sources, "demo", "abc1234");
        TestEmitter.Result second = new TestGenerator().generate(sources, "demo", "abc1234");

        assertEquals(first.getManifest(), second.getManifest(), "two generations must be identical");
        assertEquals(first.getFiles(), second.getFiles());

        String source = first.getFiles().get(FILE);
        assertTrue(source.contains("gen_Calculator_divide_01"), source);
        assertTrue(source.contains("assertThrows(java.lang.ArithmeticException.class"), source);
        assertTrue(source.contains("gen_Calculator_greet_01"), source);
        assertTrue(first.getManifest().contains("\"targetSignature\": \"reset()\""),
                "void method must be reported as skipped:\n" + first.getManifest());

        Path generatedRoot = tempDir.resolve("generated-tests");
        Path dir = new GeneratedTestWriter().write(generatedRoot, "demo", "abc1234", first);
        assertTrue(Files.exists(dir.resolve("manifest.json")));

        assertCompiles(dir.resolve(FILE));
    }

    private void assertCompiles(Path generatedTest) throws Exception {
        Path src = Files.createDirectories(tempDir.resolve("compile/src"));
        Path out = Files.createDirectories(tempDir.resolve("compile/out"));
        Path target = Files.createDirectories(src.resolve("demo")).resolve("Calculator.java");
        Files.writeString(target, CALCULATOR, StandardCharsets.UTF_8);
        Path copy = Files.createDirectories(src.resolve("rts/generated")).resolve("CalculatorGeneratedTest.java");
        Files.copy(generatedTest, copy);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            boolean ok = compiler.getTask(null, fm, diagnostics,
                    List.of("-d", out.toString(), "-classpath", System.getProperty("java.class.path"), "-proc:none"),
                    null, fm.getJavaFileObjectsFromFiles(List.of(target.toFile(), copy.toFile()))).call();
            assertTrue(ok, "Generated source did not compile: " + diagnostics.getDiagnostics());
        }
    }
}
