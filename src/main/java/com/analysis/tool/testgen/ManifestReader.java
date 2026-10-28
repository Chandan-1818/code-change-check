package com.analysis.tool.testgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Phase 8B / G6: reads a manifest.json written by TestEmitter. Hand-written
 * JSON parser (no library), enough for the manifest format only.
 */
public final class ManifestReader {

    public static final class Expected {
        private final String kind;
        private final String value;

        Expected(String kind, String value) {
            this.kind = kind;
            this.value = value;
        }

        public String getKind() { return kind; }
        public String getValue() { return value; }
    }

    public static final class Data {
        private final String revision;
        private final Map<String, String> methodHashes = new TreeMap<>();
        private final Map<String, Expected> expectedByCase = new TreeMap<>();
        private final Map<String, List<String>> testsByMethod = new TreeMap<>();
        private final List<String[]> retired = new ArrayList<>();

        Data(String revision) {
            this.revision = revision;
        }

        public String getRevision() { return revision; }
        public Map<String, String> getMethodHashes() { return methodHashes; }
        public Map<String, Expected> getExpectedByCase() { return expectedByCase; }
        public Map<String, List<String>> getTestsByMethod() { return testsByMethod; }
        /** Each entry is {testMethod, retiredAtRevision}. */
        public List<String[]> getRetired() { return retired; }

        public static Data empty() {
            return new Data("");
        }
    }

    @SuppressWarnings("unchecked")
    public Data read(String json) {
        Object parsed = new Parser(json).parse();
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("Manifest must be a JSON object");
        }
        Map<String, Object> root = (Map<String, Object>) parsed;
        Data data = new Data(text(root.get("revision")));

        for (Object o : list(root.get("methods"))) {
            Map<String, Object> m = (Map<String, Object>) o;
            data.methodHashes.put(MethodKey.of(text(m.get("targetClass")), text(m.get("targetSignature"))),
                    text(m.get("methodBodyHash")));
        }
        for (Object o : list(root.get("tests"))) {
            Map<String, Object> t = (Map<String, Object>) o;
            String cls = text(t.get("targetClass"));
            String sig = text(t.get("targetSignature"));
            List<String> inputs = new ArrayList<>();
            for (Object input : list(t.get("inputs"))) {
                inputs.add(text(input));
            }
            Map<String, Object> expected = t.get("expected") instanceof Map
                    ? (Map<String, Object>) t.get("expected") : Collections.<String, Object>emptyMap();
            data.expectedByCase.put(MethodKey.ofCase(cls, sig, inputs),
                    new Expected(text(expected.get("kind")), text(expected.get("value"))));
            data.testsByMethod.computeIfAbsent(MethodKey.of(cls, sig), k -> new ArrayList<>())
                    .add(text(t.get("testMethod")));
        }
        for (Object o : list(root.get("retired"))) {
            Map<String, Object> r = (Map<String, Object>) o;
            data.retired.add(new String[] {text(r.get("testMethod")), text(r.get("retiredAtRevision"))});
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value instanceof List ? (List<Object>) value : Collections.<Object>emptyList();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        Object parse() {
            Object value = value();
            skipWhitespace();
            if (i != s.length()) {
                throw error("unexpected trailing text");
            }
            return value;
        }

        private Object value() {
            skipWhitespace();
            if (i >= s.length()) {
                throw error("unexpected end");
            }
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            if (start == i) {
                throw error("unexpected character '" + c + "'");
            }
            return s.substring(start, i);
        }

        private Map<String, Object> object() {
            i++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                i++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = string();
                skipWhitespace();
                expect(':');
                map.put(key, value());
                skipWhitespace();
                char c = next();
                if (c == '}') return map;
                if (c != ',') throw error("expected , or }");
            }
        }

        private List<Object> array() {
            i++;
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                i++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWhitespace();
                char c = next();
                if (c == ']') return list;
                if (c != ',') throw error("expected , or ]");
            }
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return sb.toString();
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw error("bad unicode escape");
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default:
                        throw error("bad escape \\" + e);
                }
            }
        }

        private void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private char peek() {
            if (i >= s.length()) throw error("unexpected end");
            return s.charAt(i);
        }

        private char next() {
            if (i >= s.length()) throw error("unexpected end");
            return s.charAt(i++);
        }

        private void expect(char c) {
            if (next() != c) {
                throw error("expected '" + c + "'");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid manifest JSON at position " + i + ": " + message);
        }
    }
}
