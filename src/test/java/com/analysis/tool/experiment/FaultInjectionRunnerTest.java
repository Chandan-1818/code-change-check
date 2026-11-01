package com.analysis.tool.experiment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safety check: for every injected fault in the sample project, every test
 * that fails in the full suite must be among the tests the tool selected.
 * Uses the pinned sample-project commit V8 = ebec3c0 as the base revision
 * (never a HEAD-relative ref).
 */
public class FaultInjectionRunnerTest {

    private static final String SAMPLE_PROJECT_PATH =
            com.analysis.tool.testsupport.SamplePaths.SAMPLE_PROJECT_PATH;
    private static final String VERSION_8_COMMIT = "ebec3c0";

    @Test
    public void shouldClassifyOutcomes() {
        assertEquals(FaultInjectionRunner.Outcome.CAUGHT,
                FaultInjectionRunner.classify(Set.of("a", "b"), Set.of("a")));
        assertEquals(FaultInjectionRunner.Outcome.MISSED,
                FaultInjectionRunner.classify(Set.of("a"), Set.of("a", "c")));
        assertEquals(FaultInjectionRunner.Outcome.NOT_DETECTED_BY_SUITE,
                FaultInjectionRunner.classify(Set.of("a"), Collections.emptySet()));
        assertEquals(FaultInjectionRunner.Outcome.NOT_DETECTED_BY_SUITE,
                FaultInjectionRunner.classify(Collections.emptySet(), Collections.emptySet()));
    }

    @Test
    public void shouldRenderTableAndSummaryFromSyntheticResults() {
        FaultInjectionRunner.Fault fault = new FaultInjectionRunner.Fault(
                "Calculator.add", "a + b -> a - b", "x.java", "a", "b");
        FaultInjectionRunner.FaultResult caught = new FaultInjectionRunner.FaultResult(
                fault, "abc1234", Set.of("testA", "testB"), Set.of("testA"));
        FaultInjectionRunner.FaultResult undetected = new FaultInjectionRunner.FaultResult(
                fault, "def5678", Collections.emptySet(), Collections.emptySet());

        String markdown = FaultInjectionRunner.toMarkdown(List.of(caught, undetected));

        assertTrue(markdown.contains("| Calculator.add | a + b -> a - b | testA, testB | testA | CAUGHT | 50% | 100% |"),
                "Actual markdown:\n" + markdown);
        assertTrue(markdown.contains("| Calculator.add | a + b -> a - b | - | - | NOT_DETECTED_BY_SUITE | n/a | n/a |"),
                "Actual markdown:\n" + markdown);
        assertEquals("Faults injected: 2; caught: 1; missed: 0; not detected by the suite: 1",
                FaultInjectionRunner.summary(List.of(caught, undetected)));
    }

    @Test
    public void shouldLocateOnlyASingleOccurrence() {
        assertEquals(4, FaultInjectionRunner.singleOccurrence("int a = 1;", "a"));
        assertEquals(-1, FaultInjectionRunner.singleOccurrence("int a = 1; int a = 2;", "a"),
                "a text that occurs twice cannot be replaced unambiguously");
        assertEquals(-1, FaultInjectionRunner.singleOccurrence("int a = 1;", "b"));
        assertEquals(0, FaultInjectionRunner.singleOccurrence("int a = 1;", "int a = 1;"));
        assertEquals(-1, FaultInjectionRunner.singleOccurrence("int a = 1;", ""),
                "an empty needle occurs everywhere, never exactly once");
    }

