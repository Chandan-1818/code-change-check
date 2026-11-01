package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inline sources only: no Git, no files, no sample project. */
public class ReceiverBuilderTest {

    private static final String BASIC = """
            package demo;
            public class Calculator { public int add(int a, int b) { return a + b; } }
            public class OrderRepository { public int findTotal(String id) { return 0; } }
            public class OrderService {
                public OrderService(OrderRepository r) { }
                public int total(String id) { return 0; }
            }
            public class Greeter {
                public Greeter(String name, int times) { }
                public String hi() { return "x"; }
            }
            public class TwoCtors {
                public TwoCtors(Missing m) { }
                public TwoCtors(int n) { }
                public int value() { return 1; }
            }
            public class NeedsUnknown { public NeedsUnknown(Missing m) { } public int value() { return 1; } }
            public class PrivateCtor { private PrivateCtor() { } public int value() { return 1; } }
            public class CycleA { public CycleA(CycleB b) { } public int value() { return 1; } }
            public class CycleB { public CycleB(CycleA a) { } public int value() { return 1; } }
            public abstract class Shape { public int area() { return 0; } }
            public interface Marker { }
            class Hidden { public int value() { return 1; } }
            """;

    private static final String CHAIN_OF_FOUR = """
            package demo;
            public class C0 { public C0(C1 c) { } }
            public class C1 { public C1(C2 c) { } }
            public class C2 { public C2(C3 c) { } }
            public class C3 { }
            """;

    private static final String CHAIN_OF_FIVE = """
            package demo;
            public class C0 { public C0(C1 c) { } }
            public class C1 { public C1(C2 c) { } }
            public class C2 { public C2(C3 c) { } }
            public class C3 { public C3(C4 c) { } }
            public class C4 { }
            """;

    @Test
    public void buildsExpressionsForConstructibleClasses() {
        ReceiverBuilder builder = new ReceiverBuilder(List.of(BASIC));

        assertEquals(Optional.of("new Calculator()"), builder.build("Calculator"));
        assertEquals(Optional.of("new OrderService(new OrderRepository())"), builder.build("OrderService"));
        assertEquals(Optional.of("new Greeter(\"\", 0)"), builder.build("Greeter"));
        assertEquals(Optional.of("new TwoCtors(0)"), builder.build("TwoCtors"),
                "the first constructor that can be built is used");
    }

    @Test
    public void returnsEmptyWhenTheClassCannotBeConstructed() {
        ReceiverBuilder builder = new ReceiverBuilder(List.of(BASIC));

        for (String name : List.of("NeedsUnknown", "PrivateCtor", "CycleA", "CycleB",
                "Shape", "Marker", "Hidden", "DoesNotExist")) {
            assertTrue(builder.build(name).isEmpty(), name);
        }
    }

    @Test
    public void depthLimitMatchesTheDiscoverer() {
        assertEquals(Optional.of("new C0(new C1(new C2(new C3())))"),
                new ReceiverBuilder(List.of(CHAIN_OF_FOUR)).build("C0"));
        assertTrue(new ReceiverBuilder(List.of(CHAIN_OF_FIVE)).build("C0").isEmpty(),
                "five levels exceed the depth limit of 3");
    }

    @Test
    public void agreesWithTargetDiscovererOnEveryInstanceMethod() {
        List<String> sources = List.of(BASIC);
        ReceiverBuilder builder = new ReceiverBuilder(sources);

        for (DiscoveredMethod method : new TargetDiscoverer().discover(sources)) {
            if (method.isStatic()) {
                continue;
            }
            boolean notConstructible = method.getReason().startsWith("receiver not constructible");
            if (method.isSupported()) {
                assertTrue(builder.build(method.getClassName()).isPresent(), method.toString());
            } else if (notConstructible) {
                assertTrue(builder.build(method.getClassName()).isEmpty(), method.toString());
            }
        }
    }
}
