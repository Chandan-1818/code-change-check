package com.analysis.tool.testgen;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs inside the separate JVM started by OracleCapture. The generated class
 * RtsOracleCases calls into this class for every case. Depends on the JDK only.
 *
 * Protocol (one line per case, tab separated):
 *   index STATUS detail
 * where STATUS is one of OracleOutcome.Status. The target's own System.out and
 * System.err are silenced by init() so they cannot corrupt the protocol.
 * Each case runs on a daemon thread with a timeout (system property
 * rts.caseTimeoutMs, default 2000 ms); a stuck thread is abandoned and the
 * process ends with halt() in finish().
 */
public final class OracleRuntime {

    public static final String TIMEOUT_PROPERTY = "rts.caseTimeoutMs";
    public static final long DEFAULT_CASE_TIMEOUT_MS = 2000L;

    public interface Maker<R> {
        R make() throws Throwable;
    }

    public interface Invoker<R> {
        Object invoke(R receiver) throws Throwable;
    }

    public interface StaticInvoker {
        Object invoke() throws Throwable;
    }

    private static final class Result {
        final String status;
        final String detail;

        Result(String status, String detail) {
            this.status = status;
            this.detail = detail;
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "rts-oracle-case");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile PrintStream protocol = System.out;

    private OracleRuntime() {
    }

    /** Keeps the real stdout for the protocol and silences stdout/stderr for the target code. */
    public static void init() {
        protocol = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
    }

    public static void finish() {
        protocol.flush();
        Runtime.getRuntime().halt(0);
    }

    static void setProtocolForTest(PrintStream stream) {
        protocol = stream;
    }

    public static <R> void instance(int index, Maker<R> maker, Invoker<R> invoker) {
        report(index, run(() -> {
            R receiver;
            try {
                receiver = maker.make();
            } catch (Throwable t) {
                return new Result("CONSTRUCTION_FAILED", t.getClass().getName());
            }
            Object value;
            try {
                value = invoker.invoke(receiver);
            } catch (Throwable t) {
                return new Result("THREW", t.getClass().getName());
            }
            return valueResult(value);
        }));
    }

    public static void staticCall(int index, StaticInvoker invoker) {
        report(index, run(() -> {
            Object value;
            try {
                value = invoker.invoke();
            } catch (Throwable t) {
                return new Result("THREW", t.getClass().getName());
            }
            return valueResult(value);
        }));
    }

    private static Result valueResult(Object value) {
        try {
            return new Result("RETURNED", literal(value));
        } catch (IllegalArgumentException e) {
            return new Result("UNSUPPORTED_VALUE", value.getClass().getName());
        }
    }

    private static Result run(Callable<Result> task) {
        Future<Result> future = EXECUTOR.submit(task);
        try {
            return future.get(Long.getLong(TIMEOUT_PROPERTY, DEFAULT_CASE_TIMEOUT_MS), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new Result("TIMED_OUT", "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result("TIMED_OUT", "interrupted");
        } catch (ExecutionException e) {
            return new Result("THREW", e.getCause().getClass().getName());
        }
    }

    private static void report(int index, Result result) {
        protocol.print(index + "\t" + result.status + "\t" + result.detail + "\n");
        protocol.flush();
    }

    /**
     * The value as a Java source expression that evaluates to an equal value.
     * ASCII only, no raw tabs or newlines.
     *
     * @throws IllegalArgumentException for a type that is not a supported value type
     */
    public static String literal(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            StringBuilder sb = new StringBuilder("\"");
            for (char c : ((String) value).toCharArray()) {
                sb.append(escape(c, '"'));
            }
            return sb.append('"').toString();
        }
        if (value instanceof Character) {
            return "'" + escape((Character) value, '\'') + "'";
        }
        if (value instanceof Long) {
            return value + "L";
        }
        if (value instanceof Short) {
            return "(short) " + value;
        }
        if (value instanceof Byte) {
            return "(byte) " + value;
        }
        if (value instanceof Float) {
            float f = (Float) value;
            if (Float.isNaN(f)) return "Float.NaN";
            if (f == Float.POSITIVE_INFINITY) return "Float.POSITIVE_INFINITY";
            if (f == Float.NEGATIVE_INFINITY) return "Float.NEGATIVE_INFINITY";
            return f + "f";
        }
        if (value instanceof Double) {
            double d = (Double) value;
            if (Double.isNaN(d)) return "Double.NaN";
            if (d == Double.POSITIVE_INFINITY) return "Double.POSITIVE_INFINITY";
            if (d == Double.NEGATIVE_INFINITY) return "Double.NEGATIVE_INFINITY";
            return Double.toString(d);
        }
        if (value instanceof Integer || value instanceof Boolean) {
            return value.toString();
        }
        throw new IllegalArgumentException("Unsupported value type: " + value.getClass().getName());
    }

    private static String escape(char c, char quote) {
        switch (c) {
            case '\\': return "\\\\";
            case '\n': return "\\n";
            case '\r': return "\\r";
            case '\t': return "\\t";
            case '\b': return "\\b";
            case '\f': return "\\f";
            default:
                break;
        }
        if (c == quote) {
            return "\\" + c;
        }
        if (c >= 32 && c < 127) {
            return String.valueOf(c);
        }
        // Octal escapes below 256 and \\u escapes above: a \\u escape for a
        // quote, backslash or line break would be processed before lexing.
        if (c < 256) {
            return String.format("\\%03o", (int) c);
        }
        return String.format("\\u%04x", (int) c);
    }
}
