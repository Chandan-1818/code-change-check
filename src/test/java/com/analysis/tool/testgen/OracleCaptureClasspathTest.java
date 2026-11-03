package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The oracle capture must compile and run the target's main sources against the
 * project's own classpath: production code that references a third-party
 * library (Spring, Hibernate, ...) is exactly the case that used to fail with
 * "package ... does not exist".
 *
 * The "library" below is compiled by the JDK into a separate directory, which
 * is then handed to the capture as the project classpath - local, no Maven, no
 * network, and it fails if the classpath is not really used.
 */
class OracleCaptureClasspathTest {

    private static final String LIBRARY = """
            package demo;
            public class ExternalLib {
                public static String tag() { return "external"; }
            }
            """;

    private static final String TARGET = """
            package demo;
            public class UsesLibrary {
                public String describe() { return ExternalLib.tag(); }
            }
            """;

    @TempDir
    Path libraryDir;

    private List<OracleCase> cases() {
        return List.of(new OracleCase("UsesLibrary", "describe", false,
                "new UsesLibrary()", List.of(), List.of()));
    }

    /** Compiles the fake third-party library and returns the directory holding its classes. */
    private String compileLibrary() throws Exception {
        Path source = libraryDir.resolve("src/demo/ExternalLib.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, LIBRARY, StandardCharsets.UTF_8);
        Path classes = Files.createDirectories(libraryDir.resolve("classes"));
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", classes.toString(), source.toString());
        assertEquals(0, result, "the test library must compile with the JDK");
        return classes.toString();
    }

    @Test
    void compilesAndRunsTheTargetAgainstTheProjectClasspath() throws Exception {
        String projectClasspath = compileLibrary();

        List<OracleOutcome> outcomes = new OracleCapture(5000, 120)
                .capture(List.of(TARGET), cases(), projectClasspath);

        assertEquals(new OracleOutcome(OracleOutcome.Status.RETURNED, "\"external\""), outcomes.get(0));
    }

    @Test
    void withoutTheProjectClasspathTheDependencyIsMissing() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OracleCapture(5000, 120).capture(List.of(TARGET), cases()));

        assertTrue(error.getMessage().startsWith("Compilation failed"), error.getMessage());
        assertTrue(error.getMessage().contains("ExternalLib"), error.getMessage());
    }
}
