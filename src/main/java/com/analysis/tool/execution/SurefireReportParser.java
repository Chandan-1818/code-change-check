package com.analysis.tool.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/**
 * Reads Maven Surefire XML reports (H8, GAP-16).
 *
 * Each {@code TEST-*.xml} file is parsed into {@link TestResult}s so the
 * execution report can name the individual passed, failed, errored and
 * skipped tests instead of only quoting the aggregate console line.
 *
 * Robustness (H8 requirement C): a missing directory yields an empty list; a
 * malformed, truncated or unreadable report is skipped rather than aborting
 * the whole run, so a partially written report cannot crash the tool.
 */
public final class SurefireReportParser {

    /**
     * Keeps a malformed report from printing a stack trace to stderr; the
     * parse still throws and the file is skipped by the caller.
     */
    private static final ErrorHandler SILENT_HANDLER = new ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) {
        }

        @Override
        public void error(SAXParseException exception) {
        }

        @Override
        public void fatalError(SAXParseException exception) {
        }
    };

    private SurefireReportParser() {
    }

    public static List<TestResult> parse(Path reportsDir) {
        List<TestResult> results = new ArrayList<>();
        if (reportsDir == null || !Files.isDirectory(reportsDir)) {
            return results;
        }
        List<Path> xmlFiles;
        try (Stream<Path> files = Files.list(reportsDir)) {
            xmlFiles = files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("TEST-") && name.endsWith(".xml");
                    })
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return results;
        }
        for (Path xml : xmlFiles) {
            results.addAll(parseFile(xml));
        }
        return results;
    }

    private static List<TestResult> parseFile(Path xml) {
        List<TestResult> results = new ArrayList<>();
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(SILENT_HANDLER);
            document = builder.parse(xml.toFile());
        } catch (Exception e) {
            // Malformed or partially written report: skip this file, keep the rest.
            return results;
        }

        NodeList cases = document.getElementsByTagName("testcase");
        for (int i = 0; i < cases.getLength(); i++) {
            Element testCase = (Element) cases.item(i);
            String rawName = testCase.getAttribute("name");
            String className = testCase.getAttribute("classname");
            if (className == null || className.isEmpty()) {
                className = classFromName(rawName);
            }
            TestResult.Status status;
            if (testCase.getElementsByTagName("skipped").getLength() > 0) {
                status = TestResult.Status.SKIPPED;
            } else if (testCase.getElementsByTagName("error").getLength() > 0) {
                status = TestResult.Status.ERROR;
            } else if (testCase.getElementsByTagName("failure").getLength() > 0) {
                status = TestResult.Status.FAILED;
            } else {
                status = TestResult.Status.PASSED;
            }
            results.add(new TestResult(className, methodName(rawName), status,
                    parseTime(testCase.getAttribute("time"))));
        }
        return results;
    }

    /** JUnit 5 parameterised/repeated cases embed the arguments: strip them. */
    private static String methodName(String raw) {
        int paren = raw.indexOf('(');
        return paren > 0 ? raw.substring(0, paren) : raw;
    }

    private static String classFromName(String raw) {
        int paren = raw.indexOf('(');
        if (paren >= 0 && raw.endsWith(")")) {
            return raw.substring(paren + 1, raw.length() - 1);
        }
        return "";
    }

    private static double parseTime(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
