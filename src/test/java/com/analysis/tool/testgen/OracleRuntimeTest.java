package com.analysis.tool.testgen;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Runs in the test JVM; no files, no Git, no sample project. */
public class OracleRuntimeTest {

    private ByteArrayOutputStream captured;
    private PrintStream originalOut;

    @BeforeEach
    public void captureProtocol() {
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        OracleRuntime.setProtocolForTest(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    public void restoreProtocol() {
        OracleRuntime.setProtocolForTest(originalOut);
        System.clearProperty(OracleRuntime.TIMEOUT_PROPERTY);
    }

    private String output() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    public void literalsOfNumbersAndBooleans() {
        assertEquals("5", OracleRuntime.literal(5));
        assertEquals("-7", OracleRuntime.literal(-7));
        assertEquals("5L", OracleRuntime.literal(5L));
        assertEquals("(short) -1", OracleRuntime.literal((short) -1));
        assertEquals("(byte) 2", OracleRuntime.literal((byte) 2));
        assertEquals("true", OracleRuntime.literal(true));
        assertEquals("1.5", OracleRuntime.literal(1.5));
        assertEquals("2.5f", OracleRuntime.literal(2.5f));
    }

    @Test
    public void literalsOfCharactersAndStrings() {
        assertEquals("null", OracleRuntime.literal(null));
        assertEquals("'a'", OracleRuntime.literal('a'));
        assertEquals("'\\''", OracleRuntime.literal('\''));
        assertEquals("'\\n'", OracleRuntime.literal('\n'));
        assertEquals("\"Hello a\"", OracleRuntime.literal("Hello a"));
        assertEquals("\"a\\\"b\\\\c\\n\"", OracleRuntime.literal("a\"b\\c\n"));
        assertEquals("\"\\351\"", OracleRuntime.literal("\u00e9"));
        assertEquals("\"\\u20ac\"", OracleRuntime.literal("\u20ac"));
    }

    @Test
    public void literalsOfFloatingPointSpecialValues() {
        assertEquals("Double.NaN", OracleRuntime.literal(Double.NaN));
        assertEquals("Double.POSITIVE_INFINITY", OracleRuntime.literal(Double.POSITIVE_INFINITY));
        assertEquals("Double.NEGATIVE_INFINITY", OracleRuntime.literal(Double.NEGATIVE_INFINITY));
        assertEquals("Float.NaN", OracleRuntime.literal(Float.NaN));
        assertEquals("-0.0", OracleRuntime.literal(-0.0));
    }

    @Test
    public void unsupportedLiteralIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> OracleRuntime.literal(new Object()));
    }

    @Test
    public void recordsReturnedValue() {
        OracleRuntime.instance(0, () -> "abc", s -> s.length());

        assertEquals("0\tRETURNED\t3\n", output());
    }

    @Test
    public void recordsConstructionFailureSeparatelyFromMethodFailure() {
        OracleRuntime.<Object>instance(1, () -> {
            throw new IllegalStateException("no");
        }, r -> 1);

        assertEquals("1\tCONSTRUCTION_FAILED\tjava.lang.IllegalStateException\n", output());
    }

    @Test
    public void recordsThrownException() {
        OracleRuntime.staticCall(2, () -> {
            throw new ArithmeticException("x");
        });

        assertEquals("2\tTHREW\tjava.lang.ArithmeticException\n", output());
    }

    @Test
    public void recordsTimeout() {
        System.setProperty(OracleRuntime.TIMEOUT_PROPERTY, "200");

        OracleRuntime.staticCall(3, () -> {
            Thread.sleep(5000);
            return 1;
        });

        assertEquals("3\tTIMED_OUT\t\n", output());
    }

    @Test
    public void recordsUnsupportedValue() {
        OracleRuntime.staticCall(4, () -> new Object());

        assertEquals("4\tUNSUPPORTED_VALUE\tjava.lang.Object\n", output());
    }
}
