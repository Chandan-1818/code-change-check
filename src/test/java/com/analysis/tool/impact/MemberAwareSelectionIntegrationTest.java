package com.analysis.tool.impact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * H3 end-to-end selection at member granularity, using a throwaway repo:
 *
 * 1. changing one overload must NOT select tests that only call a
 *    different-arity overload;
 * 2. an added constructor participates in selection (the test that
 *    constructs the class is selected), handled precisely rather than by
 *    whole-file expansion;
 * 3. a varargs change falls back to name matching, so no test is missed
 *    (conservative: arity is not a safe filter for varargs).
 */
class MemberAwareSelectionIntegrationTest {

    private static final String SRC = "src/main/java/demo/Calc.java";
    private static final String TEST = "src/test/java/demo/CalcTest.java";
    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    @TempDir
    Path repoDir;

    private TestSelectionReport analyze(String oldSource, String newSource, String testSource)
            throws Exception {
        String oldCommit;
        String newCommit;
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(SRC, oldSource);
            write(TEST, testSource);
            git.add().addFilepattern(".").call();
            oldCommit = commit(git, "V1");

            write(SRC, newSource);
            git.add().addFilepattern(SRC).call();
            newCommit = commit(git, "V2");
        }
        List<FileMapping> mappings = Collections.singletonList(new FileMapping(SRC, TEST));
        return new ChangeImpactAnalyzer().analyzeTransitively(
                repoDir.toString(), oldCommit, newCommit, mappings);
    }

    @Test
    void changingOneOverloadDoesNotSelectTestsForADifferentArityOverload() throws Exception {
        TestSelectionReport report = analyze(
                "package demo;\npublic class Calc {\n"
                        + "    public int f(int a) { return a; }\n"
                        + "    public int f(int a, int b) { return a + b; }\n}\n",
                "package demo;\npublic class Calc {\n"
                        + "    public int f(int a) { return a + 1; }\n"
                        + "    public int f(int a, int b) { return a + b; }\n}\n",
                "package demo;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class CalcTest {\n"
                        + "    @Test public void testOneArg() { new Calc().f(1); }\n"
                        + "    @Test public void testTwoArg() { new Calc().f(1, 2); }\n"
                        + "}\n");

        assertTrue(report.getSelectedTests().contains("testOneArg"),
                "Selected: " + report.getSelectedTests());
        assertFalse(report.getSelectedTests().contains("testTwoArg"),
                "A change to f(int) must not select the f(int,int) test: "
                        + report.getSelectedTests());
    }

    @Test
    void addedConstructorSelectsTheTestThatConstructsTheClass() throws Exception {
        TestSelectionReport report = analyze(
                "package demo;\npublic class Calc {\n"
                        + "    public int get() { return 0; }\n}\n",
                "package demo;\npublic class Calc {\n"
                        + "    public int value;\n"
                        + "    public Calc(int seed) { value = seed; }\n"
                        + "    public int get() { return 0; }\n}\n",
                "package demo;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class CalcTest {\n"
                        + "    @Test public void testConstruct() { new Calc(5); }\n"
                        + "}\n");

        assertTrue(report.getSelectedTests().contains("testConstruct"),
                "Selected: " + report.getSelectedTests());
        assertTrue(report.getConservativelyExpandedFiles().isEmpty(),
                "An added constructor must be handled precisely, not by whole-file expansion");
    }

    @Test
    void changingAConstructorDoesNotSelectTestsThatConstructADifferentClass() throws Exception {
        TestSelectionReport report = analyze(
                "package demo;\npublic class Calc {\n"
                        + "    private int value;\n"
                        + "    public Calc(int seed) { value = seed; }\n"
                        + "    public int get() { return value; }\n}\n",
                "package demo;\npublic class Calc {\n"
                        + "    private int value;\n"
                        + "    public Calc(int seed) { value = seed + 1; }\n"
                        + "    public int get() { return value; }\n}\n",
                "package demo;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class CalcTest {\n"
                        + "    @Test public void testCalc() { new Calc(5); }\n"
                        + "    @Test public void testOther() { new Other(7); }\n"
                        + "}\n"
                        + "class Other { Other(int x) { } }\n");

        assertTrue(report.getSelectedTests().contains("testCalc"),
                "Selected: " + report.getSelectedTests());
        assertFalse(report.getSelectedTests().contains("testOther"),
                "Changing Calc's one-argument constructor must not select a test that "
                        + "constructs a different one-argument class (shared <init> name): "
                        + report.getSelectedTests());
    }

    @Test
    void varargsChangeFallsBackToNameMatchingSoNoTestIsMissed() throws Exception {
        TestSelectionReport report = analyze(
                "package demo;\npublic class Calc {\n"
                        + "    public int count(int... xs) { return xs.length; }\n}\n",
                "package demo;\npublic class Calc {\n"
                        + "    public int count(int... xs) { return xs.length + 1; }\n}\n",
                "package demo;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class CalcTest {\n"
                        + "    @Test public void testNone() { new Calc().count(); }\n"
                        + "    @Test public void testTwo() { new Calc().count(1, 2); }\n"
                        + "}\n");

        assertTrue(report.getSelectedTests().contains("testNone"),
                "Selected: " + report.getSelectedTests());
        assertTrue(report.getSelectedTests().contains("testTwo"),
                "Varargs arity must not narrow selection: " + report.getSelectedTests());
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = repoDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String commit(Git git, String message) throws Exception {
        return git.commit().setMessage(message).setAuthor(IDENT).setCommitter(IDENT)
                .setSign(false).call().getName();
    }
}
