package com.analysis.tool.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PHASE 12 and PHASE 14 scenarios: the non-interactive remote workflow CLI.
 *
 * <p>Every repository is a throwaway local Git repository addressed through a
 * {@code file://} URL, so these tests never reach the public internet. Only the
 * clone/analysis path is exercised here; the isolated Maven execution path is
 * covered by the existing sample-project execution tests.
 */
class RemoteWorkflowCliTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    private static final String POM =
            "<project>\n"
          + "  <modelVersion>4.0.0</modelVersion>\n"
          + "  <groupId>demo</groupId><artifactId>demo</artifactId><version>1.0</version>\n"
          + "  <dependencies>\n"
          + "    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId>"
          + "<version>5.10.2</version><scope>test</scope></dependency>\n"
          + "  </dependencies>\n"
          + "</project>\n";

    private static final String CALC_V1 =
            "package demo;\npublic class Calculator {\n    public int add(int a, int b) { return a + b; }\n}\n";
    private static final String CALC_V2 =
            "package demo;\npublic class Calculator {\n    public int add(int a, int b) { return a + b + 0; }\n}\n";
    private static final String CALC_TEST =
            "package demo;\n"
          + "import org.junit.jupiter.api.Test;\n"
          + "public class CalculatorTest {\n"
          + "    @Test public void testAdd() { new Calculator().add(1, 2); }\n"
          + "}\n";

    @TempDir
    Path repoDir;

    private static final class Result {
        int exitCode;
        String out;
        String err;
    }

    private Result runCli(String... args) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        Result r = new Result();
        r.exitCode = RemoteWorkflowCli.run(args,
                new PrintStream(o, true, StandardCharsets.UTF_8),
                new PrintStream(e, true, StandardCharsets.UTF_8));
        r.out = o.toString(StandardCharsets.UTF_8);
        r.err = e.toString(StandardCharsets.UTF_8);
        return r;
    }

    // ---- arguments and usage -------------------------------------------

    @Test
    void shouldPrintHelpAndVersion() {
        Result help = runCli("--help");
        assertEquals(0, help.exitCode);
        assertTrue(help.out.contains("Usage:"));
        assertTrue(help.out.contains("--url="));
        assertTrue(help.out.contains("4 : Repository cloning or network failure"));

        Result version = runCli("--version");
        assertEquals(0, version.exitCode);
        assertTrue(version.out.contains("CODE-CHANGE-ANALYSIS"));
    }

    @Test
    void shouldRejectMissingOrInvalidArguments() {
        Result missing = runCli();
        assertEquals(1, missing.exitCode);
        assertTrue(missing.err.contains("Missing required argument --url="));

        Result invalid = runCli("--url=not-a-url");
        assertEquals(1, invalid.exitCode);
        assertTrue(invalid.err.contains("Invalid Git repository URL"));

        Result unknown = runCli("--url=https://example.com/user/repo.git", "--nope");
        assertEquals(1, unknown.exitCode);
        assertTrue(unknown.err.contains("Unrecognised argument"));
    }

    @Test
    void shouldRequireBaseAndTargetTogether() throws Exception {
        initJavaRepository(true);
        Result result = runCli("--url=" + repoUrl(), "--base=abc1234");
        assertEquals(1, result.exitCode);
        assertTrue(result.err.contains("--base and --target must be given together"));
    }

    @Test
    void shouldReportCloneFailureWithExitCodeFour() throws Exception {
        Path missing = repoDir.resolve("missing-repository");
        Result result = runCli("--url=" + missing.toUri());
        assertEquals(4, result.exitCode);
        assertTrue(result.err.contains("Repository cloning/network failure"),
                "Actual error output: " + result.err);
    }

    // ---- analysis and reporting ----------------------------------------

    @Test
    void shouldAnalyzeAJavaChangeFromARemoteStyleUrl() throws Exception {
        initJavaRepository(true);
        Result result = runCli("--url=" + repoUrl());

        assertEquals(0, result.exitCode, result.err);
        assertTrue(result.out.contains("Repository URL received:"), result.out);
        assertTrue(result.out.contains("CHANGE-SENSITIVE REGRESSION TESTING REPORT"), result.out);
        assertTrue(result.out.contains("REPOSITORY INFORMATION"), result.out);
        assertTrue(result.out.contains("PROJECT DISCOVERY"), result.out);
        assertTrue(result.out.contains("FILE CLASSIFICATION"), result.out);
        assertTrue(result.out.contains("CHANGE DETECTION"), result.out);
        assertTrue(result.out.contains("DEPENDENCY AND IMPACT ANALYSIS"), result.out);
        assertTrue(result.out.contains("REGRESSION TEST SELECTION"), result.out);
        assertTrue(result.out.contains("FINAL RESULT"), result.out);
        assertTrue(result.out.contains("Java production files: 1"), result.out);
        assertTrue(result.out.contains("Test execution was not requested"), result.out);
    }

    @Test
    void shouldSupportCustomBaseAndTargetRevisions() throws Exception {
        String[] commits = initJavaRepository(true);
        Result result = runCli("--url=" + repoUrl(),
                "--base=" + commits[0], "--target=" + commits[1]);

        assertEquals(0, result.exitCode, result.err);
        assertTrue(result.out.contains("Base revision: " + commits[0]), result.out);
        assertTrue(result.out.contains("Target revision: " + commits[1]), result.out);
    }

    @Test
    void shouldRejectAnUnresolvableRevisionWithExitCodeOne() throws Exception {
        initJavaRepository(true);
        Result result = runCli("--url=" + repoUrl(), "--base=nosuchrevision", "--target=nosuchrevision");
        assertEquals(1, result.exitCode, result.err);
        assertTrue(result.out.contains("invalid base revision")
                        || result.out.contains("invalid target revision"),
                "Actual output: " + result.out);
    }

    @Test
    void shouldExplainWhenOnlyNonJavaFilesChanged() throws Exception {
        initJavaRepository(false);
        // A second commit that adds only a JavaScript file.
        try (Git git = Git.open(repoDir.toFile())) {
            write("app.js", "console.log('hi');\n");
            git.add().addFilepattern(".").call();
            commit(git, "add a javascript file");
        }

        Result result = runCli("--url=" + repoUrl());

        assertEquals(0, result.exitCode, result.err);
        assertTrue(result.out.contains("No supported Java source changes were detected."),
                "Actual output: " + result.out);
        assertTrue(result.out.contains("Java regression test generation was not required."), result.out);
        assertTrue(result.out.contains("Other-language changes were detected but are outside the "
                + "current testing scope."), result.out);
        assertTrue(result.out.contains("Other programming language files: 1"), result.out);
        assertTrue(result.out.contains("AUTOMATIC TEST GENERATION"), result.out);
        assertTrue(result.out.contains("EXECUTION STATUS"), result.out);
        assertTrue(result.out.contains("NOT RUN"), result.out);
    }

    @Test
    void shouldSelectConservativelyWhenOnlyTheBuildFileChanged() throws Exception {
        initJavaRepository(false);
        try (Git git = Git.open(repoDir.toFile())) {
            write("pom.xml", POM.replace("<version>1.0</version>", "<version>1.1</version>"));
            git.add().addFilepattern(".").call();
            commit(git, "bump the build version");
        }

        Result result = runCli("--url=" + repoUrl());

        assertEquals(0, result.exitCode, result.err);
        assertTrue(result.out.contains("A build file changed (pom.xml)"), result.out);
        assertTrue(result.out.contains("Confidence: FALLBACK"),
                "A build-file change must fall back to the full suite. Actual output: " + result.out);
        assertTrue(result.out.contains("Build/resource changes (1)"), result.out);
    }

    // ---- security confirmation -----------------------------------------

    @Test
    void shouldRefuseExecutionWithoutExplicitConfirmation() throws Exception {
        initJavaRepository(true);
        Result result = runCli("--url=" + repoUrl(), "--execute");

        assertEquals(1, result.exitCode, result.err);
        assertTrue(result.out.contains("SECURITY WARNING"),
                "The security warning must be displayed before execution. Actual output: " + result.out);
        assertTrue(result.err.contains("--yes"),
                "The user must be told how to confirm. Actual error output: " + result.err);
    }

    private String repoUrl() {
        return repoDir.toUri().toString();
    }

    /**
     * Builds a single-module Maven repository with a Java source and its test.
     * When {@code twoCommits} is true a second commit modifies the source.
     *
     * @return the two commit ids (identical when there is only one commit)
     */
    private String[] initJavaRepository(boolean twoCommits) throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write("pom.xml", POM);
            write("src/main/java/demo/Calculator.java", CALC_V1);
            write("src/test/java/demo/CalculatorTest.java", CALC_TEST);
            git.add().addFilepattern(".").call();
            String v1 = commit(git, "V1: Calculator with a test");

            if (!twoCommits) {
                return new String[] {v1, v1};
            }
            write("src/main/java/demo/Calculator.java", CALC_V2);
            git.add().addFilepattern(".").call();
            String v2 = commit(git, "V2: modify Calculator.add");
            return new String[] {v1, v2};
        }
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = repoDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private String commit(Git git, String message) throws Exception {
        return git.commit()
                .setMessage(message)
                .setAuthor(IDENT)
                .setCommitter(IDENT)
                .setSign(false)
                .call()
                .getName();
    }
}
