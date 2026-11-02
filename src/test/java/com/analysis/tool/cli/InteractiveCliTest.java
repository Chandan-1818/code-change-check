package com.analysis.tool.cli;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PHASE 1 and PHASE 14 scenarios 1-5: the interactive session.
 *
 * <p>Input is injected through a {@link StringReader}, so the whole interaction
 * is exercised deterministically without a real terminal. The repositories are
 * throwaway local Git repositories, so nothing reaches the network.
 */
class InteractiveCliTest {

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

    private Result run(String scriptedInput) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        Result r = new Result();
        r.exitCode = InteractiveCli.run(
                new PrintStream(o, true, StandardCharsets.UTF_8),
                new PrintStream(e, true, StandardCharsets.UTF_8),
                new BufferedReader(new StringReader(scriptedInput)));
        r.out = o.toString(StandardCharsets.UTF_8);
        r.err = e.toString(StandardCharsets.UTF_8);
        return r;
    }

    @Test
    void shouldPrintTheBannerAndExitCleanly() {
        Result result = run("exit\n");

        assertEquals(0, result.exitCode);
        assertTrue(result.out.contains("CHANGE-SENSITIVE REGRESSION TEST SELECTION SYSTEM"), result.out);
        assertTrue(result.out.contains("Enter Git repository URL"), result.out);
    }

    @Test
    void shouldNotCrashWhenInputEndsImmediately() {
        Result result = run("");
        assertEquals(0, result.exitCode, "End of input must end the session, not crash");
    }

    @Test
    void shouldRepromptOnEmptyInputAndInvalidUrl() {
        Result result = run("\n\nnot-a-url\nexit\n");

        assertEquals(0, result.exitCode);
        assertTrue(result.out.contains("No repository URL entered."), result.out);
        assertTrue(result.err.contains("Invalid Git repository URL"), result.err);
    }

    @Test
    void shouldExplainTheSingleCommitCaseAndReturnToThePrompt() throws Exception {
        initRepository(false);
        Result result = run(repoDir.toUri() + "\n1\nexit\n");

        assertEquals(0, result.exitCode, result.err);
        assertTrue(result.err.contains("contains only one commit"),
                "A single-commit repository must be explained. Actual error output: " + result.err);
        assertTrue(result.out.contains("Returning to the repository prompt."), result.out);
    }

    @Test
    void shouldRunTheAnalysisAndReturnToTheMenu() throws Exception {
        initRepository(true);
        Result result = run(repoDir.toUri() + "\n1\n1\n6\n");

        assertEquals(0, result.exitCode, result.err);
        assertTrue(result.out.contains("Repository URL received."), result.out);
        assertTrue(result.out.contains("Comparing the latest two commits:"), result.out);
        assertTrue(result.out.contains("1. Analyze repository changes"), result.out);
        assertTrue(result.out.contains("6. Exit"), result.out);
        assertTrue(result.out.contains("CHANGE-SENSITIVE REGRESSION TESTING REPORT"), result.out);
        assertTrue(result.out.contains("Returning to the operations menu."), result.out);
    }

    @Test
    void shouldRejectAnInvalidMenuSelectionWithoutCrashing() throws Exception {
        initRepository(true);
        Result result = run(repoDir.toUri() + "\n1\n99\n\n6\n");

        assertEquals(0, result.exitCode, result.err);
        assertTrue(result.out.contains("Invalid selection '99'"), result.out);
        assertTrue(result.out.contains("No selection entered"), result.out);
    }

    @Test
    void shouldReturnToTheMenuWhenExecutionIsNotConfirmed() throws Exception {
        initRepository(true);
        // Operation 4 (analyze, generate and execute), then refuse the
        // security confirmation with "n", then exit.
        Result result = run(repoDir.toUri() + "\n1\n4\nn\n6\n");

        assertEquals(0, result.exitCode, result.err);
        assertTrue(result.out.contains("SECURITY WARNING"), result.out);
        assertTrue(result.out.contains("Execution not confirmed"), result.out);
        assertTrue(result.out.contains("Returning to the operations menu."), result.out);
    }

    @Test
    void shouldSupportCustomRevisions() throws Exception {
        String[] commits = initRepository(true);
        Result result = run(repoDir.toUri() + "\n2\n" + commits[0] + "\n" + commits[1] + "\n1\n6\n");

        assertEquals(0, result.exitCode, result.err);
        assertTrue(result.out.contains("Custom comparison: " + commits[0] + " -> " + commits[1]), result.out);
        assertTrue(result.out.contains("CHANGE-SENSITIVE REGRESSION TESTING REPORT"), result.out);
    }

    /** @return {baseCommit, targetCommit}; the target equals the base when single. */
    private String[] initRepository(boolean twoCommits) throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write("pom.xml", POM);
            write("src/main/java/demo/Calculator.java", CALC_V1);
            write("src/test/java/demo/CalculatorTest.java", CALC_TEST);
            git.add().addFilepattern(".").call();
            String v1 = commit(git, "V1");

            if (!twoCommits) {
                return new String[] {v1, v1};
            }
            write("src/main/java/demo/Calculator.java", CALC_V2);
            git.add().addFilepattern(".").call();
            String v2 = commit(git, "V2");
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
