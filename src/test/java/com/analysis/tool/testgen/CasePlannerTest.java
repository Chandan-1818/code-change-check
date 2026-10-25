package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inline sources only. */
public class CasePlannerTest {

    private static final String SOURCE = """
            package demo;
            public class Calculator {
                public int add(int a, int b) { return a + b; }
                public static int twice(int a) { return a * 2; }
                public void reset() { }
            }
            public class OrderRepository {
                public int findTotal(String id) { return 0; }
            }
            public class OrderService {
                public OrderService(OrderRepository r) { }
                public int total(String id) { return 0; }
            }
            """;

    private List<OracleCase> plan() {
        return new CasePlanner().plan(List.of(SOURCE));
    }

    private static List<OracleCase> forMethod(List<OracleCase> all, String className, String methodName) {
        return all.stream()
                .filter(c -> c.getClassName().equals(className) && c.getMethodName().equals(methodName))
                .collect(Collectors.toList());
    }

    @Test
    public void plansCappedCasesWithReceiverAndTypes() {
        List<OracleCase> add = forMethod(plan(), "Calculator", "add");

        assertEquals(12, add.size());
        assertEquals("new Calculator()", add.get(0).getReceiverExpression());
        assertEquals(List.of("int", "int"), add.get(0).getParameterTypes());
        assertEquals(List.of("0", "0"), add.get(0).getArgumentExpressions());
    }

    @Test
    public void staticMethodsHaveNoReceiverAndSkippedMethodsHaveNoCases() {
        List<OracleCase> all = plan();

        List<OracleCase> twice = forMethod(all, "Calculator", "twice");
        assertEquals(5, twice.size());
        assertTrue(twice.get(0).isStatic());
        assertNull(twice.get(0).getReceiverExpression());
        assertTrue(forMethod(all, "Calculator", "reset").isEmpty(), "void methods are skipped");
    }

    @Test
    public void serviceReceiverIsBuiltFromRepository() {
        List<OracleCase> total = forMethod(plan(), "OrderService", "total");

        assertEquals(4, total.size());
        assertEquals("new OrderService(new OrderRepository())", total.get(0).getReceiverExpression());
        assertEquals("null", total.get(0).getArgumentExpressions().get(0));
        assertEquals("(String) null", total.get(0).renderArguments());
    }

    @Test
    public void planIsDeterministic() {
        assertEquals(plan(), plan());
    }
}
