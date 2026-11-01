package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ManifestReaderTest {

    private static final List<DiscoveredMethod> DISCOVERED = List.of(
            DiscoveredMethod.supported("demo", "Calculator", "add", "add(int,int)", false),
            DiscoveredMethod.supported("demo", "Calculator", "echo", "echo(String)", true));

    private static final OracleCase ADD = new OracleCase("Calculator", "add", false, "new Calculator()",
            List.of("int", "int"), List.of("1", "2"));
    private static final OracleCase ECHO = new OracleCase("Calculator", "echo", true, null,
            List.of("String"), List.of("\"a\""));

    @Test
    public void roundTripsHashesExpectedValuesAndTestNames() {
        TestEmitter.Result r = new TestEmitter().emit("demo", "rev1", DISCOVERED,
                List.of(ADD),
                List.of(new OracleOutcome(OracleOutcome.Status.RETURNED, "3")),
                Map.of("Calculator\tadd(int,int)", "H1"), null, List.of());

        ManifestReader.Data data = new ManifestReader().read(r.getManifest());

        assertEquals("rev1", data.getRevision());
        assertEquals("H1", data.getMethodHashes().get("Calculator\tadd(int,int)"));
        ManifestReader.Expected expected = data.getExpectedByCase().get(
                MethodKey.ofCase("Calculator", "add(int,int)", List.of("1", "2")));
        assertEquals("RETURN", expected.getKind());
        assertEquals("3", expected.getValue());
        assertEquals(List.of("gen_Calculator_add_01"),
                data.getTestsByMethod().get("Calculator\tadd(int,int)"));
    }

    @Test
    public void readsRetiredEntries() {
        TestEmitter.Result r = new TestEmitter().emit("demo", "rev2", DISCOVERED, List.of(), List.of(),
                Map.of(), null, List.<String[]>of(new String[] {"gen_Calculator_old_01", "rev2"}));

        ManifestReader.Data data = new ManifestReader().read(r.getManifest());

        assertEquals(1, data.getRetired().size());
        assertEquals("gen_Calculator_old_01", data.getRetired().get(0)[0]);
        assertEquals("rev2", data.getRetired().get(0)[1]);
    }

    @Test
    public void decodesUnicodeEscapesWrittenByTheEmitter() {
        TestEmitter.Result r = new TestEmitter().emit("demo", "rev1", DISCOVERED,
                List.of(ECHO),
                List.of(new OracleOutcome(OracleOutcome.Status.RETURNED, "\"\u00e9\"")),
                Map.of(), null, List.of());

        ManifestReader.Data data = new ManifestReader().read(r.getManifest());

        assertEquals("\"\u00e9\"", data.getExpectedByCase().get(
                MethodKey.ofCase("Calculator", "echo(String)", List.of("\"a\""))).getValue());
    }

    @Test
    public void malformedJsonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ManifestReader().read("{\"tests\": ["));
        assertThrows(IllegalArgumentException.class, () -> new ManifestReader().read("[1, 2]"));
    }
}
