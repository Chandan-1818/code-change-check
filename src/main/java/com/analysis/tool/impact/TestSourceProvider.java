package com.analysis.tool.impact;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.analysis.tool.graph.MethodNode;
import com.analysis.tool.parser.JavaParserAnalyzer;
import com.analysis.tool.parser.SourceParseException;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

/**
 * Discovers the test methods declared in one or more test sources (H7).
 *
 * A method counts as a test when it carries one of the JUnit 5 test
 * annotations in {@link #TEST_ANNOTATIONS}: {@code @Test},
 * {@code @ParameterizedTest}, {@code @RepeatedTest}, {@code @TestFactory} or
 * {@code @TestTemplate}. Lifecycle methods ({@code @BeforeEach},
 * {@code @BeforeAll}, {@code @AfterEach}, {@code @AfterAll}) are also found
 * and attributed to the tests of the class they belong to, so a change that
 * is reachable only through a lifecycle method still selects that class's
 * tests.
 *
 * Only directly declared methods are attributed to a class, so a {@code @Nested}
 * class's tests are no longer also recorded against the enclosing class (the
 * duplicate/wrong-owner problem of the previous top-level-only scan). The
 * Surefire identifier of a nested test keeps the enclosing-type chain
 * ({@code OuterTest$InnerTest#method}).
 *
 * Discovery is naturally idempotent: results are backed by sets keyed on
 * {@code (className, methodName)}, so the same test supplied twice (for
 * example from a mapped test file and from the changed-file list) is recorded
 * once.
 *
 * A source that cannot be parsed raises {@link SourceParseException}; the
 * caller decides how to react (this project falls back to selecting every
 * discovered test).
 */
public class TestSourceProvider {

    /** JUnit 5 annotations that mark a method as a test. */
    public static final Set<String> TEST_ANNOTATIONS = Set.of(
            "Test",
            "ParameterizedTest",
            "RepeatedTest",
            "TestFactory",
            "TestTemplate");

    /** JUnit 5 lifecycle annotations attributed to their class's tests. */
    public static final Set<String> LIFECYCLE_ANNOTATIONS = Set.of(
            "BeforeEach",
            "BeforeAll",
            "AfterEach",
            "AfterAll");

    /** The Maven standard test-source root. */
    public static final String TEST_SOURCE_ROOT = "src/test/java/";

    /**
     * True for a Java test file, whatever its name: any {@code .java} file
     * under the Maven test-source root. Discovery is therefore not limited to
     * the {@code Foo -> FooTest} naming convention (GAP-08).
     */
    public static boolean isTestSourceFile(String path) {
        return path != null
                && path.startsWith(TEST_SOURCE_ROOT)
                && path.endsWith(".java");
    }

    /**
     * Immutable discovery result: the test methods, their Surefire
     * identifiers, and the lifecycle-to-tests attribution.
     */
    public static final class Discovery {

        private final Set<MethodNode> testMethods;
        private final Map<MethodNode, String> identifiers;
        private final Map<MethodNode, Set<MethodNode>> lifecycleAttribution;

        Discovery(Set<MethodNode> testMethods,
                  Map<MethodNode, String> identifiers,
                  Map<MethodNode, Set<MethodNode>> lifecycleAttribution) {
            this.testMethods = Collections.unmodifiableSet(testMethods);
            this.identifiers = Collections.unmodifiableMap(identifiers);
            this.lifecycleAttribution = Collections.unmodifiableMap(lifecycleAttribution);
        }

        /** Every discovered test method, as a graph node. */
        public Set<MethodNode> getTestMethods() {
            return testMethods;
        }

        /** Test node -> "Outer$Inner#method" identifier (Surefire -Dtest form). */
        public Map<MethodNode, String> getIdentifiers() {
            return identifiers;
        }

        /** The Surefire identifier of a test node, or a class#method fallback. */
        public String identifierOf(MethodNode testMethod) {
            String identifier = identifiers.get(testMethod);
            return identifier != null
                    ? identifier
                    : testMethod.getClassName() + "#" + testMethod.getMethodName();
        }

        /**
         * Lifecycle method node -> the tests of the class it belongs to
         * (including tests of nested classes, matching JUnit's inherited
         * lifecycle semantics).
         */
        public Map<MethodNode, Set<MethodNode>> getLifecycleAttribution() {
            return lifecycleAttribution;
        }
    }

    /**
     * Discovers the tests declared across all given test sources, dropping
     * duplicates and blank sources. A null or empty list yields an empty
     * result.
     */
    public Discovery discover(List<String> testSources) {
        Set<MethodNode> testMethods = new LinkedHashSet<>();
        Map<MethodNode, String> identifiers = new LinkedHashMap<>();
        Map<MethodNode, Set<MethodNode>> lifecycleAttribution = new LinkedHashMap<>();

        if (testSources != null) {
            for (String testSource : testSources) {
                collect(testSource, testMethods, identifiers, lifecycleAttribution);
            }
        }
        return new Discovery(testMethods, identifiers, lifecycleAttribution);
    }

    /** Convenience overload for a single test source. */
    public Discovery discover(String testSource) {
        return discover(testSource == null
                ? Collections.<String>emptyList()
                : Collections.singletonList(testSource));
    }

