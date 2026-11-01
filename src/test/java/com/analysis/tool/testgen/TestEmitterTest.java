package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestEmitterTest {

    private static final String FILE = "src/test/java/rts/generated/CalculatorGeneratedTest.java";

    private static final List<DiscoveredMethod> DISCOVERED = List.of(
            DiscoveredMethod.supported("demo", "Calculator", "add", "add(int,int)", false),
            DiscoveredMethod.skipped("demo", "Calculator", "reset", "reset()", false, "void return type"));

    private static OracleCase instance(String method, List<String> types, List<String> args) {
        return new OracleCase("Calculator", method, false, "new Calculator()", types, args);
    }

    private static OracleOutcome returned(String literal) {
        return new OracleOutcome(OracleOutcome.Status.RETURNED, literal);
    }

    private TestEmitter.Result emit(List<OracleCase> cases, List<OracleOutcome> outcomes) {
        return new TestEmitter().emit("demo", "abc1234", DISCOVERED, cases, outcomes);
    }

    @Test
    public void returnedIntBecomesAssertEqualsWithStableName() {
        TestEmitter.Result r = emit(
                List.of(instance("add", List.of("int", "int"), List.of("1", "2"))),
                List.of(returned("3")));

        String source = r.getFiles().get(FILE);
        assertTrue(source.contains("public void gen_Calculator_add_01() throws Throwable {"), source);
        assertTrue(source.contains("assertEquals(3, new Calculator().add(1, 2));"), source);
        assertTrue(source.contains("package rts.generated;"), source);
        assertTrue(source.contains("import demo.*;"), source);
        assertEquals(1, r.getGeneratedCount());
    }

    @Test
    public void threwBecomesAssertThrows() {
        TestEmitter.Result r = emit(
                List.of(instance("divide", List.of("int", "int"), List.of("1", "0"))),
                List.of(new OracleOutcome(OracleOutcome.Status.THREW, "java.lang.ArithmeticException")));

        assertTrue(r.getFiles().get(FILE).contains(
                "assertThrows(java.lang.ArithmeticException.class, () -> new Calculator().divide(1, 0));"));
        assertTrue(r.getManifest().contains("\"kind\": \"EXCEPTION\""), r.getManifest());
    }

    @Test
    public void doubleBooleanNullAndStringAssertionsAreChosenByLiteral() {
        TestEmitter.Result r = emit(
                List.of(instance("half", List.of("double"), List.of("1.0")),
                        instance("isPositive", List.of("int"), List.of("1")),
                        instance("isPositive", List.of("int"), List.of("-1")),
                        new OracleCase("Calculator", "greet", true, null, List.of("String"), List.of("null")),
                        instance("name", List.of(), List.of())),
                List.of(returned("0.5"), returned("true"), returned("false"),
                        returned("\"Hi null\""), returned("null")));

        String source = r.getFiles().get(FILE);
        assertTrue(source.contains("assertEquals(0.5, (double) (new Calculator().half(1.0)), 0.0);"), source);
        assertTrue(source.contains("assertTrue(new Calculator().isPositive(1));"), source);
        assertTrue(source.contains("assertFalse(new Calculator().isPositive(-1));"), source);
        assertTrue(source.contains("assertEquals(\"Hi null\", Calculator.greet((String) null));"), source);
        assertTrue(source.contains("assertNull(new Calculator().name());"), source);
    }

    @Test
    public void droppedCaseKeepsTheNumberingOfTheOthersAndIsReported() {
        TestEmitter.Result r = emit(
                List.of(instance("add", List.of("int", "int"), List.of("1", "2")),
                        instance("add", List.of("int", "int"), List.of("2", "2"))),
                List.of(new OracleOutcome(OracleOutcome.Status.NONDETERMINISTIC, "run 1 differs"),
                        returned("4")));

        String source = r.getFiles().get(FILE);
        assertTrue(source.contains("gen_Calculator_add_02()"), source);
        assertFalse(source.contains("gen_Calculator_add_01()"), source);
        assertEquals(1, r.getDroppedCount());
        assertTrue(r.getManifest().contains("\"status\": \"NONDETERMINISTIC\""), r.getManifest());
    }

    @Test
    public void methodWithNoUsableCaseIsSkippedWithReasonAndGetsNoFile() {
        TestEmitter.Result r = emit(
                List.of(instance("add", List.of("int", "int"), List.of("1", "2"))),
                List.of(new OracleOutcome(OracleOutcome.Status.TIMED_OUT, "")));

        assertTrue(r.getFiles().isEmpty());
        assertTrue(r.getManifest().contains("\"reason\": \"TIMED_OUT\""), r.getManifest());
    }

    @Test
    public void discoverySkippedMethodIsReportedWithItsReason() {
        TestEmitter.Result r = emit(List.of(), List.of());

        assertTrue(r.getManifest().contains("\"targetSignature\": \"reset()\""), r.getManifest());
        assertTrue(r.getManifest().contains("\"detail\": \"void return type\""), r.getManifest());
        assertEquals(1, r.getSkippedCount());
    }

    @Test
    public void sameInputGivesByteIdenticalOutput() {
        List<OracleCase> cases = List.of(instance("add", List.of("int", "int"), List.of("1", "2")));
        List<OracleOutcome> outcomes = List.of(returned("3"));

        TestEmitter.Result a = emit(cases, outcomes);
        TestEmitter.Result b = emit(cases, outcomes);

        assertEquals(a.getManifest(), b.getManifest());
        assertEquals(a.getFiles(), b.getFiles());
    }

    @Test
    public void mismatchedCasesAndOutcomesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> emit(
                List.of(instance("add", List.of("int", "int"), List.of("1", "2"))), List.of()));
    }

    @Test
    public void jsonQuotingEscapesSpecialAndNonAsciiCharacters() {
        assertEquals("\"a\\\"b\\\\c\\n\\u00e9\"", TestEmitter.q("a\"b\\c\n\u00e9"));
    }
}
