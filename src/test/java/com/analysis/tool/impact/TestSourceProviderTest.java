package com.analysis.tool.impact;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.analysis.tool.graph.MethodNode;
import com.analysis.tool.parser.SourceParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H7: unit tests for the test-source discovery used to decide which test
 * methods exist in a repository. Covers every JUnit 5 test annotation
 * (GAP-08), lifecycle attribution (GAP-09), nested tests (GAP-13), the
 * enclosing-type identifier used for execution, idempotent discovery, and
 * the "tests calling a removed method" query (GAP-11).
 */
class TestSourceProviderTest {

    private final TestSourceProvider provider = new TestSourceProvider();

    private static Set<String> methodNames(Set<MethodNode> nodes) {
        Set<String> names = new LinkedHashSet<>();
        for (MethodNode node : nodes) {
            names.add(node.getMethodName());
        }
        return names;
    }

    @Test
    void discoversEveryJUnit5TestAnnotation() {
        String source = "package demo;\n"
                + "class T {\n"
                + "    @Test void a() {}\n"
                + "    @ParameterizedTest void b() {}\n"
                + "    @RepeatedTest(3) void c() {}\n"
                + "    @TestFactory void d() {}\n"
                + "    @TestTemplate void e() {}\n"
                + "    void helper() {}\n"
                + "}\n";

        TestSourceProvider.Discovery discovery = provider.discover(source);

        assertEquals(Set.of("a", "b", "c", "d", "e"), methodNames(discovery.getTestMethods()),
                "All five JUnit 5 test annotations must be recognised, and the plain helper ignored");
    }

    @Test
    void nestedTestKeepsEnclosingIdentifierAndIsNotDoubleAttributed() {
        String source = "package demo;\n"
                + "class OuterTest {\n"
                + "    @Test void outer() {}\n"
                + "    @Nested class InnerTest {\n"
                + "        @Test void inner() {}\n"
                + "    }\n"
                + "}\n";

        TestSourceProvider.Discovery discovery = provider.discover(source);

        assertEquals(2, discovery.getTestMethods().size());
        assertTrue(discovery.getTestMethods().contains(new MethodNode("OuterTest", "outer")));
        assertTrue(discovery.getTestMethods().contains(new MethodNode("InnerTest", "inner")));
        assertEquals("OuterTest$InnerTest#inner",
                discovery.identifierOf(new MethodNode("InnerTest", "inner")),
                "A nested test keeps the enclosing-type chain in its Surefire identifier");
        assertEquals("OuterTest#outer",
                discovery.identifierOf(new MethodNode("OuterTest", "outer")));
        assertFalse(discovery.getIdentifiers().containsValue("OuterTest#inner"),
                "The nested test must not also be attributed to the enclosing class");
    }

    @Test
    void lifecycleMethodIsAttributedToItsOwnClassAndNestedTests() {
        String source = "package demo;\n"
                + "class OuterTest {\n"
                + "    @BeforeEach void setUp() {}\n"
                + "    @Test void a() {}\n"
                + "    @Nested class InnerTest {\n"
                + "        @Test void b() {}\n"
                + "    }\n"
                + "}\n";

        Map<MethodNode, Set<MethodNode>> attribution =
                provider.discover(source).getLifecycleAttribution();

        assertEquals(
                Set.of(new MethodNode("OuterTest", "a"), new MethodNode("InnerTest", "b")),
                attribution.get(new MethodNode("OuterTest", "setUp")),
                "A lifecycle method runs for its own class's tests and its nested classes' tests");
    }

    @Test
    void lifecycleInsideNestedClassIsAttributedToThatClassOnly() {
        String source = "package demo;\n"
                + "class OuterTest {\n"
                + "    @Test void a() {}\n"
                + "    @Nested class InnerTest {\n"
                + "        @BeforeEach void innerSetup() {}\n"
                + "        @Test void b() {}\n"
                + "    }\n"
                + "}\n";

        Map<MethodNode, Set<MethodNode>> attribution =
                provider.discover(source).getLifecycleAttribution();

        assertEquals(Set.of(new MethodNode("InnerTest", "b")),
                attribution.get(new MethodNode("InnerTest", "innerSetup")),
                "A nested lifecycle method covers the nested class's tests only");
    }