    /**
     * Returns the test methods whose body calls one of the given method names,
     * used to flag tests that reference a method removed between revisions.
     * Only methods carrying a test annotation are considered.
     */
    public Set<MethodNode> findTestMethodsCalling(String testSource, Set<String> calleeNames) {
        Set<MethodNode> callers = new LinkedHashSet<>();
        if (isBlank(testSource) || calleeNames == null || calleeNames.isEmpty()) {
            return callers;
        }
        for (ClassOrInterfaceDeclaration classDecl
                : JavaParserAnalyzer.parseWithoutComments(testSource)
                        .findAll(ClassOrInterfaceDeclaration.class)) {
            for (MethodDeclaration method : classDecl.getMethods()) {
                if (!hasAnyAnnotation(method, TEST_ANNOTATIONS)) {
                    continue;
                }
                boolean calls = method.findAll(MethodCallExpr.class).stream()
                        .anyMatch(call -> calleeNames.contains(call.getNameAsString()));
                if (calls) {
                    callers.add(new MethodNode(
                            classDecl.getNameAsString(), method.getNameAsString()));
                }
            }
        }
        return callers;
    }

    private void collect(String testSource,
                         Set<MethodNode> testMethods,
                         Map<MethodNode, String> identifiers,
                         Map<MethodNode, Set<MethodNode>> lifecycleAttribution) {

        if (isBlank(testSource)) {
            return;
        }

        List<ClassOrInterfaceDeclaration> classes =
                JavaParserAnalyzer.parseWithoutComments(testSource)
                        .findAll(ClassOrInterfaceDeclaration.class);

        Map<ClassOrInterfaceDeclaration, Set<MethodNode>> ownTests = new IdentityHashMap<>();
        Map<ClassOrInterfaceDeclaration, Set<MethodNode>> ownLifecycle = new IdentityHashMap<>();

        for (ClassOrInterfaceDeclaration classDecl : classes) {
            Set<MethodNode> tests = new LinkedHashSet<>();
            Set<MethodNode> lifecycle = new LinkedHashSet<>();

            for (MethodDeclaration method : classDecl.getMethods()) {
                MethodNode node = new MethodNode(
                        classDecl.getNameAsString(), method.getNameAsString());
                if (hasAnyAnnotation(method, TEST_ANNOTATIONS)) {
                    tests.add(node);
                } else if (hasAnyAnnotation(method, LIFECYCLE_ANNOTATIONS)) {
                    lifecycle.add(node);
                }
            }
            ownTests.put(classDecl, tests);
            ownLifecycle.put(classDecl, lifecycle);
        }

        // A lifecycle method runs for every test declared in its own class and
        // in any class nested inside it, so attribute upwards.
        Map<ClassOrInterfaceDeclaration, Set<MethodNode>> aggregate = new IdentityHashMap<>();
        for (ClassOrInterfaceDeclaration classDecl : classes) {
            aggregate.computeIfAbsent(classDecl, key -> new LinkedHashSet<>())
                    .addAll(ownTests.get(classDecl));
            for (ClassOrInterfaceDeclaration ancestor : ancestors(classDecl)) {
                aggregate.computeIfAbsent(ancestor, key -> new LinkedHashSet<>())
                        .addAll(ownTests.get(classDecl));
            }
        }

        for (ClassOrInterfaceDeclaration classDecl : classes) {
            String qualifiedName = qualifiedName(classDecl);
            for (MethodNode test : ownTests.get(classDecl)) {
                testMethods.add(test);
                identifiers.putIfAbsent(test, qualifiedName + "#" + test.getMethodName());
            }
            for (MethodNode lifecycleMethod : ownLifecycle.get(classDecl)) {
                Set<MethodNode> attributed = lifecycleAttribution
                        .computeIfAbsent(lifecycleMethod, key -> new LinkedHashSet<>());
                attributed.addAll(aggregate.getOrDefault(classDecl, Collections.emptySet()));
            }
        }
    }

    private static Set<ClassOrInterfaceDeclaration> ancestors(ClassOrInterfaceDeclaration classDecl) {
        Set<ClassOrInterfaceDeclaration> ancestors = new LinkedHashSet<>();
        Node current = classDecl.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof ClassOrInterfaceDeclaration) {
                ancestors.add((ClassOrInterfaceDeclaration) current);
            }
            current = current.getParentNode().orElse(null);
        }
        return ancestors;
    }

    private static String qualifiedName(ClassOrInterfaceDeclaration classDecl) {
        Deque<String> names = new ArrayDeque<>();
        names.push(classDecl.getNameAsString());
        Node current = classDecl.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof TypeDeclaration) {
                names.push(((TypeDeclaration<?>) current).getNameAsString());
            }
            current = current.getParentNode().orElse(null);
        }
        return String.join("$", names);
    }

    private static boolean hasAnyAnnotation(MethodDeclaration method, Set<String> annotations) {
        for (String annotation : annotations) {
            if (method.isAnnotationPresent(annotation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String source) {
        return source == null || source.trim().isEmpty();
    }
}
