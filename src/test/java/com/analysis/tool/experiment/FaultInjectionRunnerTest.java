package com.analysis.tool.experiment;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safety check: for every injected fault in the sample project, every test
 * that fails in the full suite must be among the tests the tool selected.
 * Uses the pinned sample-project commit V8 = ebec3c0 as the base revision
 * (never a HEAD-relative ref).
 */
public class FaultInjectionRunnerTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";
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
