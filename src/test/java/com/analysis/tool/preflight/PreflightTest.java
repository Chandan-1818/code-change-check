package com.analysis.tool.preflight;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H9: filesystem-based preflight validation. Every check is exercised against
 * a throwaway directory: a supported single-module JUnit 5 repo, and each
 * refused (unsupported) or reduced (lower-reliability) configuration.
 */
class PreflightTest {

    private static final String PLAIN_SOURCE =
            "package demo;\npublic class Calculator { public int add(int a, int b) { return a + b; } }\n";

    private static final String JUNIT5_TEST =
            "package demo;\nimport org.junit.jupiter.api.Test;\n"
                    + "public class CalculatorTest { @Test public void testAdd() {} }\n";

    @TempDir
    Path dir;

    private void gitDirectory() throws IOException {
        Files.createDirectories(dir.resolve(".git"));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = dir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String singleModuleJupiterPom() {
        return "<project><artifactId>x</artifactId>"
                + "<dependencies><dependency><groupId>org.junit.jupiter</groupId>"
                + "<artifactId>junit-jupiter</artifactId><version>5.10.2</version></dependency>"
                + "</dependencies></project>";
    }

    @Test
    void singleModuleJupiterRepositoryIsSupported() throws IOException {
        gitDirectory();
        write("pom.xml", singleModuleJupiterPom());
        write("src/main/java/demo/Calculator.java", PLAIN_SOURCE);
        write("src/test/java/demo/CalculatorTest.java", JUNIT5_TEST);

        PreflightResult result = Preflight.validate(dir);

        assertEquals(PreflightResult.Status.SUPPORTED, result.getStatus(), result.toString());
        assertTrue(result.getRejections().isEmpty(), result.toString());
        assertTrue(result.getReductions().isEmpty(), result.toString());
    }

    @Test
    void plainGitRepositoryWithoutPomIsSupported() throws IOException {
        gitDirectory();
        write("src/main/java/demo/Calculator.java", PLAIN_SOURCE);

        PreflightResult result = Preflight.validate(dir);

        assertEquals(PreflightResult.Status.SUPPORTED, result.getStatus(), result.toString());
    }

    @Test
    void missingGitDirectoryIsRefused() throws IOException {
        write("src/main/java/demo/Calculator.java", PLAIN_SOURCE);

        PreflightResult result = Preflight.validate(dir);

        assertTrue(result.isUnsupported(), result.toString());
        assertTrue(result.getRejections().get(0).contains("not a Git repository"), result.toString());
    }

    @Test
    void gitFileInsteadOfDirectoryIsRefused() throws IOException {
        Files.writeString(dir.resolve(".git"), "gitdir: ../elsewhere/.git");

        PreflightResult result = Preflight.validate(dir);

        assertTrue(result.isUnsupported(), result.toString());
        assertTrue(result.getRejections().get(0).contains("unsupported Git layout"), result.toString());
    }

    @Test
    void multiModuleMavenProjectIsRefused() throws IOException {
        gitDirectory();
        write("pom.xml", "<project><modules><module>a</module></modules></project>");

        PreflightResult result = Preflight.validate(dir);

        assertTrue(result.isUnsupported(), result.toString());
        assertTrue(result.getRejections().get(0).contains("multi-module"), result.toString());
    }

    @Test
    void junit4DependencyIsRefused() throws IOException {
        gitDirectory();
        write("pom.xml", "<project><dependencies><dependency><groupId>junit</groupId>"
                + "<artifactId>junit</artifactId><version>4.13.2</version></dependency></dependencies></project>");

        PreflightResult result = Preflight.validate(dir);

        assertTrue(result.isUnsupported(), result.toString());
        assertTrue(result.getRejections().get(0).contains("JUnit 4"), result.toString());
    }

    @Test
    void junit4SourceAnnotationsAreRefused() throws IOException {
        gitDirectory();
        write("src/test/java/demo/OldTest.java",
                "package demo;\nimport org.junit.Test;\npublic class OldTest { @Test public void t() {} }\n");

        PreflightResult result = Preflight.validate(dir);

        assertTrue(result.isUnsupported(), result.toString());
        assertTrue(result.getRejections().get(0).contains("JUnit 4"), result.toString());
    }

    @Test
    void springUsageReducesConfidence() throws IOException {
        gitDirectory();
        write("src/main/java/demo/Service.java",
                "package demo;\nimport org.springframework.stereotype.Component;\n"
                        + "@Component public class Service {}\n");

        PreflightResult result = Preflight.validate(dir);

        assertEquals(PreflightResult.Status.REDUCED, result.getStatus(), result.toString());
        assertTrue(result.getReductions().get(0).contains("Spring"), result.toString());
    }

    @Test
    void mockitoUsageReducesConfidence() throws IOException {
        gitDirectory();
        write("src/test/java/demo/ServiceTest.java",
                "package demo;\nimport org.mockito.Mockito;\npublic class ServiceTest {}\n");

        PreflightResult result = Preflight.validate(dir);

        assertTrue(result.isReduced(), result.toString());
        assertTrue(result.getReductions().get(0).contains("Mockito"), result.toString());
    }

    @Test
    void lombokUsageReducesConfidence() throws IOException {
        gitDirectory();
        write("src/main/java/demo/Dto.java", "package demo;\nimport lombok.Data;\n"
                + "@Data public class Dto {}\n");

        PreflightResult result = Preflight.validate(dir);

        assertTrue(result.isReduced(), result.toString());
        assertTrue(result.getReductions().get(0).contains("Lombok"), result.toString());
    }

    @Test
    void reflectionUsageReducesConfidence() throws IOException {
        gitDirectory();
        write("src/main/java/demo/Loader.java", "package demo;\npublic class Loader {\n"
                + "    public Object load(String name) throws Exception { return Class.forName(name); }\n}\n");

        PreflightResult result = Preflight.validate(dir);

        assertTrue(result.isReduced(), result.toString());
        assertTrue(result.getReductions().get(0).contains("reflection"), result.toString());
    }

    @Test
    void reductionsAreReportedInADeterministicOrder() throws IOException {
        gitDirectory();
        write("src/main/java/demo/A.java", "package demo;\nimport org.mockito.Mockito;\npublic class A {}\n");
        write("src/main/java/demo/B.java",
                "package demo;\nimport org.springframework.stereotype.Component;\npublic class B {}\n");

        PreflightResult result = Preflight.validate(dir);

        assertEquals(PreflightResult.Status.REDUCED, result.getStatus(), result.toString());
        assertEquals(2, result.getReductions().size(), result.toString());
        assertTrue(result.getReductions().get(0).contains("Spring"), result.toString());
        assertTrue(result.getReductions().get(1).contains("Mockito"), result.toString());
    }

    @Test
    void nonExistentPathIsRefused() {
        PreflightResult result = Preflight.validate(dir.resolve("does-not-exist"));

        assertTrue(result.isUnsupported(), result.toString());
        assertTrue(result.getRejections().get(0).contains("does not exist"), result.toString());
    }

    @Test
    void fileInsteadOfDirectoryIsRefused() throws IOException {
        Path file = dir.resolve("a-file.txt");
        Files.writeString(file, "x");

        PreflightResult result = Preflight.validate(file);

        assertTrue(result.isUnsupported(), result.toString());
        assertTrue(result.getRejections().get(0).contains("not a directory"), result.toString());
    }

    @Test
    void blankOrNullPathIsRefused() {
        assertTrue(Preflight.validate("   ").isUnsupported());
        assertTrue(Preflight.validate((String) null).isUnsupported());
    }
}