    @Test
    void recognisesAllFourLifecycleAnnotations() {
        String source = "package demo;\n"
                + "class T {\n"
                + "    @BeforeEach void a() {}\n"
                + "    @BeforeAll void b() {}\n"
                + "    @AfterEach void c() {}\n"
                + "    @AfterAll void d() {}\n"
                + "    @Test void t() {}\n"
                + "}\n";

        Map<MethodNode, Set<MethodNode>> attribution =
                provider.discover(source).getLifecycleAttribution();

        assertEquals(4, attribution.size(), "All four lifecycle annotations must be recognised");
        for (Map.Entry<MethodNode, Set<MethodNode>> entry : attribution.entrySet()) {
            assertEquals(Set.of(new MethodNode("T", "t")), entry.getValue(),
                    "Every lifecycle method covers the class's single test");
        }
    }

    @Test
    void duplicateAndBlankSourcesAreIdempotent() {
        String source = "package demo;\n"
                + "class T {\n"
                + "    @Test void a() {}\n"
                + "}\n";

        TestSourceProvider.Discovery discovery = provider.discover(List.of(source, source, "", "   "));

        assertEquals(1, discovery.getTestMethods().size(),
                "The same test supplied twice (and blank sources) must be recorded once");
        assertEquals(1, discovery.getIdentifiers().size());
    }

    @Test
    void nullOrEmptyInputYieldsEmptyDiscovery() {
        assertTrue(provider.discover((List<String>) null).getTestMethods().isEmpty());
        assertTrue(provider.discover(List.of()).getTestMethods().isEmpty());
        assertTrue(provider.discover((String) null).getTestMethods().isEmpty());
    }

    @Test
    void unparsableSourceThrowsTypedParseException() {
        assertThrows(SourceParseException.class,
                () -> provider.discover("public class Broken { void m( }"));
    }

    @Test
    void recognisesAnyJavaFileUnderTheTestSourceRoot() {
        assertTrue(TestSourceProvider.isTestSourceFile("src/test/java/demo/WeirdlyNamed.java"),
                "Any .java file under src/test/java is a test source, whatever its name (GAP-08)");
        assertFalse(TestSourceProvider.isTestSourceFile("src/main/java/demo/Calculator.java"));
        assertFalse(TestSourceProvider.isTestSourceFile("src/test/java/demo/README.md"));
        assertFalse(TestSourceProvider.isTestSourceFile("src/test/java/demo/Foo.txt"));
        assertFalse(TestSourceProvider.isTestSourceFile(null));
    }

    @Test
    void findTestMethodsCallingReturnsOnlyAnnotatedTestCallers() {
        String source = "package demo;\n"
                + "class T {\n"
                + "    @Test void a() { new C().removedMethod(); }\n"
                + "    void helper() { new C().removedMethod(); }\n"
                + "    @Test void b() { new C().other(); }\n"
                + "}\n";

        Set<MethodNode> callers = provider.findTestMethodsCalling(source, Set.of("removedMethod"));

        assertEquals(Set.of(new MethodNode("T", "a")), callers,
                "Only test-annotated methods that call the removed method are returned");
    }

    @Test
    void findTestMethodsCallingIgnoresBlankSourcesAndEmptyCalleeSets() {
        assertTrue(provider.findTestMethodsCalling(null, Set.of("x")).isEmpty());
        assertTrue(provider.findTestMethodsCalling("   ", Set.of("x")).isEmpty());
        assertTrue(provider.findTestMethodsCalling("class T { @Test void a() {} }", Set.of()).isEmpty());
    }
}
