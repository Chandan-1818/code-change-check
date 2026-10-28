package com.analysis.tool.cli;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Throwaway repo, no sample project. Starts a few JVMs (a few seconds). */
class GenerateTestsCliTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");
    private static final String SRC = "src/main/java/demo/Calculator.java";

    private static final String V1 = "package demo;\n"
            + "public class Calculator {\n"
            + "    public int add(int a, int b) { return a + b; }\n"
            + "    public int sub(int a, int b) { return a - b; }\n"
            + "}\n";
    private static final String V2 = "package demo;\n"
            + "public class Calculator {\n"
            + "    public int add(int a, int b) { return a + b; }\n"
            + "    public int sub(int a, int b) { return a - b - 1; }\n"
            + "    public int mul(int a, int b) { return a * b; }\n"
            + "}\n";

    @TempDir
    Path repoDir;
    @TempDir
    Path outDir;

    private static class Result {
        int exitCode;
        String out;
        String err;
    }

    private Result runCli(String... args) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        Result r = new Result();
        r.exitCode = GenerateTestsCli.run(args,
                new PrintStream(o, true, StandardCharsets.UTF_8), new PrintStream(e, true, StandardCharsets.UTF_8));
        r.out = o.toString(StandardCharsets.UTF_8);
        r.err = e.toString(StandardCharsets.UTF_8);
        return r;
    }

    private String commit(Git git, String content, String message) throws Exception {
        Path file = repoDir.resolve(SRC);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        git.add().addFilepattern(".").call();
        return git.commit().setMessage(message).setAuthor(IDENT).setCommitter(IDENT)
                .setSign(false).call().getName();
    }

    @Test
    void initialThenIncrementalThenAccept() throws Exception {
        String c1;
        String c2;
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            c1 = commit(git, V1, "V1");
            c2 = commit(git, V2, "V2");
        }
        String repo = repoDir.toString();
        String out = outDir.toString();

        Result initial = runCli(repo, c1, "--project=demo", "--out=" + out);
        assertEquals(0, initial.exitCode, initial.err);
        assertTrue(initial.out.contains("Mode           : initial"), initial.out);
        assertTrue(Files.exists(outDir.resolve("demo").resolve(c1).resolve("manifest.json")));

        Result update = runCli(repo, c2, "--previous=" + c1, "--project=demo", "--out=" + out);
        assertEquals(0, update.exitCode, update.err);
        assertTrue(update.out.contains("Added methods (1):"), update.out);
        assertTrue(update.out.contains("Calculator.mul(int,int)"), update.out);
        assertTrue(update.out.contains("Modified methods (1):"), update.out);
        assertTrue(update.out.contains("WARNING - BEHAVIOUR_CHANGED"), update.out);
        assertTrue(update.out.contains("Calculator.sub(int,int)"), update.out);

        Result again = runCli(repo, c2, "--previous=" + c1, "--project=demo", "--out=" + out);
        assertEquals(0, again.exitCode, "an identical rerun is accepted: " + again.err);

        Path generated = outDir.resolve("demo").resolve(c2)
                .resolve("src/test/java/rts/generated/CalculatorGeneratedTest.java");
        Files.writeString(generated, Files.readString(generated) + "\n// tampered\n");
        Result refused = runCli(repo, c2, "--previous=" + c1, "--project=demo", "--out=" + out);
        assertEquals(2, refused.exitCode, "a different existing suite must not be overwritten silently");
        Result accepted = runCli(repo, c2, "--previous=" + c1, "--accept=Calculator#sub(int,int)",
                "--project=demo", "--out=" + out);
        assertEquals(0, accepted.exitCode, accepted.err);
        assertFalse(accepted.out.contains("WARNING"), accepted.out);
    }

    @Test
    void invalidArgumentsGiveExitCode1() {
        assertEquals(1, runCli("onlyOneArgument").exitCode);
        assertEquals(1, runCli("repo", "rev", "--bogus").exitCode);
        assertEquals(1, runCli("repo", "rev", "--accept=Calculator#sub(int,int)").exitCode);
        assertEquals(1, runCli("repo", "rev", "--previous=x", "--accept=nohash").exitCode);
    }

    @Test
    void missingPreviousManifestGivesExitCode2() throws Exception {
        String c1;
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            c1 = commit(git, V1, "V1");
        }
        Result r = runCli(repoDir.toString(), c1, "--previous=" + c1, "--project=demo", "--out=" + outDir);
        assertEquals(2, r.exitCode);
        assertTrue(r.err.contains("no manifest for the previous revision"), r.err);
    }
}