    @Test
    public void shouldExplainWhyAFaultCannotBeInjected(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("src/main/java/com/sample/Calculator.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "int x = 1;\nint y = 2;\n", StandardCharsets.UTF_8);

        assertNull(FaultInjectionRunner.injectionProblem(dir, new FaultInjectionRunner.Fault(
                        "Calculator.x", "x", "src/main/java/com/sample/Calculator.java", "int x = 1;", "int x = 2;")),
                "an injectable fault must report no problem");

        assertEquals("the fault file is not present at the base revision: src/main/java/com/sample/Missing.java",
                FaultInjectionRunner.injectionProblem(dir, new FaultInjectionRunner.Fault(
                        "Missing.m", "m", "src/main/java/com/sample/Missing.java", "x", "y")),
                "a file removed at a later revision must be reported, not thrown");

        assertTrue(FaultInjectionRunner.injectionProblem(dir, new FaultInjectionRunner.Fault(
                        "Calculator.x", "x", "src/main/java/com/sample/Calculator.java", "int x = 9;", "int x = 2;"))
                        .contains("not present exactly once"),
                "absent mutation text must be reported");

        assertTrue(FaultInjectionRunner.injectionProblem(dir, new FaultInjectionRunner.Fault(
                        "Calculator.x", "x", "src/main/java/com/sample/Calculator.java", "int", "long"))
                        .contains("not present exactly once"),
                "ambiguous mutation text must be reported");
    }

    @Test
    public void shouldRecordAnUninjectableFaultAsSkipped() {
        FaultInjectionRunner.Fault fault = new FaultInjectionRunner.Fault(
                "Calculator.compute", "result + 1", "Calculator.java", "return a + b;", "return a + b + 1;");
        FaultInjectionRunner.FaultResult skipped = FaultInjectionRunner.FaultResult.skipped(
                fault, "the fault file is not present at the base revision: Calculator.java");
        FaultInjectionRunner.FaultResult caught = new FaultInjectionRunner.FaultResult(
                fault, "abc1234", Set.of("testA"), Set.of("testA"));

        assertEquals(FaultInjectionRunner.Outcome.SKIPPED, skipped.getOutcome());
        assertNull(skipped.getMutantCommit());
        assertTrue(skipped.getSelectedTests().isEmpty());
        assertTrue(skipped.getFailingTests().isEmpty());
        assertNull(caught.getDetail(), "only a skipped fault carries a reason");

        String markdown = FaultInjectionRunner.toMarkdown(List.of(skipped, caught));
        assertTrue(markdown.contains(
                        "| SKIPPED (the fault file is not present at the base revision: Calculator.java) | n/a | n/a |"),
                "Actual markdown:\n" + markdown);
        assertEquals("Faults injected: 2; caught: 1; missed: 0; not detected by the suite: 0"
                        + "; skipped (not injectable at this revision): 1",
                FaultInjectionRunner.summary(List.of(skipped, caught)));
    }

    @Test
    public void shouldSelectEveryFailingTestForEachInjectedFault() throws Exception {
        Map<String, Set<String>> expectedFailures = new HashMap<>();
        expectedFailures.put("Calculator.add", Set.of("testAdd"));
        expectedFailures.put("Calculator.subtract", Set.of("testSubtract"));
        expectedFailures.put("Calculator.compute", Set.of("testCompute"));
        expectedFailures.put("Calculator.negate", Collections.emptySet());
        expectedFailures.put("Multiplier.multiply", Set.of("testMultiply", "testCompute"));
        expectedFailures.put("OrderRepository.save", Set.of("testSaveAndFindTotal", "testPlaceOrder"));
        expectedFailures.put("OrderRepository.findTotal", Set.of("testSaveAndFindTotal", "testPlaceOrder"));
        expectedFailures.put("OrderService.placeOrder", Set.of("testPlaceOrder"));
        expectedFailures.put("OrderService.getOrderTotal", Set.of("testPlaceOrder"));

        List<FaultInjectionRunner.FaultResult> results = new FaultInjectionRunner().run(
                SAMPLE_PROJECT_PATH, VERSION_8_COMMIT, FaultInjectionRunner.sampleProjectFaults());

        String table = FaultInjectionRunner.toMarkdown(results);
        assertEquals(9, results.size(), "Actual table:\n" + table);

        for (FaultInjectionRunner.FaultResult result : results) {
            String method = result.getFault().getMethod();
            Set<String> expected = expectedFailures.get(method);

            assertNotEquals(FaultInjectionRunner.Outcome.MISSED, result.getOutcome(),
                    method + " left a failing test unselected. Actual table:\n" + table);
            assertTrue(result.getFailingTests().containsAll(expected),
                    method + " should fail at least " + expected + ". Actual table:\n" + table);
        }

        FaultInjectionRunner.FaultResult negate = results.stream()
                .filter(r -> r.getFault().getMethod().equals("Calculator.negate"))
                .findFirst().orElseThrow();
        assertEquals(FaultInjectionRunner.Outcome.NOT_DETECTED_BY_SUITE, negate.getOutcome(),
                "negate has no test, so no test can fail. Actual table:\n" + table);

        assertTrue(FaultInjectionRunner.summary(results).contains("missed: 0"),
                "Actual table:\n" + table);
    }
}
