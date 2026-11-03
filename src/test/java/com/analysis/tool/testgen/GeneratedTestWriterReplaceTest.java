package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneratedTestWriterReplaceTest {

    @TempDir
    Path root;

    private static TestEmitter.Result result(String className, String literal) {
        return new TestEmitter().emit("demo", "rev1",
                List.of(DiscoveredMethod.supported("demo", className, "add", "add(int,int)", false)),
                List.of(new OracleCase(className, "add", false, "new " + className + "()",
                        List.of("int", "int"), List.of("1", "2"))),
                List.of(new OracleOutcome(OracleOutcome.Status.RETURNED, literal)));
    }

    @Test
    public void replaceOverwritesWhereWriteRefuses() throws Exception {
        GeneratedTestWriter writer = new GeneratedTestWriter();
        Path dir = writer.write(root, "demo", "rev1", result("Calculator", "3"));

        assertThrows(IllegalStateException.class,
                () -> writer.write(root, "demo", "rev1", result("Calculator", "99")));

        writer.replace(root, "demo", "rev1", result("Calculator", "99"));
        Path test = dir.resolve("src/test/java/rts/generated/CalculatorGeneratedTest.java");
        assertTrue(Files.readString(test).contains("assertEquals(99,"));
    }

    @Test
    public void replaceDeletesGeneratedFilesThatAreNoLongerPartOfTheSuite() throws Exception {
        GeneratedTestWriter writer = new GeneratedTestWriter();
        Path dir = writer.write(root, "demo", "rev1", result("Calculator", "3"));
        Path stale = dir.resolve("src/test/java/rts/generated/CalculatorGeneratedTest.java");
        assertTrue(Files.exists(stale));

        writer.replace(root, "demo", "rev1", result("Adder", "3"));

        assertFalse(Files.exists(stale), "the old class file must be removed");
        assertTrue(Files.exists(dir.resolve("src/test/java/rts/generated/AdderGeneratedTest.java")));
    }
}
