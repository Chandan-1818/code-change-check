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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T3 focused tests for the live workflow's visible output, using a throwaway
 * Git repository (no sample project, no Maven subprocess):
 *
 * <ul>
 *   <li>the named arguments {@code --repo/--base/--target} are equivalent to
 *       the positional form;</li>
 *   <li>a missing named argument and an unresolvable revision are rejected as
 *       invalid arguments (exit code 1) with an actionable message;</li>
 *   <li>the Git diff is printed with A/M/D markers and a change summary;</li>
 *   <li>changed members, impacted methods and the consolidated final report
 *       are printed, and existing tests are distinguished from generated
 *       ones in the selection summary.</li>
 * </ul>
 */
class SelectTestsCliNamedArgsTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    private static final String CALCULATOR = "src/main/java/demo/Calculator.java";
    private static final String HELPER = "src/main/java/demo/Helper.java";
    private static final String CALCULATOR_TEST = "src/test/java/demo/CalculatorTest.java";

    @TempDir
    Path repoDir;

    private String oldCommit;
    private String newCommit;

    private static final class Result {
        int exitCode;
        String out;
        String err;
    }

    private Result runCli(String... args) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        Result r = new Result();
        r.exitCode = SelectTestsCli.run(args,
                new PrintStream(o, true, StandardCharsets.UTF_8),
                new PrintStream(e, true, StandardCharsets.UTF_8));
        r.out = o.toString(StandardCharsets.UTF_8);
        r.err = e.toString(StandardCharsets.UTF_8);
        return r;
    }

    @BeforeEach
    void buildHistory() throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(CALCULATOR, "package demo;\npublic class Calculator {\n"
                    + "    public int add(int a, int b) { return a + b; }\n}\n");
            write(CALCULATOR_TEST, "package demo;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "import static org.junit.jupiter.api.Assertions.assertEquals;\n"
                    + "public class CalculatorTest {\n"
                    + "    @Test\n"
                    + "    public void testAdd() { assertEquals(3, new Calculator().add(1, 2)); }\n"
                    + "}\n");
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1");

            // V2 modifies Calculator.add's body and adds a new production file.
            write(CALCULATOR, "package demo;\npublic class Calculator {\n"
                    + "    public int add(int a, int b) { int r = a + b; return r; }\n}\n");
            write(HELPER, "package demo;\npublic class Helper {\n"
                    + "    public int twice(int v) { return v * 2; }\n}\n");
            git.add().addFilepattern(".").call();
            newCommit = commit(git, "V2");
        }
    }

    @Test
    void namedArgumentsAreEquivalentToThePositionalForm() {
        Result positional = runCli(repoDir.toString(), oldCommit, newCommit);
        Result named = runCli("--repo=" + repoDir, "--base=" + oldCommit, "--target=" + newCommit);

        assertEquals(0, named.exitCode, named.err);
        assertEquals(positional.exitCode, named.exitCode);
        assertEquals(positional.out, named.out,
                "The named and positional forms must produce identical output");
        assertTrue(named.out.contains("Selected tests (1):"), named.out);
        assertTrue(named.out.contains("CalculatorTest#testAdd"), named.out);
    }

    @Test
    void aMissingNamedArgumentIsRejected() {
        Result result = runCli("--repo=" + repoDir, "--base=" + oldCommit);

        assertEquals(1, result.exitCode);
        assertTrue(result.err.contains("--repo, --base and --target must all be given"), result.err);
    }

    @Test
    void anUnresolvableBaseRevisionIsRejected() {
        Result result = runCli("--repo=" + repoDir, "--base=no-such-revision", "--target=" + newCommit);

        assertEquals(1, result.exitCode);
        assertTrue(result.err.contains("Invalid base revision"), result.err);
    }

    @Test
    void anUnresolvableTargetRevisionIsRejected() {
        Result result = runCli("--repo=" + repoDir, "--base=" + oldCommit, "--target=no-such-revision");

        assertEquals(1, result.exitCode);
        assertTrue(result.err.contains("Invalid target revision"), result.err);
    }

    @Test
    void printsTheGitDiffWithChangeMarkers() {
        Result result = runCli("--repo=" + repoDir, "--base=" + oldCommit, "--target=" + newCommit);

        assertEquals(0, result.exitCode, result.err);
        assertTrue(result.out.contains("Git diff       : 2 file(s) changed between " + oldCommit
                        + " and " + newCommit + " (1 added, 1 modified, 0 deleted)"),
                "Expected the file-level diff summary. Actual output:\n" + result.out);
        assertTrue(result.out.contains("  M " + CALCULATOR),
                "Expected a modified marker for Calculator.java. Actual output:\n" + result.out);
        assertTrue(result.out.contains("  A " + HELPER),
                "Expected an added marker for Helper.java. Actual output:\n" + result.out);
    }

    @Test
    void printsChangedMembersImpactedMethodsAndTheFinalReport() {
        Result result = runCli("--repo=" + repoDir, "--base=" + oldCommit, "--target=" + newCommit);

        assertEquals(0, result.exitCode, result.err);
        assertTrue(result.out.contains("Changed members (2):"), result.out);
        assertTrue(result.out.contains("  - Calculator#add"), result.out);
        assertTrue(result.out.contains("Impacted methods ("), result.out);

        assertTrue(result.out.contains(" Final report"), result.out);
        assertTrue(result.out.contains("Git diff         : 2 file(s) changed (1 added, 1 modified, 0 deleted)"),
                result.out);
        assertTrue(result.out.contains("Selected tests   : 1 (1 existing, 0 generated)"),
                "Expected the existing/generated split in the final report. Actual output:\n" + result.out);
        assertTrue(result.out.contains("Execution        : not requested"), result.out);
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = repoDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private String commit(Git git, String message) throws Exception {
        return git.commit().setMessage(message).setAuthor(IDENT).setCommitter(IDENT)
                .setSign(false).call().getName();
    }
}
