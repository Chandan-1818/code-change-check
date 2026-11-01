package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G6 rules on synthetic outcomes (no JVM): each outcome is computed from the
 * case's own arguments by a Java lambda that mirrors the method body, so the
 * pinned-oracle logic is exercised without running the capture.
 */
public class IncrementalGeneratorTest {

    private static final String FILE = "src/test/java/rts/generated/CalculatorGeneratedTest.java";
    private static final String ADD = "Calculator\tadd(int,int)";
    private static final String SUB = "Calculator\tsub(int,int)";
    private static final String FIRST = "Calculator\tfirst(int,int)";
    private static final String MUL = "Calculator\tmul(int,int)";
    private static final String ZERO = "Calculator\tzero(int,int)";

    private static final String V1 = "package demo;\n"
            + "public class Calculator {\n"
            + "    public int add(int a, int b) { return a + b; }\n"
            + "    public int sub(int a, int b) { return a - b; }\n"
            + "    public int first(int a, int b) { return a; }\n"
            + "    public int zero(int a, int b) { return 0; }\n"
            + "}\n";

    // add: refactor (same behaviour); sub: behaviour change; first: removed; mul: added
    private static final String V2 = "package demo;\n"
            + "public class Calculator {\n"
            + "    public int add(int a, int b) { return b + a; }\n"
            + "    public int sub(int a, int b) { return a - b - 1; }\n"
            + "    public int zero(int a, int b) { return 0; }\n"
            + "    public int mul(int a, int b) { return a * b; }\n"
            + "}\n";

    private static final Map<String, IntBinaryOperator> V1_OPS = Map.of(
            "add", (a, b) -> a + b, "sub", (a, b) -> a - b, "first", (a, b) -> a, "zero", (a, b) -> 0);
    private static final Map<String, IntBinaryOperator> V2_OPS = Map.of(
            "add", (a, b) -> a + b, "sub", (a, b) -> a - b - 1, "zero", (a, b) -> 0, "mul", (a, b) -> a * b);

    private static List<OracleOutcome> run(List<OracleCase> cases, Map<String, IntBinaryOperator> ops) {
        List<OracleOutcome> outcomes = new ArrayList<>();
        for (OracleCase c : cases) {
            int a = Integer.parseInt(c.getArgumentExpressions().get(0));
            int b = Integer.parseInt(c.getArgumentExpressions().get(1));
            outcomes.add(new OracleOutcome(OracleOutcome.Status.RETURNED,
                    String.valueOf(ops.get(c.getMethodName()).applyAsInt(a, b))));
        }
        return outcomes;
    }

    private static TestEmitter.Result baseline() {
        List<String> sources = List.of(V1);
        List<OracleCase> cases = new CasePlanner().plan(sources);
        return new TestEmitter().emit("demo", "rev1", new TargetDiscoverer().discover(sources), cases,
                run(cases, V1_OPS), new MethodHasher().hash(sources), null, Collections.<String[]>emptyList());
    }

    private static IncrementalGenerator.Outcome update(Set<String> accepted) {
        List<String> sources = List.of(V2);
        List<OracleCase> cases = new CasePlanner().plan(sources);
        return new IncrementalGenerator().apply(baseline().getManifest(), sources, "demo", "rev2", accepted,
                new TargetDiscoverer().discover(sources), cases, run(cases, V2_OPS));
    }

    private static List<String> linesContaining(String source, String text) {
        return source.lines().filter(l -> l.contains(text)).collect(Collectors.toList());
    }

    @Test
    public void classifiesTheTransitionBetweenRevisions() {
        IncrementalPlanner.Plan plan = update(Set.of()).getPlan();

        assertEquals(Set.of(ADD, SUB), plan.getModified());
        assertEquals(Set.of(FIRST), plan.getRemoved());
        assertEquals(Set.of(MUL), plan.getAdded());
        assertEquals(Set.of(ZERO), plan.getUnchanged());
    }

    @Test
    public void behaviourChangeIsFlaggedAndTheOldOracleIsKept() {
        IncrementalGenerator.Outcome outcome = update(Set.of());

        assertEquals(Set.of(SUB), outcome.getBehaviourChanged(),
                "add was only refactored, so only sub changed behaviour");

        String manifest = outcome.getResult().getManifest();
        List<String> flagged = manifest.lines().filter(l -> l.contains("BEHAVIOUR_CHANGED"))
                .collect(Collectors.toList());
        assertFalse(flagged.isEmpty());
        assertTrue(flagged.stream().allMatch(l -> l.contains("gen_Calculator_sub_")), flagged.toString());

        List<String> oldSub = linesContaining(baseline().getFiles().get(FILE), ".sub(");
        List<String> newSub = linesContaining(outcome.getResult().getFiles().get(FILE), ".sub(");
        assertFalse(oldSub.isEmpty());
        assertEquals(oldSub, newSub, "the sub assertions must still carry the old expected values");
    }

    @Test
    public void refactoredMethodWithSameBehaviourStaysActive() {
        String manifest = update(Set.of()).getResult().getManifest();

        List<String> addEntries = manifest.lines().filter(l -> l.contains("\"testMethod\": \"gen_Calculator_add_"))
                .collect(Collectors.toList());
        assertFalse(addEntries.isEmpty());
        assertTrue(addEntries.stream().allMatch(l -> l.contains("\"state\": \"ACTIVE\"")), addEntries.toString());
    }

    @Test
    public void explicitAcceptRerecordsTheOracle() {
        IncrementalGenerator.Outcome outcome = update(Set.of(SUB));

        assertTrue(outcome.getBehaviourChanged().isEmpty());
        assertFalse(outcome.getResult().getManifest().contains("BEHAVIOUR_CHANGED"));
        assertNotEquals(linesContaining(baseline().getFiles().get(FILE), ".sub("),
                linesContaining(outcome.getResult().getFiles().get(FILE), ".sub("),
                "after accept the sub assertions must carry the new expected values");
    }

    @Test
    public void removedMethodIsRetiredAndAddedMethodGetsTests() {
        TestEmitter.Result result = update(Set.of()).getResult();

        assertTrue(result.getManifest().contains("\"testMethod\": \"gen_Calculator_first_01\", "
                + "\"retiredAtRevision\": \"rev2\""), result.getManifest());
        assertFalse(result.getFiles().get(FILE).contains(".first("));
        assertTrue(result.getFiles().get(FILE).contains("gen_Calculator_mul_01"));
    }

    @Test
    public void withoutAPreviousManifestEverythingIsGeneratedAsNew() {
        List<String> sources = List.of(V1);
        List<OracleCase> cases = new CasePlanner().plan(sources);

        IncrementalGenerator.Outcome outcome = new IncrementalGenerator().apply(null, sources, "demo", "rev1",
                Set.of(), new TargetDiscoverer().discover(sources), cases, run(cases, V1_OPS));

        assertEquals(Set.of(ADD, SUB, FIRST, ZERO), outcome.getPlan().getAdded());
        assertTrue(outcome.getBehaviourChanged().isEmpty());
    }
}
