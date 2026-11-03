package com.analysis.tool.impact;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H7 acceptance criteria, end to end through ChangeImpactAnalyzer on
 * throwaway repositories (portable, no JVM launched, independent of the
 * sample project):
 *   - a non-conventionally named test file is discovered and associated
 *     (GAP-08),
 *   - an added or modified test method is selected unconditionally (GAP-10),
 *   - tests that call a removed method are reported as potentially broken
 *     (GAP-11),
 *   - a change reachable only through a @BeforeEach method selects the
 *     class's tests (GAP-09 / TS-18),
 *   - every selection carries an explanation chain (GAP-24).
 */
class H7TestDiscoveryIntegrationTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.com");

    private static final String CALC = "src/main/java/demo/Calculator.java";
    private static final String CALC_TEST = "src/test/java/demo/CalculatorTest.java";

    @TempDir
    Path repoDir;

    private String oldCommit;
    private String newCommit;

    private final ChangeImpactAnalyzer analyzer = new ChangeImpactAnalyzer();

    // ---------------------------------------------------------------- GAP-08

    @Test
    void nonConventionallyNamedTestFileIsDiscoveredAndAssociated() throws Exception {
        String checks = "src/test/java/demo/Checks.java";

        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(CALC, "package demo;\n"
                    + "public class Calculator {\n"
                    + "    public int add(int a, int b) { return a + b; }\n"
                    + "}\n");
            write(checks, "package demo;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "public class Checks {\n"
                    + "    @Test\n"
                    + "    public void checksAdd() { new Calculator().add(1, 2); }\n"
                    + "}\n");
            oldCommit = commit(git, "V1: Calculator + non-conventionally named test");

            write(CALC, "package demo;\n"
                    + "public class Calculator {\n"
                    + "    public int add(int a, int b) { int r = a + b; return r; }\n"
                    + "}\n");
            newCommit = commit(git, "V2: change add body");
        }

        // The supplied mapping's test file does not exist: discovery must find
        // Checks.java because it lives under src/test/java (not by name).
        TestSelectionReport report = analyzer.analyzeTransitively(
                repoDir.toString(), oldCommit, newCommit,
                List.of(new FileMapping(CALC, CALC_TEST)));

        assertTrue(report.getSelectedTests().contains("checksAdd"),
                "The non-conventionally named test must be discovered and selected. Report: " + report);
        assertTrue(report.getSelectedTestIdentifiers().contains("Checks#checksAdd"),
                "Its identifier must use the real class name");
        assertTrue(report.getUntestedChangedMethods().isEmpty(),
                "The changed method is reached, so it is not untested");

        List<String> chain = report.getExplanations().get("Checks#checksAdd");
        assertNotNull(chain, "A selected test must have an explanation chain");
        assertEquals("Checks#checksAdd", chain.get(0),
                "The chain starts with the selected test");
        assertTrue(chain.contains("Calculator#add"),
                "The chain must include the changed method it reaches");
    }

    // ---------------------------------------------------------------- GAP-10

    @Test
    void addedTestMethodIsSelectedUnconditionally() throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(CALC, "package demo;\n"
                    + "public class Calculator {\n"
                    + "    public int add(int a, int b) { return a + b; }\n"
                    + "    public int subtract(int a, int b) { return a - b; }\n"
                    + "}\n");
            write(CALC_TEST, "package demo;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "public class CalculatorTest {\n"
                    + "    @Test\n"
                    + "    public void testAdd() { new Calculator().add(1, 2); }\n"
                    + "}\n");
            oldCommit = commit(git, "V1: add + subtract, test for add");

            write(CALC, "package demo;\n"
                    + "public class Calculator {\n"
                    + "    public int add(int a, int b) { int r = a + b; return r; }\n"
                    + "    public int subtract(int a, int b) { return a - b; }\n"
                    + "}\n");
            write(CALC_TEST, "package demo;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "public class CalculatorTest {\n"
                    + "    @Test\n"
                    + "    public void testAdd() { new Calculator().add(1, 2); }\n"
                    + "    @Test\n"
                    + "    public void testSubtractOnly() { new Calculator().subtract(5, 2); }\n"
                    + "}\n");
            newCommit = commit(git, "V2: change add, add an unrelated new test");
        }

        TestSelectionReport report = analyzer.analyzeTransitively(
                repoDir.toString(), oldCommit, newCommit,
                List.of(new FileMapping(CALC, CALC_TEST)));

        assertTrue(report.getSelectedTests().contains("testAdd"),
                "The impacted test is selected");
        assertTrue(report.getSelectedTests().contains("testSubtractOnly"),
                "The added test is selected unconditionally even though it does not reach the change");
        assertTrue(report.getSelectedTestIdentifiers().contains("CalculatorTest#testSubtractOnly"));
    }

    @Test
    void modifiedTestMethodIsSelectedUnconditionally() throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(CALC, "package demo;\n"
                    + "public class Calculator {\n"
                    + "    public int add(int a, int b) { return a + b; }\n"
                    + "}\n");
            write(CALC_TEST, "package demo;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "public class CalculatorTest {\n"
                    + "    @Test\n"
                    + "    public void testAdd() { new Calculator().add(1, 2); }\n"
                    + "}\n");
            oldCommit = commit(git, "V1: Calculator + test");

            // Only the test changes: no production method is added or modified.
            write(CALC_TEST, "package demo;\n"
                    + "import static org.junit.jupiter.api.Assertions.assertEquals;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "public class CalculatorTest {\n"
                    + "    @Test\n"
                    + "    public void testAdd() { assertEquals(3, new Calculator().add(1, 2)); }\n"
                    + "}\n");
            newCommit = commit(git, "V2: strengthen the test only");
        }

        TestSelectionReport report = analyzer.analyzeTransitively(
                repoDir.toString(), oldCommit, newCommit,
                List.of(new FileMapping(CALC, CALC_TEST)));

        assertEquals(Set.of("testAdd"), report.getSelectedTests(),
                "A modified test method must be selected unconditionally");
        assertTrue(report.getUntestedChangedMethods().isEmpty(),
                "No production method changed, so nothing is untested");
    }

    // ---------------------------------------------------------------- GAP-11

    @Test
    void testsCallingARemovedMethodAreReportedPotentiallyBroken() throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(CALC, "package demo;\n"
                    + "public class Calculator {\n"
                    + "    public int add(int a, int b) { return a + b; }\n"
                    + "    public int negate(int a) { return -a; }\n"
                    + "}\n");
            write(CALC_TEST, "package demo;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "public class CalculatorTest {\n"
                    + "    @Test\n"
                    + "    public void testAdd() { new Calculator().add(1, 2); }\n"
                    + "    @Test\n"
                    + "    public void testNegate() { new Calculator().negate(1); }\n"
                    + "}\n");
            oldCommit = commit(git, "V1: add + negate + tests");

            write(CALC, "package demo;\n"
                    + "public class Calculator {\n"
                    + "    public int add(int a, int b) { return a + b; }\n"
                    + "}\n");
            newCommit = commit(git, "V2: remove negate");
        }

        TestSelectionReport report = analyzer.analyzeTransitively(
                repoDir.toString(), oldCommit, newCommit,
                List.of(new FileMapping(CALC, CALC_TEST)));

        assertEquals(Set.of("negate"), report.getRemovedMethods());
        assertTrue(report.getSelectedTests().isEmpty(),
                "A removal alone does not change the impact-based selection");
        assertTrue(report.getUntestedChangedMethods().isEmpty());
        assertTrue(report.getPotentiallyBrokenTests().contains("CalculatorTest#testNegate"),
                "The test calling the removed method must be reported as potentially broken. Report: " + report);
    }

    // ---------------------------------------------------------------- GAP-09 / TS-18

    @Test
    void changeReachableOnlyThroughBeforeEachSelectsTheClassTests() throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            write(CALC, "package demo;\n"
                    + "public class Calculator {\n"
                    + "    public int add(int a, int b) { return a + b; }\n"
                    + "    public void prepare() { }\n"
                    + "}\n");
            write(CALC_TEST, "package demo;\n"
                    + "import org.junit.jupiter.api.BeforeEach;\n"
                    + "import org.junit.jupiter.api.Test;\n"
                    + "public class CalculatorTest {\n"
                    + "    @BeforeEach\n"
                    + "    public void setUp() { new Calculator().prepare(); }\n"
                    + "    @Test\n"
                    + "    public void testAdd() { new Calculator().add(1, 2); }\n"
                    + "}\n");
            oldCommit = commit(git, "V1: prepare + setUp + testAdd");

            write(CALC, "package demo;\n"
                    + "public class Calculator {\n"
                    + "    public int add(int a, int b) { return a + b; }\n"
                    + "    public void prepare() { int x = 1; }\n"
                    + "}\n");
            newCommit = commit(git, "V2: change prepare only (reachable via @BeforeEach)");
        }

        TestSelectionReport report = analyzer.analyzeTransitively(
                repoDir.toString(), oldCommit, newCommit,
                List.of(new FileMapping(CALC, CALC_TEST)));

        assertEquals(Set.of("testAdd"), report.getSelectedTests(),
                "A change reachable only via @BeforeEach must select the class's tests. Report: " + report);

        List<String> chain = report.getExplanations().get("CalculatorTest#testAdd");
        assertNotNull(chain, "The lifecycle-driven selection must be explained");
        assertEquals("CalculatorTest#testAdd", chain.get(0));
        assertTrue(chain.contains("CalculatorTest#setUp"),
                "The chain must show the lifecycle method through which the change is reached");
        assertTrue(chain.contains("Calculator#prepare"),
                "The chain must end at the changed method");
    }

    // ---------------------------------------------------------------- helpers

    private void write(String relativePath, String content) throws Exception {
        Path file = repoDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String commit(Git git, String message) throws Exception {
        git.add().addFilepattern(".").call();
        return git.commit().setMessage(message).setAuthor(IDENT).setCommitter(IDENT)
                .setSign(false).call().getName();
    }
}
