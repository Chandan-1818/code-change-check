package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TargetDiscoverer using inline source strings only (no Git, no
 * sample project, no hard-coded paths).
 */
public class TargetDiscovererTest {

    private static final String CALCULATOR =
            "package demo;\n"
          + "public class Calculator {\n"
          + "    public int add(int a, int b) { return a + b; }\n"
          + "    public int negate(int a) { return -a; }\n"
          + "    public static int twice(int a) { return a * 2; }\n"
          + "    public void reset() { }\n"
          + "    public Object raw() { return null; }\n"
          + "    public int sum(int[] values) { return 0; }\n"
          + "    public long stamp() { return System.currentTimeMillis(); }\n"
          + "    public <T> int generic(int a) { return a; }\n"
          + "    int packagePrivate(int a) { return a; }\n"
          + "}\n";

    private static final String ORDERS =
            "package demo;\n"
          + "public class OrderRepository {\n"
          + "    public int findTotal(String id) { return 0; }\n"
          + "}\n"
          + "class Hidden {\n"
          + "    public int value() { return 1; }\n"
          + "}\n";

    private static final String SERVICE =
            "package demo;\n"
          + "public class OrderService {\n"
          + "    private final OrderRepository repository;\n"
          + "    public OrderService(OrderRepository repository) { this.repository = repository; }\n"
          + "    public int total(String id) { return repository.findTotal(id); }\n"
          + "}\n"
          + "public class NeedsUnknown {\n"
          + "    public NeedsUnknown(Missing m) { }\n"
          + "    public int value() { return 1; }\n"
          + "}\n"
          + "public abstract class Shape {\n"
          + "    public int area() { return 0; }\n"
          + "}\n"
          + "public interface Greeter {\n"
          + "    String greet(String name);\n"
          + "}\n";

    private List<DiscoveredMethod> discover() {
        return new TargetDiscoverer().discover(Arrays.asList(CALCULATOR, ORDERS, SERVICE));
    }

    private static DiscoveredMethod find(List<DiscoveredMethod> all, String className, String signature) {
        for (DiscoveredMethod m : all) {
            if (m.getClassName().equals(className) && m.getSignature().equals(signature)) {
                return m;
            }
        }
        throw new AssertionError("Not discovered: " + className + "." + signature + " in " + all);
    }

    private static void assertSkipped(List<DiscoveredMethod> all, String className, String signature,
                                      String reasonFragment) {
        DiscoveredMethod m = find(all, className, signature);
        assertFalse(m.isSupported(), m.toString());
        assertTrue(m.getReason().contains(reasonFragment),
                "Expected reason containing '" + reasonFragment + "' but was: " + m.getReason());
    }

    @Test
    public void shouldSupportSimplePublicMethodsWithPrimitiveTypes() {
        List<DiscoveredMethod> all = discover();

        assertTrue(find(all, "Calculator", "add(int,int)").isSupported());
        assertTrue(find(all, "Calculator", "negate(int)").isSupported());

        DiscoveredMethod twice = find(all, "Calculator", "twice(int)");
        assertTrue(twice.isSupported());
        assertTrue(twice.isStatic());
        assertEquals("demo", twice.getPackageName());
    }

    @Test
    public void shouldSkipUnsupportedMethodsWithAReason() {
        List<DiscoveredMethod> all = discover();

        assertSkipped(all, "Calculator", "reset()", "void return");
        assertSkipped(all, "Calculator", "raw()", "unsupported return type: Object");
        assertSkipped(all, "Calculator", "sum(int[])", "unsupported parameter type: int[]");
        assertSkipped(all, "Calculator", "stamp()", "System.currentTimeMillis");
        assertSkipped(all, "Calculator", "generic(int)", "generic method");
    }

    @Test
    public void shouldIgnoreNonPublicMethodsAndReportNonPublicClasses() {
        List<DiscoveredMethod> all = discover();

        assertTrue(all.stream().noneMatch(m -> m.getMethodName().equals("packagePrivate")),
                "Package-private methods are not candidates");
        assertSkipped(all, "Hidden", "value()", "class is not public");
    }

    @Test
    public void shouldSupportInstanceMethodWhenReceiverIsBuiltFromConstructibleProjectClass() {
        List<DiscoveredMethod> all = discover();

        assertTrue(find(all, "OrderService", "total(String)").isSupported(),
                "OrderService(OrderRepository) should be constructible: OrderRepository has a default constructor");
    }

    @Test
    public void shouldSkipWhenReceiverCannotBeConstructed() {
        assertSkipped(discover(), "NeedsUnknown", "value()", "receiver not constructible");
    }

    @Test
    public void shouldSkipAbstractClassesAndInterfaces() {
        List<DiscoveredMethod> all = discover();

        assertSkipped(all, "Shape", "area()", "class is abstract");
        assertSkipped(all, "Greeter", "greet(String)", "declared in an interface");
    }

    @Test
    public void shouldBeDeterministic() {
        assertEquals(discover(), discover());
    }
}
