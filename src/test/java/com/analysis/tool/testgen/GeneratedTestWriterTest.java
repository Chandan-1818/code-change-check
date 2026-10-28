package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneratedTestWriterTest {

    @TempDir
    Path root;

    private static TestEmitter.Result result(String expectedLiteral) {
        return new TestEmitter().emit("demo", "abc1234",
                List.of(DiscoveredMethod.supported("demo", "Calculator", "add", "add(int,int)", false)),
                List.of(new OracleCase("Calculator", "add", false, "new Calculator()",
                        List.of("int", "int"), List.of("1", "2"))),
                List.of(new OracleOutcome(OracleOutcome.Status.RETURNED, expectedLiteral)));
    }

    @Test
    public void writesTestsAndManifestUnderProjectAndRevision() throws Exception {
        Path dir = new GeneratedTestWriter().write(root, "demo", "abc1234", result("3"));

        assertEquals(root.resolve("demo").resolve("abc1234"), dir);
        assertTrue(Files.exists(dir.resolve("manifest.json")));
        assertTrue(Files.exists(dir.resolve("src/test/java/rts/generated/CalculatorGeneratedTest.java")));
    }

    @Test
    public void identicalRewriteIsAcceptedButDifferentOracleIsRefused() throws Exception {
        GeneratedTestWriter writer = new GeneratedTestWriter();
        writer.write(root, "demo", "abc1234", result("3"));

        writer.write(root, "demo", "abc1234", result("3"));
        assertThrows(IllegalStateException.class,
                () -> writer.write(root, "demo", "abc1234", result("99")));

        Path test = root.resolve("demo/abc1234/src/test/java/rts/generated/CalculatorGeneratedTest.java");
        assertTrue(Files.readString(test).contains("assertEquals(3,"), "existing oracle must be untouched");
    }

    @Test
    public void unsafeProjectOrRevisionNamesAreRejected() {
        GeneratedTestWriter writer = new GeneratedTestWriter();
        assertThrows(IllegalArgumentException.class, () -> writer.write(root, "..", "abc", result("3")));
        assertThrows(IllegalArgumentException.class, () -> writer.write(root, "demo", "a/b", result("3")));
    }
}
