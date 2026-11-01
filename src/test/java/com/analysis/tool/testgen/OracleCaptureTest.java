package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real compile-and-run tests: each capture compiles inline sources with the JDK
 * and starts two JVMs, so a capture takes a few seconds. No Git, no sample project.
 */
public class OracleCaptureTest {

    private static final String CALCULATOR = """
            package demo;
            public class Calculator {
                public int add(int a, int b) { return a + b; }
                public int divide(int a, int b) { return a / b; }
                public String greet(String name) { return "Hello " + name; }
                public int noisy(int a) { System.out.println("junk output"); return a; }
            }
            """;

    private static final String EXTRAS = """
            package demo;
            public class Extras {
                public int loop(int a) { while (true) { } }
                public long nano() { return System.nanoTime(); }
            }
            """;

    private static final String BROKEN = """
            package demo;
            public class Broken {
                public Broken() { throw new IllegalStateException("no"); }
                public int value() { return 1; }
            }
            """;

    private static OracleOutcome find(List<OracleCase> cases, List<OracleOutcome> outcomes,
                                      String className, String methodName, List<String> arguments) {
        for (int i = 0; i < cases.size(); i++) {
            OracleCase c = cases.get(i);
            if (c.getClassName().equals(className) && c.getMethodName().equals(methodName)
                    && c.getArgumentExpressions().equals(arguments)) {
                return outcomes.get(i);
            }
        }
        throw new AssertionError("No case " + className + "." + methodName + arguments);
    }

    @Test
    public void recordsReturnsAndExceptionsAndIgnoresTargetOutput() throws Exception {
        List<OracleCase> cases = new CasePlanner().plan(List.of(CALCULATOR));

        List<OracleOutcome> outcomes = new OracleCapture(5000, 60).capture(List.of(CALCULATOR), cases);

        assertEquals(cases.size(), outcomes.size());
        assertEquals(new OracleOutcome(OracleOutcome.Status.RETURNED, "0"),
                find(cases, outcomes, "Calculator", "add", List.of("0", "0")));
        assertEquals(new OracleOutcome(OracleOutcome.Status.THREW, "java.lang.ArithmeticException"),
                find(cases, outcomes, "Calculator", "divide", List.of("0", "0")));
        assertEquals(new OracleOutcome(OracleOutcome.Status.RETURNED, "\"Hello null\""),
                find(cases, outcomes, "Calculator", "greet", List.of("null")));
        assertEquals(new OracleOutcome(OracleOutcome.Status.RETURNED, "\"Hello a\""),
                find(cases, outcomes, "Calculator", "greet", List.of("\"a\"")));
        assertEquals(new OracleOutcome(OracleOutcome.Status.RETURNED, "2"),
                find(cases, outcomes, "Calculator", "noisy", List.of("2")),
                "System.out output of the target must not corrupt the protocol");
        assertTrue(outcomes.stream().allMatch(OracleOutcome::isUsable), "Outcomes: " + outcomes);
    }

    @Test
    public void reportsTimeoutNondeterminismAndConstructionFailure() throws Exception {
        // loop() is first on purpose: it is expected to time out, so a slow cold start cannot cause a false failure.
        List<OracleCase> cases = List.of(
                new OracleCase("Extras", "loop", false, "new Extras()", List.of("int"), List.of("1")),
                new OracleCase("Extras", "nano", false, "new Extras()", List.of(), List.of()),
                new OracleCase("Broken", "value", false, "new Broken()", List.of(), List.of()));

        List<OracleOutcome> outcomes = new OracleCapture(500, 60).capture(List.of(EXTRAS, BROKEN), cases);

        assertEquals(OracleOutcome.Status.TIMED_OUT, outcomes.get(0).getStatus(), outcomes.toString());
        assertEquals(OracleOutcome.Status.NONDETERMINISTIC, outcomes.get(1).getStatus(), outcomes.toString());
        assertEquals(new OracleOutcome(OracleOutcome.Status.CONSTRUCTION_FAILED, "java.lang.IllegalStateException"),
                outcomes.get(2));
    }

    @Test
    public void compileFailureIsReported() {
        String bad = """
                package demo;
                public class Bad { public int value() { return "x"; } }
                """;
        List<OracleCase> cases = List.of(
                new OracleCase("Bad", "value", false, "new Bad()", List.of(), List.of()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OracleCapture(500, 60).capture(List.of(bad), cases));

        assertTrue(error.getMessage().startsWith("Compilation failed"), error.getMessage());
    }

    @Test
    public void emptyCaseListReturnsEmpty() throws Exception {
        assertTrue(new OracleCapture().capture(List.of(CALCULATOR), List.of()).isEmpty());
    }
}
